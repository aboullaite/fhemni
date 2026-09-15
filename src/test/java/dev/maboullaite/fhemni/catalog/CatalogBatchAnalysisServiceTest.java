package dev.maboullaite.fhemni.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.RevisionSummary;
import dev.maboullaite.fhemni.analysis.AnalysisService;
import dev.maboullaite.fhemni.catalog.CatalogBatchAnalysisService.BatchState;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogBatchAnalysisServiceTest {

    @Test
    void analyzesPendingEpisodesSequentiallyAndSkipsCompletedOnes() {
        CatalogVideoRepository catalog = mock(CatalogVideoRepository.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        AnalysisService analyses = mock(AnalysisService.class);
        ExecutorService executor = mock(ExecutorService.class);
        CatalogVideo completedVideo = video("n5B3boj2MFM", "Completed episode");
        CatalogVideo pendingVideo = video("TMgYfNkfF14", "Pending episode");
        when(catalog.findAll(500)).thenReturn(List.of(completedVideo, pendingVideo));
        when(revisions.latestByVideo()).thenReturn(Map.of(
                completedVideo.youtubeVideoId(),
                new RevisionSummary(
                        UUID.randomUUID(), completedVideo.youtubeVideoId(), OutputLanguage.DARIJA,
                        AnalysisStatus.COMPLETED, Instant.now(), false)));
        when(analyses.create(eq(pendingVideo.canonicalUrl()), eq("ary")))
                .thenReturn(completedAnalysis(pendingVideo));

        CatalogBatchAnalysisService service = new CatalogBatchAnalysisService(
                catalog, revisions, analyses, executor, Duration.ofMinutes(30));
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);

        var accepted = service.start("ary", 20);
        verify(executor).execute(work.capture());
        assertEquals(BatchState.RUNNING, accepted.state());
        work.getValue().run();

        var finished = service.latest();
        assertEquals(BatchState.COMPLETED, finished.state());
        assertEquals(1, finished.total());
        assertEquals(1, finished.completed());
        assertEquals(0, finished.failed());
        verify(analyses).create(pendingVideo.canonicalUrl(), "ary");
        verify(analyses, never()).create(eq(completedVideo.canonicalUrl()), any());
    }

    @Test
    void forceReprocessesTheLatestFailedEpisode() {
        CatalogVideoRepository catalog = mock(CatalogVideoRepository.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        AnalysisService analyses = mock(AnalysisService.class);
        ExecutorService executor = mock(ExecutorService.class);
        CatalogVideo failedVideo = video("TMgYfNkfF14", "Failed episode");
        when(catalog.findAll(500)).thenReturn(List.of(failedVideo));
        when(revisions.latestByVideo()).thenReturn(Map.of(
                failedVideo.youtubeVideoId(),
                new RevisionSummary(
                        UUID.randomUUID(), failedVideo.youtubeVideoId(), OutputLanguage.DARIJA,
                        AnalysisStatus.FAILED, Instant.now(), false)));
        when(analyses.reprocess(eq(failedVideo.canonicalUrl()), eq("ary")))
                .thenReturn(completedAnalysis(failedVideo));

        CatalogBatchAnalysisService service = new CatalogBatchAnalysisService(
                catalog, revisions, analyses, executor, Duration.ofMinutes(30));
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);

        service.start("ary", 20);
        verify(executor).execute(work.capture());
        work.getValue().run();

        assertEquals(BatchState.COMPLETED, service.latest().state());
        verify(analyses).reprocess(failedVideo.canonicalUrl(), "ary");
        verify(analyses, never()).create(eq(failedVideo.canonicalUrl()), any());
    }

    @Test
    void continuesWithTheNextEpisodeWhenOneAnalysisFails() {
        CatalogVideoRepository catalog = mock(CatalogVideoRepository.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        AnalysisService analyses = mock(AnalysisService.class);
        ExecutorService executor = mock(ExecutorService.class);
        CatalogVideo brokenVideo = video("n5B3boj2MFM", "Broken episode");
        CatalogVideo healthyVideo = video("TMgYfNkfF14", "Healthy episode");
        when(catalog.findAll(500)).thenReturn(List.of(brokenVideo, healthyVideo));
        when(revisions.latestByVideo()).thenReturn(Map.of());
        when(analyses.create(brokenVideo.canonicalUrl(), "ary"))
                .thenReturn(failedAnalysis(brokenVideo, "The audio could not be decoded."));
        when(analyses.create(healthyVideo.canonicalUrl(), "ary"))
                .thenReturn(completedAnalysis(healthyVideo));

        CatalogBatchAnalysisService service = new CatalogBatchAnalysisService(
                catalog, revisions, analyses, executor, Duration.ofMinutes(30));
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);

        service.start("ary", 20);
        verify(executor).execute(work.capture());
        work.getValue().run();

        var finished = service.latest();
        assertEquals(BatchState.COMPLETED, finished.state());
        assertEquals(2, finished.total());
        assertEquals(1, finished.completed());
        assertEquals(1, finished.failed());
        assertEquals("The audio could not be decoded.", finished.error());
        verify(analyses).create(brokenVideo.canonicalUrl(), "ary");
        verify(analyses).create(healthyVideo.canonicalUrl(), "ary");
    }

    @Test
    void continuesWhenStartingOneEpisodeThrows() {
        CatalogVideoRepository catalog = mock(CatalogVideoRepository.class);
        AnalysisRevisionRepository revisions = mock(AnalysisRevisionRepository.class);
        AnalysisService analyses = mock(AnalysisService.class);
        ExecutorService executor = mock(ExecutorService.class);
        CatalogVideo brokenVideo = video("n5B3boj2MFM", "Broken episode");
        CatalogVideo healthyVideo = video("TMgYfNkfF14", "Healthy episode");
        when(catalog.findAll(500)).thenReturn(List.of(brokenVideo, healthyVideo));
        when(revisions.latestByVideo()).thenReturn(Map.of());
        when(analyses.create(brokenVideo.canonicalUrl(), "ary"))
                .thenThrow(new IllegalStateException("Decoder unavailable."));
        when(analyses.create(healthyVideo.canonicalUrl(), "ary"))
                .thenReturn(completedAnalysis(healthyVideo));

        CatalogBatchAnalysisService service = new CatalogBatchAnalysisService(
                catalog, revisions, analyses, executor, Duration.ofMinutes(30));
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);

        service.start("ary", 20);
        verify(executor).execute(work.capture());
        work.getValue().run();

        var finished = service.latest();
        assertEquals(BatchState.COMPLETED, finished.state());
        assertEquals(1, finished.completed());
        assertEquals(1, finished.failed());
        verify(analyses).create(healthyVideo.canonicalUrl(), "ary");
    }

    private CatalogVideo video(String youtubeId, String title) {
        Instant now = Instant.now();
        return new CatalogVideo(
                UUID.randomUUID(), youtubeId, "episode-" + youtubeId,
                "https://www.youtube.com/watch?v=" + youtubeId,
                title, "2M", "https://i.ytimg.com/vi/" + youtubeId + "/hqdefault.jpg",
                "ساعة الصراحة", null, "ar", null, null,
                CatalogStatus.CATALOGUED, null, true, now, now);
    }

    private AnalysisSnapshot completedAnalysis(CatalogVideo video) {
        return new AnalysisSnapshot(
                UUID.randomUUID(), video.canonicalUrl(), video.youtubeVideoId(), OutputLanguage.DARIJA,
                AnalysisStatus.COMPLETED, 100, "Analysis complete", false, Instant.now(),
                null, null, List.of(), false, null);
    }

    private AnalysisSnapshot failedAnalysis(CatalogVideo video, String error) {
        return new AnalysisSnapshot(
                UUID.randomUUID(), video.canonicalUrl(), video.youtubeVideoId(), OutputLanguage.DARIJA,
                AnalysisStatus.FAILED, 40, "Analysis failed", false, Instant.now(),
                null, error, List.of(), false, null);
    }
}
