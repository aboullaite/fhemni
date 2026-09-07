package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        var programme = programmes.createProgramme(new DraftProgramme(
                "PAM",
                localized("برنامج 2026", "Programme 2026", "2026 programme"),
                localized("الخلاصة", "Le résumé", "The summary"),
                "https://pam.ma/fr/programme-electoral/",
                "Programme électoral officiel 2026",
                "fr",
                "Frozen official programme text for editorial review.",
                true));
        assertThat(programme.electionYear()).isEqualTo(2026);
        assertThat(programme.termStartYear()).isEqualTo(2026);
        assertThat(programme.termEndYear()).isEqualTo(2031);
        assertThat(programme.sourceSha256()).hasSize(64);

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
                localized("ممكن ولكن صعيب", "Possible, mais difficile", "Possible, but hard"),
                localized("خاص نمو قوي", "Une croissance forte est nécessaire", "Strong growth is required"),
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

        assertThatThrownBy(() -> programmes.publishProgramme(programme.id()))
                .isInstanceOf(IllegalStateException.class);
        programmes.publishAssessment(assessment.id());
        programmes.publishPromise(promise.promise().id());
        programmes.publishProgramme(programme.id());

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("promiseDetail")));
    }

    private static LocalizedText localized(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
