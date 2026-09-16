package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
}
