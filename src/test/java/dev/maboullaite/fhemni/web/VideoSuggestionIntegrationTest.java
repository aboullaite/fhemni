package dev.maboullaite.fhemni.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import dev.maboullaite.fhemni.catalog.VideoMetadataGateway;
import dev.maboullaite.fhemni.catalog.VideoMetadataGateway.VideoMetadata;
import dev.maboullaite.fhemni.catalog.VideoSuggestionRepository;
import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:suggestion-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class VideoSuggestionIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VideoSuggestionRepository suggestions;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private VideoMetadataGateway metadataGateway;

    @Test
    void requiresLoginDeduplicatesSuggestionsAndProtectsTheAdminQueue() throws Exception {
        when(metadataGateway.fetch(anyString(), anyString())).thenAnswer(invocation -> {
            String videoId = invocation.getArgument(1);
            return new VideoMetadata(
                    "Suggested episode " + videoId,
                    "2M",
                    "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg");
        });

        mvc.perform(post("/api/suggestions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"youtubeUrl":"https://youtu.be/n5B3boj2MFM"}
                                """))
                .andExpect(status().isUnauthorized());

        var member = users.recordLogin(new ExternalIdentityProfile(
                "test", "user", null, "Mohammed Member", null, false, null), false);
        mvc.perform(post("/api/suggestions")
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"youtubeUrl":"https://youtu.be/n5B3boj2MFM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_CATALOGUED"));

        String suggestion = """
                {"youtubeUrl":"https://youtu.be/SuGgEsT1234"}
                """;
        mvc.perform(post("/api/suggestions")
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suggestion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andExpect(jsonPath("$.submissionCount").value(1))
                .andExpect(jsonPath("$.title").value("Suggested episode SuGgEsT1234"));

        mvc.perform(post("/api/suggestions")
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suggestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_SUGGESTED"))
                .andExpect(jsonPath("$.submissionCount").value(2));

        mvc.perform(get("/api/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].youtubeVideoId").value("SuGgEsT1234"))
                .andExpect(jsonPath("$[0].title").value("Suggested episode SuGgEsT1234"))
                .andExpect(jsonPath("$[0].suggestedByFirstName").value("Mohammed"))
                .andExpect(jsonPath("$[0].voteScore").value(0))
                .andExpect(jsonPath("$[0].viewerVote").value(0));

        mvc.perform(get("/api/auth/session").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("Mohammed"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.canChat").doesNotExist())
                .andExpect(jsonPath("$.user.email").doesNotExist());
        mvc.perform(get("/api/suggestions").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].youtubeVideoId").value("SuGgEsT1234"))
                .andExpect(jsonPath("$[0].title").value("Suggested episode SuGgEsT1234"))
                .andExpect(jsonPath("$[0].authorName").value("2M"))
                .andExpect(jsonPath("$[0].moderationStatus").value("APPROVED"))
                .andExpect(jsonPath("$[0].voteScore").value(0))
                .andExpect(jsonPath("$[0].viewerVote").value(0));

        var id = suggestions.findByYouTubeId("SuGgEsT1234").orElseThrow().id();
        mvc.perform(post("/api/suggestions/{id}/votes", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/suggestions/{id}/votes", id)
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/suggestions").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].voteScore").value(1))
                .andExpect(jsonPath("$[0].upvotes").value(1))
                .andExpect(jsonPath("$[0].downvotes").value(0))
                .andExpect(jsonPath("$[0].viewerVote").value(1));

        mvc.perform(post("/api/suggestions/{id}/votes", id)
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":-1}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/suggestions")
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"youtubeUrl\":\"https://youtu.be/RankMe00002\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/suggestions").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].youtubeVideoId").value("RankMe00002"))
                .andExpect(jsonPath("$[0].suggestedByFirstName").value("Mohammed"))
                .andExpect(jsonPath("$[1].youtubeVideoId").value("SuGgEsT1234"))
                .andExpect(jsonPath("$[1].voteScore").value(-1))
                .andExpect(jsonPath("$[1].downvotes").value(1))
                .andExpect(jsonPath("$[1].viewerVote").value(-1));

        mvc.perform(post("/api/suggestions/{id}/votes", id)
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/suggestions"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/suggestions").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/suggestions").with(user("owner").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].youtubeVideoId").value("RankMe00002"))
                .andExpect(jsonPath("$[1].youtubeVideoId").value("SuGgEsT1234"))
                .andExpect(jsonPath("$[1].submissionCount").value(2));

        mvc.perform(post("/api/admin/suggestions/{id}/dismiss", id)
                        .with(user("owner").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/suggestions").with(user("owner").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].youtubeVideoId").value("RankMe00002"));

        mvc.perform(post("/api/suggestions")
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suggestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_SUGGESTED"))
                .andExpect(jsonPath("$.submissionCount").value(3));
        assertEquals("DISMISSED", suggestions.findByYouTubeId("SuGgEsT1234").orElseThrow().status().name());
        mvc.perform(get("/api/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].youtubeVideoId").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("SuGgEsT1234"))));

        when(metadataGateway.fetch(anyString(), eq("Unsafe00001"))).thenReturn(new VideoMetadata(
                "XXX video",
                "Suspicious channel",
                "https://i.ytimg.com/vi/Unsafe00001/mqdefault.jpg"));
        mvc.perform(post("/api/suggestions")
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"youtubeUrl\":\"https://youtu.be/Unsafe00001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("HELD_FOR_REVIEW"))
                .andExpect(jsonPath("$.title").value("XXX video"));

        mvc.perform(get("/api/suggestions").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].youtubeVideoId").value("RankMe00002"));

        var heldId = suggestions.findByYouTubeId("Unsafe00001").orElseThrow().id();
        mvc.perform(post("/api/suggestions/{id}/votes", heldId)
                        .with(oauth2Login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/suggestions").with(user("owner").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].youtubeVideoId").value("Unsafe00001"))
                .andExpect(jsonPath("$[0].moderationStatus").value("REVIEW_REQUIRED"));
        mvc.perform(post("/api/admin/suggestions/{id}/approve", heldId)
                        .with(user("owner").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/suggestions").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        String legacyId = "Legacy00001";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO video_suggestions (
                            id, youtube_video_id, canonical_url, status, submission_count,
                            first_suggested_at, last_suggested_at, suggested_by_user_id
                        ) VALUES (
                            :id, :youtubeId, :url, 'PENDING', 1,
                            :createdAt, :updatedAt, :suggestedByUserId
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("youtubeId", legacyId)
                .param("url", "https://www.youtube.com/watch?v=" + legacyId)
                .param("createdAt", now)
                .param("updatedAt", now)
                .param("suggestedByUserId", member.id())
                .update();
        when(metadataGateway.fetch(anyString(), eq(legacyId))).thenReturn(new VideoMetadata(
                "Recovered legacy title",
                "2M",
                "https://i.ytimg.com/vi/" + legacyId + "/mqdefault.jpg"));

        mvc.perform(post("/api/admin/suggestions/refresh-metadata")
                        .with(user("owner").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").value(1))
                .andExpect(jsonPath("$.heldForReview").value(0))
                .andExpect(jsonPath("$.failed").value(0));
        assertEquals("Recovered legacy title",
                suggestions.findByYouTubeId(legacyId).orElseThrow().title());
    }
}
