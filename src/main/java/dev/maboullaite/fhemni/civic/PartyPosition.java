package dev.maboullaite.fhemni.civic;

import java.util.List;

public record PartyPosition(
        String partyCode,
        String questionKey,
        PartyPositionStance stance,
        String evidenceSummary,
        List<PositionEvidence> evidence) {

    public record PositionEvidence(
            String label,
            String sourceUrl,
            String pageReference,
            String promiseSlug) {
    }
}
