package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.civic.position-seed=classpath:data/civic-party-positions-2026-v1.json",
        "spring.datasource.url=jdbc:h2:mem:civic-position-seed-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CivicPositionSeedIntegrationTest {

    private static final String EDITION_ID = "c1000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Test
    void packagedSeedPublishesTheCompleteReviewedMatrix() throws Exception {
        mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(216)));

        mvc.perform(post("/api/catalog/questionnaires/current/compass")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "language": "ar",
                                  "answers": [
                                    {"questionKey": "targeted-subsidies", "value": 0, "important": false}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPositionsReviewed").value(216))
                .andExpect(jsonPath("$.parties", hasSize(12)));
    }

    @Test
    void seededStancesMatchRevisedClassifications() throws Exception {
        var positions = fetchPositions();

        assertThat(positions).hasSize(216);

        var keys = positions.stream()
                .map(p -> p.get("partyCode") + "/" + p.get("questionKey"))
                .collect(java.util.stream.Collectors.toSet());
        assertThat(keys).as("216 unique party/question pairs").hasSize(216);

        assertStance(positions, "PJD", "equality-care", "OPPOSES");
        assertStance(positions, "PJD", "competition-prices", "OPPOSES");
        assertStance(positions, "PJD", "water-demand", "OPPOSES");
        assertStance(positions, "PJD", "learning-accountability", "SUPPORTS");
        assertStance(positions, "UC", "equality-care", "SUPPORTS");
        assertStance(positions, "UC", "competition-prices", "SUPPORTS");
        assertStance(positions, "UC", "essential-tax-relief", "SUPPORTS");
        assertStance(positions, "FFD", "competition-prices", "OPPOSES");
        assertStance(positions, "FFD", "sme-jobs", "MIXED");
        assertStance(positions, "FFD", "learning-accountability", "SUPPORTS");
        assertStance(positions, "FGD", "competition-prices", "MIXED");
        assertStance(positions, "FGD", "targeted-subsidies", "SUPPORTS");
        assertStance(positions, "FGD", "sme-jobs", "SUPPORTS");
        assertStance(positions, "PPS", "competition-prices", "MIXED");
        assertStance(positions, "PPS", "essential-tax-relief", "NO_POSITION");
        assertStance(positions, "USFP", "competition-prices", "MIXED");
        assertStance(positions, "USFP", "water-demand", "SUPPORTS");
        assertStance(positions, "USFP", "water-allocation", "SUPPORTS");
        assertStance(positions, "MP", "competition-prices", "MIXED");
        assertStance(positions, "RNI", "equality-care", "MIXED");
        assertStance(positions, "PUD", "equality-care", "NO_POSITION");
        assertStance(positions, "PUD", "water-allocation", "MIXED");
        assertStance(positions, "PUD", "water-demand", "MIXED");
    }

    @Test
    void pjdAndUcDifferOnExactlyFiveQuestions() throws Exception {
        var positions = fetchPositions();

        var allQuestions = positions.stream()
                .map(p -> (String) p.get("questionKey")).collect(java.util.stream.Collectors.toSet());

        var differing = allQuestions.stream()
                .filter(q -> !stanceOf(positions, "PJD", q).equals(stanceOf(positions, "UC", q)))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(differing).as("PJD/UC differences")
                .containsExactlyInAnyOrder(
                        "equality-care", "competition-prices", "water-demand",
                        "essential-tax-relief", "learning-accountability");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchPositions() throws Exception {
        String json = mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new JacksonJsonParser().parseList(json).stream()
                .map(o -> (Map<String, Object>) o).toList();
    }

    private static void assertStance(List<Map<String, Object>> positions,
                                     String party, String question, String expected) {
        String actual = stanceOf(positions, party, question);
        assertThat(actual).as(party + "/" + question).isEqualTo(expected);
    }

    private static String stanceOf(List<Map<String, Object>> positions,
                                   String party, String question) {
        return positions.stream()
                .filter(p -> party.equals(p.get("partyCode")) && question.equals(p.get("questionKey")))
                .map(p -> (String) p.get("stance"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No position for " + party + "/" + question));
    }
}
