package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:civic-questionnaire-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CivicQuestionnaireIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void exposesThePublishedEighteenQuestionEditionInEverySupportedLanguage() throws Exception {
        for (String language : new String[] {"ar", "fr", "en"}) {
            mvc.perform(get("/api/catalog/questionnaires/current").param("lang", language))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600, public"))
                    .andExpect(jsonPath("$.language").value(language))
                    .andExpect(jsonPath("$.direction").value(language.equals("ar") ? "rtl" : "ltr"))
                    .andExpect(jsonPath("$.questions", hasSize(18)))
                    .andExpect(jsonPath("$.questions[0].prompt").isNotEmpty())
                    .andExpect(jsonPath("$.questions[0].context").isNotEmpty())
                    .andExpect(jsonPath("$.questions[0].sources[0].url").isNotEmpty());
        }

        mvc.perform(get("/api/catalog/questionnaires/current").param("lang", "ar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[1].prompt").value(
                        "خاص السميگ ومعاشات التقاعد يطلعو مع الغلا، حتى إلا هادشي غادي يزيد فالمصاريف ديال الشركات والدولة."))
                .andExpect(jsonPath("$.questions[5].prompt").value(
                        "الجامعات والتكوين المهني خاصهم يربطو القراية أكثر بالستاجات وبالمهارات اللي محتاجها سوق الشغل."));

        mvc.perform(get("/api/catalog/questionnaires/current").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[5].prompt").value(
                        "Les universités et la formation professionnelle devraient davantage relier les études aux stages et aux compétences recherchées sur le marché du travail."));

        mvc.perform(get("/api/catalog/questionnaires/current").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[5].prompt").value(
                        "Universities and vocational training should link learning more closely to internships and the skills needed in the labour market."));
    }

    @Test
    void calculatesAStatelessPersonalProfileWithoutRankingParties() throws Exception {
        mvc.perform(post("/api/catalog/questionnaires/current/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "language": "en",
                                  "answers": [
                                    {"questionKey":"targeted-subsidies","value":2,"important":true},
                                    {"questionKey":"income-catch-up","value":1,"important":false},
                                    {"questionKey":"essential-tax-relief","value":-2,"important":true},
                                    {"questionKey":"competition-prices","value":1,"important":false},
                                    {"questionKey":"sme-jobs","value":2,"important":true},
                                    {"questionKey":"youth-apprenticeships","value":1,"important":false}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.answeredCount").value(6))
                .andExpect(jsonPath("$.questionCount").value(18))
                .andExpect(jsonPath("$.priorities", hasSize(2)))
                .andExpect(jsonPath("$.priorities[0].themeCode").value("PURCHASING_POWER"))
                .andExpect(jsonPath("$.strongestPositions", hasSize(3)))
                .andExpect(jsonPath("$.partyMatches").doesNotExist());
    }

    @Test
    void rejectsUnsupportedLocalesDuplicateAnswersAndOutOfRangeValues() throws Exception {
        mvc.perform(get("/api/catalog/questionnaires/current").param("lang", "de"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/catalog/questionnaires/current/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "language": "ar",
                                  "answers": [
                                    {"questionKey":"targeted-subsidies","value":3,"important":false},
                                    {"questionKey":"targeted-subsidies","value":1,"important":false}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
