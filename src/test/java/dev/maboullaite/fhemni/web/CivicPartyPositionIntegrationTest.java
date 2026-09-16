package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:civic-position-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CivicPartyPositionIntegrationTest {

    private static final String EDITION_ID = "c1000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void adminListIsEmptyWhenNoPositionsExist() throws Exception {
        mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
    }

    @Test
    @Order(2)
    void adminCanCreateADraftPositionViaBatchEndpoint() throws Exception {
        mvc.perform(post("/api/admin/civic-positions/" + EDITION_ID + "/batch")
                        .with(csrf())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "questionKey": "targeted-subsidies",
                                    "partyCode": "RNI",
                                    "stance": "SUPPORTS",
                                    "evidenceSummaryAr": "الحزب كيدعم توجيه الدعم",
                                    "evidenceSummaryFr": "Le parti soutient le ciblage des aides",
                                    "evidenceSummaryEn": "The party supports targeting subsidies",
                                    "reviewerNote": "Programme page 12"
                                  },
                                  {
                                    "questionKey": "targeted-subsidies",
                                    "partyCode": "PAM",
                                    "stance": "MIXED",
                                    "evidenceSummaryAr": "موقف مختلط",
                                    "evidenceSummaryFr": "Position mixte",
                                    "evidenceSummaryEn": "Mixed position on targeting",
                                    "reviewerNote": null
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));
    }

    @Test
    @Order(3)
    void draftPositionsAreVisibleInAdminList() throws Exception {
        mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @Order(4)
    void adminCanListDraftPositions() throws Exception {
        mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].questionKey").value("targeted-subsidies"));
    }

    @Test
    @Order(5)
    void adminCanPublishPositionsAndTheyAppearInMatrix() throws Exception {
        MvcResult listResult = mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andReturn();
        String body = listResult.getResponse().getContentAsString();
        String id1 = extractId(body, 0);
        String id2 = extractId(body, 1);

        mvc.perform(post("/api/admin/civic-positions/" + id1 + "/publish")
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/civic-positions/" + id2 + "/publish")
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @Order(6)
    void adminUpsertUpdatesExistingPosition() throws Exception {
        mvc.perform(put("/api/admin/civic-positions/" + EDITION_ID)
                        .with(csrf())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionKey": "targeted-subsidies",
                                  "partyCode": "RNI",
                                  "stance": "OPPOSES",
                                  "evidenceSummaryAr": "تحديث",
                                  "evidenceSummaryFr": "Mise à jour",
                                  "evidenceSummaryEn": "Updated stance",
                                  "reviewerNote": "Corrected after review"
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.partyCode == 'RNI' && @.questionKey == 'targeted-subsidies')].stance")
                        .value("OPPOSES"));
    }

    @Test
    @Order(7)
    void unauthenticatedUsersCannotAccessAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/civic-positions/" + EDITION_ID))
                .andExpect(status().isUnauthorized());
    }

    private static String extractId(String jsonArray, int index) {
        int start = 0;
        for (int i = 0; i <= index; i++) {
            start = jsonArray.indexOf("\"id\":\"", start) + 6;
        }
        int end = jsonArray.indexOf("\"", start);
        return jsonArray.substring(start, end);
    }
}
