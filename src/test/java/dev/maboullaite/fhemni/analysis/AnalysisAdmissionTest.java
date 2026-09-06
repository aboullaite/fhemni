package dev.maboullaite.fhemni.analysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.cost.AiBudgetExceededException;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import org.junit.jupiter.api.Test;

class AnalysisAdmissionTest {

    @Test
    void rejectsBeforeCreatingARevisionOrQueueingWorkWhenBudgetAdmissionFails() {
        VideoIntelligenceGateway gateway = mock(VideoIntelligenceGateway.class);
        AnalysisEventHub events = mock(AnalysisEventHub.class);
        ExecutorService executor = mock(ExecutorService.class);
        AiUsageGuard usage = mock(AiUsageGuard.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        when(gateway.live()).thenReturn(true);
        when(gateway.model()).thenReturn("gemini-test");
        when(gateway.promptVersion()).thenReturn("prompt-test");
        when(gateway.factCheckModel()).thenReturn("fact-model-test");
        when(gateway.factCheckPromptVersion()).thenReturn("fact-prompt-test");
        when(revisions.findReusable(
                "n5B3boj2MFM", OutputLanguage.DARIJA,
                "gemini-test", "prompt-test",
                "fact-model-test", "fact-prompt-test", false))
                .thenReturn(Optional.empty());
        when(usage.reserveAnalysis(any(), eq("gemini-test")))
                .thenThrow(new AiBudgetExceededException("The daily analysis budget has been reached."));

        AnalysisService service = new AnalysisService(
                new YouTubeUrlParser(), gateway, events, executor, usage, revisions,
                10, Duration.ofHours(1), 10, 4, 10);

        assertThrows(AiBudgetExceededException.class,
                () -> service.create("https://youtu.be/n5B3boj2MFM", "ary"));

        verify(revisions, never()).create(any(AnalysisSnapshot.class), any(), any(), any(), any());
        verify(executor, never()).execute(any());
    }

    @Test
    void requiresExplicitReprocessingWhenOnlyAStaleCompletedRevisionExists() {
        VideoIntelligenceGateway gateway = mock(VideoIntelligenceGateway.class);
        AnalysisEventHub events = mock(AnalysisEventHub.class);
        ExecutorService executor = mock(ExecutorService.class);
        AiUsageGuard usage = mock(AiUsageGuard.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        when(gateway.live()).thenReturn(true);
        when(gateway.model()).thenReturn("new-analysis-model");
        when(gateway.promptVersion()).thenReturn("new-analysis-prompt");
        when(gateway.factCheckModel()).thenReturn("new-fact-model");
        when(gateway.factCheckPromptVersion()).thenReturn("new-fact-prompt");
        when(revisions.findReusable(
                "n5B3boj2MFM", OutputLanguage.DARIJA,
                "new-analysis-model", "new-analysis-prompt",
                "new-fact-model", "new-fact-prompt", false))
                .thenReturn(Optional.empty());
        when(revisions.hasCompleted("n5B3boj2MFM", OutputLanguage.DARIJA, false)).thenReturn(true);

        AnalysisService service = new AnalysisService(
                new YouTubeUrlParser(), gateway, events, executor, usage, revisions,
                10, Duration.ofHours(1), 10, 4, 10);

        assertThrows(IllegalStateException.class,
                () -> service.create("https://youtu.be/n5B3boj2MFM", "ary"));

        verify(usage, never()).reserveAnalysis(any(), any());
        verify(revisions, never()).create(any(AnalysisSnapshot.class), any(), any(), any(), any());
        verify(executor, never()).execute(any());
    }
}
