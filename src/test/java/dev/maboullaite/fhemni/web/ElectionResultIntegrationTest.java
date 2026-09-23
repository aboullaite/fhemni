package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:election-result-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ElectionResultIntegrationTest {

    private static final UUID ELECTION_ID = UUID.fromString("20260000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private ElectionCoalitionRateLimiter coalitionRateLimiter;

    @BeforeEach
    void loadSyntheticResultSnapshot() {
        reset(coalitionRateLimiter);
        jdbc.sql("DELETE FROM election_region_party_results WHERE election_id = :electionId")
                .param("electionId", ELECTION_ID).update();
        jdbc.sql("DELETE FROM election_party_results WHERE election_id = :electionId")
                .param("electionId", ELECTION_ID).update();
        jdbc.sql("""
                        UPDATE elections
                           SET status = 'PRELIMINARY',
                               registered_voters = 2000,
                               votes_cast = 1200,
                               valid_votes = 1000,
                               vote_basis = 'OFFICIAL_AGGREGATE',
                               source_url = 'https://example.test/official-results',
                               source_updated_at = CURRENT_TIMESTAMP,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = :electionId
                        """)
                .param("electionId", ELECTION_ID).update();
        insertNational("RNI", 420, 70, 20);
        insertNational("PAM", 330, 55, 15);
        insertNational("PJD", 250, 31, 9);

        jdbc.sql("""
                        UPDATE election_regions
                           SET allocated_seats = 12, status = 'PARTIAL', updated_at = CURRENT_TIMESTAMP
                         WHERE election_id = :electionId AND code = 'casablanca-settat'
                        """).param("electionId", ELECTION_ID).update();
        insertRegional("casablanca-settat", "RNI", 4, 1);
        insertRegional("casablanca-settat", "PAM", 3, 1);
        insertRegional("casablanca-settat", "PJD", 2, 1);
    }

    @Test
    void servesACompletePublicSnapshotInTheRequestedLanguage() throws Exception {
        mvc.perform(get("/api/catalog/elections/2026/results").param("lang", "ar"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=5")))
                .andExpect(jsonPath("$.language").value("ar"))
                .andExpect(jsonPath("$.election.totalSeats").value(395))
                .andExpect(jsonPath("$.election.majoritySeats").value(198))
                .andExpect(jsonPath("$.election.declaredSeats").value(200))
                .andExpect(jsonPath("$.election.turnoutPercent").value(60.0))
                .andExpect(jsonPath("$.parties", hasSize(3)))
                .andExpect(jsonPath("$.parties[0].code").value("RNI"))
                .andExpect(jsonPath("$.regions", hasSize(12)))
                .andExpect(jsonPath("$.regions[5].mapKey").value("MA-06"))
                .andExpect(jsonPath("$.regions[5].parties", hasSize(3)));
    }

    @Test
    void exposesTheResponsiveResultPageAndItsInteractiveSurfaces() throws Exception {
        mvc.perform(get("/elections/2026"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/election-results.html"));
        mvc.perform(get("/election-results.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"electionMapPanel\"")))
                .andExpect(content().string(containsString("id=\"electionNationalPanel\"")))
                .andExpect(content().string(containsString("id=\"electionCoalitionPanel\"")))
                .andExpect(content().string(containsString("/js/election-results.js?v=20260923-7")));
    }

    @Test
    void evaluatesCoalitionSeatsAndProgrammeAlignmentWithoutAuthentication() throws Exception {
        mvc.perform(post("/api/catalog/elections/2026/coalitions/evaluate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"en","partyCodes":["RNI","PAM","PJD"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.selectedSeats").value(200))
                .andExpect(jsonPath("$.majoritySeats").value(198))
                .andExpect(jsonPath("$.hasMajority").value(true))
                .andExpect(jsonPath("$.seatsAboveMajority").value(2))
                .andExpect(jsonPath("$.alignment.status").exists());
        verify(coalitionRateLimiter).check(anyString());
    }

    @Test
    void doesNotChargeQuotaForRequestsRejectedByCsrfOrMediaTypeValidation() throws Exception {
        String body = """
                {"language":"en","partyCodes":["RNI","PAM"]}
                """;
        mvc.perform(post("/api/catalog/elections/2026/coalitions/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/catalog/elections/2026/coalitions/evaluate")
                        .with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(body))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(coalitionRateLimiter);
    }

    @Test
    void returnsStructuredRateLimitResponsesAfterRequestValidation() throws Exception {
        doThrow(new ElectionCoalitionRateLimitException(17))
                .when(coalitionRateLimiter).check(anyString());

        mvc.perform(post("/api/catalog/elections/2026/coalitions/evaluate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"en","partyCodes":["RNI","PAM"]}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "17"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void rejectsOversizedCoalitionJsonBeforeRequestBodyDeserialization() throws Exception {
        mvc.perform(post("/api/catalog/elections/2026/coalitions/evaluate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(ElectionCoalitionRequestFilter.MAX_REQUEST_BYTES + 1)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413));
        verifyNoInteractions(coalitionRateLimiter);
    }

    @Test
    void rejectsUnknownLanguagesYearsAndCoalitionParties() throws Exception {
        mvc.perform(get("/api/catalog/elections/2026/results").param("lang", "es"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/elections/2025/results"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/catalog/elections/2026/coalitions/evaluate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"ar","partyCodes":["RNI","NOTREAL"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void insertNational(String partyCode, long votes, int localSeats, int regionalSeats) {
        jdbc.sql("""
                        INSERT INTO election_party_results (
                            election_id, party_code, votes, local_seats,
                            regional_list_seats, total_seats, updated_at
                        ) VALUES (
                            :electionId, :partyCode, :votes, :localSeats,
                            :regionalSeats, :totalSeats, CURRENT_TIMESTAMP
                        )
                        """)
                .param("electionId", ELECTION_ID)
                .param("partyCode", partyCode)
                .param("votes", votes)
                .param("localSeats", localSeats)
                .param("regionalSeats", regionalSeats)
                .param("totalSeats", localSeats + regionalSeats)
                .update();
    }

    private void insertRegional(String regionCode, String partyCode, int localSeats, int regionalSeats) {
        jdbc.sql("""
                        INSERT INTO election_region_party_results (
                            election_id, region_code, party_code, local_seats,
                            regional_list_seats, total_seats, updated_at
                        ) VALUES (
                            :electionId, :regionCode, :partyCode, :localSeats,
                            :regionalSeats, :totalSeats, CURRENT_TIMESTAMP
                        )
                        """)
                .param("electionId", ELECTION_ID)
                .param("regionCode", regionCode)
                .param("partyCode", partyCode)
                .param("localSeats", localSeats)
                .param("regionalSeats", regionalSeats)
                .param("totalSeats", localSeats + regionalSeats)
                .update();
    }
}
