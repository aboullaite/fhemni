package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.analysis.VideoContextRepository;
import dev.maboullaite.fhemni.analysis.VideoContextRepository.PublishedContextMigrationCandidate;
import dev.maboullaite.fhemni.analysis.VideoContextRepository.VideoContext;
import dev.maboullaite.fhemni.catalog.PublishedAnalysisMigrationService.MigrationState;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GatewayContextResult;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.model.OutputLanguage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublishedAnalysisMigrationServiceTest {

    @Test
    void buildsOneCompactContextWithoutChangingThePublishedAnalysis() {
        Fixture fixture = new Fixture();
        PublishedContextMigrationCandidate candidate = candidate("n5B3boj2MFM", "Episode one");
        AiUsage usage = new AiUsage(12, 34, 5, 0, 0, 0);
        Reservation reservation = new Reservation(UUID.randomUUID());
        fixture.stubCurrentGateway();
        when(fixture.contexts.findMissingPublished(
                "gemini-model", "context-prompt", "credential-v2", 100))
                .thenReturn(List.of(candidate));
        when(fixture.contexts.find(
                candidate.youtubeVideoId(), candidate.language(),
                "gemini-model", "context-prompt", "credential-v2"))
                .thenReturn(Optional.empty());
        when(fixture.usage.reserveAnalysis(any(), eq("gemini-model"))).thenReturn(reservation);
        when(fixture.gateway.buildChatContext(
                candidate.videoUrl(), candidate.youtubeVideoId(), candidate.language()))
                .thenReturn(new GatewayContextResult("interaction-v2", usage));

        PublishedAnalysisMigrationService service = fixture.service(true);
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        assertThat(service.start(UUID.randomUUID(), null).state()).isEqualTo(MigrationState.RUNNING);
        verify(fixture.executor).execute(work.capture());

        work.getValue().run();

        assertThat(service.overview().latest().state()).isEqualTo(MigrationState.COMPLETED);
        assertThat(service.overview().latest().completed()).isOne();
        verify(fixture.contexts).save(
                candidate.youtubeVideoId(), candidate.language(),
                "gemini-model", "context-prompt", "credential-v2", "interaction-v2");
        verify(fixture.usage).succeeded(reservation, usage);
    }

    @Test
    void skipsAnEpisodeThatBecameCurrentBeforeTheWorkerReachedIt() {
        Fixture fixture = new Fixture();
        PublishedContextMigrationCandidate candidate = candidate("TMgYfNkfF14", "Episode two");
        fixture.stubCurrentGateway();
        when(fixture.contexts.findMissingPublished(
                "gemini-model", "context-prompt", "credential-v2", 10))
                .thenReturn(List.of(candidate));
        when(fixture.contexts.find(
                candidate.youtubeVideoId(), candidate.language(),
                "gemini-model", "context-prompt", "credential-v2"))
                .thenReturn(Optional.of(new VideoContext("interaction-v2", Instant.now())));

        PublishedAnalysisMigrationService service = fixture.service(true);
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        service.start(UUID.randomUUID(), 10);
        verify(fixture.executor).execute(work.capture());
        work.getValue().run();

        verify(fixture.usage, never()).reserveAnalysis(any(), any());
        verify(fixture.gateway, never()).buildChatContext(any(), any(), any());
        assertThat(service.overview().latest().state()).isEqualTo(MigrationState.COMPLETED);
    }

    @Test
    void recordsOneFailureAndContinuesWithTheRemainingEpisodes() {
        Fixture fixture = new Fixture();
        PublishedContextMigrationCandidate first = candidate("n5B3boj2MFM", "Broken episode");
        PublishedContextMigrationCandidate second = candidate("TMgYfNkfF14", "Good episode");
        Reservation firstReservation = new Reservation(UUID.randomUUID());
        Reservation secondReservation = new Reservation(UUID.randomUUID());
        fixture.stubCurrentGateway();
        when(fixture.contexts.findMissingPublished(
                "gemini-model", "context-prompt", "credential-v2", 100))
                .thenReturn(List.of(first, second));
        when(fixture.contexts.find(
                any(), any(), eq("gemini-model"), eq("context-prompt"), eq("credential-v2")))
                .thenReturn(Optional.empty());
        when(fixture.usage.reserveAnalysis(any(), eq("gemini-model")))
                .thenReturn(firstReservation, secondReservation);
        when(fixture.gateway.buildChatContext(
                first.videoUrl(), first.youtubeVideoId(), first.language()))
                .thenThrow(new IllegalStateException("Provider rejected this video"));
        when(fixture.gateway.buildChatContext(
                second.videoUrl(), second.youtubeVideoId(), second.language()))
                .thenReturn(new GatewayContextResult("interaction-good", AiUsage.empty()));

        PublishedAnalysisMigrationService service = fixture.service(true);
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        service.start(UUID.randomUUID(), 100);
        verify(fixture.executor).execute(work.capture());
        work.getValue().run();

        assertThat(service.overview().latest().state()).isEqualTo(MigrationState.COMPLETED_WITH_ERRORS);
        assertThat(service.overview().latest().completed()).isOne();
        assertThat(service.overview().latest().failed()).isOne();
        verify(fixture.usage).failed(firstReservation, AiUsage.empty());
        verify(fixture.contexts).save(
                second.youtubeVideoId(), second.language(),
                "gemini-model", "context-prompt", "credential-v2", "interaction-good");
    }

    private static PublishedContextMigrationCandidate candidate(String videoId, String title) {
        return new PublishedContextMigrationCandidate(
                UUID.randomUUID(), videoId,
                "https://www.youtube.com/watch?v=" + videoId,
                title, OutputLanguage.DARIJA);
    }

    private static final class Fixture {
        private final VideoContextRepository contexts = mock(VideoContextRepository.class);
        private final VideoIntelligenceGateway gateway = mock(VideoIntelligenceGateway.class);
        private final AiUsageGuard usage = mock(AiUsageGuard.class);
        private final ExecutorService executor = mock(ExecutorService.class);

        void stubCurrentGateway() {
            when(gateway.live()).thenReturn(true);
            when(gateway.model()).thenReturn("gemini-model");
            when(gateway.contextPromptVersion()).thenReturn("context-prompt");
            when(gateway.credentialVersion()).thenReturn("credential-v2");
            when(usage.analysisEnabled()).thenReturn(true);
        }

        PublishedAnalysisMigrationService service(boolean enabled) {
            return new PublishedAnalysisMigrationService(contexts, gateway, usage, executor, enabled);
        }
    }
}
