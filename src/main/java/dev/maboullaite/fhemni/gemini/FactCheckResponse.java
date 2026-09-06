package dev.maboullaite.fhemni.gemini;

import java.util.List;

import dev.maboullaite.fhemni.model.SourceReference;

record FactCheckResponse(List<Item> assessments) {

    FactCheckResponse {
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
    }

    record Item(
            String claimId,
            String verdict,
            String explanation,
            String evidenceStrength,
            List<SourceReference> sources) {

        Item {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }
}
