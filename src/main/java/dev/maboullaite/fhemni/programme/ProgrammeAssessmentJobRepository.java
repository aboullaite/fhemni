package dev.maboullaite.fhemni.programme;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.ProgrammeAssessmentJob.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProgrammeAssessmentJobRepository {

    private static final String JOB_COLUMNS = """
            id, programme_id, status, provider_mode, total_items, completed_items, failed_items,
            current_promise_slug, last_error_code, last_error_message,
            created_at, started_at, updated_at, finished_at
            """;
    private static final String ITEM_COLUMNS = """
            job_id, promise_id, promise_slug, status, attempt_count, max_attempts,
            next_attempt_at, last_error_code, last_error_message
            """;

    private final JdbcClient jdbc;

    public ProgrammeAssessmentJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ProgrammeAssessmentJob create(
            UUID programmeId,
            String providerMode,
            List<PartyPromise> promises,
            int maxAttempts,
            Instant now) {
        UUID jobId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO programme_assessment_jobs (
                            id, programme_id, status, provider_mode, total_items,
                            completed_items, failed_items, active_marker,
                            created_at, updated_at
                        ) VALUES (
                            :id, :programmeId, 'QUEUED', :providerMode, :totalItems,
                            0, 0, TRUE, :createdAt, :updatedAt
                        )
                        """)
                .param("id", jobId)
                .param("programmeId", programmeId)
                .param("providerMode", providerMode)
                .param("totalItems", promises.size())
                .param("createdAt", utc(now))
                .param("updatedAt", utc(now))
                .update();
        for (PartyPromise promise : promises) {
            jdbc.sql("""
                            INSERT INTO programme_assessment_job_items (
                                job_id, promise_id, promise_slug, status, attempt_count,
                                max_attempts, created_at, updated_at
                            ) VALUES (
                                :jobId, :promiseId, :promiseSlug, 'PENDING', 0,
                                :maxAttempts, :createdAt, :updatedAt
                            )
                            """)
                    .param("jobId", jobId)
                    .param("promiseId", promise.id())
                    .param("promiseSlug", promise.slug())
                    .param("maxAttempts", maxAttempts)
                    .param("createdAt", utc(now))
                    .param("updatedAt", utc(now))
                    .update();
        }
        if (promises.isEmpty()) {
            finish(jobId, Status.COMPLETED, now, null, null);
        }
        return find(jobId).orElseThrow();
    }

    public Optional<ProgrammeAssessmentJob> find(UUID jobId) {
        return jdbc.sql("SELECT " + JOB_COLUMNS + " FROM programme_assessment_jobs WHERE id = :id")
                .param("id", jobId)
                .query(this::mapJob)
                .optional();
    }

    public Optional<ProgrammeAssessmentJob> activeForProgramme(UUID programmeId) {
        return jdbc.sql("""
                        SELECT %s FROM programme_assessment_jobs
                         WHERE programme_id = :programmeId AND active_marker = TRUE
                        """.formatted(JOB_COLUMNS))
                .param("programmeId", programmeId)
                .query(this::mapJob)
                .optional();
    }

    public Optional<ProgrammeAssessmentJob> latestForProgramme(UUID programmeId) {
        return jdbc.sql("""
                        SELECT %s FROM programme_assessment_jobs
                         WHERE programme_id = :programmeId
                         ORDER BY created_at DESC LIMIT 1
                        """.formatted(JOB_COLUMNS))
                .param("programmeId", programmeId)
                .query(this::mapJob)
                .optional();
    }

    public Map<UUID, ProgrammeAssessmentJob> latestByProgramme() {
        List<ProgrammeAssessmentJob> jobs = jdbc.sql(
                        "SELECT " + JOB_COLUMNS + " FROM programme_assessment_jobs ORDER BY created_at DESC")
                .query(this::mapJob)
                .list();
        Map<UUID, ProgrammeAssessmentJob> latest = new LinkedHashMap<>();
        jobs.forEach(job -> latest.putIfAbsent(job.programmeId(), job));
        return Map.copyOf(latest);
    }

    public List<UUID> dispatchable(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT job.id
                          FROM programme_assessment_jobs job
                         WHERE job.active_marker = TRUE
                           AND job.status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                           AND (job.lease_until IS NULL OR job.lease_until <= :now)
                           AND EXISTS (
                               SELECT 1 FROM programme_assessment_job_items item
                                WHERE item.job_id = job.id
                                  AND (item.status = 'PENDING'
                                       OR (item.status = 'RETRY_WAIT' AND item.next_attempt_at <= :now))
                           )
                         ORDER BY job.created_at
                         LIMIT :limit
                        """)
                .param("now", utc(now))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    @Transactional
    public void recoverExpiredLeases(Instant now) {
        jdbc.sql("""
                        UPDATE programme_assessment_job_items
                           SET status = 'RETRY_WAIT', next_attempt_at = :now,
                               last_error_code = 'WORKER_INTERRUPTED',
                               last_error_message = 'The previous worker stopped; this batch will resume.',
                               updated_at = :now
                         WHERE status = 'RUNNING'
                           AND job_id IN (
                               SELECT id FROM programme_assessment_jobs
                                WHERE active_marker = TRUE AND status = 'RUNNING'
                                  AND lease_until IS NOT NULL AND lease_until <= :now
                           )
                        """)
                .param("now", utc(now))
                .update();
        jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET status = 'RETRY_WAIT', lock_owner = NULL, lease_until = NULL,
                               current_promise_slug = NULL, last_error_code = 'WORKER_INTERRUPTED',
                               last_error_message = 'The previous worker stopped; this job will resume.',
                               updated_at = :now
                         WHERE active_marker = TRUE AND status = 'RUNNING'
                           AND lease_until IS NOT NULL AND lease_until <= :now
                        """)
                .param("now", utc(now))
                .update();
    }

    @Transactional
    public Optional<Lease> claim(UUID jobId, String owner, Instant now, Instant leaseUntil) {
        int updated = jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET status = 'RUNNING', lock_owner = :owner, lease_until = :leaseUntil,
                               lease_token = lease_token + 1,
                               started_at = COALESCE(started_at, :now), updated_at = :now
                         WHERE id = :id AND active_marker = TRUE
                           AND status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                           AND (lease_until IS NULL OR lease_until <= :now)
                        """)
                .param("owner", owner)
                .param("leaseUntil", utc(leaseUntil))
                .param("now", utc(now))
                .param("id", jobId)
                .update();
        if (updated != 1) {
            return Optional.empty();
        }
        long token = jdbc.sql("SELECT lease_token FROM programme_assessment_jobs WHERE id = :id")
                .param("id", jobId)
                .query(Long.class)
                .single();
        return Optional.of(new Lease(jobId, owner, token));
    }

    public List<ProgrammeAssessmentJobItem> readyItems(UUID jobId, Instant now, int limit) {
        return jdbc.sql("""
                        SELECT %s FROM programme_assessment_job_items
                         WHERE job_id = :jobId
                           AND (status = 'PENDING'
                                OR (status = 'RETRY_WAIT' AND next_attempt_at <= :now))
                         ORDER BY created_at, promise_slug
                         LIMIT :limit
                        """.formatted(ITEM_COLUMNS))
                .param("jobId", jobId)
                .param("now", utc(now))
                .param("limit", limit)
                .query(this::mapItem)
                .list();
    }

    @Transactional
    public void markRunning(Lease lease, List<UUID> promiseIds, String currentSlug, Instant now) {
        if (promiseIds.isEmpty()) {
            return;
        }
        requireOwnedLease(lease, now);
        jdbc.sql("""
                        UPDATE programme_assessment_job_items
                           SET status = 'RUNNING', attempt_count = attempt_count + 1,
                               next_attempt_at = NULL, updated_at = :now
                         WHERE job_id = :jobId AND promise_id IN (:promiseIds)
                           AND status IN ('PENDING', 'RETRY_WAIT')
                           AND EXISTS (
                               SELECT 1 FROM programme_assessment_jobs job
                                WHERE job.id = :jobId AND job.active_marker = TRUE
                                  AND job.lock_owner = :owner AND job.lease_token = :leaseToken
                                  AND job.lease_until > :now
                           )
                        """)
                .param("now", utc(now))
                .param("jobId", lease.jobId())
                .param("owner", lease.owner())
                .param("leaseToken", lease.token())
                .param("promiseIds", promiseIds)
                .update();
        jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET status = 'RUNNING', current_promise_slug = :currentSlug,
                               last_error_code = NULL, last_error_message = NULL, updated_at = :now
                         WHERE id = :jobId
                        """)
                .param("currentSlug", currentSlug)
                .param("now", utc(now))
                .param("jobId", lease.jobId())
                .update();
    }

    @Transactional
    public void completeItems(Lease lease, List<UUID> promiseIds, Instant now) {
        requireOwnedLease(lease, now);
        updateItemStatus(lease, promiseIds, "COMPLETED", now, null, null, null);
        refreshCounts(lease.jobId(), now);
    }

    @Transactional
    public void retryItems(
            Lease lease,
            List<UUID> promiseIds,
            Instant nextAttemptAt,
            String errorCode,
            String message,
            Instant now) {
        requireOwnedLease(lease, now);
        updateItemStatus(lease, promiseIds, "RETRY_WAIT", now, nextAttemptAt, errorCode, message);
        refreshCounts(lease.jobId(), now);
        jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET status = 'RETRY_WAIT', current_promise_slug = NULL,
                               last_error_code = :errorCode, last_error_message = :message,
                               lock_owner = NULL, lease_until = NULL, updated_at = :now
                         WHERE id = :jobId AND active_marker = TRUE
                        """)
                .param("errorCode", errorCode)
                .param("message", message)
                .param("now", utc(now))
                .param("jobId", lease.jobId())
                .update();
    }

    @Transactional
    public void retryItemsWhileRunning(
            Lease lease,
            List<UUID> promiseIds,
            Instant nextAttemptAt,
            String errorCode,
            String message,
            Instant now) {
        requireOwnedLease(lease, now);
        updateItemStatus(lease, promiseIds, "RETRY_WAIT", now, nextAttemptAt, errorCode, message);
        refreshCounts(lease.jobId(), now);
        jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET last_error_code = :errorCode, last_error_message = :message, updated_at = :now
                         WHERE id = :jobId AND active_marker = TRUE
                        """)
                .param("errorCode", errorCode)
                .param("message", message)
                .param("now", utc(now))
                .param("jobId", lease.jobId())
                .update();
    }

    @Transactional
    public void failItems(
            Lease lease,
            List<UUID> promiseIds,
            String errorCode,
            String message,
            Instant now) {
        requireOwnedLease(lease, now);
        updateItemStatus(lease, promiseIds, "FAILED", now, null, errorCode, message);
        refreshCounts(lease.jobId(), now);
    }

    @Transactional
    public void returnToPending(Lease lease, List<UUID> promiseIds, Instant now) {
        requireOwnedLease(lease, now);
        updateItemStatus(lease, promiseIds, "PENDING", now, null, null, null);
    }

    public Counts counts(UUID jobId) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS total,
                               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                               SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                               SUM(CASE WHEN status IN ('PENDING', 'RUNNING', 'RETRY_WAIT') THEN 1 ELSE 0 END) AS remaining,
                               MIN(CASE WHEN status = 'RETRY_WAIT' THEN next_attempt_at END) AS next_attempt_at
                          FROM programme_assessment_job_items WHERE job_id = :jobId
                        """)
                .param("jobId", jobId)
                .query((rs, rowNumber) -> new Counts(
                        rs.getInt("total"), rs.getInt("completed"), rs.getInt("failed"),
                        rs.getInt("remaining"), nullableInstant(rs, "next_attempt_at")))
                .single();
    }

    @Transactional
    public ProgrammeAssessmentJob settle(Lease lease, Instant now) {
        requireOwnedLease(lease, now);
        UUID jobId = lease.jobId();
        Counts counts = counts(jobId);
        if (counts.remaining() == 0) {
            finishOwned(lease, counts.failed() == 0 ? Status.COMPLETED : Status.COMPLETED_WITH_ERRORS,
                    now, null, null);
        } else {
            String nextStatus = counts.nextAttemptAt() == null ? "RUNNING" : "RETRY_WAIT";
            jdbc.sql("""
                            UPDATE programme_assessment_jobs
                               SET status = :status, completed_items = :completed, failed_items = :failed,
                                   current_promise_slug = NULL, lock_owner = NULL, lease_until = NULL,
                                   updated_at = :now
                             WHERE id = :jobId AND active_marker = TRUE
                            """)
                    .param("status", nextStatus)
                    .param("completed", counts.completed())
                    .param("failed", counts.failed())
                    .param("now", utc(now))
                    .param("jobId", jobId)
                    .update();
        }
        return find(jobId).orElseThrow();
    }

    @Transactional
    public void invalidateAndFailJob(UUID jobId, String errorCode, String message, Instant now) {
        jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET status = 'FAILED', active_marker = NULL, current_promise_slug = NULL,
                               last_error_code = :errorCode, last_error_message = :message,
                               lock_owner = NULL, lease_until = NULL, lease_token = lease_token + 1,
                               finished_at = :now, updated_at = :now
                         WHERE id = :jobId
                        """)
                .param("errorCode", errorCode)
                .param("message", message)
                .param("now", utc(now))
                .param("jobId", jobId)
                .update();
        jdbc.sql("""
                        UPDATE programme_assessment_job_items
                           SET status = 'FAILED', last_error_code = :errorCode,
                               last_error_message = :message, updated_at = :now
                         WHERE job_id = :jobId AND status IN ('PENDING', 'RUNNING', 'RETRY_WAIT')
                        """)
                .param("errorCode", errorCode)
                .param("message", message)
                .param("now", utc(now))
                .param("jobId", jobId)
                .update();
        refreshCounts(jobId, now);
    }

    @Transactional
    public void failOwnedJob(Lease lease, String errorCode, String message, Instant now) {
        requireOwnedLease(lease, now);
        UUID jobId = lease.jobId();
        jdbc.sql("""
                        UPDATE programme_assessment_job_items
                           SET status = 'FAILED', last_error_code = :errorCode,
                               last_error_message = :message, updated_at = :now
                         WHERE job_id = :jobId AND status IN ('PENDING', 'RUNNING', 'RETRY_WAIT')
                        """)
                .param("errorCode", errorCode)
                .param("message", message)
                .param("now", utc(now))
                .param("jobId", jobId)
                .update();
        refreshCounts(jobId, now);
        finishOwned(lease, Status.FAILED, now, errorCode, message);
    }

    @Transactional
    public boolean renewLease(Lease lease, Instant now, Instant leaseUntil) {
        return jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET lease_until = :leaseUntil, updated_at = :now
                         WHERE id = :jobId AND active_marker = TRUE AND status = 'RUNNING'
                           AND lock_owner = :owner AND lease_token = :leaseToken
                           AND lease_until > :now
                        """)
                .param("leaseUntil", utc(leaseUntil))
                .param("now", utc(now))
                .param("jobId", lease.jobId())
                .param("owner", lease.owner())
                .param("leaseToken", lease.token())
                .update() == 1;
    }

    void requireOwnedLease(Lease lease, Instant now) {
        boolean owned = jdbc.sql("""
                        SELECT id FROM programme_assessment_jobs
                         WHERE id = :jobId AND active_marker = TRUE AND status = 'RUNNING'
                           AND lock_owner = :owner AND lease_token = :leaseToken
                           AND lease_until > :now
                         FOR UPDATE
                        """)
                .param("jobId", lease.jobId())
                .param("owner", lease.owner())
                .param("leaseToken", lease.token())
                .param("now", utc(now))
                .query(UUID.class)
                .optional()
                .isPresent();
        if (!owned) {
            throw new ProgrammeJobLeaseLostException();
        }
    }

    private void updateItemStatus(
            Lease lease,
            List<UUID> promiseIds,
            String status,
            Instant now,
            Instant nextAttemptAt,
            String errorCode,
            String message) {
        if (promiseIds.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        UPDATE programme_assessment_job_items
                           SET status = :status, next_attempt_at = :nextAttemptAt,
                               last_error_code = :errorCode, last_error_message = :message,
                               completed_at = CASE WHEN :status IN ('COMPLETED', 'FAILED') THEN :now ELSE NULL END,
                               updated_at = :now
                         WHERE job_id = :jobId AND promise_id IN (:promiseIds)
                           AND EXISTS (
                               SELECT 1 FROM programme_assessment_jobs job
                                WHERE job.id = :jobId AND job.active_marker = TRUE
                                  AND job.lock_owner = :owner AND job.lease_token = :leaseToken
                                  AND job.lease_until > :now
                           )
                        """)
                .param("status", status)
                .param("nextAttemptAt", nextAttemptAt == null ? null : utc(nextAttemptAt), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("errorCode", errorCode, Types.VARCHAR)
                .param("message", message, Types.VARCHAR)
                .param("now", utc(now))
                .param("jobId", lease.jobId())
                .param("owner", lease.owner())
                .param("leaseToken", lease.token())
                .param("promiseIds", promiseIds)
                .update();
    }

    private void refreshCounts(UUID jobId, Instant now) {
        Counts counts = counts(jobId);
        jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET completed_items = :completed, failed_items = :failed, updated_at = :now
                         WHERE id = :jobId
                        """)
                .param("completed", counts.completed())
                .param("failed", counts.failed())
                .param("now", utc(now))
                .param("jobId", jobId)
                .update();
    }

    private void finishOwned(Lease lease, Status status, Instant now, String errorCode, String message) {
        finish(lease.jobId(), status, now, errorCode, message);
    }

    private void finish(UUID jobId, Status status, Instant now, String errorCode, String message) {
        jdbc.sql("""
                        UPDATE programme_assessment_jobs
                           SET status = :status, active_marker = NULL, current_promise_slug = NULL,
                               last_error_code = :errorCode, last_error_message = :message,
                               lock_owner = NULL, lease_until = NULL, finished_at = :now, updated_at = :now
                         WHERE id = :jobId
                        """)
                .param("status", status.name())
                .param("errorCode", errorCode, Types.VARCHAR)
                .param("message", message, Types.VARCHAR)
                .param("now", utc(now))
                .param("jobId", jobId)
                .update();
    }

    private ProgrammeAssessmentJob mapJob(ResultSet rs, int rowNumber) throws SQLException {
        return new ProgrammeAssessmentJob(
                rs.getObject("id", UUID.class), rs.getObject("programme_id", UUID.class),
                Status.valueOf(rs.getString("status")), rs.getString("provider_mode"),
                rs.getInt("total_items"), rs.getInt("completed_items"), rs.getInt("failed_items"),
                rs.getString("current_promise_slug"), rs.getString("last_error_code"),
                rs.getString("last_error_message"), instant(rs, "created_at"),
                nullableInstant(rs, "started_at"), instant(rs, "updated_at"),
                nullableInstant(rs, "finished_at"));
    }

    private ProgrammeAssessmentJobItem mapItem(ResultSet rs, int rowNumber) throws SQLException {
        return new ProgrammeAssessmentJobItem(
                rs.getObject("job_id", UUID.class), rs.getObject("promise_id", UUID.class),
                rs.getString("promise_slug"), ProgrammeAssessmentJobItem.Status.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                nullableInstant(rs, "next_attempt_at"), rs.getString("last_error_code"),
                rs.getString("last_error_message"));
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

    public record Counts(int total, int completed, int failed, int remaining, Instant nextAttemptAt) {
    }

    public record Lease(UUID jobId, String owner, long token) {
    }
}
