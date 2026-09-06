package dev.maboullaite.fhemni.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VideoSuggestionRepository {

    private static final String COLUMNS = """
            id, youtube_video_id, canonical_url, title, author_name, thumbnail_url,
            status, moderation_status, moderation_reason, submission_count,
            suggested_by_user_id, metadata_checked_at, first_suggested_at, last_suggested_at
            """;

    private final JdbcClient jdbc;

    public VideoSuggestionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SaveResult record(
            String youtubeVideoId,
            String canonicalUrl,
            VideoMetadataGateway.VideoMetadata metadata,
            SuggestionSafetyPolicy.Assessment assessment,
            UUID suggestedByUserId) {
        Objects.requireNonNull(suggestedByUserId, "A suggestion must have a submitter");
        Instant now = Instant.now();
        int updated = incrementExisting(
                youtubeVideoId, canonicalUrl, metadata, assessment, suggestedByUserId, now);
        if (updated == 1) {
            return new SaveResult(false, findByYouTubeId(youtubeVideoId).orElseThrow());
        }

        try {
            jdbc.sql("""
                        INSERT INTO video_suggestions (
                            id, youtube_video_id, canonical_url, title, author_name, thumbnail_url,
                            status, moderation_status, moderation_reason, submission_count,
                            suggested_by_user_id, metadata_checked_at, first_suggested_at, last_suggested_at
                        ) VALUES (
                            :id, :youtubeVideoId, :canonicalUrl, :title, :authorName, :thumbnailUrl,
                            'PENDING', :moderationStatus, :moderationReason, 1,
                            :suggestedByUserId, :metadataCheckedAt, :firstSuggestedAt, :lastSuggestedAt
                        )
                        """)
                    .param("id", UUID.randomUUID())
                    .param("youtubeVideoId", youtubeVideoId)
                    .param("canonicalUrl", canonicalUrl)
                    .param("title", metadata.title())
                    .param("authorName", metadata.authorName())
                    .param("thumbnailUrl", metadata.thumbnailUrl())
                    .param("moderationStatus", assessment.status().name())
                    .param("moderationReason", assessment.reason(), Types.VARCHAR)
                    .param("suggestedByUserId", suggestedByUserId, Types.OTHER)
                    .param("metadataCheckedAt", utc(now))
                    .param("firstSuggestedAt", utc(now))
                    .param("lastSuggestedAt", utc(now))
                    .update();
            return new SaveResult(true, findByYouTubeId(youtubeVideoId).orElseThrow());
        } catch (DuplicateKeyException concurrentSuggestion) {
            incrementExisting(youtubeVideoId, canonicalUrl, metadata, assessment, suggestedByUserId, now);
            return new SaveResult(false, findByYouTubeId(youtubeVideoId).orElseThrow());
        }
    }

    private int incrementExisting(
            String youtubeVideoId,
            String canonicalUrl,
            VideoMetadataGateway.VideoMetadata metadata,
            SuggestionSafetyPolicy.Assessment assessment,
            UUID suggestedByUserId,
            Instant now) {
        return jdbc.sql("""
                        UPDATE video_suggestions
                           SET canonical_url = :canonicalUrl,
                               title = CASE WHEN metadata_checked_at IS NULL THEN :title ELSE title END,
                               author_name = CASE WHEN metadata_checked_at IS NULL THEN :authorName ELSE author_name END,
                               thumbnail_url = CASE WHEN metadata_checked_at IS NULL THEN :thumbnailUrl ELSE thumbnail_url END,
                               moderation_status = CASE WHEN metadata_checked_at IS NULL THEN :moderationStatus ELSE moderation_status END,
                               moderation_reason = CASE WHEN metadata_checked_at IS NULL THEN :moderationReason ELSE moderation_reason END,
                               metadata_checked_at = COALESCE(metadata_checked_at, :metadataCheckedAt),
                               suggested_by_user_id = COALESCE(suggested_by_user_id, :suggestedByUserId),
                               submission_count = submission_count + 1,
                               last_suggested_at = :lastSuggestedAt
                         WHERE youtube_video_id = :youtubeVideoId
                        """)
                .param("canonicalUrl", canonicalUrl)
                .param("title", metadata.title())
                .param("authorName", metadata.authorName())
                .param("thumbnailUrl", metadata.thumbnailUrl())
                .param("moderationStatus", assessment.status().name())
                .param("moderationReason", assessment.reason(), Types.VARCHAR)
                .param("metadataCheckedAt", utc(now))
                .param("suggestedByUserId", suggestedByUserId, Types.OTHER)
                .param("lastSuggestedAt", utc(now))
                .param("youtubeVideoId", youtubeVideoId)
                .update();
    }

    public Optional<VideoSuggestion> findByYouTubeId(String youtubeVideoId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM video_suggestions WHERE youtube_video_id = :youtubeVideoId")
                .param("youtubeVideoId", youtubeVideoId)
                .query(this::map)
                .optional();
    }

    public List<RankedVideoSuggestion> findPending(int limit) {
        return jdbc.sql("""
                        SELECT s.id, s.youtube_video_id, s.canonical_url,
                               s.title, s.author_name, s.thumbnail_url,
                               s.moderation_status, s.moderation_reason, s.submission_count,
                               COALESCE(SUM(v.vote_value), 0) AS vote_score,
                               COALESCE(SUM(CASE WHEN v.vote_value = 1 THEN 1 ELSE 0 END), 0) AS upvotes,
                               COALESCE(SUM(CASE WHEN v.vote_value = -1 THEN 1 ELSE 0 END), 0) AS downvotes,
                               0 AS viewer_vote,
                               submitter.display_name AS suggested_by_display_name,
                               s.last_suggested_at
                          FROM video_suggestions s
                          LEFT JOIN suggestion_votes v ON v.suggestion_id = s.id
                          LEFT JOIN app_users submitter ON submitter.id = s.suggested_by_user_id
                         WHERE s.status = 'PENDING'
                         GROUP BY s.id, s.youtube_video_id, s.canonical_url,
                                  s.title, s.author_name, s.thumbnail_url,
                                  s.moderation_status, s.moderation_reason,
                                  s.submission_count, submitter.display_name, s.last_suggested_at
                         ORDER BY CASE WHEN s.moderation_status = 'REVIEW_REQUIRED' THEN 0 ELSE 1 END,
                                  vote_score DESC, s.submission_count DESC, s.last_suggested_at DESC
                         LIMIT :limit
                        """)
                .param("limit", limit)
                .query(this::mapRanked)
                .list();
    }

    public List<RankedVideoSuggestion> findPendingForViewer(UUID viewerId, int limit) {
        return jdbc.sql("""
                        SELECT s.id, s.youtube_video_id, s.canonical_url,
                               s.title, s.author_name, s.thumbnail_url,
                               s.moderation_status, s.moderation_reason, s.submission_count,
                               COALESCE(SUM(v.vote_value), 0) AS vote_score,
                               COALESCE(SUM(CASE WHEN v.vote_value = 1 THEN 1 ELSE 0 END), 0) AS upvotes,
                               COALESCE(SUM(CASE WHEN v.vote_value = -1 THEN 1 ELSE 0 END), 0) AS downvotes,
                               COALESCE(MAX(CASE WHEN v.user_id = :viewerId THEN v.vote_value END), 0) AS viewer_vote,
                               submitter.display_name AS suggested_by_display_name,
                               s.last_suggested_at
                          FROM video_suggestions s
                          LEFT JOIN suggestion_votes v ON v.suggestion_id = s.id
                          LEFT JOIN app_users submitter ON submitter.id = s.suggested_by_user_id
                         WHERE s.status = 'PENDING' AND s.moderation_status = 'APPROVED'
                         GROUP BY s.id, s.youtube_video_id, s.canonical_url,
                                  s.title, s.author_name, s.thumbnail_url,
                                  s.moderation_status, s.moderation_reason,
                                  s.submission_count, submitter.display_name, s.last_suggested_at
                         ORDER BY vote_score DESC, s.submission_count DESC, s.last_suggested_at DESC
                         LIMIT :limit
                        """)
                .param("viewerId", viewerId)
                .param("limit", limit)
                .query(this::mapRanked)
                .list();
    }

    public List<RankedVideoSuggestion> findPendingPublic(int limit) {
        return jdbc.sql("""
                        SELECT s.id, s.youtube_video_id, s.canonical_url,
                               s.title, s.author_name, s.thumbnail_url,
                               s.moderation_status, s.moderation_reason, s.submission_count,
                               COALESCE(SUM(v.vote_value), 0) AS vote_score,
                               COALESCE(SUM(CASE WHEN v.vote_value = 1 THEN 1 ELSE 0 END), 0) AS upvotes,
                               COALESCE(SUM(CASE WHEN v.vote_value = -1 THEN 1 ELSE 0 END), 0) AS downvotes,
                               0 AS viewer_vote,
                               submitter.display_name AS suggested_by_display_name,
                               s.last_suggested_at
                          FROM video_suggestions s
                          LEFT JOIN suggestion_votes v ON v.suggestion_id = s.id
                          LEFT JOIN app_users submitter ON submitter.id = s.suggested_by_user_id
                         WHERE s.status = 'PENDING' AND s.moderation_status = 'APPROVED'
                         GROUP BY s.id, s.youtube_video_id, s.canonical_url,
                                  s.title, s.author_name, s.thumbnail_url,
                                  s.moderation_status, s.moderation_reason,
                                  s.submission_count, submitter.display_name, s.last_suggested_at
                         ORDER BY vote_score DESC, s.submission_count DESC, s.last_suggested_at DESC
                         LIMIT :limit
                        """)
                .param("limit", limit)
                .query(this::mapRanked)
                .list();
    }

    public void setVote(UUID suggestionId, UUID userId, int value) {
        if (value != -1 && value != 1) {
            throw new IllegalArgumentException("A suggestion vote must be either 1 or -1.");
        }
        boolean pending = jdbc.sql("""
                        SELECT COUNT(*) FROM video_suggestions
                         WHERE id = :suggestionId
                           AND status = 'PENDING'
                           AND moderation_status = 'APPROVED'
                        """)
                .param("suggestionId", suggestionId)
                .query(Integer.class)
                .single() > 0;
        if (!pending) {
            throw new NoSuchElementException("This pending suggestion was not found.");
        }

        Instant now = Instant.now();
        int updated = updateVote(suggestionId, userId, value, now);
        if (updated == 1) {
            return;
        }
        try {
            jdbc.sql("""
                            INSERT INTO suggestion_votes
                                (suggestion_id, user_id, vote_value, created_at, updated_at)
                            VALUES
                                (:suggestionId, :userId, :value, :createdAt, :updatedAt)
                            """)
                    .param("suggestionId", suggestionId)
                    .param("userId", userId)
                    .param("value", value)
                    .param("createdAt", utc(now))
                    .param("updatedAt", utc(now))
                    .update();
        } catch (DuplicateKeyException concurrentVote) {
            updateVote(suggestionId, userId, value, now);
        }
    }

    private int updateVote(UUID suggestionId, UUID userId, int value, Instant now) {
        return jdbc.sql("""
                        UPDATE suggestion_votes
                           SET vote_value = :value, updated_at = :updatedAt
                         WHERE suggestion_id = :suggestionId AND user_id = :userId
                        """)
                .param("value", value)
                .param("updatedAt", utc(now))
                .param("suggestionId", suggestionId)
                .param("userId", userId)
                .update();
    }

    public void dismiss(UUID id) {
        int updated = jdbc.sql("""
                        UPDATE video_suggestions
                           SET status = 'DISMISSED'
                         WHERE id = :id AND status = 'PENDING'
                        """)
                .param("id", id)
                .update();
        if (updated == 0) {
            throw new NoSuchElementException("This pending suggestion was not found.");
        }
    }

    public void approve(UUID id) {
        int updated = jdbc.sql("""
                        UPDATE video_suggestions
                           SET moderation_status = 'APPROVED', moderation_reason = NULL
                         WHERE id = :id
                           AND status = 'PENDING'
                           AND moderation_status = 'REVIEW_REQUIRED'
                        """)
                .param("id", id)
                .update();
        if (updated == 0) {
            throw new NoSuchElementException("This suggestion does not require moderation.");
        }
    }

    public List<VideoSuggestion> findPendingWithoutMetadata(int limit) {
        return jdbc.sql("""
                        SELECT %s
                          FROM video_suggestions
                         WHERE status = 'PENDING' AND metadata_checked_at IS NULL
                         ORDER BY last_suggested_at DESC
                         LIMIT :limit
                        """.formatted(COLUMNS))
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    public void updateMetadata(
            UUID id,
            VideoMetadataGateway.VideoMetadata metadata,
            SuggestionSafetyPolicy.Assessment assessment) {
        jdbc.sql("""
                        UPDATE video_suggestions
                           SET title = :title,
                               author_name = :authorName,
                               thumbnail_url = :thumbnailUrl,
                               moderation_status = :moderationStatus,
                               moderation_reason = :moderationReason,
                               metadata_checked_at = :metadataCheckedAt
                         WHERE id = :id AND status = 'PENDING'
                        """)
                .param("title", metadata.title())
                .param("authorName", metadata.authorName())
                .param("thumbnailUrl", metadata.thumbnailUrl())
                .param("moderationStatus", assessment.status().name())
                .param("moderationReason", assessment.reason(), Types.VARCHAR)
                .param("metadataCheckedAt", utc(Instant.now()))
                .param("id", id)
                .update();
    }

    public void markAcceptedByYouTubeId(String youtubeVideoId) {
        jdbc.sql("""
                        UPDATE video_suggestions
                           SET status = 'ACCEPTED'
                         WHERE youtube_video_id = :youtubeVideoId
                        """)
                .param("youtubeVideoId", youtubeVideoId)
                .update();
    }

    private VideoSuggestion map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new VideoSuggestion(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("youtube_video_id"),
                resultSet.getString("canonical_url"),
                resultSet.getString("title"),
                resultSet.getString("author_name"),
                resultSet.getString("thumbnail_url"),
                VideoSuggestionStatus.valueOf(resultSet.getString("status")),
                SuggestionModerationStatus.valueOf(resultSet.getString("moderation_status")),
                resultSet.getString("moderation_reason"),
                resultSet.getInt("submission_count"),
                resultSet.getObject("suggested_by_user_id", UUID.class),
                nullableInstant(resultSet, "metadata_checked_at"),
                instant(resultSet, "first_suggested_at"),
                instant(resultSet, "last_suggested_at"));
    }

    private RankedVideoSuggestion mapRanked(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RankedVideoSuggestion(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("youtube_video_id"),
                resultSet.getString("canonical_url"),
                resultSet.getString("title"),
                resultSet.getString("author_name"),
                resultSet.getString("thumbnail_url"),
                SuggestionModerationStatus.valueOf(resultSet.getString("moderation_status")),
                resultSet.getString("moderation_reason"),
                resultSet.getInt("submission_count"),
                resultSet.getInt("vote_score"),
                resultSet.getInt("upvotes"),
                resultSet.getInt("downvotes"),
                resultSet.getInt("viewer_vote"),
                firstName(resultSet.getString("suggested_by_display_name")),
                instant(resultSet, "last_suggested_at"));
    }

    private static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return displayName.strip().split("\\s+", 2)[0];
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record SaveResult(boolean created, VideoSuggestion suggestion) {
    }
}
