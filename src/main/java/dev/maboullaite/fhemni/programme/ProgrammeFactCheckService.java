package dev.maboullaite.fhemni.programme;

import java.util.List;

import dev.maboullaite.fhemni.cost.AiOperation;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.AssessmentResult;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.GeneratedAssessment;
import dev.maboullaite.fhemni.openai.OpenAiProgrammeFactCheckGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProgrammeFactCheckService {

    private static final String METHODOLOGY_VERSION = "fhemni-feasibility-v2";

    private final ProgrammeIntelligenceGateway gemini;
    private final OpenAiProgrammeFactCheckGateway openAi;
    private final AiUsageGuard usageGuard;
    private final ProgrammeFactCheckMode mode;

    public ProgrammeFactCheckService(
            ProgrammeIntelligenceGateway gemini,
            OpenAiProgrammeFactCheckGateway openAi,
            AiUsageGuard usageGuard,
            @Value("${fhemni.programme-fact-check.mode:gemini}") String mode) {
        this.gemini = gemini;
        this.openAi = openAi;
        this.usageGuard = usageGuard;
        this.mode = ProgrammeFactCheckMode.from(mode);
    }

    public boolean ready() {
        return switch (mode) {
            case GEMINI -> gemini.live();
            case OPENAI -> gemini.live() && openAi.configured();
            case CONSENSUS -> gemini.live() && openAi.configured();
        };
    }

    public ProgrammeFactCheckMode mode() {
        return mode;
    }

    public FactCheckResult assess(String sourceUrl, List<ExtractedPromise> promises) {
        return switch (mode) {
            case GEMINI -> result(callGemini(sourceUrl, promises), mode, gemini.model());
            case OPENAI -> result(callOpenAi(sourceUrl, promises), mode, openAi.model());
            case CONSENSUS -> consensus(sourceUrl, promises);
        };
    }

    private FactCheckResult consensus(String sourceUrl, List<ExtractedPromise> promises) {
        AssessmentResult geminiResult = callGemini(sourceUrl, promises);
        OpenAiProgrammeFactCheckGateway.AssessmentResult openAiResult = callOpenAi(sourceUrl, promises);
        OpenAiProgrammeFactCheckGateway.AssessmentResult finalResult = callOpenAiConsensus(
                sourceUrl, promises, geminiResult.assessments(), openAiResult.assessments());
        return new FactCheckResult(
                finalResult.assessments(), mode.value(), gemini.model() + ", " + openAi.model(),
                METHODOLOGY_VERSION + "-consensus");
    }

    private AssessmentResult callGemini(String sourceUrl, List<ExtractedPromise> promises) {
        Reservation reservation = usageGuard.reserveEditorial(AiOperation.PROMISE_FEASIBILITY, gemini.model());
        AiUsage consumed = AiUsage.empty();
        try {
            AssessmentResult result = gemini.assess(sourceUrl, promises);
            consumed = result.usage();
            validateCoverage(promises, result.assessments());
            usageGuard.succeeded(reservation, consumed);
            return result;
        } catch (GeminiApiException exception) {
            usageGuard.failed(reservation, exception.usage());
            throw new ProgrammeFactCheckException("Gemini could not complete the programme fact check.", exception);
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation, consumed);
            throw new ProgrammeFactCheckException("Gemini returned an invalid programme fact check.", exception);
        }
    }

    private OpenAiProgrammeFactCheckGateway.AssessmentResult callOpenAi(
            String sourceUrl,
            List<ExtractedPromise> promises) {
        Reservation reservation = usageGuard.reserveEditorial(AiOperation.PROMISE_FEASIBILITY, openAi.model());
        AiUsage consumed = AiUsage.empty();
        try {
            var result = openAi.assess(sourceUrl, promises);
            consumed = result.usage();
            validateCoverage(promises, result.assessments());
            usageGuard.succeeded(reservation, consumed);
            return result;
        } catch (ProgrammeFactCheckException exception) {
            usageGuard.failed(reservation);
            throw exception;
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation, consumed);
            throw new ProgrammeFactCheckException("OpenAI returned an invalid programme fact check.", exception);
        }
    }

    private OpenAiProgrammeFactCheckGateway.AssessmentResult callOpenAiConsensus(
            String sourceUrl,
            List<ExtractedPromise> promises,
            List<GeneratedAssessment> geminiAssessments,
            List<GeneratedAssessment> openAiAssessments) {
        Reservation reservation = usageGuard.reserveEditorial(AiOperation.PROMISE_FEASIBILITY, openAi.model());
        AiUsage consumed = AiUsage.empty();
        try {
            var result = openAi.reconcile(sourceUrl, promises, geminiAssessments, openAiAssessments);
            consumed = result.usage();
            validateCoverage(promises, result.assessments());
            usageGuard.succeeded(reservation, consumed);
            return result;
        } catch (ProgrammeFactCheckException exception) {
            usageGuard.failed(reservation);
            throw exception;
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation, consumed);
            throw new ProgrammeFactCheckException("OpenAI returned an invalid consensus fact check.", exception);
        }
    }

    private static FactCheckResult result(
            AssessmentResult result,
            ProgrammeFactCheckMode mode,
            String model) {
        return new FactCheckResult(
                result.assessments(), mode.value(), model, METHODOLOGY_VERSION + "-" + mode.value());
    }

    private static FactCheckResult result(
            OpenAiProgrammeFactCheckGateway.AssessmentResult result,
            ProgrammeFactCheckMode mode,
            String model) {
        return new FactCheckResult(
                result.assessments(), mode.value(), model, METHODOLOGY_VERSION + "-" + mode.value());
    }

    private static void validateCoverage(
            List<ExtractedPromise> promises,
            List<GeneratedAssessment> assessments) {
        SetView expected = new SetView(promises.stream().map(ExtractedPromise::slug).toList());
        SetView actual = new SetView(assessments.stream().map(GeneratedAssessment::promiseSlug).toList());
        if (actual.hasDuplicates() || !actual.values().equals(expected.values())) {
            throw new IllegalArgumentException("The fact-check provider did not assess every promise exactly once.");
        }
    }

    public record FactCheckResult(
            List<GeneratedAssessment> assessments,
            String providerMode,
            String modelNames,
            String methodologyVersion) {
    }

    private record SetView(java.util.Set<String> values, boolean hasDuplicates) {
        private SetView(List<String> items) {
            this(new java.util.LinkedHashSet<>(items), new java.util.LinkedHashSet<>(items).size() != items.size());
        }
    }
}
