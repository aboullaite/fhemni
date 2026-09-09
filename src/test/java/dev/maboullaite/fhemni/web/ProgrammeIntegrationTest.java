package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import dev.maboullaite.fhemni.programme.FeasibilityVerdict;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftAssessment;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftProgramme;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftPromise;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.EvidenceDraft;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ProgrammeExtraction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:programme-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ProgrammeIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PartyProgrammeService programmes;

    @Test
    void keepsDraftsPrivateAndPublishesAnImmutableEvidenceBackedFiveYearAssessment() throws Exception {
        mvc.perform(get("/api/admin/programmes"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/programmes").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/programmes").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(get("/api/admin/programmes/assessment-jobs"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/programmes/assessment-jobs").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/programmes/assessment-jobs").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        var programme = programmes.createProgramme(new DraftProgramme(
                "PAM",
                localized("برنامج 2026", "Programme 2026", "2026 programme"),
                localized("الخلاصة", "Le résumé", "The summary"),
                "https://pam.ma/fr/programme-electoral/",
                "Programme électoral officiel 2026",
                "fr",
                "Frozen official programme text for editorial review.",
                true,
                List.of("Page 14 was difficult to read.")));
        assertThat(programme.electionYear()).isEqualTo(2026);
        assertThat(programme.termStartYear()).isEqualTo(2026);
        assertThat(programme.termEndYear()).isEqualTo(2031);
        assertThat(programme.sourceSha256()).hasSize(64);
        assertThat(programmes.programmeBySourceUrl("https://pam.ma/fr/programme-electoral/")
                .orElseThrow().extractionWarnings()).containsExactly("Page 14 was difficult to read.");

        mvc.perform(get("/api/catalog/parties/PAM/programme"))
                .andExpect(status().isNotFound());

        var promise = programmes.createPromise(programme.id(), new DraftPromise(
                "million-net-jobs",
                "employment",
                localized("مليون منصب شغل", "Un million d'emplois", "One million jobs"),
                "Créer un million d'emplois nets pendant le mandat.",
                "Engagement 4, page 12",
                "Investment and labour-market measures.",
                "The programme states an aggregate envelope."));
        var assessment = programmes.createAssessment(promise.promise().id(), new DraftAssessment(
                FeasibilityVerdict.HARD,
                localized("HARD. ممكن ولكن صعيب", "C’est POSSIBLE, mais difficile", "HARD. Possible, but hard"),
                localized("خاص نمو قوي", "Une croissance forte est nécessaire", "INSUFFICIENT_DATA without a baseline"),
                localized("النتيجة كتبدل مع النمو", "Le résultat dépend de la croissance", "The result depends on growth"),
                localized("200 ألف منصب فالسنة", "200 000 emplois par an", "200,000 jobs per year"),
                "fhemni-feasibility-v1",
                LocalDate.of(2026, 9, 1),
                List.of(new EvidenceDraft(
                        "HCP",
                        "Labour market indicators",
                        "https://www.hcp.ma/",
                        LocalDate.of(2026, 8, 1),
                        "Official baseline for employment."))));

        assertThat(assessment.summary().ar()).startsWith("صعيب التحقيق فـ5 سنين.");
        assertThat(assessment.summary().fr()).startsWith("C’est réalisable");
        assertThat(assessment.summary().en()).startsWith("difficult to achieve within five years.");
        assertThat(assessment.requirements().en()).startsWith("insufficient data");

        assertThatThrownBy(() -> programmes.publishProgramme(programme.id()))
                .isInstanceOf(IllegalStateException.class);
        programmes.publishAssessment(assessment.id());
        programmes.publishPromise(promise.promise().id());
        programmes.publishProgramme(programme.id());

        var chatDossier = programmes.publishedChatDossier("PAM");
        assertThat(chatDossier.programme().sourceSnapshot())
                .isEqualTo("Frozen official programme text for editorial review.");
        assertThat(chatDossier.promises()).singleElement().satisfies(item -> {
            assertThat(item.promise().slug()).isEqualTo("million-net-jobs");
            assertThat(item.assessment()).isNotNull();
            assertThat(item.assessment().status().name()).isEqualTo("PUBLISHED");
            assertThat(item.assessment().evidence()).singleElement()
                    .satisfies(evidence -> assertThat(evidence.publisher()).isEqualTo("HCP"));
        });

        mvc.perform(get("/api/catalog/parties/PAM/programme"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("max-age=300")))
                .andExpect(jsonPath("$.electionYear").value(2026))
                .andExpect(jsonPath("$.termStartYear").value(2026))
                .andExpect(jsonPath("$.termEndYear").value(2031))
                .andExpect(jsonPath("$.promises[0].slug").value("million-net-jobs"))
                .andExpect(jsonPath("$.promises[0].verdict").value("HARD"))
                .andExpect(jsonPath("$.sourceSnapshot").doesNotExist());

        mvc.perform(get("/api/catalog/promises/million-net-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyCode").value("PAM"))
                .andExpect(jsonPath("$.assessment.horizonYears").value(5))
                .andExpect(jsonPath("$.assessment.evidence[0].publisher").value("HCP"));

        var revisedAssessment = programmes.createAssessment(
                promise.promise().id(),
                assessment(FeasibilityVerdict.POSSIBLE, "https://www.hcp.ma/revised"));
        programmes.publishAssessment(revisedAssessment.id());

        var revisedPromise = programmes.adminProgrammes().stream()
                .filter(item -> item.id().equals(programme.id()))
                .flatMap(item -> item.promises().stream())
                .filter(item -> item.promise().id().equals(promise.promise().id()))
                .findFirst()
                .orElseThrow();
        assertThat(revisedPromise.assessments())
                .extracting(item -> item.status().name())
                .containsExactly("PUBLISHED", "SUPERSEDED");
        mvc.perform(get("/api/catalog/promises/million-net-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment.verdict").value("POSSIBLE"))
                .andExpect(jsonPath("$.assessment.revisionNumber").value(2));

        var ppsProgramme = programmes.createProgramme(new DraftProgramme(
                "PPS",
                localized("برنامج التقدم والاشتراكية", "Programme du PPS", "PPS programme"),
                localized("الخلاصة", "Le résumé", "The summary"),
                "https://pps.ma/programme-2026",
                "Official PPS programme",
                "fr",
                "Frozen PPS programme text.",
                true));
        var ppsPromise = programmes.createPromise(ppsProgramme.id(), new DraftPromise(
                "pps-health-promise", "health",
                localized("وعد الصحة", "Promesse santé", "Health promise"),
                "A published health promise.", "Page 7", "Mechanism", "Financing"));
        programmes.createAssessment(ppsPromise.promise().id(), assessment("https://www.hcp.ma/health"));
        programmes.publishAll(ppsProgramme.id());

        assertThat(programmes.featuredPublishedPromises(6))
                .extracting(PartyProgrammeService.PublicPromiseHighlight::partyCode)
                .contains("PAM", "PPS")
                .doesNotHaveDuplicates();

        mvc.perform(get("/api/catalog/promises?size=3"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("max-age=300")))
                .andExpect(jsonPath("$[*].slug", org.hamcrest.Matchers.hasItem("million-net-jobs")))
                .andExpect(jsonPath("$[*].partyCode", org.hamcrest.Matchers.hasItem("PAM")))
                .andExpect(jsonPath("$[*].partyCode", org.hamcrest.Matchers.hasItem("PPS")))
                .andExpect(jsonPath("$[*].verdict", org.hamcrest.Matchers.hasItem("HARD")));

        assertThatThrownBy(() -> programmes.createPromise(programme.id(), new DraftPromise(
                "second-promise", "economy", localized("وعد", "Promesse", "Promise"),
                "Text", "Page 1", "", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable");

        mvc.perform(get("/promises/million-net-jobs"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/promise.html"));
        mvc.perform(get("/promise.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("promiseDetail")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("promise-deep-dive")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("promise-ai-attribution")));
    }

    @Test
    void publishesACompletePartyProgrammeAtomicallyAndRejectsAnIncompleteOne() throws Exception {
        var programme = programmes.createProgramme(new DraftProgramme(
                "RNI",
                localized("برنامج التجمع", "Programme du RNI", "RNI programme"),
                localized("الخلاصة", "Le résumé", "The summary"),
                "https://rni.ma/programme-2026",
                "Official RNI programme",
                "fr",
                "Frozen RNI programme text.",
                true));
        var first = programmes.createPromise(programme.id(), new DraftPromise(
                "rni-first-promise", "economy", localized("الوعد اللول", "Première promesse", "First promise"),
                "First exact promise.", "Page 4", "Mechanism", "Financing"));
        mvc.perform(post("/api/admin/programmes/{programmeId}/promises", programme.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "rni-first-promise",
                                  "topic": "economy",
                                  "title": {"ar":"وعد","fr":"Promesse","en":"Promise"},
                                  "promiseText": "Duplicate promise.",
                                  "sourceLocator": "Page 5",
                                  "mechanism": "Mechanism",
                                  "financing": "Financing"
                                }
                                """))
                .andExpect(status().isConflict());
        programmes.createAssessment(first.promise().id(), assessment("https://www.hcp.ma/first"));
        programmes.createPromise(programme.id(), new DraftPromise(
                "rni-second-promise", "jobs", localized("الوعد الثاني", "Deuxième promesse", "Second promise"),
                "Second exact promise.", "Page 8", "Mechanism", "Financing"));

        assertThatThrownBy(() -> programmes.publishAll(programme.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Every promise");
        var untouched = programmes.adminProgrammes().stream()
                .filter(item -> item.id().equals(programme.id()))
                .findFirst()
                .orElseThrow();
        assertThat(untouched.status().name()).isEqualTo("DRAFT");
        assertThat(untouched.promises())
                .allSatisfy(item -> assertThat(item.promise().status().name()).isEqualTo("DRAFT"));
        assertThat(untouched.promises().getFirst().assessments().getFirst().status().name()).isEqualTo("DRAFT");

        var second = untouched.promises().stream()
                .filter(item -> item.promise().slug().equals("rni-second-promise"))
                .findFirst()
                .orElseThrow();
        programmes.createAssessment(second.promise().id(), assessment("https://www.hcp.ma/second"));

        mvc.perform(post("/api/admin/programmes/{programmeId}/publish-all", programme.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.promises.length()").value(2))
                .andExpect(jsonPath("$.promises[0].promise.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.promises[1].promise.status").value("PUBLISHED"));

        mvc.perform(get("/api/catalog/parties/RNI/programme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promises.length()").value(2));
    }

    @Test
    void refusesToReplaceADraftProgrammeAfterAChildWasPublished() {
        LocalizedText title = localized("برنامج الاتحاد", "Programme UC", "UC programme");
        var programme = programmes.createProgramme(new DraftProgramme(
                "UC", title, title,
                "https://uc.ma/programme-2026", "Official UC programme", "fr",
                "Frozen UC programme text.", true));
        var promise = programmes.createPromise(programme.id(), new DraftPromise(
                "uc-testable-promise", "institutions", title,
                "A testable commitment.", "Page 3", "Mechanism", "Financing"));
        var assessment = programmes.createAssessment(
                promise.promise().id(), assessment("https://www.hcp.ma/uc"));
        programmes.publishAssessment(assessment.id());
        ProgrammeExtraction replacement = new ProgrammeExtraction(
                "UC", 2026, true, "Official UC programme", "fr", "Replacement snapshot",
                title, title, List.of(), List.of(new ExtractedPromise(
                        "uc-replacement", "economy", title, "Replacement", "Page 4", "", "")));

        assertThatThrownBy(() -> programmes.replaceGeneratedExtraction(
                programme.id(), "https://uc.ma/programme-2026", replacement, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published promises or assessments");

        var preserved = programmes.adminProgrammes().stream()
                .filter(item -> item.id().equals(programme.id()))
                .findFirst()
                .orElseThrow();
        assertThat(preserved.promises().getFirst().assessments().getFirst().status().name())
                .isEqualTo("PUBLISHED");
    }

    private static DraftAssessment assessment(String evidenceUrl) {
        return assessment(FeasibilityVerdict.HARD, evidenceUrl);
    }

    private static DraftAssessment assessment(FeasibilityVerdict verdict, String evidenceUrl) {
        return new DraftAssessment(
                verdict,
                localized("ممكن ولكن صعيب", "Possible, mais difficile", "Possible, but hard"),
                localized("خاص شروط", "Des conditions sont requises", "Conditions are required"),
                localized("افتراض", "Hypothèse", "Assumption"),
                localized("حساب", "Calcul", "Calculation"),
                "fhemni-feasibility-v1",
                LocalDate.of(2026, 9, 1),
                List.of(new EvidenceDraft(
                        "HCP", "Official indicator", evidenceUrl,
                        LocalDate.of(2026, 8, 1), "Official baseline.")));
    }

    private static LocalizedText localized(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
