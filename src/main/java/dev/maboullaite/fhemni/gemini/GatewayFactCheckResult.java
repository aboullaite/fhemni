package dev.maboullaite.fhemni.gemini;

import java.util.List;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.FactCheckAssessment;

public record GatewayFactCheckResult(List<FactCheckAssessment> assessments, AiUsage usage) {

    public GatewayFactCheckResult {
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
        usage = usage == null ? AiUsage.empty() : usage;
    }
}
