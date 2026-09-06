package dev.maboullaite.fhemni.gemini;

import dev.maboullaite.fhemni.cost.AiUsage;

public record GatewayContextResult(
        String interactionId,
        AiUsage usage) {

    public GatewayContextResult {
        usage = usage == null ? AiUsage.empty() : usage;
    }
}
