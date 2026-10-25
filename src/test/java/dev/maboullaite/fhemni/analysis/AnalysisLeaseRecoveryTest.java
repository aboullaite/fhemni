package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.VideoReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:lease-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class AnalysisLeaseRecoveryTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AnalysisRevisionRepository repository;
    @Autowired private AnalysisService service;
    @Autowired private org.springframework.context.ApplicationContext context;

    @Test
    void abandonedWorkerIsNotRenewedAfterFailurePersistenceThrows() {
        var revisions = spy(new AnalysisRevisionRepository(jdbc, mapper));
        var fixture = workerFixture(revisions);
        when(fixture.runner().run(any(), any(), any())).thenThrow(new IllegalStateException("Provider failed"));
        UUID id = fixture.service().reprocess("https://youtu.be/abcdefghijk", "en").id();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Temporary outage"))
                .when(revisions).fail(eq(id), any());
        fixture.work().get().run();
        Instant until = Instant.now().plusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        setLease(id, until);

        fixture.service().renewAnalysisLeases();

        assertThat(lease(id)).isEqualTo(until.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        expireLease(id);
        fixture.service().markInterruptedAnalysesFailed();
        assertThat(fixture.service().get(id).status()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void renewalIncludesLiveWorkButNotOtherOwnedRowsAndDoesNotReadSessionState() {
        var revisions = spy(new AnalysisRevisionRepository(jdbc, mapper));
        var fixture = workerFixture(revisions);
        UUID orphan = createRevision(revisions);
        Instant until = Instant.now().plusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        setLease(orphan, until);
        when(fixture.runner().run(any(), any(), any())).thenAnswer(call -> {
            UUID id = call.<VideoAnalysisRunner.RunnerInput>getArgument(0).analysisId();
            setLease(id, until);
            clearInvocations(revisions);
            fixture.service().renewAnalysisLeases();
            assertThat(lease(id)).isAfter(until.plusSeconds(200));
            assertThat(lease(orphan)).isEqualTo(until);
            verify(revisions, never()).find(any());
            return new VideoAnalysisRunner.RunnerResult(report(), "done");
        });
        fixture.service().reprocess("https://youtu.be/abcdefghijk", "en");
        fixture.work().get().run();
    }

    @Test
    void contextStorageAndLiveDeliveryDoNotHoldTheSessionLock() {
        var fixture = workerFixture();
        when(fixture.runner().run(any(), any(), any()))
                .thenReturn(new VideoAnalysisRunner.RunnerResult(report(), "done"));
        UUID id = fixture.service().reprocess("https://youtu.be/abcdefghijk", "en").id();
        doAnswer(call -> {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> fixture.service().get(id));
            return null;
        }).when(fixture.contexts()).save(any(), any(), any(), any(), any(), any());
        doAnswer(call -> {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> fixture.service().get(id));
            return null;
        }).when(fixture.events()).publish(eq(id), any());
        fixture.work().get().run();
        assertThat(status(id)).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    void leaseSchedulerRunsWhileGeneralMaintenancePoolIsBlocked() throws Exception {
        var general = context.getBean("taskScheduler", org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class);
        var leases = context.getBean("analysisLeaseScheduler", org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class);
        assertThat(general.getPoolSize()).isGreaterThan(1);
        var entered = new CountDownLatch(general.getPoolSize());
        var release = new CountDownLatch(1);
        var renewed = new CountDownLatch(1);
        try {
            for (int i = 0; i < general.getPoolSize(); i++) {
                general.execute(() -> {
                    entered.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            leases.schedule(renewed::countDown, Instant.now());
            assertThat(renewed.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void anotherInstanceDoesNotFailHealthyAdminWork() {
        var other = new AnalysisRevisionRepository(jdbc, mapper);
        UUID id = createRevision(other);

        repository.failInterrupted();

        assertThat(status(id)).isEqualTo(AnalysisStatus.QUEUED);
        expireLease(id);
        repository.failInterrupted();
        assertThat(status(id)).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void periodicMaintenanceRecoversWorkWhoseLeaseWasStillValidAtRestart() {
        var previousInstance = new AnalysisRevisionRepository(jdbc, mapper);
        UUID interruptedId = createRevision(previousInstance);
        UUID healthyOtherOwnerId = createRevision(previousInstance);
        UUID healthyCurrentOwnerId = createRevision(repository);
        UUID expiredCurrentOwnerId = createRevision(repository);

        service.markInterruptedAnalysesFailed();
        assertThat(status(interruptedId)).isEqualTo(AnalysisStatus.QUEUED);

        expireLease(interruptedId);
        expireLease(expiredCurrentOwnerId);
        service.renewAnalysisLeases();
        service.markInterruptedAnalysesFailed();

        assertThat(status(interruptedId)).isEqualTo(AnalysisStatus.FAILED);
        assertThat(status(healthyOtherOwnerId)).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(status(healthyCurrentOwnerId)).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(status(expiredCurrentOwnerId)).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void recoversLegacyInFlightRevisionsWithoutWorkerLeasesAfterUpgrade() {
        for (AnalysisStatus status : List.of(
                AnalysisStatus.QUEUED, AnalysisStatus.ANALYZING, AnalysisStatus.FACT_CHECKING)) {
            UUID id = createRevision(repository);
            jdbc.sql("""
                            UPDATE analysis_revisions
                               SET status = :status, worker_instance = NULL, worker_lease_until = NULL
                             WHERE id = :id
                            """)
                    .param("status", status.name())
                    .param("id", id)
                    .update();

            repository.failInterrupted();

            assertThat(status(id)).as("legacy %s revision", status).isEqualTo(AnalysisStatus.FAILED);
        }
    }

    @Test
    void periodicRecoveryUpdatesCachedInterruptedSessions() {
        UUID interruptedId = createRevision(new AnalysisRevisionRepository(jdbc, mapper));
        assertThat(service.get(interruptedId).status()).isEqualTo(AnalysisStatus.QUEUED);

        expireLease(interruptedId);
        service.markInterruptedAnalysesFailed();

        assertThat(service.get(interruptedId).status()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void periodicRecoveryUpdatesCachedSessionsWhenAnotherInstanceSweepsFirst() {
        var other = new AnalysisRevisionRepository(jdbc, mapper);
        UUID interruptedId = createRevision(other);
        assertThat(service.get(interruptedId).status()).isEqualTo(AnalysisStatus.QUEUED);

        expireLease(interruptedId);
        other.failInterrupted();
        service.markInterruptedAnalysesFailed();

        assertThat(service.get(interruptedId).status()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void recoveryPreservesCompletedLegacyRevisionsWithoutWorkerLeases() {
        UUID id = createRevision(repository);
        repository.complete(id, report(), "completed-interaction");
        jdbc.sql("UPDATE analysis_revisions SET worker_instance = NULL, worker_lease_until = NULL WHERE id = :id")
                .param("id", id)
                .update();

        repository.failInterrupted();

        var completed = repository.find(id).orElseThrow().snapshot();
        assertThat(completed.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.report().title()).isEqualTo("Completed report");
    }

    @Test
    void expiredOwnerCannotReviveOrCompleteRecoveredWork() {
        UUID id = createRevision(repository);
        expireLease(id);
        repository.failInterrupted();

        assertThat(repository.updateProgress(id, AnalysisStatus.ANALYZING, 12, "Late progress")).isZero();
        assertThat(status(id)).isEqualTo(AnalysisStatus.FAILED);

        assertThat(repository.complete(id, report(), "private-interaction")).isZero();
        var failed = repository.find(id).orElseThrow().snapshot();
        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.report()).isNull();
    }

    @Test
    void workerFinishingAfterRecoveryCannotExposeAnUnsavedReport() {
        var fixture = workerFixture();
        when(fixture.runner().run(any(), any(), any())).thenAnswer(call -> {
            UUID id = call.<VideoAnalysisRunner.RunnerInput>getArgument(0).analysisId();
            expireLease(id);
            fixture.service().markInterruptedAnalysesFailed();
            return new VideoAnalysisRunner.RunnerResult(report(), "late-interaction");
        });
        UUID id = fixture.service().reprocess("https://youtu.be/abcdefghijk", "en").id();

        fixture.work().get().run();

        assertThat(fixture.service().get(id).status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(fixture.service().get(id).report()).isNull();
        assertThat(repository.find(id).orElseThrow().snapshot().report()).isNull();
        verify(fixture.events(), never()).publish(eq(id), argThat(event -> event.status() == AnalysisStatus.COMPLETED));
        verifyNoInteractions(fixture.contexts());
    }

    @Test
    void lateProgressAndWorkerFailureCannotReviveOrReplaceRecoveryFailure() {
        var fixture = workerFixture();
        var observed = new AtomicReference<AnalysisSnapshot>();
        when(fixture.runner().run(any(), any(), any())).thenAnswer(call -> {
            UUID id = call.<VideoAnalysisRunner.RunnerInput>getArgument(0).analysisId();
            expireLease(id);
            fixture.service().markInterruptedAnalysesFailed();
            Consumer<AnalysisEvent> progress = call.getArgument(1);
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    progress.accept(new AnalysisEvent(AnalysisStatus.FACT_CHECKING, 68, "Late progress")))
                    .isInstanceOf(RuntimeException.class);
            observed.set(fixture.service().get(id));
            throw new IllegalStateException("Late provider failure");
        });
        UUID id = fixture.service().reprocess("https://youtu.be/abcdefghijk", "en").id();

        fixture.work().get().run();

        assertThat(observed.get().status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(fixture.service().get(id).error()).isEqualTo(repository.find(id).orElseThrow().snapshot().error());
        verify(fixture.events(), never()).publish(eq(id), argThat(event -> event.status() == AnalysisStatus.FACT_CHECKING));
        verify(fixture.events(), times(1)).publish(eq(id), argThat(event -> event.status() == AnalysisStatus.FAILED));
    }

    @Test
    void completionRejectedBeforeCleanupNeverAppearsCompleted() {
        var fixture = workerFixture();
        when(fixture.runner().run(any(), any(), any())).thenAnswer(call -> {
            expireLease(call.<VideoAnalysisRunner.RunnerInput>getArgument(0).analysisId());
            Consumer<AnalysisEvent> progress = call.getArgument(1);
            progress.accept(new AnalysisEvent(AnalysisStatus.FACT_CHECKING, 68, "Expired progress"));
            return new VideoAnalysisRunner.RunnerResult(report(), "expired-interaction");
        });
        UUID id = fixture.service().reprocess("https://youtu.be/abcdefghijk", "en").id();
        fixture.work().get().run();

        assertThat(fixture.service().get(id).status()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(fixture.service().get(id).progress()).isEqualTo(2);
        assertThat(fixture.service().get(id).report()).isNull();
        fixture.service().markInterruptedAnalysesFailed();
        assertThat(fixture.service().get(id).status()).isEqualTo(AnalysisStatus.FAILED);
        verify(fixture.events(), never()).publish(eq(id), argThat(event -> event.status() == AnalysisStatus.COMPLETED));
        verify(fixture.events(), never()).publish(eq(id), argThat(event -> event.status() == AnalysisStatus.FACT_CHECKING));
    }

    @Test
    void lostLeaseStopsThePipelineBeforeAnotherProviderCall() {
        var gateway = mock(VideoIntelligenceGateway.class);
        var usage = mock(AiUsageGuard.class);
        var executor = mock(ExecutorService.class);
        var work = new AtomicReference<Runnable>();
        doAnswer(call -> { work.set(call.getArgument(0)); return null; }).when(executor).execute(any());
        var worker = new AnalysisService(new YouTubeUrlParser(), gateway,
                new VideoAnalysisRunner(gateway, usage), mock(AnalysisEventHub.class), executor,
                usage, repository, mock(VideoContextRepository.class), 10, Duration.ofHours(1), 10, 4, 10);
        UUID id = worker.reprocess("https://youtu.be/abcdefghijk", "en").id();
        when(gateway.analyze(any(), any(), any())).thenAnswer(call -> {
            expireLease(id);
            repository.failInterrupted();
            return new dev.maboullaite.fhemni.gemini.GatewayAnalysisResult("lost", report(),
                    dev.maboullaite.fhemni.cost.AiUsage.empty());
        });
        work.get().run();
        verify(gateway, never()).factCheck(any(), any());
        assertThat(worker.get(id).status()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void successfulWorkerStillPersistsAndAnnouncesCompletion() {
        var fixture = workerFixture();
        when(fixture.runner().run(any(), any(), any()))
                .thenReturn(new VideoAnalysisRunner.RunnerResult(report(), "completed-interaction"));
        UUID id = fixture.service().reprocess("https://youtu.be/abcdefghijk", "en").id();
        fixture.work().get().run();

        assertThat(fixture.service().get(id).status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(repository.find(id).orElseThrow().snapshot().report()).isEqualTo(report());
        verify(fixture.events(), times(1)).publish(eq(id), argThat(event -> event.status() == AnalysisStatus.COMPLETED));
        var notifications = inOrder(fixture.contexts(), fixture.events());
        notifications.verify(fixture.contexts()).save(any(), any(), any(), any(), any(), eq("completed-interaction"));
        notifications.verify(fixture.events()).publish(eq(id), argThat(event -> event.status() == AnalysisStatus.COMPLETED));
    }

    @Test
    void completedRevisionCannotBeOverwrittenByALaterWorkerResult() {
        UUID id = createRevision(repository);
        assertThat(repository.updateProgress(id, AnalysisStatus.ANALYZING, 12, "Analyzing")).isEqualTo(1);
        assertThat(repository.complete(id, report(), "original-interaction")).isEqualTo(1);
        var replacement = new VideoReport("Replacement", "Summary", "Details", List.of(), List.of(), List.of(), List.of());

        assertThat(repository.complete(id, replacement, "late-interaction")).isZero();
        assertThat(repository.updateProgress(id, AnalysisStatus.ANALYZING, 12, "Late progress")).isZero();
        assertThat(repository.fail(id, "Late failure")).isZero();

        var stored = repository.find(id).orElseThrow();
        assertThat(stored.snapshot().status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(stored.snapshot().report()).isEqualTo(report());
        assertThat(stored.interactionId()).isEqualTo("original-interaction");
    }

    private WorkerFixture workerFixture() {
        return workerFixture(repository);
    }

    private WorkerFixture workerFixture(AnalysisRevisionRepository revisions) {
        var gateway = mock(VideoIntelligenceGateway.class);
        var runner = mock(VideoAnalysisRunner.class);
        var events = mock(AnalysisEventHub.class);
        var contexts = mock(VideoContextRepository.class);
        var executor = mock(ExecutorService.class);
        var work = new AtomicReference<Runnable>();
        doAnswer(call -> { work.set(call.getArgument(0)); return null; }).when(executor).execute(any());
        var workerService = new AnalysisService(new YouTubeUrlParser(), gateway, runner, events, executor,
                mock(AiUsageGuard.class), revisions, contexts, 10, Duration.ofHours(1), 10, 4, 10);
        return new WorkerFixture(workerService, runner, events, contexts, work);
    }

    private record WorkerFixture(AnalysisService service, VideoAnalysisRunner runner, AnalysisEventHub events,
                                 VideoContextRepository contexts, AtomicReference<Runnable> work) {}

    private UUID createRevision(AnalysisRevisionRepository owner) {
        UUID id = UUID.randomUUID();
        owner.create(new AnalysisSnapshot(
                        id, "https://www.youtube.com/watch?v=abcdefghijk", "abcdefghijk",
                        OutputLanguage.ENGLISH, AnalysisStatus.QUEUED, 0, "Queued", false,
                        Instant.now(), null, null, List.of(), false, null),
                "fixture", "v1", "fixture", "v1", "fixture");
        return id;
    }

    private void expireLease(UUID id) {
        setLease(id, Instant.now().minusSeconds(1));
    }

    private void setLease(UUID id, Instant until) {
        jdbc.sql("UPDATE analysis_revisions SET worker_lease_until = :expired WHERE id = :id")
                .param("id", id)
                .param("expired", until.atOffset(ZoneOffset.UTC))
                .update();
    }

    private Instant lease(UUID id) {
        return jdbc.sql("SELECT worker_lease_until FROM analysis_revisions WHERE id = :id")
                .param("id", id).query(java.time.OffsetDateTime.class).single().toInstant();
    }

    private AnalysisStatus status(UUID id) {
        return repository.find(id).orElseThrow().snapshot().status();
    }

    private VideoReport report() {
        return new VideoReport("Completed report", "Summary", "Details", List.of(), List.of(), List.of(), List.of());
    }
}
