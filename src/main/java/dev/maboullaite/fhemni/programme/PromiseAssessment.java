package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;

public record PromiseAssessment(
        UUID id,
        UUID promiseId,
        int revisionNumber,
        int horizonYears,
        FeasibilityVerdict verdict,
        LocalizedText summary,
        LocalizedText requirements,
        LocalizedText assumptions,
        LocalizedText calculationNotes,
        String methodologyVersion,
        String providerMode,
        String modelNames,
        LocalDate dataCutoff,
        EditorialStatus status,
        Instant createdAt,
        Instant publishedAt,
        List<Evidence> evidence) {

    public record Evidence(
            UUID id,
            String publisher,
            String title,
            String url,
            LocalDate publishedOn,
            String note,
            int sortOrder) {
    }
}
