package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.catalog.VideoMetadataGateway;
import dev.maboullaite.fhemni.catalog.VideoMetadataGateway.VideoMetadata;
import dev.maboullaite.fhemni.catalog.VideoPublicationDateGateway;
import dev.maboullaite.fhemni.catalog.CatalogVideoRepository;
import dev.maboullaite.fhemni.catalog.CatalogVideoRepository.ImportedCatalogVideo;
import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.Chapter;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimKind;
import dev.maboullaite.fhemni.model.ClaimVerdict;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.Participant;
import dev.maboullaite.fhemni.model.VideoReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
        "spring.datasource.url=jdbc:h2:mem:catalog-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CatalogVideoRepository catalog;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private AnalysisRevisionRepository revisions;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private VideoMetadataGateway metadataGateway;

    @MockitoBean
    private VideoPublicationDateGateway publicationDates;

    @Test
    void servesSeededCatalogueAndStablePublicPages() throws Exception {
        mvc.perform(get("/api/catalog/videos").param("q", "الصراحة"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.items[0].status").value("CATALOGUED"));

        mvc.perform(get("/api/catalog/videos").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/catalog/videos").param("q", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/catalog/videos/episode-14IF32HrTBs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.youtubeVideoId").value("14IF32HrTBs"))
                .andExpect(jsonPath("$.embedUrl").value("https://www.youtube-nocookie.com/embed/14IF32HrTBs"));

        mvc.perform(get("/videos"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/videos.html"));
        mvc.perform(get("/videos/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/videos.html"));
        mvc.perform(get("/catalog"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/videos"));
        mvc.perform(get("/community"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/community.html"));
        mvc.perform(get("/community/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/community.html"));
        mvc.perform(get("/suggestions"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community"));
        mvc.perform(get("/videos/episode-14IF32HrTBs"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/video.html"));
        mvc.perform(get("/videos/episode-14IF32HrTBs/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/video.html"));
    }

    @Test
    void importsMetadataOnlyForAdministratorsAndDeduplicatesByYouTubeId() throws Exception {
        when(metadataGateway.fetch(anyString(), anyString()))
                .thenReturn(new VideoMetadata(
                        "حلقة جديدة للاختبار",
                        "2MTV",
                        "https://i.ytimg.com/vi/AbCdEf123_-/hqdefault.jpg"));
        String body = """
                {
                  "showName": "ساعة الصراحة",
                  "sourceLanguage": "ar",
                  "items": [{
                    "youtubeUrl": "https://youtu.be/AbCdEf123_-",
                    "publishedOn": "2026-06-03"
                  }]
                }
                """;

        mvc.perform(post("/api/admin/catalog/videos/import")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        users.recordLogin(new ExternalIdentityProfile(
                "test", "user", null, "Test Member", null, false, null), false);
        mvc.perform(post("/api/suggestions")
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"youtubeUrl":"https://youtu.be/AbCdEf123_-"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/catalog/videos/import")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        mvc.perform(post("/api/admin/catalog/videos/import")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.updated").value(1));

        mvc.perform(get("/api/catalog/videos/episode-AbCdEf123_-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("حلقة جديدة للاختبار"))
                .andExpect(jsonPath("$.publishedOn").value("2026-06-03"))
                .andExpect(jsonPath("$.sourceLanguage").value("ary"));

        mvc.perform(get("/api/admin/suggestions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void fetchesAndCachesMissingPublicationDatesWithoutGemini() throws Exception {
        when(publicationDates.fetch(anyString())).thenReturn(LocalDate.of(2026, 9, 2));
        catalog.saveImported(new ImportedCatalogVideo(
                "NoDate00001",
                "https://www.youtube.com/watch?v=NoDate00001",
                "حلقة بلا تاريخ",
                "2MTV",
                "https://i.ytimg.com/vi/NoDate00001/hqdefault.jpg",
                "ساعة الصراحة",
                null,
                "ary"));

        mvc.perform(post("/api/admin/catalog/videos/refresh-dates")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.failed").value(0));

        mvc.perform(get("/api/catalog/videos/episode-NoDate00001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedOn").value("2026-09-02"))
                .andExpect(jsonPath("$.sourceLanguage").value("ary"));
    }

    @Test
    void backfillsKnownCatalogueLanguageAndPublicationDate() throws Exception {
        mvc.perform(get("/api/catalog/videos/episode-n5B3boj2MFM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedOn").value("2026-09-02"))
                .andExpect(jsonPath("$.sourceLanguage").value("ary"));
    }

    @Test
    void rendersLocalizedCatalogueShell() throws Exception {
        mvc.perform(get("/videos.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("catalogFilters")))
                .andExpect(content().string(containsString("catalog-results-with-suggestion")))
                .andExpect(content().string(containsString("href=\"/community\"")))
                .andExpect(content().string(containsString("/js/catalog.js")));

        mvc.perform(get("/community.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"videoSuggestionForm\"")))
                .andExpect(content().string(containsString("id=\"suggestionAuthGate\"")))
                .andExpect(content().string(containsString("id=\"suggestionCommunity\"")))
                .andExpect(content().string(containsString("/js/suggestions.js")));
    }

    @Test
    void searchesPublishedBriefingsByGuestNameInFrenchOrArabic() throws Exception {
        catalog.saveImported(new ImportedCatalogVideo(
                "Guest000001",
                "https://www.youtube.com/watch?v=Guest000001",
                "حلقة خاصة",
                "2MTV",
                "https://i.ytimg.com/vi/Guest000001/hqdefault.jpg",
                "ساعة الصراحة",
                LocalDate.of(2026, 1, 5),
                "ary"));

        UUID analysisId = UUID.randomUUID();
        revisions.create(
                new AnalysisSnapshot(
                        analysisId,
                        "https://www.youtube.com/watch?v=Guest000001",
                        "Guest000001",
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
                "Guest episode",
                "Summary.",
                "Details.",
                List.of(new Participant("نزار بركة", "ضيف")),
                List.of(new Chapter("المقدمة", 0, "البداية")),
                List.of(new Claim("c1", "A checkable statement.", "نزار بركة", 60,
                        ClaimKind.FACT, ClaimVerdict.SUPPORTED, "Supported by evidence.", "HIGH", List.of())),
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
                .param("youtubeVideoId", "Guest000001")
                .update();

        // French query matches the Arabic briefing through the directory.
        mvc.perform(get("/api/catalog/videos").param("q", "baraka"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'episode-Guest000001')]", hasSize(1)));

        // Arabic query matches the briefing text directly.
        mvc.perform(get("/api/catalog/videos").param("q", "بركة"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'episode-Guest000001')]", hasSize(1)));

        mvc.perform(get("/api/catalog/videos").param("q", "nobody matches this"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Punctuation-only queries normalize to nothing and must not expand
        // to every known guest.
        mvc.perform(get("/api/catalog/videos").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/catalog/videos").param("q", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
