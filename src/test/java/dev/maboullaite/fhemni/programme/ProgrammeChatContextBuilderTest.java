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
                .contains("published feasibility=HARD")
                .contains("assessment=[ASSESSMENT_2]")
                .contains("Evidence [PROMISE_2_E1]: HCP — Jobs report")
                .contains("--- Official document extract 1; cite [PROGRAMME] ---")
                .contains("RECENT PRIVATE CONVERSATION")
                .doesNotContain("[PROGRAMME, document extract")
                .doesNotContain("RNI");
        assertThat(context.material().indexOf("Title: Create 200,000 jobs each year"))
                .isLessThan(context.material().indexOf("Title: Digitise courts"));
        assertThat(context.sources()).containsKeys(
                "PROGRAMME", "PROMISE_2", "ASSESSMENT_2", "PROMISE_2_E1");
    }

    @Test
    void usesRecentQuestionsToKeepFollowUpRetrievalOnTheSamePromise() {
        UUID programmeId = UUID.randomUUID();
        PartyProgramme programme = new PartyProgramme(
                programmeId, "PJD", 2026, 2026, 2031,
                text("برنامج", "Programme", "Programme"), text("", "", ""),
                "https://pjd.example/programme.pdf", "Official programme", "ar", "",
                List.of(), "sha", Instant.now(), true, EditorialStatus.PUBLISHED,
                Instant.now(), Instant.now(), Instant.now());
        PartyPromise justice = promise(programmeId, "justice", "Digitise courts", "page 45");
        PartyPromise jobs = promise(programmeId, "jobs", "Create industrial jobs", "page 12");
        ProgrammeChatDossier dossier = new ProgrammeChatDossier(programme, List.of(
                new ProgrammeChatPromise(justice, null),
                new ProgrammeChatPromise(jobs, assessment(jobs.id()))));

        ProgrammeChatContext context = new ProgrammeChatContextBuilder(90_000).build(
                dossier, "How would that be funded?", OutputLanguage.ENGLISH,
                List.of(new ConversationTurn("What does it promise for industrial jobs?", "It promises jobs.")));

        assertThat(context.material().indexOf("Title: Create industrial jobs"))
                .isLessThan(context.material().indexOf("Title: Digitise courts"));
    }

    @Test
    void keepsCompactPublishedVerdictsAvailableBeyondTheDetailedTopEight() {
        UUID programmeId = UUID.randomUUID();
        PartyProgramme programme = programme(programmeId, "Short official source");
        List<ProgrammeChatPromise> promises = new java.util.ArrayList<>();
        for (int index = 1; index <= 9; index++) {
            promises.add(new ProgrammeChatPromise(
                    promise(programmeId, "topic-" + index, "Promise " + index, "page " + index), null));
        }
        PartyPromise last = promise(programmeId, "hard-promise", "Promise 10", "page 10");
        promises.add(new ProgrammeChatPromise(last, assessment(last.id())));

        ProgrammeChatContext context = new ProgrammeChatContextBuilder(90_000).build(
                new ProgrammeChatDossier(programme, promises),
                "Give me a general overview", OutputLanguage.ENGLISH, List.of());

        assertThat(context.material())
                .contains("[PROMISE_10] Promise 10")
                .contains("assessment=[ASSESSMENT_10]")
                .contains("published feasibility=HARD")
                .doesNotContain("Title: Promise 10");
        assertThat(context.sources()).containsKey("ASSESSMENT_10");
        assertThat(context.sources()).doesNotContainKey("PROMISE_10_E1");
    }

    @Test
    void boundsTheCompactAssessmentInventoryForLargeProgrammes() {
        UUID programmeId = UUID.randomUUID();
        PartyProgramme programme = programme(programmeId, "Short official source");
        List<ProgrammeChatPromise> promises = new java.util.ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            PartyPromise promise = promise(programmeId, "topic-" + index, "Promise " + index, "page " + index);
            promises.add(new ProgrammeChatPromise(
                    promise, assessment(promise.id(), "Detailed feasibility summary ".repeat(80))));
        }

        ProgrammeChatContext context = new ProgrammeChatContextBuilder(90_000).build(
                new ProgrammeChatDossier(programme, promises),
                "Give me a general overview", OutputLanguage.ENGLISH, List.of());

        String inventory = context.material().substring(
                context.material().indexOf("PUBLISHED PROMISE INVENTORY"),
                context.material().indexOf("RELEVANT OFFICIAL PROGRAMME EXTRACTS"));
        assertThat(inventory.length()).isLessThan(12_000);
        assertThat(context.material()).contains("RELEVANT EXTRACTS FROM THE FROZEN OFFICIAL DOCUMENT");
    }

    @Test
    void expandsEnglishRetrievalWithThePromisesOriginalArabicWording() {
        UUID programmeId = UUID.randomUUID();
        String source = "مقدمة عامة ".repeat(400)
                + " خلق مناصب الشغل الصناعية وتمويلها عبر الاستثمار ";
        PartyProgramme programme = programme(programmeId, source);
        PartyPromise jobs = new PartyPromise(
                UUID.randomUUID(), programmeId, "jobs", "employment",
                text("خلق مناصب الشغل الصناعية", "Créer des emplois industriels", "Create industrial jobs"),
                "خلق مناصب الشغل الصناعية", "الصفحة 12", "تمويلها عبر الاستثمار", "الاستثمار",
                EditorialStatus.PUBLISHED, Instant.now(), Instant.now(), Instant.now());

        ProgrammeChatContext context = new ProgrammeChatContextBuilder(90_000).build(
                new ProgrammeChatDossier(programme, List.of(new ProgrammeChatPromise(jobs, null))),
                "What does it say about industrial jobs?", OutputLanguage.ENGLISH, List.of());

        String extracts = context.material().substring(
                context.material().indexOf("RELEVANT EXTRACTS FROM THE FROZEN OFFICIAL DOCUMENT"));
        assertThat(extracts).startsWith("RELEVANT EXTRACTS FROM THE FROZEN OFFICIAL DOCUMENT")
                .contains("--- Official document extract 2; cite [PROGRAMME] ---");
    }

    private static PartyProgramme programme(UUID programmeId, String sourceSnapshot) {
        return new PartyProgramme(
                programmeId, "PJD", 2026, 2026, 2031,
                text("برنامج 2026", "Programme 2026", "2026 programme"),
                text("خلاصة", "Résumé", "Summary"),
                "https://pjd.example/programme.pdf", "PJD official programme", "ar",
                sourceSnapshot, List.of(), "sha", Instant.now(), true, EditorialStatus.PUBLISHED,
                Instant.now(), Instant.now(), Instant.now());
    }

    private static PartyPromise promise(UUID programmeId, String slug, String title, String locator) {
        return new PartyPromise(
                UUID.randomUUID(), programmeId, slug, slug, text(title, title, title), title, locator, "", "",
                EditorialStatus.PUBLISHED, Instant.now(), Instant.now(), Instant.now());
    }

    private static PromiseAssessment assessment(UUID promiseId) {
        return assessment(promiseId, "Difficult");
    }

    private static PromiseAssessment assessment(UUID promiseId, String summary) {
        LocalizedText summaryText = text(summary, summary, summary);
        LocalizedText detailText = text("صعيب", "Difficile", "Difficult");
        return new PromiseAssessment(
                UUID.randomUUID(), promiseId, 1, 5, FeasibilityVerdict.HARD,
                summaryText, detailText, detailText, detailText,
                "v1", "consensus", "models", LocalDate.of(2026, 9, 1),
                EditorialStatus.PUBLISHED, Instant.now(), Instant.now(),
                List.of(new Evidence(
                        UUID.randomUUID(), "HCP", "Jobs report", "https://hcp.example/jobs",
                        LocalDate.of(2026, 8, 1), "Official employment baseline", 0)));
    }

    private static LocalizedText text(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
