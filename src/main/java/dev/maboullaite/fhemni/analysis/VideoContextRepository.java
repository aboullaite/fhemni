package dev.maboullaite.fhemni.analysis;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.model.OutputLanguage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VideoContextRepository {

    private final JdbcClient jdbc;

    public VideoContextRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(
            String youtubeVideoId,
            OutputLanguage language,
            String model,
            String promptVersion,
            String credentialVersion,
            String interactionId) {
        String normalizedInteraction = normalized(interactionId);
        if (normalizedInteraction.isBlank()) {
            throw new IllegalArgumentException("A provider interaction ID is required.");
        }
        Instant now = Instant.now();
        int updated = updateExisting(
                youtubeVideoId, language, model, promptVersion, credentialVersion,
                normalizedInteraction, now);
        if (updated == 1) {
            return;
        }
        try {
            jdbc.sql("""
                            INSERT INTO video_contexts (
                                id, youtube_video_id, output_language, model, prompt_version,
                                provider_credential_version, provider_interaction_id,
                                created_at, updated_at
                            ) VALUES (
                                :id, :youtubeVideoId, :outputLanguage, :model, :promptVersion,
                                :credentialVersion, :interactionId,
                                :createdAt, :updatedAt
                            )
                            """)
                    .param("id", UUID.randomUUID())
                    .param("youtubeVideoId", youtubeVideoId)
                    .param("outputLanguage", language.name())
                    .param("model", normalized(model))
                    .param("promptVersion", normalized(promptVersion))
                    .param("credentialVersion", normalized(credentialVersion))
                    .param("interactionId", normalizedInteraction)
                    .param("createdAt", utc(now))
                    .param("updatedAt", utc(now))
                    .update();
        } catch (DuplicateKeyException exception) {
            updateExisting(
                    youtubeVideoId, language, model, promptVersion, credentialVersion,
                    normalizedInteraction, now);
        }
    }

    public Optional<VideoContext> find(
            String youtubeVideoId,
            OutputLanguage language,
            String model,
            String promptVersion,
            String credentialVersion) {
        return jdbc.sql("""
                        SELECT provider_interaction_id, created_at
                          FROM video_contexts
                         WHERE youtube_video_id = :youtubeVideoId
                           AND output_language = :outputLanguage
                           AND model = :model
                           AND prompt_version = :promptVersion
                           AND provider_credential_version = :credentialVersion
                        """)
                .param("youtubeVideoId", youtubeVideoId)
                .param("outputLanguage", language.name())
                .param("model", normalized(model))
                .param("promptVersion", normalized(promptVersion))
                .param("credentialVersion", normalized(credentialVersion))
                .query((resultSet, rowNumber) -> new VideoContext(
                        resultSet.getString("provider_interaction_id"),
                        instant(resultSet, "created_at")))
                .optional();
    }

    public List<PublishedContextMigrationCandidate> findMissingPublished(
            String model,
            String promptVersion,
            String credentialVersion,
            int limit) {
        return jdbc.sql("""
                        SELECT ar.id AS analysis_id,
                               ar.youtube_video_id,
                               ar.video_url,
                               ar.output_language,
                               cv.title
                          FROM catalog_videos cv
                          JOIN analysis_revisions ar
                            ON ar.id = cv.published_analysis_id
                         WHERE cv.status = 'PUBLISHED'
                           AND cv.listed = TRUE
                           AND ar.status = 'COMPLETED'
                           AND ar.demo = FALSE
                           AND ar.report_json IS NOT NULL
                           AND NOT EXISTS (
                                SELECT 1
                                  FROM video_contexts context
                                 WHERE context.youtube_video_id = ar.youtube_video_id
                                   AND context.output_language = ar.output_language
                                   AND context.model = :model
                                   AND context.prompt_version = :promptVersion
                                   AND context.provider_credential_version = :credentialVersion
                           )
                         ORDER BY cv.created_at ASC
                         LIMIT :limit
                        """)
                .param("model", normalized(model))
                .param("promptVersion", normalized(promptVersion))
                .param("credentialVersion", normalized(credentialVersion))
                .param("limit", limit)
                .query((resultSet, rowNumber) -> new PublishedContextMigrationCandidate(
                        resultSet.getObject("analysis_id", UUID.class),
                        resultSet.getString("youtube_video_id"),
                        resultSet.getString("video_url"),
                        resultSet.getString("title"),
                        OutputLanguage.valueOf(resultSet.getString("output_language"))))
                .list();
    }

    public int countMissingPublished(
            String model,
            String promptVersion,
            String credentialVersion) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                          FROM catalog_videos cv
                          JOIN analysis_revisions ar
                            ON ar.id = cv.published_analysis_id
                         WHERE cv.status = 'PUBLISHED'
                           AND cv.listed = TRUE
                           AND ar.status = 'COMPLETED'
                           AND ar.demo = FALSE
                           AND ar.report_json IS NOT NULL
                           AND NOT EXISTS (
                                SELECT 1
                                  FROM video_contexts context
                                 WHERE context.youtube_video_id = ar.youtube_video_id
                                   AND context.output_language = ar.output_language
                                   AND context.model = :model
                                   AND context.prompt_version = :promptVersion
                                   AND context.provider_credential_version = :credentialVersion
                           )
                        """)
                .param("model", normalized(model))
                .param("promptVersion", normalized(promptVersion))
                .param("credentialVersion", normalized(credentialVersion))
                .query(Integer.class)
                .single();
    }

    private int updateExisting(
            String youtubeVideoId,
            OutputLanguage language,
            String model,
            String promptVersion,
            String credentialVersion,
            String interactionId,
            Instant updatedAt) {
        return jdbc.sql("""
                        UPDATE video_contexts
                           SET provider_interaction_id = :interactionId,
                               updated_at = :updatedAt
                         WHERE youtube_video_id = :youtubeVideoId
                           AND output_language = :outputLanguage
                           AND model = :model
                           AND prompt_version = :promptVersion
                           AND provider_credential_version = :credentialVersion
                        """)
                .param("interactionId", interactionId)
                .param("updatedAt", utc(updatedAt))
                .param("youtubeVideoId", youtubeVideoId)
                .param("outputLanguage", language.name())
                .param("model", normalized(model))
                .param("promptVersion", normalized(promptVersion))
                .param("credentialVersion", normalized(credentialVersion))
                .update();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    public record VideoContext(
            String interactionId,
            Instant createdAt) {
    }

    public record PublishedContextMigrationCandidate(
            UUID publishedAnalysisId,
            String youtubeVideoId,
            String videoUrl,
            String title,
            OutputLanguage language) {
    }
}
