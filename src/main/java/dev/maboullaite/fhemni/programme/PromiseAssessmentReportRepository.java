package dev.maboullaite.fhemni.programme;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Category;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Status;
import org.springframework.dao.DuplicateKeyException;
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

    PromiseAssessmentReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
        int updated = updateExisting(assessmentId, reporterUserId, category, details, sourceUrl, now);
        if (updated == 0) {
            UUID id = UUID.randomUUID();
            try {
                jdbc.sql("""
                                INSERT INTO promise_assessment_reports (
                                    id, promise_id, assessment_id, reporter_user_id, category,
                                    details, source_url, status, created_at, updated_at
                                ) VALUES (
                                    :id, :promiseId, :assessmentId, :reporterUserId, :category,
                                    :details, :sourceUrl, 'OPEN', :createdAt, :updatedAt
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
            } catch (DuplicateKeyException race) {
                updateExisting(assessmentId, reporterUserId, category, details, sourceUrl, now);
            }
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
    void resolveForPromise(UUID promiseId, Instant now) {
        jdbc.sql("""
                        UPDATE promise_assessment_reports
                           SET status = 'RESOLVED', closed_at = :closedAt, updated_at = :updatedAt
                         WHERE promise_id = :promiseId AND status = 'OPEN'
                        """)
                .param("closedAt", utc(now))
                .param("updatedAt", utc(now))
                .param("promiseId", promiseId)
                .update();
    }

    private int updateExisting(
            UUID assessmentId,
            UUID reporterUserId,
            Category category,
            String details,
            String sourceUrl,
            Instant now) {
        return jdbc.sql("""
                        UPDATE promise_assessment_reports
                           SET category = :category, details = :details, source_url = :sourceUrl,
                               status = 'OPEN', closed_at = NULL, updated_at = :updatedAt
                         WHERE assessment_id = :assessmentId AND reporter_user_id = :reporterUserId
                        """)
                .param("category", category.name())
                .param("details", details)
                .param("sourceUrl", sourceUrl, Types.VARCHAR)
                .param("updatedAt", utc(now))
                .param("assessmentId", assessmentId)
                .param("reporterUserId", reporterUserId)
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
