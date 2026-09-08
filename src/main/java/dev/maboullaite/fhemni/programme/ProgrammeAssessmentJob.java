package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.UUID;

public record ProgrammeAssessmentJob(
        UUID id,
        UUID programmeId,
        Status status,
        String providerMode,
        int totalItems,
        int completedItems,
        int failedItems,
        String currentPromiseSlug,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant finishedAt) {

    public boolean active() {
        return status == Status.QUEUED || status == Status.RUNNING || status == Status.RETRY_WAIT;
    }

    public enum Status {
        QUEUED,
        RUNNING,
        RETRY_WAIT,
        COMPLETED,
        COMPLETED_WITH_ERRORS,
        FAILED
    }
}
