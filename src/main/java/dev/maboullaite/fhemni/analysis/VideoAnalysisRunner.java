package dev.maboullaite.fhemni.analysis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimKind;
import dev.maboullaite.fhemni.model.ClaimVerdict;
import dev.maboullaite.fhemni.model.FactCheckAssessment;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.VideoReport;
import org.springframework.stereotype.Component;

/** Analysis pipeline only: it grants no access and never publishes a report. */
@Component
public class VideoAnalysisRunner {

    private final VideoIntelligenceGateway gateway;
    private final AiUsageGuard usage;

    public VideoAnalysisRunner(VideoIntelligenceGateway gateway, AiUsageGuard usage) {
        this.gateway = gateway;
        this.usage = usage;
    }

    public record RunnerInput(UUID analysisId, String canonicalUrl, String videoId, OutputLanguage language) {}

    public record RunnerResult(VideoReport report, String providerInteractionId) {}

    public RunnerResult run(RunnerInput input, Consumer<AnalysisEvent> progress) {
        Reservation analysisReservation = gateway.live()
                ? usage.reserveAnalysis(input.analysisId(), gateway.model())
                : null;
        return run(input, progress, analysisReservation);
    }

    public RunnerResult run(
            RunnerInput input,
            Consumer<AnalysisEvent> progress,
            Reservation analysisReservation) {
        Reservation factCheckReservation = null;
        AiUsage analysisUsage = AiUsage.empty();
        AiUsage factCheckUsage = AiUsage.empty();
        try {
            progress.accept(new AnalysisEvent(AnalysisStatus.ANALYZING, 12, "Analyzing the source video"));
            var analysis = gateway.analyze(input.canonicalUrl(), input.videoId(), input.language());
            analysisUsage = analysis.usage();
            usage.succeeded(analysisReservation, analysisUsage);
            analysisReservation = null;

            progress.accept(new AnalysisEvent(AnalysisStatus.FACT_CHECKING, 68, "Checking factual claims"));
            if (gateway.live()
                    && analysis.report().claims().stream().anyMatch(claim -> claim.kind() == ClaimKind.FACT)) {
                factCheckReservation = usage.reserveFactCheck(input.analysisId(), gateway.factCheckModel());
            }
            var checked = gateway.factCheck(analysis.report().claims(), input.language());
            factCheckUsage = checked.usage();
            usage.succeeded(factCheckReservation, factCheckUsage);
            factCheckReservation = null;

            return new RunnerResult(
                    analysis.report().withClaims(merge(analysis.report().claims(), checked.assessments())),
                    analysis.interactionId());
        } catch (RuntimeException failure) {
            if (failure instanceof GeminiApiException provider) {
                if (analysisReservation != null) {
                    analysisUsage = provider.usage();
                }
                if (factCheckReservation != null) {
                    factCheckUsage = provider.usage();
                }
            }
            usage.failed(analysisReservation, analysisUsage);
            usage.failed(factCheckReservation, factCheckUsage);
            throw failure;
        }
    }

    private static List<Claim> merge(List<Claim> claims, List<FactCheckAssessment> assessments) {
        Map<String, FactCheckAssessment> indexed = new HashMap<>();
        assessments.forEach(item -> indexed.put(item.claimId(), item));
        return claims.stream().map(claim -> {
            if (claim.kind() != ClaimKind.FACT) {
                return new Claim(
                        claim.id(), claim.statement(), claim.speaker(), claim.startSeconds(), claim.kind(),
                        ClaimVerdict.NOT_APPLICABLE,
                        "Opinions, proposals, and predictions are shown separately rather than rated true or false.",
                        "", List.of());
            }
            return claim.withAssessment(indexed.getOrDefault(
                    claim.id(),
                    new FactCheckAssessment(
                            claim.id(), ClaimVerdict.UNVERIFIABLE,
                            "No sufficiently reliable evidence was returned for this claim.",
                            "LOW", List.of())));
        }).toList();
    }
}
