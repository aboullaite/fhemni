package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.UUID;

public record PromiseAssessmentReport(
        UUID id,
        UUID promiseId,
        UUID assessmentId,
        UUID reporterUserId,
        Category category,
        String details,
        String sourceUrl,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    public enum Category {
        FACTUAL_OR_LEGAL_ERROR,
        OUTDATED_OR_MISSING_SOURCE,
        UNCLEAR_REASONING,
        OTHER
    }

    public enum Status {
        OPEN,
        RESOLVED,
        DISMISSED
    }
}
