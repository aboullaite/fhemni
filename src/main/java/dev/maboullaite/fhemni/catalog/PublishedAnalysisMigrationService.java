package dev.maboullaite.fhemni.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.analysis.VideoContextRepository;
import dev.maboullaite.fhemni.analysis.VideoContextRepository.PublishedContextMigrationCandidate;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GatewayContextResult;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublishedAnalysisMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PublishedAnalysisMigrationService.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final VideoContextRepository contexts;
    private final VideoIntelligenceGateway gateway;
    private final AiUsageGuard usageGuard;
    private final ExecutorService executor;
    private final boolean enabled;
    private final Object startMonitor = new Object();
    private volatile MigrationRun latest;

    public PublishedAnalysisMigrationService(
            VideoContextRepository contexts,
            VideoIntelligenceGateway gateway,
            AiUsageGuard usageGuard,
            @Qualifier("catalogBatchAnalysisExecutor") ExecutorService executor,
            @Value("${fhemni.catalog.context-migration-enabled:false}") boolean enabled) {
        this.contexts = contexts;
        this.gateway = gateway;
        this.usageGuard = usageGuard;
        this.executor = executor;
        this.enabled = enabled;
    }

    public MigrationOverview overview() {
        int pending = enabled ? countPending() : 0;
        MigrationRun current = latest;
        return new MigrationOverview(enabled, pending, current == null ? null : current.snapshot());
    }

    public MigrationSnapshot start(UUID administratorId, Integer requestedLimit) {
        if (administratorId == null) {
            throw new IllegalArgumentException("An administrator is required to start the migration.");
        }
        if (!enabled) {
            throw new IllegalStateException("The one-off Gemini context migration is disabled.");
        }
        if (!gateway.live() || !usageGuard.analysisEnabled()) {
            throw new IllegalStateException("Live analysis must be enabled before starting the migration.");
        }
        int limit = requestedLimit == null ? MAX_BATCH_SIZE : requestedLimit;
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Migration size must be between 1 and " + MAX_BATCH_SIZE + ".");
        }

        synchronized (startMonitor) {
            MigrationRun active = latest;
            if (active != null && active.running()) {
                return active.snapshot();
            }

            List<PublishedContextMigrationCandidate> candidates = contexts.findMissingPublished(
                    gateway.model(), gateway.contextPromptVersion(), gateway.credentialVersion(), limit);
            MigrationRun created = new MigrationRun(UUID.randomUUID(), candidates.size(), Instant.now());
            latest = created;
            log.info(
                    "Administrator {} started Gemini context migration {} for {} published videos",
                    administratorId, created.id(), candidates.size());
            if (candidates.isEmpty()) {
                created.finish();
            } else {
                try {
                    executor.execute(() -> process(created, candidates));
                } catch (RuntimeException exception) {
                    created.abort("The migration worker is unavailable. Please retry later.");
                    throw exception;
                }
            }
            return created.snapshot();
        }
    }

    private void process(MigrationRun run, List<PublishedContextMigrationCandidate> candidates) {
        for (PublishedContextMigrationCandidate candidate : candidates) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                run.abort("The migration was interrupted. Run it again to resume remaining episodes.");
                return;
            }
            run.begin(candidate.title());
            Reservation reservation = null;
            AiUsage usage = AiUsage.empty();
            try {
                if (currentContextExists(candidate)) {
                    run.episodeCompleted();
                    continue;
                }
                reservation = usageGuard.reserveAnalysis(UUID.randomUUID(), gateway.model());
                GatewayContextResult result = gateway.buildChatContext(
                        candidate.videoUrl(), candidate.youtubeVideoId(), candidate.language());
                usage = result.usage();
                contexts.save(
                        candidate.youtubeVideoId(), candidate.language(),
                        gateway.model(), gateway.contextPromptVersion(), gateway.credentialVersion(),
                        result.interactionId());
                usageGuard.succeeded(reservation, usage);
                reservation = null;
                run.episodeCompleted();
            } catch (GeminiApiException exception) {
                usageGuard.failed(reservation, exception.usage());
                run.episodeFailed(safeMessage(exception));
                log.warn("Gemini context migration failed for video {}: {}",
                        candidate.youtubeVideoId(), safeMessage(exception));
            } catch (RuntimeException exception) {
                usageGuard.failed(reservation, usage);
                run.episodeFailed(safeMessage(exception));
                log.warn("Gemini context migration failed for video {}: {}",
                        candidate.youtubeVideoId(), safeMessage(exception));
            }
        }
        run.finish();
        MigrationSnapshot result = run.snapshot();
        log.info("Gemini context migration {} finished: {} completed, {} failed",
                result.id(), result.completed(), result.failed());
    }

    private boolean currentContextExists(PublishedContextMigrationCandidate candidate) {
        return contexts.find(
                candidate.youtubeVideoId(), candidate.language(),
                gateway.model(), gateway.contextPromptVersion(), gateway.credentialVersion())
                .isPresent();
    }

    private int countPending() {
        return contexts.countMissingPublished(
                gateway.model(), gateway.contextPromptVersion(), gateway.credentialVersion());
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "An episode could not be migrated."
                : message;
    }

    public enum MigrationState {
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_ERRORS,
        FAILED
    }

    public record MigrationOverview(
            boolean enabled,
            int pending,
            MigrationSnapshot latest) {
    }

    public record MigrationSnapshot(
            UUID id,
            MigrationState state,
            int total,
            int completed,
            int failed,
            String currentVideoTitle,
            String error,
            Instant startedAt,
            Instant updatedAt) {
    }

    private static final class MigrationRun {
        private final UUID id;
        private final int total;
        private final Instant startedAt;
        private MigrationState state = MigrationState.RUNNING;
        private int completed;
        private int failed;
        private String currentVideoTitle;
        private String error;
        private Instant updatedAt;

        private MigrationRun(UUID id, int total, Instant startedAt) {
            this.id = id;
            this.total = total;
            this.startedAt = startedAt;
            this.updatedAt = startedAt;
        }

        synchronized boolean running() {
            return state == MigrationState.RUNNING;
        }

        synchronized void begin(String title) {
            currentVideoTitle = title;
            updatedAt = Instant.now();
        }

        synchronized void episodeCompleted() {
            completed += 1;
            currentVideoTitle = null;
            updatedAt = Instant.now();
        }

        synchronized void episodeFailed(String message) {
            failed += 1;
            error = message;
            currentVideoTitle = null;
            updatedAt = Instant.now();
        }

        synchronized void finish() {
            state = failed == 0 ? MigrationState.COMPLETED : MigrationState.COMPLETED_WITH_ERRORS;
            currentVideoTitle = null;
            updatedAt = Instant.now();
        }

        synchronized void abort(String message) {
            state = MigrationState.FAILED;
            error = message;
            updatedAt = Instant.now();
        }

        synchronized MigrationSnapshot snapshot() {
            return new MigrationSnapshot(
                    id, state, total, completed, failed, currentVideoTitle,
                    error, startedAt, updatedAt);
        }

        UUID id() {
            return id;
        }
    }
}
