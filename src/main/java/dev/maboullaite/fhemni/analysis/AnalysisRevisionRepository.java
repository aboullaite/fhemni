package dev.maboullaite.fhemni.analysis;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.VideoReport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AnalysisRevisionRepository {

    private static final String REVISION_COLUMNS = """
            ar.id, ar.youtube_video_id, ar.video_url, ar.output_language,
            ar.status, ar.progress, ar.progress_message, ar.demo,
            ar.model, ar.prompt_version, ar.fact_check_model, ar.fact_check_prompt_version,
            ar.provider_interaction_id,
            ar.report_json, ar.error, ar.created_at,
            CASE WHEN cv.published_analysis_id = ar.id
                       AND cv.status = 'PUBLISHED'
                       AND cv.listed = TRUE
                 THEN TRUE ELSE FALSE END AS is_published,
            CASE WHEN cv.published_analysis_id = ar.id THEN cv.slug ELSE NULL END AS catalog_slug
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AnalysisRevisionRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void create(
            AnalysisSnapshot snapshot,
            String model,
            String promptVersion,
            String factCheckModel,
            String factCheckPromptVersion) {
        jdbc.sql("""
                        INSERT INTO analysis_revisions (
                            id, youtube_video_id, video_url, output_language,
                            status, progress, progress_message, demo,
                            model, prompt_version, fact_check_model, fact_check_prompt_version,
                            created_at, updated_at
                        ) VALUES (
                            :id, :youtubeVideoId, :videoUrl, :outputLanguage,
                            :status, :progress, :progressMessage, :demo,
                            :model, :promptVersion, :factCheckModel, :factCheckPromptVersion,
                            :createdAt, :updatedAt
                        )
                        """)
                .param("id", snapshot.id())
                .param("youtubeVideoId", snapshot.videoId())
                .param("videoUrl", snapshot.videoUrl())
                .param("outputLanguage", snapshot.language().name())
                .param("status", snapshot.status().name())
                .param("progress", snapshot.progress())
                .param("progressMessage", snapshot.progressMessage())
                .param("demo", snapshot.demo())
                .param("model", normalized(model))
                .param("promptVersion", normalized(promptVersion))
                .param("factCheckModel", normalized(factCheckModel))
                .param("factCheckPromptVersion", normalized(factCheckPromptVersion))
                .param("createdAt", utc(snapshot.createdAt()))
                .param("updatedAt", utc(snapshot.createdAt()))
                .update();
    }

    public void updateProgress(UUID id, AnalysisStatus status, int progress, String message) {
        jdbc.sql("""
                        UPDATE analysis_revisions
                           SET status = :status,
                               progress = :progress,
                               progress_message = :message,
                               updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .param("status", status.name())
                .param("progress", progress)
                .param("message", message)
                .param("updatedAt", utc(Instant.now()))
                .param("id", id)
                .update();
    }

    public void complete(UUID id, VideoReport report, String interactionId) {
        Instant now = Instant.now();
        jdbc.sql("""
                        UPDATE analysis_revisions
                           SET status = 'COMPLETED',
                               progress = 100,
                               progress_message = 'Analysis complete',
                               provider_interaction_id = :interactionId,
                               report_json = :reportJson,
                               error = NULL,
                               completed_at = :completedAt,
                               updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .param("interactionId", interactionId, Types.VARCHAR)
                .param("reportJson", writeReport(report), Types.LONGVARCHAR)
                .param("completedAt", utc(now))
                .param("updatedAt", utc(now))
                .param("id", id)
                .update();
    }

    public void fail(UUID id, String error) {
        jdbc.sql("""
                        UPDATE analysis_revisions
                           SET status = 'FAILED',
                               progress_message = 'Analysis failed',
                               error = :error,
                               updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .param("error", error, Types.LONGVARCHAR)
                .param("updatedAt", utc(Instant.now()))
                .param("id", id)
                .update();
    }

    public int failInterrupted() {
        Instant now = Instant.now();
        return jdbc.sql("""
                        UPDATE analysis_revisions
                           SET status = 'FAILED',
                               progress_message = 'Analysis interrupted',
                               error = 'The application restarted before this analysis finished. Start a new analysis to retry.',
                               updated_at = :updatedAt
                         WHERE status IN ('QUEUED', 'ANALYZING', 'FACT_CHECKING')
                        """)
                .param("updatedAt", utc(now))
                .update();
    }

    public Optional<StoredRevision> find(UUID id) {
        return baseSelect("WHERE ar.id = :id")
                .param("id", id)
                .query(this::mapRevision)
                .optional();
    }

    public Optional<StoredRevision> findPublished(UUID id) {
        return baseSelect("""
                WHERE ar.id = :id
                  AND cv.published_analysis_id = ar.id
                  AND cv.status = 'PUBLISHED'
                  AND cv.listed = TRUE
                """)
                .param("id", id)
                .query(this::mapRevision)
                .optional();
    }

    public Optional<StoredRevision> findReusable(
            String youtubeVideoId,
            OutputLanguage language,
            String model,
            String promptVersion,
            String factCheckModel,
            String factCheckPromptVersion,
            boolean demo) {
        return baseSelect("""
                WHERE ar.youtube_video_id = :youtubeVideoId
                  AND ar.output_language = :outputLanguage
                  AND ar.model = :model
                  AND ar.prompt_version = :promptVersion
                  AND ar.fact_check_model = :factCheckModel
                  AND ar.fact_check_prompt_version = :factCheckPromptVersion
                  AND ar.demo = :demo
                  AND ar.status = 'COMPLETED'
                  AND ar.report_json IS NOT NULL
                ORDER BY ar.created_at DESC
                LIMIT 1
                """)
                .param("youtubeVideoId", youtubeVideoId)
                .param("outputLanguage", language.name())
                .param("model", normalized(model))
                .param("promptVersion", normalized(promptVersion))
                .param("factCheckModel", normalized(factCheckModel))
                .param("factCheckPromptVersion", normalized(factCheckPromptVersion))
                .param("demo", demo)
                .query(this::mapRevision)
                .optional();
    }

    public boolean hasCompleted(String youtubeVideoId, OutputLanguage language, boolean demo) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                          FROM analysis_revisions
                         WHERE youtube_video_id = :youtubeVideoId
                           AND output_language = :outputLanguage
                           AND demo = :demo
                           AND status = 'COMPLETED'
                           AND report_json IS NOT NULL
                        """)
                .param("youtubeVideoId", youtubeVideoId)
                .param("outputLanguage", language.name())
                .param("demo", demo)
                .query(Long.class)
                .single() > 0;
    }

    public Map<String, RevisionSummary> latestByVideo() {
        List<RevisionSummary> summaries = jdbc.sql("""
                        SELECT ranked.id, ranked.youtube_video_id, ranked.output_language,
                               ranked.status, ranked.created_at,
                               CASE WHEN cv.published_analysis_id = ranked.id
                                          AND cv.status = 'PUBLISHED'
                                    THEN TRUE ELSE FALSE END AS is_published
                          FROM (
                                SELECT ar.id, ar.youtube_video_id, ar.output_language,
                                       ar.status, ar.created_at,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY ar.youtube_video_id
                                           ORDER BY ar.created_at DESC
                                       ) AS row_number
                                  FROM analysis_revisions ar
                               ) ranked
                          LEFT JOIN catalog_videos cv
                            ON cv.youtube_video_id = ranked.youtube_video_id
                         WHERE ranked.row_number = 1
                        """)
                .query((resultSet, rowNumber) -> new RevisionSummary(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("youtube_video_id"),
                        OutputLanguage.valueOf(resultSet.getString("output_language")),
                        AnalysisStatus.valueOf(resultSet.getString("status")),
                        instant(resultSet, "created_at"),
                        resultSet.getBoolean("is_published")))
                .list();
        return summaries.stream().collect(Collectors.toUnmodifiableMap(
                RevisionSummary::youtubeVideoId,
                Function.identity()));
    }

    public PublicationState publication(UUID analysisId) {
        return jdbc.sql("""
                        SELECT cv.slug, cv.published_analysis_id,
                               CASE WHEN cv.id IS NULL THEN FALSE ELSE TRUE END AS catalogued
                          FROM analysis_revisions ar
                          LEFT JOIN catalog_videos cv
                            ON cv.youtube_video_id = ar.youtube_video_id
                         WHERE ar.id = :analysisId
                        """)
                .param("analysisId", analysisId)
                .query((resultSet, rowNumber) -> publicationState(analysisId, resultSet))
                .optional()
                .orElseThrow(() -> new java.util.NoSuchElementException("Analysis not found."));
    }

    @Transactional
    public PublicationState publish(UUID analysisId, UUID administratorId) {
        StoredRevision revision = find(analysisId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Analysis not found."));
        AnalysisSnapshot snapshot = revision.snapshot();
        if (snapshot.status() != AnalysisStatus.COMPLETED || snapshot.report() == null) {
            throw new IllegalStateException("Only a completed analysis can be published.");
        }
        if (snapshot.demo()) {
            throw new IllegalStateException("A demonstration report cannot be published.");
        }

        Instant now = Instant.now();
        int updated = jdbc.sql("""
                        UPDATE catalog_videos
                           SET status = 'PUBLISHED',
                               short_summary = :shortSummary,
                               published_analysis_id = :analysisId,
                               updated_at = :updatedAt
                         WHERE youtube_video_id = :youtubeVideoId
                        """)
                .param("shortSummary", snapshot.report().summary(), Types.LONGVARCHAR)
                .param("analysisId", analysisId)
                .param("updatedAt", utc(now))
                .param("youtubeVideoId", snapshot.videoId())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Add this video to the catalogue before publishing its analysis.");
        }

        jdbc.sql("""
                        UPDATE analysis_revisions
                           SET published_at = COALESCE(published_at, :publishedAt),
                               published_by = COALESCE(published_by, :publishedBy),
                               updated_at = :updatedAt
                         WHERE id = :analysisId
                        """)
                .param("publishedAt", utc(now))
                .param("publishedBy", administratorId)
                .param("updatedAt", utc(now))
                .param("analysisId", analysisId)
                .update();
        return publication(analysisId);
    }

    private JdbcClient.StatementSpec baseSelect(String suffix) {
        return jdbc.sql("""
                SELECT %s
                  FROM analysis_revisions ar
                  LEFT JOIN catalog_videos cv
                    ON cv.published_analysis_id = ar.id
                %s
                """.formatted(REVISION_COLUMNS, suffix));
    }

    private StoredRevision mapRevision(ResultSet resultSet, int rowNumber) throws SQLException {
        VideoReport report = readReport(resultSet.getString("report_json"));
        AnalysisSnapshot snapshot = new AnalysisSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("video_url"),
                resultSet.getString("youtube_video_id"),
                OutputLanguage.valueOf(resultSet.getString("output_language")),
                AnalysisStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("progress"),
                resultSet.getString("progress_message"),
                resultSet.getBoolean("demo"),
                instant(resultSet, "created_at"),
                report,
                resultSet.getString("error"),
                List.of(),
                resultSet.getBoolean("is_published"),
                resultSet.getString("catalog_slug"));
        return new StoredRevision(
                snapshot,
                resultSet.getString("provider_interaction_id"),
                resultSet.getString("model"),
                resultSet.getString("prompt_version"),
                resultSet.getString("fact_check_model"),
                resultSet.getString("fact_check_prompt_version"));
    }

    private PublicationState publicationState(UUID analysisId, ResultSet resultSet) throws SQLException {
        boolean catalogued = resultSet.getBoolean("catalogued");
        UUID publishedAnalysisId = resultSet.getObject("published_analysis_id", UUID.class);
        return new PublicationState(
                analysisId,
                catalogued,
                catalogued && analysisId.equals(publishedAnalysisId),
                resultSet.getString("slug"));
    }

    private String writeReport(VideoReport report) {
        try {
            return mapper.writeValueAsString(report);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not store the completed video report.", exception);
        }
    }

    private VideoReport readReport(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, VideoReport.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("A stored video report could not be read.", exception);
        }
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

    public record StoredRevision(
            AnalysisSnapshot snapshot,
            String interactionId,
            String model,
            String promptVersion,
            String factCheckModel,
            String factCheckPromptVersion) {
    }

    public record RevisionSummary(
            UUID id,
            String youtubeVideoId,
            OutputLanguage language,
            AnalysisStatus status,
            Instant createdAt,
            boolean published) {
    }

    public record PublicationState(
            UUID analysisId,
            boolean catalogued,
            boolean published,
            String catalogSlug) {
    }
}
