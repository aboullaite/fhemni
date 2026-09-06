package dev.maboullaite.fhemni.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.Chapter;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.Participant;
import dev.maboullaite.fhemni.model.VideoReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.auth.google.client-id=",
        "fhemni.auth.google.client-secret=",
        "fhemni.auth.github.client-id=",
        "fhemni.auth.github.client-secret=",
        "fhemni.auth.bootstrap-admin-email=owner@example.com",
        "spring.datasource.url=jdbc:h2:mem:analysis-publication-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class AnalysisPublicationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AnalysisRevisionRepository revisions;

    @Autowired
    private UserAccountRepository users;

    @Test
    void persistsReviewsAndPublishesOneExactRevisionForPublicReuse() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "publisher", null, "Mohammed", "owner@example.com", true, null), true);

        UUID analysisId = UUID.randomUUID();
        AnalysisSnapshot queued = new AnalysisSnapshot(
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
                null);
        VideoReport report = new VideoReport(
                "تحليل محفوظ",
                "خلاصة محفوظة بلا إعادة تشغيل Gemini.",
                "تفاصيل التحليل المحفوظ.",
                List.of(new Participant("ضيف", "متدخل")),
                List.of(new Chapter("المقدمة", 0, "بداية الحلقة")),
                List.of(),
                List.of("شنو هي الخلاصة؟"));
        revisions.create(queued, "gemini-test", "prompt-test", "fact-model-test", "fact-prompt-test");
        revisions.complete(analysisId, report, "interaction-test");

        assertThat(revisions.findReusable(
                queued.videoId(), queued.language(),
                "gemini-test", "prompt-test", "fact-model-test", "fact-prompt-test", false))
                .isPresent();
        assertThat(revisions.findReusable(
                queued.videoId(), queued.language(),
                "gemini-test", "prompt-test", "new-fact-model", "fact-prompt-test", false))
                .isEmpty();
        assertThat(revisions.findReusable(
                queued.videoId(), queued.language(),
                "gemini-test", "prompt-test", "fact-model-test", "new-fact-prompt", false))
                .isEmpty();

        mvc.perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/admin/analyses/{id}/publish", analysisId)
                        .with(oauth2Login().attributes(attributes -> attributes.put("sub", "publisher")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.catalogSlug").value("episode-n5B3boj2MFM"));

        mvc.perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.report.title").value("تحليل محفوظ"))
                .andExpect(jsonPath("$.report.chapters[0].title").value("المقدمة"))
                .andExpect(jsonPath("$.report.topics").doesNotExist());

        mvc.perform(get("/api/catalog/videos/episode-n5B3boj2MFM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAnalysisId").value(analysisId.toString()));

        mvc.perform(post("/api/admin/analyses/reprocess")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"youtubeUrl":"https://youtu.be/n5B3boj2MFM","language":"ary"}
                                """))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/analyses/reprocess")
                        .with(oauth2Login().attributes(attributes -> attributes.put("sub", "publisher")))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"youtubeUrl":"https://youtu.be/n5B3boj2MFM","language":"ary"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(analysisId.toString())))
                .andExpect(jsonPath("$.published").value(false));

        mvc.perform(get("/api/catalog/videos/episode-n5B3boj2MFM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedAnalysisId").value(analysisId.toString()));
    }
}
