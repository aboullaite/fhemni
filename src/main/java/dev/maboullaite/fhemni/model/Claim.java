package dev.maboullaite.fhemni.model;

import java.util.List;

public record Claim(
        String id,
        String statement,
        String speaker,
        int startSeconds,
        ClaimKind kind,
        ClaimVerdict verdict,
        String explanation,
        String evidenceStrength,
        List<SourceReference> sources) {

    public Claim {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public Claim withAssessment(FactCheckAssessment assessment) {
        return new Claim(
                id,
                statement,
                speaker,
                startSeconds,
                kind,
                assessment.verdict(),
                assessment.explanation(),
                assessment.evidenceStrength(),
                assessment.sources());
    }
}
