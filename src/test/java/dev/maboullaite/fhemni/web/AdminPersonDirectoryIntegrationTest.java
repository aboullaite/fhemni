package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.Participant;
import dev.maboullaite.fhemni.model.VideoReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.catalog.people-cache-ttl=PT0S",
        "spring.datasource.url=jdbc:h2:mem:admin-person-directory-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class AdminPersonDirectoryIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AnalysisRevisionRepository revisions;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void publishEpisodeWithAnExtractedGuest() {
        if (jdbc.sql("SELECT COUNT(*) FROM directory_persons WHERE slug = 'guest-to-review'")
                .query(Integer.class).single() > 0) {
            return;
        }
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
                "Episode with a guest",
                "Summary.",
                "Details.",
                List.of(new Participant("Guest To Review", "Guest")),
                List.of(),
                List.of(),
                List.of()), "interaction-test");
        jdbc.sql("""
                        UPDATE catalog_videos
                           SET status = 'PUBLISHED',
                               short_summary = 'Summary.',
                               published_analysis_id = :analysisId
                         WHERE youtube_video_id = 'n5B3boj2MFM'
                        """)
                .param("analysisId", analysisId)
                .update();
    }

    @Test
    void letsOnlyAnAdministratorCurateAnExtractedGuestAndAssignAParty() throws Exception {
        mvc.perform(get("/admin-people.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nonAffiliatedPeople")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("admin.nonAffiliatedTitle")));

        mvc.perform(get("/api/admin/people"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/people").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/people").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.parties[*].code", hasItem("PJD")))
                .andExpect(jsonPath("$.unmatched[*].slug", hasItem("guest-to-review")));

        mvc.perform(post("/api/admin/people")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "guest-to-review",
                                  "displayNameFr": "Guest To Review",
                                  "displayNameAr": "ضيف للمراجعة",
                                  "aliases": ["Guest To Review"],
                                  "affiliation": {
                                    "partyCode": "UNKNOWN"
                                  }
                                }
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/people").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmatched[*].slug", not(hasItem("guest-to-review"))))
                .andExpect(jsonPath("$.people[?(@.slug == 'guest-to-review')].partyCode").value(hasItem("UNKNOWN")));

        mvc.perform(get("/api/catalog/people/guest-to-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[0].slug").value("episode-n5B3boj2MFM"));

        mvc.perform(post("/api/admin/people/guest-to-review/affiliations")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "partyCode": "PJD"
                                }
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/catalog/parties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("PJD")));
        mvc.perform(get("/api/catalog/parties/PJD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[0].slug").value("episode-n5B3boj2MFM"));

        long affiliationId = jdbc.sql("""
                        SELECT id FROM person_affiliations
                         WHERE person_slug = 'guest-to-review' AND party_code = 'PJD'
                        """)
                .query(Long.class)
                .single();
        mvc.perform(post("/api/admin/people/guest-to-review/affiliations/" + affiliationId + "/transition")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "partyCode": "RNI",
                                  "validFrom": "2026-06-01",
                                  "validUntil": null
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("SELECT COUNT(*) FROM person_affiliations WHERE person_slug = 'guest-to-review'")
                .query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("""
                        SELECT valid_until FROM person_affiliations
                         WHERE id = :id
                        """)
                .param("id", affiliationId)
                .query(LocalDate.class)
                .single()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(jdbc.sql("""
                        SELECT COUNT(*) FROM person_affiliations
                         WHERE person_slug = 'guest-to-review'
                           AND party_code = 'RNI'
                           AND valid_from = DATE '2026-06-01'
                        """)
                .query(Integer.class).single()).isEqualTo(1);
    }
}
