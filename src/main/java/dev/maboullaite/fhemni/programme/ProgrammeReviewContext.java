package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProgrammeReviewContext(String prompt, List<ReportSnapshot> reports) {

    public ProgrammeReviewContext {
        reports = reports == null ? List.of() : List.copyOf(reports);
    }

    public record ReportSnapshot(UUID id, Instant updatedAt) {
    }
}
