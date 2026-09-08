package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.GeneratedAssessment;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftProgramme;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftPromise;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.EvidenceDraft;
import dev.maboullaite.fhemni.programme.ProgrammeAssessmentJob.Status;
import dev.maboullaite.fhemni.programme.ProgrammeFactCheckService.FactCheckResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:programme-job-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "fhemni.programme-jobs.retry-delay=PT0.05S",
        "fhemni.programme-jobs.dispatch-interval-ms=3600000",
        "fhemni.programme-jobs.lease-renew-interval-ms=3600000"
})
class ProgrammeAssessmentJobIntegrationTest {

    @Autowired
    private PartyProgrammeService programmes;

    @Autowired
    private ProgrammeAssessmentJobService jobs;

    @Autowired
    private ProgrammeAssessmentJobRepository jobRepository;

    @Autowired
    private ProgrammeAssessmentCommitter committer;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private ProgrammeFactCheckService factChecks;

    @AfterEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM party_programmes").update();
    }

    @Test
    void persistsProgressAndAutomaticallyRetriesOnlyTheUngroundedPromise() throws Exception {
        var programme = programme();
        var first = programmes.createPromise(programme.id(), promise("pjd-first"));
        var second = programmes.createPromise(programme.id(), promise("pjd-second"));

        when(factChecks.ready()).thenReturn(true);
        when(factChecks.mode()).thenReturn(ProgrammeFactCheckMode.CONSENSUS);
        when(factChecks.assess(anyString(), anyList())).thenAnswer(invocation -> {
            List<ExtractedPromise> requested = invocation.getArgument(1);
            if (requested.size() == 2) {
                throw new ProgrammeFactCheckException(
                        "missing citation", new UngroundedProgrammeEvidenceException("pjd-first"));
            }
            return result(requested);
        });

        ProgrammeAssessmentJob started = jobs.start(programme.id());
        ProgrammeAssessmentJob completed = eventually(
                () -> dispatchAndGet(programme.id()),
                job -> job.status() == Status.COMPLETED);

        assertThat(started.status()).isIn(Status.QUEUED, Status.RUNNING);
        assertThat(completed.completedItems()).isEqualTo(2);
        assertThat(completed.failedItems()).isZero();
        assertThat(programmes.adminProgramme(programme.id()).promises())
                .allSatisfy(item -> assertThat(item.assessments()).hasSize(1));
        assertThat(first.promise().id()).isNotEqualTo(second.promise().id());
    }

    @Test
    void deduplicatesAnActiveJobForTheSameProgramme() throws Exception {
        var programme = programme();
        programmes.createPromise(programme.id(), promise("pjd-only"));
        when(factChecks.ready()).thenReturn(true);
        when(factChecks.mode()).thenReturn(ProgrammeFactCheckMode.CONSENSUS);
        when(factChecks.assess(anyString(), anyList())).thenAnswer(invocation -> {
            Thread.sleep(100);
            return result(invocation.getArgument(1));
        });

        ProgrammeAssessmentJob first = jobs.start(programme.id());
        ProgrammeAssessmentJob second = jobs.start(programme.id());

        assertThat(second.id()).isEqualTo(first.id());
        eventually(() -> dispatchAndGet(programme.id()), job -> job.status() == Status.COMPLETED);
    }

    @Test
    void recoversRunningItemsAfterAWorkerLeaseExpires() {
        var programme = programme();
        var promise = programmes.createPromise(programme.id(), promise("pjd-recover"));
        Instant now = Instant.now();
        UUID jobId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO programme_assessment_jobs (
                            id, programme_id, status, provider_mode, total_items,
                            active_marker, lock_owner, lease_until, created_at, started_at, updated_at
                        ) VALUES (
                            :id, :programmeId, 'RUNNING', 'consensus', 1,
                            TRUE, 'old-worker', :leaseUntil, :now, :now, :now
                        )
                        """)
                .param("id", jobId)
                .param("programmeId", programme.id())
                .param("leaseUntil", now.plusSeconds(86_400).atOffset(ZoneOffset.UTC))
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
        jdbc.sql("""
                        INSERT INTO programme_assessment_job_items (
                            job_id, promise_id, promise_slug, status, attempt_count,
                            max_attempts, created_at, updated_at
                        ) VALUES (
                            :jobId, :promiseId, 'pjd-recover', 'RUNNING', 1,
                            3, :now, :now
                        )
                        """)
                .param("jobId", jobId)
                .param("promiseId", promise.promise().id())
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();

        Instant recoveredAt = now.plusSeconds(172_800);
        jobRepository.recoverExpiredLeases(recoveredAt);

        assertThat(jobRepository.find(jobId).orElseThrow().status()).isEqualTo(Status.RETRY_WAIT);
        assertThat(jobRepository.readyItems(jobId, recoveredAt.plusSeconds(1), 6))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo(ProgrammeAssessmentJobItem.Status.RETRY_WAIT);
                    assertThat(item.attemptCount()).isEqualTo(1);
                });
    }

    @Test
    void rejectsResultsFromAWorkerAfterItsLeaseWasReclaimed() {
        var programme = programme();
        var promise = programmes.createPromise(programme.id(), promise("pjd-fenced"));
        Instant startedAt = Instant.parse("2026-09-08T12:00:00Z");
        ProgrammeAssessmentJob job = jobRepository.create(
                programme.id(), "consensus", List.of(promise.promise()), 3, startedAt);
        var oldLease = jobRepository.claim(
                job.id(), "old-worker", startedAt, startedAt.plusSeconds(1)).orElseThrow();
        jobRepository.markRunning(
                oldLease, List.of(promise.promise().id()), promise.promise().slug(), startedAt);

        Instant reclaimedAt = startedAt.plusSeconds(2);
        jobRepository.recoverExpiredLeases(reclaimedAt);
        var newLease = jobRepository.claim(
                job.id(), "new-worker", reclaimedAt, reclaimedAt.plusSeconds(120)).orElseThrow();
        List<ExtractedPromise> requested = List.of(new ExtractedPromise(
                promise.promise().slug(), promise.promise().topic(), promise.promise().title(),
                promise.promise().promiseText(), promise.promise().sourceLocator(),
                promise.promise().mechanism(), promise.promise().financing()));
        FactCheckResult generated = result(requested);

        assertThatThrownBy(() -> committer.saveAndComplete(
                oldLease, programme.id(), requested, generated,
                List.of(promise.promise().id()), reclaimedAt))
                .isInstanceOf(ProgrammeJobLeaseLostException.class);
        assertThat(programmes.adminProgramme(programme.id()).promises().getFirst().assessments()).isEmpty();

        committer.saveAndComplete(
                newLease, programme.id(), requested, generated,
                List.of(promise.promise().id()), reclaimedAt.plusSeconds(1));
        assertThat(programmes.adminProgramme(programme.id()).promises().getFirst().assessments()).hasSize(1);
    }

    private PartyProgrammeService.AdminProgrammeView programme() {
        return programmes.createProgramme(new DraftProgramme(
                "PJD", text("برنامج", "Programme", "Programme"),
                text("خلاصة", "Résumé", "Summary"),
                "https://party.ma/programme-2026.pdf", "Official 2026 programme", "ar",
                "Frozen official source snapshot.", false, List.of()));
    }

    private static DraftPromise promise(String slug) {
        return new DraftPromise(
                slug, "employment", text("وعد", "Promesse", "Promise"),
                "Create measurable outcomes by 2031.", "Page 12", "Legislation", "Budget law");
    }

    private static FactCheckResult result(List<ExtractedPromise> requested) {
        List<GeneratedAssessment> assessments = requested.stream()
                .map(promise -> new GeneratedAssessment(
                        promise.slug(), FeasibilityVerdict.HARD,
                        text("صعيب", "Difficile", "Hard"),
                        text("شروط", "Conditions", "Conditions"),
                        text("فرضيات", "Hypothèses", "Assumptions"),
                        text("حساب", "Calcul", "Calculation"),
                        List.of(new EvidenceDraft(
                                "HCP", "Official indicator", "https://www.hcp.ma/indicator",
                                LocalDate.of(2026, 8, 1), "Official baseline"))))
                .toList();
        return new FactCheckResult(assessments, "consensus", "models", "method-v1");
    }

    private static LocalizedText text(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }

    private ProgrammeAssessmentJob dispatchAndGet(UUID programmeId) {
        jobs.dispatch();
        return jobs.latest(programmeId);
    }

    private static <T> T eventually(
            Supplier<T> supplier,
            java.util.function.Predicate<T> done) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        T value = supplier.get();
        while (!done.test(value) && System.nanoTime() < deadline) {
            Thread.sleep(25);
            value = supplier.get();
        }
        assertThat(done.test(value)).as("background job reached expected state").isTrue();
        return value;
    }
}
