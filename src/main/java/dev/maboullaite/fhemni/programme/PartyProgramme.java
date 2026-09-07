package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartyProgramme(
        UUID id,
        String partyCode,
        int electionYear,
        int termStartYear,
        int termEndYear,
        LocalizedText title,
        LocalizedText summary,
        String sourceUrl,
        String sourceLabel,
        String sourceLanguage,
        String sourceSnapshot,
        List<String> extractionWarnings,
        String sourceSha256,
        Instant sourceRetrievedAt,
        boolean sourceVerified,
        EditorialStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt) {

    public record LocalizedText(String ar, String fr, String en) {
    }
}
