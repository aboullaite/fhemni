package dev.maboullaite.fhemni.gemini;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.VideoReport;

public record GatewayAnalysisResult(String interactionId, VideoReport report, AiUsage usage) {

    public GatewayAnalysisResult(String interactionId, VideoReport report) {
        this(interactionId, report, AiUsage.empty());
    }
}
