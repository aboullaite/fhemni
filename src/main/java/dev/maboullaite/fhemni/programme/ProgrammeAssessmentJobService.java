package dev.maboullaite.fhemni.programme;

import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminPromiseView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProgrammeAssessmentJobService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeAssessmentJobService.class);
    private static final int ASSESSMENT_BATCH_SIZE = 6;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    private final ProgrammeAssessmentJobRepository jobs;
    private final PartyProgrammeService programmes;
    private final ProgrammeAssessmentCommitter committer;
    private final ProgrammeFactCheckService factChecks;
    private final ExecutorService executor;
    private final int concurrency;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final boolean workerEnabled;
    private final Clock clock;
    private final String owner = UUID.randomUUID().toString();
    private final Set<UUID> submitted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ProgrammeAssessmentJobRepository.Lease> activeLeases = new ConcurrentHashMap<>();

    @Autowired
    public ProgrammeAssessmentJobService(
            ProgrammeAssessmentJobRepository jobs,
            PartyProgrammeService programmes,
            ProgrammeAssessmentCommitter committer,
            ProgrammeFactCheckService factChecks,
            @Qualifier("programmeAssessmentExecutor") ExecutorService executor,
            @Value("${fhemni.programme-jobs.concurrency:1}") int concurrency,
            @Value("${fhemni.programme-jobs.max-attempts:3}") int maxAttempts,
            @Value("${fhemni.programme-jobs.retry-delay:PT30S}") Duration retryDelay,
            @Value("${fhemni.programme-jobs.worker-enabled:true}") boolean workerEnabled) {
        this(jobs, programmes, committer, factChecks, executor,
                concurrency, maxAttempts, retryDelay, workerEnabled, Clock.systemUTC());
    }

    ProgrammeAssessmentJobService(
            ProgrammeAssessmentJobRepository jobs,
            PartyProgrammeService programmes,
            ProgrammeAssessmentCommitter committer,
            ProgrammeFactCheckService factChecks,
            ExecutorService executor,
            int concurrency,
            int maxAttempts,
            Duration retryDelay,
            Clock clock) {
        this(jobs, programmes, committer, factChecks, executor,
                concurrency, maxAttempts, retryDelay, true, clock);
    }

    ProgrammeAssessmentJobService(
            ProgrammeAssessmentJobRepository jobs,
            PartyProgrammeService programmes,
            ProgrammeAssessmentCommitter committer,
            ProgrammeFactCheckService factChecks,
            ExecutorService executor,
            int concurrency,
            int maxAttempts,
            Duration retryDelay,
            boolean workerEnabled,
            Clock clock) {
        if (concurrency < 1 || concurrency > 4) {
            throw new IllegalArgumentException("Programme job concurrency must be between 1 and 4.");
        }
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("Programme job attempts must be between 1 and 5.");
        }
        if (retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("Programme retry delay must be positive.");
        }
        this.jobs = jobs;
        this.programmes = programmes;
        this.committer = committer;
        this.factChecks = factChecks;
        this.executor = executor;
        this.concurrency = concurrency;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
        this.workerEnabled = workerEnabled;
        this.clock = clock;
    }

    public synchronized ProgrammeAssessmentJob start(UUID programmeId) {
        ProgrammeAssessmentJob active = jobs.activeForProgramme(programmeId).orElse(null);
        if (active != null && !active.providerMode().equals(factChecks.mode().value())) {
            jobs.invalidateAndFailJob(active.id(), "PROVIDER_MODE_CHANGED",
                    "The configured assessment mode changed. A new job is required.", clock.instant());
            active = null;
        }
        if (active != null) {
            dispatch();
            return active;
        }
        if (!factChecks.ready()) {
            throw new IllegalStateException(
                    "The credentials required by the selected programme fact-check mode are not configured.");
        }
        AdminProgrammeView programme = programmes.adminProgramme(programmeId);
        if (programme.status() != EditorialStatus.DRAFT) {
            throw new IllegalStateException("Published programmes are not processed by the draft assessment queue.");
        }
        List<PartyPromise> pending = programme.promises().stream()
                .filter(item -> item.assessments().isEmpty())
                .map(AdminPromiseView::promise)
                .toList();
        try {
            ProgrammeAssessmentJob created = jobs.create(
                    programmeId, factChecks.mode().value(), pending, maxAttempts, clock.instant());
            dispatch();
            return created;
        } catch (DataIntegrityViolationException race) {
            return jobs.activeForProgramme(programmeId).orElseThrow(() -> race);
        }
    }

    public synchronized ProgrammeAssessmentJob startReassessment(UUID promiseId, String reviewContext) {
        if (!factChecks.ready()) {
            throw new IllegalStateException(
                    "The credentials required by the selected programme fact-check mode are not configured.");
        }
        AdminPromiseView promise = programmes.adminPromise(promiseId);
        if (promise.promise().status() != EditorialStatus.PUBLISHED) {
            throw new IllegalStateException("Only a published promise can be reanalysed.");
        }
        boolean hasPublished = promise.assessments().stream()
                .anyMatch(assessment -> assessment.status() == EditorialStatus.PUBLISHED);
        boolean hasDraft = promise.assessments().stream()
                .anyMatch(assessment -> assessment.status() == EditorialStatus.DRAFT);
        if (!hasPublished) {
            throw new IllegalStateException("Publish the first assessment before requesting a revision.");
        }
        if (hasDraft) {
            throw new IllegalStateException("Review or discard the existing draft revision first.");
        }
        UUID programmeId = promise.promise().programmeId();
        ProgrammeAssessmentJob active = jobs.activeForProgramme(programmeId).orElse(null);
        if (active != null) {
            throw new IllegalStateException("Another assessment job is already active for this programme.");
        }
        try {
            ProgrammeAssessmentJob created = jobs.createReassessment(
                    programmeId, factChecks.mode().value(), promise.promise(), reviewContext,
                    maxAttempts, clock.instant());
            dispatch();
            return created;
        } catch (DataIntegrityViolationException race) {
            throw new IllegalStateException("Another assessment job started for this programme.", race);
        }
    }

    public ProgrammeAssessmentJob latest(UUID programmeId) {
        return jobs.latestForProgramme(programmeId)
                .orElseThrow(() -> new NoSuchElementException("No assessment job exists for this programme."));
    }

    public Map<UUID, ProgrammeAssessmentJob> latest() {
        return jobs.latestByProgramme();
    }

    @Scheduled(fixedDelayString = "${fhemni.programme-jobs.dispatch-interval-ms:3000}")
    public void dispatch() {
        if (!workerEnabled) {
            return;
        }
        Instant now = clock.instant();
        jobs.recoverExpiredLeases(now);
        for (UUID jobId : jobs.dispatchable(now, Math.max(concurrency * 2, 2))) {
            if (submitted.add(jobId)) {
                executor.submit(() -> run(jobId));
            }
        }
    }

    @Scheduled(fixedDelayString = "${fhemni.programme-jobs.lease-renew-interval-ms:30000}")
    public void renewLeases() {
        if (!workerEnabled) {
            return;
        }
        Instant now = clock.instant();
        activeLeases.entrySet().removeIf(entry ->
                !jobs.renewLease(entry.getValue(), now, now.plus(LEASE_DURATION)));
    }

    private void run(UUID jobId) {
        ProgrammeAssessmentJobRepository.Lease lease = null;
        try {
            Instant now = clock.instant();
            lease = jobs.claim(jobId, owner, now, now.plus(LEASE_DURATION)).orElse(null);
            if (lease == null) {
                return;
            }
            activeLeases.put(jobId, lease);
            process(lease);
        } catch (NoSuchElementException deleted) {
            log.info("Programme assessment job {} disappeared while it was queued", jobId);
        } catch (ProgrammeJobLeaseLostException lost) {
            log.info("Programme assessment job {} stopped because its lease moved to another worker", jobId);
        } catch (RuntimeException unexpected) {
            log.error("Programme assessment job {} stopped unexpectedly", jobId, unexpected);
            if (lease != null) {
                try {
                    jobs.failOwnedJob(lease, "INTERNAL_JOB_FAILURE",
                            "The background worker stopped unexpectedly. Start a new job to retry missing promises.",
                            clock.instant());
                } catch (ProgrammeJobLeaseLostException lost) {
                    log.info("Programme assessment job {} was already reclaimed after its worker failed", jobId);
                }
            }
        } finally {
            if (lease != null) {
                activeLeases.remove(jobId, lease);
            }
            submitted.remove(jobId);
        }
    }

    private void process(ProgrammeAssessmentJobRepository.Lease lease) {
        UUID jobId = lease.jobId();
        ProgrammeAssessmentJob claimed = jobs.find(jobId).orElseThrow();
        if (!claimed.providerMode().equals(factChecks.mode().value())) {
            jobs.failOwnedJob(lease, "PROVIDER_MODE_CHANGED",
                    "The configured assessment mode changed. Start a new job for missing promises.",
                    clock.instant());
            return;
        }
        while (true) {
            Instant now = clock.instant();
            List<ProgrammeAssessmentJobItem> ready = jobs.readyItems(jobId, now, ASSESSMENT_BATCH_SIZE);
            if (ready.isEmpty()) {
                jobs.settle(lease, now);
                return;
            }

            AdminProgrammeView programme = programmes.adminProgramme(
                    jobs.find(jobId).orElseThrow().programmeId());
            Map<UUID, AdminPromiseView> byId = programme.promises().stream()
                    .collect(java.util.stream.Collectors.toMap(item -> item.promise().id(), item -> item));
            List<ProgrammeAssessmentJobItem> alreadyDone = ready.stream()
                    .filter(item -> !claimed.reassessment()
                            && byId.get(item.promiseId()) != null
                            && !byId.get(item.promiseId()).assessments().isEmpty())
                    .toList();
            if (!alreadyDone.isEmpty()) {
                jobs.completeItems(lease, ids(alreadyDone), now);
                ready = ready.stream().filter(item -> !alreadyDone.contains(item)).toList();
                if (ready.isEmpty()) {
                    continue;
                }
            }

            List<ExtractedPromise> requested = new ArrayList<>();
            for (ProgrammeAssessmentJobItem item : ready) {
                AdminPromiseView promise = byId.get(item.promiseId());
                if (promise == null) {
                    jobs.failItems(lease, List.of(item.promiseId()), "PROMISE_REMOVED",
                            "The promise was removed before it could be assessed.", now);
                    continue;
                }
                requested.add(extracted(promise.promise()));
            }
            if (requested.isEmpty()) {
                continue;
            }

            List<ProgrammeAssessmentJobItem> batch = ready.stream()
                    .filter(item -> requested.stream().anyMatch(promise -> promise.slug().equals(item.promiseSlug())))
                    .toList();
            jobs.markRunning(lease, ids(batch), batch.getFirst().promiseSlug(), now);
            try {
                var result = claimed.reviewContext() == null || claimed.reviewContext().isBlank()
                        ? factChecks.assess(programme.sourceUrl(), requested)
                        : factChecks.assess(programme.sourceUrl(), requested, claimed.reviewContext());
                committer.saveAndComplete(
                        lease, programme.id(), requested, result, ids(batch),
                        claimed.reassessment(), clock.instant());
            } catch (RuntimeException failure) {
                if (failure instanceof ProgrammeJobLeaseLostException) {
                    throw failure;
                }
                Failure classified = classify(failure);
                if (classified.promiseSlug() != null) {
                    handleUngrounded(lease, batch, classified, clock.instant());
                    continue;
                }
                if (!classified.retryable()) {
                    jobs.failOwnedJob(lease, classified.code(), classified.message(), clock.instant());
                    return;
                }
                pauseOrExhaust(lease, batch, classified, clock.instant());
                return;
            }
        }
    }

    private void handleUngrounded(
            ProgrammeAssessmentJobRepository.Lease lease,
            List<ProgrammeAssessmentJobItem> batch,
            Failure failure,
            Instant now) {
        ProgrammeAssessmentJobItem blocked = batch.stream()
                .filter(item -> item.promiseSlug().equals(failure.promiseSlug()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The provider identified an unknown ungrounded promise."));
        int attempt = blocked.attemptCount() + 1;
        if (attempt >= blocked.maxAttempts()) {
            jobs.failItems(lease, List.of(blocked.promiseId()), failure.code(), failure.message(), now);
        } else {
            jobs.retryItemsWhileRunning(lease, List.of(blocked.promiseId()),
                    now.plus(backoff(attempt)), failure.code(), failure.message(), now);
        }
        List<UUID> remaining = batch.stream()
                .filter(item -> !item.promiseId().equals(blocked.promiseId()))
                .map(ProgrammeAssessmentJobItem::promiseId)
                .toList();
        jobs.returnToPending(lease, remaining, now);
    }

    private void pauseOrExhaust(
            ProgrammeAssessmentJobRepository.Lease lease,
            List<ProgrammeAssessmentJobItem> batch,
            Failure failure,
            Instant now) {
        List<UUID> exhausted = batch.stream()
                .filter(item -> item.attemptCount() + 1 >= item.maxAttempts())
                .map(ProgrammeAssessmentJobItem::promiseId)
                .toList();
        List<UUID> retry = batch.stream()
                .filter(item -> item.attemptCount() + 1 < item.maxAttempts())
                .map(ProgrammeAssessmentJobItem::promiseId)
                .toList();
        jobs.failItems(lease, exhausted, failure.code(), failure.message(), now);
        if (retry.isEmpty()) {
            jobs.settle(lease, now);
        } else {
            int nextAttempt = batch.stream()
                    .filter(item -> retry.contains(item.promiseId()))
                    .mapToInt(item -> item.attemptCount() + 1)
                    .max()
                    .orElse(1);
            jobs.retryItems(lease, retry, now.plus(backoff(nextAttempt)),
                    failure.code(), failure.message(), now);
        }
    }

    private Duration backoff(int attempt) {
        return retryDelay.multipliedBy(1L << Math.min(Math.max(attempt - 1, 0), 4));
    }

    private static Failure classify(Throwable failure) {
        UngroundedProgrammeEvidenceException ungrounded = cause(
                failure, UngroundedProgrammeEvidenceException.class);
        if (ungrounded != null) {
            return new Failure("EVIDENCE_REQUIRED",
                    "No verifiable citation was returned for this promise.", true,
                    ungrounded.promiseSlug());
        }
        GeminiApiException gemini = cause(failure, GeminiApiException.class);
        if (gemini != null && gemini.upstreamStatus() != null) {
            int status = gemini.upstreamStatus();
            if (status == 401 || status == 403) {
                return new Failure("AI_CREDENTIAL_REJECTED",
                        "The configured AI credential was rejected.", false, null);
            }
            if (status == 429) {
                return new Failure("AI_RATE_LIMITED",
                        "The AI provider is temporarily rate limiting requests.", true, null);
            }
            if (status >= 400 && status < 500) {
                return new Failure("AI_REQUEST_REJECTED",
                        "The AI provider rejected this assessment request.", false, null);
            }
        }
        if (cause(failure, HttpTimeoutException.class) != null) {
            return new Failure("AI_TIMEOUT", "The AI provider timed out.", true, null);
        }
        if (cause(failure, ProgrammeFactCheckException.class) != null) {
            return new Failure("AI_TEMPORARY_FAILURE",
                    "The configured AI assessment did not complete.", true, null);
        }
        return new Failure("AI_RESULT_INVALID",
                "The AI assessment could not be saved safely.", true, null);
    }

    private static <T extends Throwable> T cause(Throwable failure, Class<T> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }

    private static ExtractedPromise extracted(PartyPromise promise) {
        return new ExtractedPromise(
                promise.slug(), promise.topic(), promise.title(), promise.promiseText(),
                promise.sourceLocator(), promise.mechanism(), promise.financing());
    }

    private static List<UUID> ids(List<ProgrammeAssessmentJobItem> items) {
        return items.stream().map(ProgrammeAssessmentJobItem::promiseId).toList();
    }

    private record Failure(
            String code,
            String message,
            boolean retryable,
            String promiseSlug) {
    }
}
