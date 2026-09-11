package dev.maboullaite.fhemni.programme;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.sql.DataSource;

import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Category;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PromiseAssessmentReportRepository {

    private static final String COLUMNS = """
            id, promise_id, assessment_id, reporter_user_id, category, details, source_url,
            status, created_at, updated_at, closed_at
            """;

    private final JdbcClient jdbc;
    private final boolean postgres;

    PromiseAssessmentReportRepository(JdbcClient jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        try (var connection = dataSource.getConnection()) {
            this.postgres = connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(Locale.ROOT)
                    .contains("postgresql");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not identify the assessment report database.", exception);
        }
    }

    @Transactional
    PromiseAssessmentReport save(
            UUID promiseId,
            UUID assessmentId,
            UUID reporterUserId,
            Category category,
            String details,
            String sourceUrl,
            Instant now) {
        UUID id = UUID.randomUUID();
        if (postgres) {
            upsertPostgres(id, promiseId, assessmentId, reporterUserId, category, details, sourceUrl, now);
        } else {
            upsertPortable(id, promiseId, assessmentId, reporterUserId, category, details, sourceUrl, now);
        }
        return jdbc.sql("""
                        SELECT %s FROM promise_assessment_reports
                         WHERE assessment_id = :assessmentId AND reporter_user_id = :reporterUserId
                        """.formatted(COLUMNS))
                .param("assessmentId", assessmentId)
                .param("reporterUserId", reporterUserId)
                .query(this::map)
                .single();
    }

    List<PromiseAssessmentReport> openReports() {
        return jdbc.sql("""
                        SELECT %s FROM promise_assessment_reports
                         WHERE status = 'OPEN'
                         ORDER BY created_at
                        """.formatted(COLUMNS))
                .query(this::map)
                .list();
    }

    List<PromiseAssessmentReport> openReports(UUID promiseId) {
        return jdbc.sql("""
                        SELECT %s FROM promise_assessment_reports
                         WHERE promise_id = :promiseId AND status = 'OPEN'
                         ORDER BY created_at
                        """.formatted(COLUMNS))
                .param("promiseId", promiseId)
                .query(this::map)
                .list();
    }

    @Transactional
    void close(UUID reportId, Status status, Instant now) {
        if (status == Status.OPEN) {
            throw new IllegalArgumentException("An open report cannot be closed as OPEN.");
        }
        int updated = jdbc.sql("""
                        UPDATE promise_assessment_reports
                           SET status = :status, closed_at = :closedAt, updated_at = :updatedAt
                         WHERE id = :id AND status = 'OPEN'
                        """)
                .param("status", status.name())
                .param("closedAt", utc(now))
                .param("updatedAt", utc(now))
                .param("id", reportId)
                .update();
        if (updated != 1) {
            throw new java.util.NoSuchElementException("Open assessment report not found.");
        }
    }

    @Transactional
    void resolveForAssessment(UUID assessmentId, Instant now) {
        jdbc.sql("""
                        UPDATE promise_assessment_reports report
                           SET status = 'RESOLVED', closed_at = :closedAt, updated_at = :updatedAt
                         WHERE report.status = 'OPEN'
                           AND EXISTS (
                               SELECT 1
                                 FROM programme_assessment_job_reports captured
                                 JOIN programme_assessment_job_items item
                                   ON item.job_id = captured.job_id
                                WHERE item.generated_assessment_id = :assessmentId
                                  AND captured.report_id = report.id
                                  AND captured.report_updated_at = report.updated_at
                           )
                        """)
                .param("closedAt", utc(now))
                .param("updatedAt", utc(now))
                .param("assessmentId", assessmentId)
                .update();
    }

    private void upsertPostgres(
            UUID id,
            UUID promiseId,
            UUID assessmentId,
            UUID reporterUserId,
            Category category,
            String details,
            String sourceUrl,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO promise_assessment_reports (
                            id, promise_id, assessment_id, reporter_user_id, category,
                            details, source_url, status, created_at, updated_at
                        ) VALUES (
                            :id, :promiseId, :assessmentId, :reporterUserId, :category,
                            :details, :sourceUrl, 'OPEN', :createdAt, :updatedAt
                        )
                        ON CONFLICT (assessment_id, reporter_user_id) DO UPDATE
                           SET category = EXCLUDED.category,
                               details = EXCLUDED.details,
                               source_url = EXCLUDED.source_url,
                               status = 'OPEN',
                               closed_at = NULL,
                               updated_at = EXCLUDED.updated_at
                        """)
                .param("id", id)
                .param("promiseId", promiseId)
                .param("assessmentId", assessmentId)
                .param("reporterUserId", reporterUserId)
                .param("category", category.name())
                .param("details", details)
                .param("sourceUrl", sourceUrl, Types.VARCHAR)
                .param("createdAt", utc(now))
                .param("updatedAt", utc(now))
                .update();
    }

    private void upsertPortable(
            UUID id,
            UUID promiseId,
            UUID assessmentId,
            UUID reporterUserId,
            Category category,
            String details,
            String sourceUrl,
            Instant now) {
        jdbc.sql("""
                        MERGE INTO promise_assessment_reports report
                        USING (VALUES (
                            :id, :promiseId, :assessmentId, :reporterUserId, :category,
                            :details, :sourceUrl, :createdAt, :updatedAt
                        )) incoming (
                            id, promise_id, assessment_id, reporter_user_id, category,
                            details, source_url, created_at, updated_at
                        )
                           ON report.assessment_id = incoming.assessment_id
                          AND report.reporter_user_id = incoming.reporter_user_id
                        WHEN MATCHED THEN UPDATE SET
                            category = incoming.category,
                            details = incoming.details,
                            source_url = incoming.source_url,
                            status = 'OPEN',
                            closed_at = NULL,
                            updated_at = incoming.updated_at
                        WHEN NOT MATCHED THEN INSERT (
                            id, promise_id, assessment_id, reporter_user_id, category,
                            details, source_url, status, created_at, updated_at
                        ) VALUES (
                            incoming.id, incoming.promise_id, incoming.assessment_id,
                            incoming.reporter_user_id, incoming.category, incoming.details,
                            incoming.source_url, 'OPEN', incoming.created_at, incoming.updated_at
                        )
                        """)
                .param("id", id)
                .param("promiseId", promiseId)
                .param("assessmentId", assessmentId)
                .param("reporterUserId", reporterUserId)
                .param("category", category.name())
                .param("details", details)
                .param("sourceUrl", sourceUrl, Types.VARCHAR)
                .param("createdAt", utc(now))
                .param("updatedAt", utc(now))
                .update();
    }

    private PromiseAssessmentReport map(ResultSet rs, int rowNumber) throws SQLException {
        return new PromiseAssessmentReport(
                rs.getObject("id", UUID.class),
                rs.getObject("promise_id", UUID.class),
                rs.getObject("assessment_id", UUID.class),
                rs.getObject("reporter_user_id", UUID.class),
                Category.valueOf(rs.getString("category")),
                rs.getString("details"),
                rs.getString("source_url"),
                Status.valueOf(rs.getString("status")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                nullableInstant(rs, "closed_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
