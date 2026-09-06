package dev.maboullaite.fhemni.gemini;

import java.util.List;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.SourceReference;

public record GatewayAnswerResult(
        String interactionId,
        String answer,
        List<SourceReference> sources,
        AiUsage usage) {

    public GatewayAnswerResult(String interactionId, String answer, List<SourceReference> sources) {
        this(interactionId, answer, sources, AiUsage.empty());
    }

    public GatewayAnswerResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
        usage = usage == null ? AiUsage.empty() : usage;
    }
}
