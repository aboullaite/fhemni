package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.Chapter;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimKind;
import dev.maboullaite.fhemni.model.ClaimVerdict;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.Participant;
import dev.maboullaite.fhemni.model.VideoReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:public-people-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class PublicPeopleIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AnalysisRevisionRepository revisions;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void publishOneEpisode() {
        UUID analysisId = UUID.randomUUID();
        revisions.create(
                new AnalysisSnapshot(
                        analysisId,
                        "https://www.youtube.com/watch?v=n5B3boj2MFM",
                        "n5B3boj2MFM",
                        OutputLanguage.DARIJA,
                        AnalysisStatus.QUEUED,
                        2,
                        "Video accepted",
                        false,
                        Instant.now(),
                        null,
                        null,
                        List.of(),
                        false,
                        null),
                "test-model", "test-prompt", "test-fact-model", "test-fact-prompt", "test-credential");
        revisions.complete(analysisId, new VideoReport(
                "Episode with guests",
                "Summary.",
                "Details.",
                List.of(
                        new Participant("Nizar Baraka", "Guest"),
                        new Participant("Nabila Mounib", "Guest"),
                        new Participant("Abdeslam El Aziz", "Guest")),
                List.of(new Chapter("Introduction", 0, "Opening")),
                List.of(new Claim("c1", "A checkable statement.", "Nizar Baraka", 60,
                        ClaimKind.FACT, ClaimVerdict.NEEDS_CONTEXT, "Needs context.", "MEDIUM", List.of())),
                List.of()), "interaction-test");
        jdbc.sql("""
                        UPDATE catalog_videos
                           SET status = 'PUBLISHED',
                               short_summary = :summary,
                               published_analysis_id = :analysisId
                         WHERE youtube_video_id = :youtubeVideoId
                        """)
                .param("summary", "Summary.")
                .param("analysisId", analysisId)
                .param("youtubeVideoId", "n5B3boj2MFM")
                .update();
    }

    @Test
    void servesPublicGuestAndPartySheetsWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/catalog/people"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")))
                .andExpect(jsonPath("$[?(@.slug == 'nizar-baraka')].partyCode").value(hasItem("PI")))
                .andExpect(jsonPath("$[?(@.slug == 'nizar-baraka')].displayName").value(hasItem("Nizar Baraka")))
                .andExpect(jsonPath("$[?(@.slug == 'nizar-baraka')].displayNameAr").value(hasItem("نزار بركة")));

        mvc.perform(get("/api/catalog/people").param("q", "baraka"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/catalog/people").param("q", "nobody matches this"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/catalog/people/nizar-baraka"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.partyCode").value("PI"))
                .andExpect(jsonPath("$.episodes[0].slug").value("episode-n5B3boj2MFM"))
                .andExpect(jsonPath("$.claims[0].statement").value("A checkable statement."));

        mvc.perform(get("/api/catalog/people/no-such-guest"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/catalog/parties"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")))
                .andExpect(jsonPath("$[*].code", hasItem("PI")))
                .andExpect(jsonPath("$[*].code", hasItem("FGD")))
                .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.not(hasItem("PSU"))))
                .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.not(hasItem("PJD"))))
                .andExpect(jsonPath("$[0].symbolAsset").value("/assets/parties/pi-display.png"));

        mvc.perform(get("/api/catalog/parties/PI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PI"))
                .andExpect(jsonPath("$.topMembers").doesNotExist())
                .andExpect(jsonPath("$.recentClaims").doesNotExist())
                .andExpect(jsonPath("$.episodes[0].slug").value("episode-n5B3boj2MFM"));

        mvc.perform(get("/api/catalog/parties/FGD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberPartyCodes", hasItem("FGD")))
                .andExpect(jsonPath("$.memberPartyCodes", hasItem("PSU")))
                .andExpect(jsonPath("$.members").value(2))
                .andExpect(jsonPath("$.appearances").value(1));

        mvc.perform(get("/api/catalog/parties/PSU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FGD"));

        mvc.perform(get("/api/catalog/people").param("party", "FGD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("nabila-mounib")))
                .andExpect(jsonPath("$[*].slug", hasItem("abdeslam-el-aziz")));

        mvc.perform(get("/api/catalog/parties/XX"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/catalog/parties/PJD"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/catalog/parties/UNKNOWN"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/catalog/parties/IND"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOversizedSearchQueries() throws Exception {
        mvc.perform(get("/api/catalog/people").param("q", "x".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forwardsPublicPartyPagesAndServesTheirShells() throws Exception {
        mvc.perform(get("/parties"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/parties.html"));
        mvc.perform(get("/parties/PI"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/party.html"));
        mvc.perform(get("/people/nizar-baraka"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/person.html"));

        mvc.perform(get("/people"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/parties.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("partiesGrid")));
        mvc.perform(get("/party.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("partyDetail")));
        mvc.perform(get("/person.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("personDetail")));
    }
}
