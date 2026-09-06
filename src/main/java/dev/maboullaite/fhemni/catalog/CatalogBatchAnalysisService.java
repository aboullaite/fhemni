package dev.maboullaite.fhemni.catalog;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.RevisionSummary;
import dev.maboullaite.fhemni.analysis.AnalysisService;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CatalogBatchAnalysisService {

    private static final int CATALOG_SCAN_LIMIT = 500;
    private static final int MAX_BATCH_SIZE = 100;

    private final CatalogVideoRepository catalog;
    private final AnalysisRevisionRepository revisions;
    private final AnalysisService analyses;
    private final ExecutorService executor;
    private final Duration analysisTimeout;
    private final Object startMonitor = new Object();
    private volatile BatchRun latest;

    public CatalogBatchAnalysisService(
            CatalogVideoRepository catalog,
            AnalysisRevisionRepository revisions,
            AnalysisService analyses,
            @Qualifier("catalogBatchAnalysisExecutor") ExecutorService executor,
            @Value("${fhemni.catalog.batch-analysis-timeout:PT30M}") Duration analysisTimeout) {
        if (analysisTimeout.isNegative() || analysisTimeout.isZero()) {
            throw new IllegalArgumentException("The catalogue batch analysis timeout must be positive");
        }
        this.catalog = catalog;
        this.revisions = revisions;
        this.analyses = analyses;
        this.executor = executor;
        this.analysisTimeout = analysisTimeout;
    }

    public BatchSnapshot start(String languageCode, Integer requestedLimit) {
        OutputLanguage language = OutputLanguage.fromCode(languageCode == null ? "ary" : languageCode);
        int limit = requestedLimit == null ? 20 : requestedLimit;
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch size must be between 1 and " + MAX_BATCH_SIZE + ".");
        }

        synchronized (startMonitor) {
            BatchRun active = latest;
            if (active != null && active.running()) {
                return active.snapshot();
            }

            Map<String, RevisionSummary> latestByVideo = revisions.latestByVideo();
            List<CatalogVideo> pending = catalog.findAll(CATALOG_SCAN_LIMIT).stream()
                    .filter(video -> needsAnalysis(video, latestByVideo))
                    .limit(limit)
                    .toList();
            BatchRun created = new BatchRun(UUID.randomUUID(), language, pending.size(), Instant.now());
            latest = created;
            if (pending.isEmpty()) {
                created.complete();
            } else {
                try {
                    executor.execute(() -> process(created, pending));
                } catch (RuntimeException exception) {
                    created.fail("The catalogue batch worker is unavailable. Please retry later.");
                    throw exception;
                }
            }
            return created.snapshot();
        }
    }

    public BatchSnapshot latest() {
        BatchRun current = latest;
        return current == null ? null : current.snapshot();
    }

    private boolean needsAnalysis(CatalogVideo video, Map<String, RevisionSummary> latestByVideo) {
        RevisionSummary revision = latestByVideo.get(video.youtubeVideoId());
        return revision == null || revision.status() == AnalysisStatus.FAILED;
    }

    private void process(BatchRun run, List<CatalogVideo> videos) {
        try {
            for (CatalogVideo video : videos) {
                run.begin(video.title());
                AnalysisSnapshot analysis = analyses.create(video.canonicalUrl(), run.language().code());
                run.analysisStarted(analysis.id());
                AnalysisSnapshot terminal = awaitTerminal(analysis);
                if (terminal.status() == AnalysisStatus.FAILED) {
                    throw new IllegalStateException(terminal.error() == null
                            ? "The episode analysis failed."
                            : terminal.error());
                }
                run.episodeCompleted();
            }
            run.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            run.fail("The batch was interrupted. Its current analysis may still be running.");
        } catch (RuntimeException exception) {
            run.fail(safeMessage(exception));
        }
    }

    private AnalysisSnapshot awaitTerminal(AnalysisSnapshot initial) throws InterruptedException {
        AnalysisSnapshot analysis = initial;
        Instant deadline = Instant.now().plus(analysisTimeout);
        while (analysis.status() != AnalysisStatus.COMPLETED && analysis.status() != AnalysisStatus.FAILED) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("An episode did not finish within the configured batch timeout.");
            }
            Thread.sleep(Duration.ofSeconds(3));
            analysis = analyses.get(analysis.id());
        }
        return analysis;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "The catalogue batch could not continue." : message;
    }

    public enum BatchState {
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record BatchSnapshot(
            UUID id,
            BatchState state,
            OutputLanguage language,
            int total,
            int completed,
            String currentVideoTitle,
            UUID currentAnalysisId,
            String error,
            Instant startedAt,
            Instant updatedAt) {
    }

    private static final class BatchRun {
        private final UUID id;
        private final OutputLanguage language;
        private final int total;
        private final Instant startedAt;
        private BatchState state = BatchState.RUNNING;
        private int completed;
        private String currentVideoTitle;
        private UUID currentAnalysisId;
        private String error;
        private Instant updatedAt;

        private BatchRun(UUID id, OutputLanguage language, int total, Instant startedAt) {
            this.id = id;
            this.language = language;
            this.total = total;
            this.startedAt = startedAt;
            this.updatedAt = startedAt;
        }

        synchronized boolean running() {
            return state == BatchState.RUNNING;
        }

        OutputLanguage language() {
            return language;
        }

        synchronized void begin(String title) {
            currentVideoTitle = title;
            currentAnalysisId = null;
            updatedAt = Instant.now();
        }

        synchronized void analysisStarted(UUID analysisId) {
            currentAnalysisId = analysisId;
            updatedAt = Instant.now();
        }

        synchronized void episodeCompleted() {
            completed += 1;
            currentVideoTitle = null;
            currentAnalysisId = null;
            updatedAt = Instant.now();
        }

        synchronized void complete() {
            state = BatchState.COMPLETED;
            currentVideoTitle = null;
            currentAnalysisId = null;
            updatedAt = Instant.now();
        }

        synchronized void fail(String message) {
            state = BatchState.FAILED;
            error = message;
            updatedAt = Instant.now();
        }

        synchronized BatchSnapshot snapshot() {
            return new BatchSnapshot(
                    id, state, language, total, completed, currentVideoTitle,
                    currentAnalysisId, error, startedAt, updatedAt);
        }
    }
}
