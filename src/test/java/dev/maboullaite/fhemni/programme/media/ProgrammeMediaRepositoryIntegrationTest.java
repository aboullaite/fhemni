package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.FeasibilityVerdict;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftAssessment;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftProgramme;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftPromise;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.EvidenceDraft;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:programme-media-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "fhemni.gemini.api-key=",
        "fhemni.programme-jobs.dispatch-interval-ms=3600000",
        "fhemni.programme-jobs.lease-renew-interval-ms=3600000"
})
class ProgrammeMediaRepositoryIntegrationTest {

    @Autowired
    private PartyProgrammeService programmes;

    @Autowired
    private ProgrammeMediaRepository media;

    @Autowired
    private ProgrammeMediaService service;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM party_programmes").update();
    }

    @Test
    void fencesAStaleWorkerAndPublishesOnlyTheReviewedEdition() {
        var programme = publishedProgramme();
        Instant start = Instant.parse("2026-09-11T10:00:00Z");
        ProgrammeMedia created = media.create(
                programme.id(), programme.partyCode(), programme.sourceSha256(), "darija-v1", 3, start);
        var oldLease = media.claim(created.id(), "old", start, start.plusSeconds(1)).orElseThrow();

        Instant reclaimedAt = start.plusSeconds(2);
        media.recoverExpired(reclaimedAt);
        var newLease = media.claim(created.id(), "new", reclaimedAt, reclaimedAt.plusSeconds(120)).orElseThrow();
        ProgrammeMediaScript script = script(programme.promises().getFirst().promise().id());

        assertThatThrownBy(() -> media.completeScript(
                oldLease, script, "old worker text", "model", reclaimedAt))
                .isInstanceOf(ProgrammeMediaLeaseLostException.class);

        ProgrammeMedia review = media.completeScript(
                newLease, script, "reviewed text", "model", reclaimedAt.plusSeconds(1));
        assertThat(review.status()).isEqualTo(ProgrammeMediaStatus.SCRIPT_REVIEW);
        ProgrammeMedia queued = media.queueMedia(
                review.id(), script, "reviewed text", reclaimedAt.plusSeconds(2));
        var renderLease = media.claim(
                queued.id(), "renderer", reclaimedAt.plusSeconds(3), reclaimedAt.plusSeconds(123)).orElseThrow();
        ProgrammeMedia rendered = media.completeMedia(
                renderLease, "tts-model", "Charon", "image-model", 5,
                "audio.mp3", "video.mp4", "captions.vtt",
                300_000, reclaimedAt.plusSeconds(4));
        assertThat(rendered.status()).isEqualTo(ProgrammeMediaStatus.MEDIA_REVIEW);
        assertThat(rendered.imageModel()).isEqualTo("image-model");
        assertThat(rendered.illustrationCount()).isEqualTo(5);

        ProgrammeMedia published = media.publish(rendered.id(), reclaimedAt.plusSeconds(5));

        assertThat(published.status()).isEqualTo(ProgrammeMediaStatus.PUBLISHED);
        assertThat(media.working(programme.id())).isEmpty();
        assertThat(media.published(programme.id())).contains(published);
        assertThat(service.published("PJD")).satisfies(value -> {
            assertThat(value.durationMs()).isEqualTo(300_000);
            assertThat(value.videoUrl()).endsWith("/programme/media/video?v=" + published.id());
            assertThat(value.captionsUrl()).endsWith("/programme/media/captions?v=" + published.id());
            assertThat(value.transcript()).hasSize(10);
        });

        service.invalidateForAssessment(programme.promises().getFirst().promise().id());

        assertThat(media.published(programme.id())).isEmpty();
        assertThat(media.find(published.id()).orElseThrow().status()).isEqualTo(ProgrammeMediaStatus.STALE);
        assertThatThrownBy(() -> service.published("PJD"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void failsAnExpiredWorkerThatExhaustedItsAttempts() {
        var programme = publishedProgramme();
        Instant start = Instant.parse("2026-09-11T12:00:00Z");
        ProgrammeMedia created = media.create(
                programme.id(), programme.partyCode(), programme.sourceSha256(), "darija-v1", 1, start);
        media.claim(created.id(), "worker", start, start.plusSeconds(1)).orElseThrow();

        Instant recoveredAt = start.plusSeconds(2);
        media.recoverExpired(recoveredAt);

        ProgrammeMedia failed = media.find(created.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(ProgrammeMediaStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(1);
        assertThat(failed.lastErrorCode()).isEqualTo("WORKER_INTERRUPTED");
        assertThat(media.dispatchable(recoveredAt, 2)).isEmpty();

        ProgrammeMedia retried = media.retryFailed(created.id(), recoveredAt.plusSeconds(1));
        assertThat(retried.status()).isEqualTo(ProgrammeMediaStatus.QUEUED_SCRIPT);
        assertThat(retried.attemptCount()).isZero();
    }

    private PartyProgrammeService.AdminProgrammeView publishedProgramme() {
        var programme = programmes.createProgramme(new DraftProgramme(
                "PJD", text("برنامج", "Programme", "Programme"),
                text("الخلاصة", "Résumé", "Summary"),
                "https://example.org/pjd-2026.pdf", "Official programme", "ar",
                "Frozen official source snapshot for programme media.", true, List.of()));
        var promise = programmes.createPromise(programme.id(), new DraftPromise(
                "pjd-media-promise", "education", text("وعد التعليم", "Éducation", "Education"),
                "A measurable education commitment by 2031.", "Page 10", "Legislation", "Budget"));
        programmes.createAssessment(promise.promise().id(), new DraftAssessment(
                FeasibilityVerdict.HARD,
                text("ممكن بشروط", "Possible sous conditions", "Possible with conditions"),
                text("خاص التمويل", "Financement requis", "Funding required"),
                text("افتراض", "Hypothèse", "Assumption"),
                text("حساب", "Calcul", "Calculation"),
                "fhemni-feasibility-v1", LocalDate.of(2026, 9, 1),
                List.of(new EvidenceDraft(
                        "HCP", "Official source", "https://www.hcp.ma/indicator",
                        LocalDate.of(2026, 8, 1), "Official baseline."))));
        return programmes.publishAll(programme.id());
    }

    private static ProgrammeMediaScript script(UUID promiseId) {
        List<ProgrammeMediaScript.Segment> segments = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new ProgrammeMediaScript.Segment(
                        "المحور " + index,
                        "هاد مقطع محايد ومربوط بالمصدر الرسمي ديال البرنامج المنشور.",
                        List.of("PROMISE:" + promiseId)))
                .toList();
        return new ProgrammeMediaScript("البرنامج فخمسة دقايق", segments);
    }

    private static LocalizedText text(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
