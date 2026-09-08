package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.UUID;

public record ProgrammeAssessmentJobItem(
        UUID jobId,
        UUID promiseId,
        String promiseSlug,
        Status status,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        String lastErrorCode,
        String lastErrorMessage) {

    public enum Status {
        PENDING,
        RUNNING,
        RETRY_WAIT,
        COMPLETED,
        FAILED
    }
}
