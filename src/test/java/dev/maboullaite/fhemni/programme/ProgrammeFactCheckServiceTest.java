package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import dev.maboullaite.fhemni.cost.AiOperation;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.AssessmentResult;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.GeneratedAssessment;
import dev.maboullaite.fhemni.openai.OpenAiProgrammeFactCheckGateway;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import org.junit.jupiter.api.Test;

class ProgrammeFactCheckServiceTest {

    private final ProgrammeIntelligenceGateway gemini = mock(ProgrammeIntelligenceGateway.class);
    private final OpenAiProgrammeFactCheckGateway openAi = mock(OpenAiProgrammeFactCheckGateway.class);
    private final AiUsageGuard usage = mock(AiUsageGuard.class);
    private final List<ExtractedPromise> promises = List.of(new ExtractedPromise(
            "party-jobs", "employment", text("الخدمة"), "Create jobs", "p. 2", "", ""));
    private final List<GeneratedAssessment> assessments = List.of(new GeneratedAssessment(
            "party-jobs", FeasibilityVerdict.HARD, text("صعيب"), text("شروط"), text("فرضيات"),
            text("حساب"), List.of()));

    @Test
    void selectsGeminiWithoutRequiringOpenAi() {
        when(gemini.live()).thenReturn(true);
        when(gemini.model()).thenReturn("gemini-model");
        when(usage.reserveEditorial(AiOperation.PROMISE_FEASIBILITY, "gemini-model"))
                .thenReturn(new Reservation(UUID.randomUUID()));
        when(gemini.assess("https://party.ma/programme", promises))
                .thenReturn(new AssessmentResult(assessments, AiUsage.empty()));

        var service = new ProgrammeFactCheckService(gemini, openAi, usage, "gemini");
        var result = service.assess("https://party.ma/programme", promises);

        assertThat(service.ready()).isTrue();
        assertThat(result.providerMode()).isEqualTo("gemini");
        assertThat(result.modelNames()).isEqualTo("gemini-model");
        verify(openAi, times(0)).assess(any(), any());
    }

    @Test
    void consensusRunsIndependentPassesConcurrentlyAndUsesAnOpenAiReconciler() {
        String sourceUrl = sourceUrlForReconciler(false);
        CountDownLatch independentPassesStarted = new CountDownLatch(2);
        when(gemini.live()).thenReturn(true);
        when(openAi.configured()).thenReturn(true);
        when(gemini.model()).thenReturn("gemini-model");
        when(openAi.model()).thenReturn("openai-model");
        when(usage.reserveEditorial(eq(AiOperation.PROMISE_FEASIBILITY), any()))
                .thenAnswer(call -> new Reservation(UUID.randomUUID()));
        when(gemini.assess(sourceUrl, promises)).thenAnswer(call -> {
            awaitBothProviders(independentPassesStarted);
            return new AssessmentResult(assessments, AiUsage.empty());
        });
        when(openAi.assess(sourceUrl, promises)).thenAnswer(call -> {
            awaitBothProviders(independentPassesStarted);
            return new OpenAiProgrammeFactCheckGateway.AssessmentResult(assessments, AiUsage.empty());
        });
        when(openAi.reconcile(eq(sourceUrl), eq(promises), anyList(), anyList()))
                .thenReturn(new OpenAiProgrammeFactCheckGateway.AssessmentResult(assessments, AiUsage.empty()));

        var result = new ProgrammeFactCheckService(gemini, openAi, usage, "consensus")
                .assess(sourceUrl, promises);

        assertThat(result.providerMode()).isEqualTo("consensus");
        assertThat(result.modelNames()).isEqualTo("gemini-model, openai-model; final=openai-model");
        assertThat(result.methodologyVersion()).endsWith("-consensus-openai");
        verify(usage, times(3)).reserveEditorial(eq(AiOperation.PROMISE_FEASIBILITY), any());
        verify(openAi).reconcile(eq(sourceUrl), eq(promises), anyList(), anyList());
        verify(gemini, never()).reconcile(any(), anyList(), anyList(), anyList());
    }

    @Test
    void consensusAlternatesToAGeminiReconciler() {
        String sourceUrl = sourceUrlForReconciler(true);
        when(gemini.model()).thenReturn("gemini-model");
        when(openAi.model()).thenReturn("openai-model");
        when(usage.reserveEditorial(eq(AiOperation.PROMISE_FEASIBILITY), any()))
                .thenAnswer(call -> new Reservation(UUID.randomUUID()));
        when(gemini.assess(sourceUrl, promises))
                .thenReturn(new AssessmentResult(assessments, AiUsage.empty()));
        when(openAi.assess(sourceUrl, promises))
                .thenReturn(new OpenAiProgrammeFactCheckGateway.AssessmentResult(assessments, AiUsage.empty()));
        when(gemini.reconcile(eq(sourceUrl), eq(promises), anyList(), anyList()))
                .thenReturn(new AssessmentResult(assessments, AiUsage.empty()));

        var result = new ProgrammeFactCheckService(gemini, openAi, usage, "consensus")
                .assess(sourceUrl, promises);

        assertThat(result.modelNames()).isEqualTo("gemini-model, openai-model; final=gemini-model");
        assertThat(result.methodologyVersion()).endsWith("-consensus-gemini");
        verify(gemini).reconcile(eq(sourceUrl), eq(promises), anyList(), anyList());
        verify(openAi, never()).reconcile(any(), anyList(), anyList(), anyList());
    }

    @Test
    void rejectsUnknownModesAtStartup() {
        assertThatThrownBy(() -> new ProgrammeFactCheckService(gemini, openAi, usage, "automatic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gemini, openai, or consensus");
    }

    @Test
    void recordsConsumedTokensAsFailedWhenProviderOutputIsIncomplete() {
        var reservation = new Reservation(UUID.randomUUID());
        var consumed = new AiUsage(800, 200, 50, 40, null, 3);
        when(openAi.model()).thenReturn("openai-model");
        when(usage.reserveEditorial(AiOperation.PROMISE_FEASIBILITY, "openai-model"))
                .thenReturn(reservation);
        when(openAi.assess("https://party.ma/programme", promises))
                .thenReturn(new OpenAiProgrammeFactCheckGateway.AssessmentResult(List.of(), consumed));

        var service = new ProgrammeFactCheckService(gemini, openAi, usage, "openai");

        assertThatThrownBy(() -> service.assess("https://party.ma/programme", promises))
                .isInstanceOf(ProgrammeFactCheckException.class)
                .hasMessageContaining("invalid programme fact check");
        verify(usage).failed(reservation, consumed);
        verify(usage, never()).succeeded(any(), any());
    }

    private static LocalizedText text(String value) {
        return new LocalizedText(value, value, value);
    }

    private static String sourceUrlForReconciler(boolean geminiReconciler) {
        return IntStream.range(0, 100)
                .mapToObj(index -> "https://party.ma/programme-" + index)
                .filter(url -> ProgrammeFactCheckService.geminiReconciles(url) == geminiReconciler)
                .findFirst()
                .orElseThrow();
    }

    private static void awaitBothProviders(CountDownLatch started) throws InterruptedException {
        started.countDown();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
