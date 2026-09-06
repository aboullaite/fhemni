package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.StoredRevision;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.QuestionMode;
import dev.maboullaite.fhemni.model.VideoReport;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import org.junit.jupiter.api.Test;

class AnalysisCredentialVersionTest {

    @Test
    void refusesChatWhenTheStoredInteractionBelongsToAnOlderCredentialGeneration() {
        UUID analysisId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AnalysisSnapshot completed = new AnalysisSnapshot(
                analysisId,
                "https://www.youtube.com/watch?v=n5B3boj2MFM",
                "n5B3boj2MFM",
                OutputLanguage.DARIJA,
                AnalysisStatus.COMPLETED,
                100,
                "Complete",
                false,
                Instant.now(),
                new VideoReport("Title", "Summary", "Details", List.of(), List.of(), List.of(), List.of()),
                null,
                List.of(),
                true,
                "episode-n5B3boj2MFM");
        StoredRevision stale = new StoredRevision(
                completed, "old-interaction", "gemini-model", "analysis-prompt",
                "fact-model", "fact-prompt", "credential-v1");
        VideoIntelligenceGateway gateway = mock(VideoIntelligenceGateway.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        VideoContextRepository videoContexts = mock(VideoContextRepository.class);
        when(gateway.live()).thenReturn(true);
        when(gateway.model()).thenReturn("gemini-model");
        when(gateway.promptVersion()).thenReturn("analysis-prompt");
        when(gateway.factCheckModel()).thenReturn("fact-model");
        when(gateway.factCheckPromptVersion()).thenReturn("fact-prompt");
        when(gateway.credentialVersion()).thenReturn("credential-v2");
        when(gateway.contextPromptVersion()).thenReturn("context-prompt-v2");
        when(revisions.find(analysisId)).thenReturn(Optional.of(stale));
        when(videoContexts.find(
                completed.videoId(), completed.language(),
                "gemini-model", "context-prompt-v2", "credential-v2"))
                .thenReturn(Optional.empty());

        AnalysisService service = new AnalysisService(
                new YouTubeUrlParser(), gateway, mock(AnalysisEventHub.class),
                mock(ExecutorService.class), mock(AiUsageGuard.class), revisions,
                videoContexts,
                10, Duration.ofHours(1), 10, 4, 10);

        assertThatThrownBy(() -> service.ask(
                analysisId, userId, "شنو قال؟", QuestionMode.VIDEO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prepared for video chat");
        verify(gateway, never()).ask(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
