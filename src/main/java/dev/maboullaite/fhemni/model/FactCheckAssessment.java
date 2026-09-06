package dev.maboullaite.fhemni.model;

import java.util.List;

public record FactCheckAssessment(
        String claimId,
        ClaimVerdict verdict,
        String explanation,
        String evidenceStrength,
        List<SourceReference> sources) {

    public FactCheckAssessment {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
