package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.PublicationState;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GatewayAnalysisResult;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimKind;
import dev.maboullaite.fhemni.model.VideoReport;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import org.junit.jupiter.api.Test;

class AnalysisPartialUsageTest {

    @Test
    void recordsAnalysisTokensWhenTheFactCheckStageFails() {
        VideoIntelligenceGateway gateway = mock(VideoIntelligenceGateway.class);
        AnalysisEventHub events = mock(AnalysisEventHub.class);
        ExecutorService executor = mock(ExecutorService.class);
        AiUsageGuard usageGuard = mock(AiUsageGuard.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        Reservation analysisReservation = new Reservation(UUID.randomUUID());
        Reservation factCheckReservation = new Reservation(UUID.randomUUID());
        AiUsage analysisUsage = new AiUsage(3_200, 1_100, 400, 90, 0, 0);
        Claim factualClaim = new Claim(
                "claim-1", "A factual statement", "Speaker", 30,
                ClaimKind.FACT, null, null, null, List.of());
        VideoReport report = new VideoReport(
                "Report", "Summary", "Details", List.of(), List.of(), List.of(factualClaim), List.of());

        when(gateway.live()).thenReturn(true);
        when(gateway.model()).thenReturn("analysis-model");
        when(gateway.promptVersion()).thenReturn("analysis-prompt");
        when(gateway.factCheckModel()).thenReturn("fact-model");
        when(gateway.factCheckPromptVersion()).thenReturn("fact-prompt");
        when(gateway.credentialVersion()).thenReturn("credential-test");
        when(revisions.findReusable(
                anyString(), any(), anyString(), anyString(), anyString(), anyString(), eq(false)))
                .thenReturn(Optional.empty());
        when(usageGuard.reserveAnalysis(any(), eq("analysis-model"))).thenReturn(analysisReservation);
        when(usageGuard.reserveFactCheck(any(), eq("fact-model"))).thenReturn(factCheckReservation);
        when(gateway.analyze(anyString(), anyString(), any()))
                .thenReturn(new GatewayAnalysisResult("interaction", report, analysisUsage));
        when(gateway.factCheck(any(), any()))
                .thenThrow(new GeminiApiException("Fact checking failed"));
        when(revisions.publication(any())).thenAnswer(invocation ->
                new PublicationState(invocation.getArgument(0), false, false, null));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any());

        AnalysisService service = new AnalysisService(
                new YouTubeUrlParser(), gateway, events, executor, usageGuard, revisions,
                mock(VideoContextRepository.class),
                10, Duration.ofHours(1), 10, 4, 10);

        AnalysisSnapshot result = service.create("https://youtu.be/n5B3boj2MFM", "ary");

        assertThat(result.status()).isEqualTo(AnalysisStatus.FAILED);
        verify(usageGuard).succeeded(analysisReservation, analysisUsage);
        verify(usageGuard).failed(factCheckReservation, AiUsage.empty());
    }
}
