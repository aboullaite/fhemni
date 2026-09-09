package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.ProgrammeChatDossier;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.ProgrammeChatPromise;
import dev.maboullaite.fhemni.programme.PromiseAssessment.Evidence;
import dev.maboullaite.fhemni.programme.ProgrammeChatContextBuilder.ConversationTurn;
import org.junit.jupiter.api.Test;

class ProgrammeChatContextBuilderTest {

    @Test
    void buildsAQuestionRelevantDossierWithOfficialAndReviewedSources() {
        UUID programmeId = UUID.randomUUID();
        PartyProgramme programme = new PartyProgramme(
                programmeId, "PJD", 2026, 2026, 2031,
                text("برنامج 2026", "Programme 2026", "2026 programme"),
                text("خلاصة", "Résumé", "Summary"),
                "https://pjd.example/programme.pdf", "PJD official programme", "ar",
                "The official document proposes creating 200,000 jobs each year. Justice reform is elsewhere.",
                List.of(), "sha", Instant.now(), true, EditorialStatus.PUBLISHED,
                Instant.now(), Instant.now(), Instant.now());
        PartyPromise jobs = promise(programmeId, "jobs", "Create 200,000 jobs each year", "page 12");
        PromiseAssessment assessment = assessment(jobs.id());
        PartyPromise justice = promise(programmeId, "justice", "Digitise courts", "page 45");
        ProgrammeChatDossier dossier = new ProgrammeChatDossier(programme, List.of(
                new ProgrammeChatPromise(justice, null),
                new ProgrammeChatPromise(jobs, assessment)));

        ProgrammeChatContext context = new ProgrammeChatContextBuilder(90_000).build(
                dossier, "How many jobs per year?", OutputLanguage.ENGLISH,
                List.of(new ConversationTurn("Earlier question", "Earlier answer")));

        assertThat(context.material())
                .contains("Party code: PJD")
                .contains("Create 200,000 jobs each year")
                .contains("Five-year verdict: HARD")
                .contains("RECENT PRIVATE CONVERSATION")
                .doesNotContain("RNI");
        assertThat(context.material().indexOf("Title: Create 200,000 jobs each year"))
                .isLessThan(context.material().indexOf("Title: Digitise courts"));
        assertThat(context.sources()).containsKeys("PROGRAMME", "PROMISE_2", "PROMISE_2_E1");
    }

    private static PartyPromise promise(UUID programmeId, String slug, String title, String locator) {
        return new PartyPromise(
                UUID.randomUUID(), programmeId, slug, slug, text(title, title, title), title, locator, "", "",
                EditorialStatus.PUBLISHED, Instant.now(), Instant.now(), Instant.now());
    }

    private static PromiseAssessment assessment(UUID promiseId) {
        LocalizedText text = text("صعيب", "Difficile", "Difficult");
        return new PromiseAssessment(
                UUID.randomUUID(), promiseId, 1, 5, FeasibilityVerdict.HARD,
                text, text, text, text, "v1", "consensus", "models", LocalDate.of(2026, 9, 1),
                EditorialStatus.PUBLISHED, Instant.now(), Instant.now(),
                List.of(new Evidence(
                        UUID.randomUUID(), "HCP", "Jobs report", "https://hcp.example/jobs",
                        LocalDate.of(2026, 8, 1), "Official employment baseline", 0)));
    }

    private static LocalizedText text(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
