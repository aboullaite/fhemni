package dev.maboullaite.fhemni.programme;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    private static final String METHODOLOGY_VERSION = "fhemni-feasibility-v3";

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
        IndependentResults independent = runIndependentPasses(sourceUrl, promises);
        CandidatePair candidates = orderedCandidates(
                sourceUrl, independent.gemini().assessments(), independent.openAi().assessments());
        if (geminiReconciles(sourceUrl)) {
            AssessmentResult finalResult = callGeminiConsensus(
                    sourceUrl, promises, candidates.first(), candidates.second());
            return consensusResult(finalResult.assessments(), gemini.model(), "gemini");
        }
        OpenAiProgrammeFactCheckGateway.AssessmentResult finalResult = callOpenAiConsensus(
                sourceUrl, promises, candidates.first(), candidates.second());
        return consensusResult(finalResult.assessments(), openAi.model(), "openai");
    }

    private IndependentResults runIndependentPasses(String sourceUrl, List<ExtractedPromise> promises) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<AssessmentResult> geminiTask = executor.submit(() -> callGemini(sourceUrl, promises));
        Future<OpenAiProgrammeFactCheckGateway.AssessmentResult> openAiTask =
                executor.submit(() -> callOpenAi(sourceUrl, promises));
        try {
            return new IndependentResults(await(geminiTask), await(openAiTask));
        } catch (RuntimeException exception) {
            geminiTask.cancel(true);
            openAiTask.cancel(true);
            throw exception;
        } finally {
            executor.shutdownNow();
        }
    }

    private FactCheckResult consensusResult(
            List<GeneratedAssessment> assessments,
            String reconcilerModel,
            String reconciler) {
        return new FactCheckResult(
                assessments,
                mode.value(),
                gemini.model() + ", " + openAi.model() + "; final=" + reconcilerModel,
                METHODOLOGY_VERSION + "-consensus-" + reconciler);
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

    private AssessmentResult callGeminiConsensus(
            String sourceUrl,
            List<ExtractedPromise> promises,
            List<GeneratedAssessment> candidateA,
            List<GeneratedAssessment> candidateB) {
        Reservation reservation = usageGuard.reserveEditorial(AiOperation.PROMISE_FEASIBILITY, gemini.model());
        AiUsage consumed = AiUsage.empty();
        try {
            AssessmentResult result = gemini.reconcile(sourceUrl, promises, candidateA, candidateB);
            consumed = result.usage();
            validateCoverage(promises, result.assessments());
            usageGuard.succeeded(reservation, consumed);
            return result;
        } catch (GeminiApiException exception) {
            usageGuard.failed(reservation, exception.usage());
            throw new ProgrammeFactCheckException("Gemini could not reconcile the programme fact check.", exception);
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation, consumed);
            throw new ProgrammeFactCheckException("Gemini returned an invalid consensus fact check.", exception);
        }
    }

    private static <T> T await(Future<T> task) {
        try {
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProgrammeFactCheckException("The programme fact check was interrupted.", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ProgrammeFactCheckException("The programme fact check failed.", exception.getCause());
        }
    }

    static boolean geminiReconciles(String sourceUrl) {
        return stableChoice(sourceUrl, "reconciler");
    }

    private static CandidatePair orderedCandidates(
            String sourceUrl,
            List<GeneratedAssessment> geminiAssessments,
            List<GeneratedAssessment> openAiAssessments) {
        return stableChoice(sourceUrl, "candidate-order")
                ? new CandidatePair(geminiAssessments, openAiAssessments)
                : new CandidatePair(openAiAssessments, geminiAssessments);
    }

    private static boolean stableChoice(String sourceUrl, String purpose) {
        return ((sourceUrl + '|' + purpose).hashCode() & 1) == 0;
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

    private record IndependentResults(
            AssessmentResult gemini,
            OpenAiProgrammeFactCheckGateway.AssessmentResult openAi) {
    }

    private record CandidatePair(List<GeneratedAssessment> first, List<GeneratedAssessment> second) {
    }
}
