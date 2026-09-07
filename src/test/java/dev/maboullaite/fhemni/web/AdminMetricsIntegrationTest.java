package dev.maboullaite.fhemni.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.maboullaite.fhemni.cost.AiOperation;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageRepository;
import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
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
        "spring.datasource.url=jdbc:h2:mem:admin-metrics-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class AdminMetricsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private AiUsageRepository usage;

    @Test
    void exposesAggregateUsageOnlyToAdministrators() throws Exception {
        mvc.perform(get("/api/admin/metrics/overview"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/metrics/overview").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        var member = users.recordLogin(new ExternalIdentityProfile(
                "test", "metrics-member", null, "Metrics Member", null, false, null), false);
        users.recordLogin(new ExternalIdentityProfile(
                "test", "metrics-admin", null, "Metrics Admin", null, false, null), true);

        Instant now = Instant.now();
        UUID analysisId = UUID.randomUUID();
        UUID chat = usage.reserve(AiOperation.CHAT_VIDEO, member.id(), analysisId, "gemini-test", now);
        usage.complete(chat, "SUCCEEDED", new AiUsage(100, 200, 50, 25, 10, 1), now);
        UUID failedChat = usage.reserve(AiOperation.CHAT_CHECK, member.id(), analysisId, "gemini-test", now);
        usage.complete(failedChat, "FAILED", new AiUsage(20, 30, null, 5, null, null), now);
        usage.reserve(AiOperation.CHAT_VIDEO, member.id(), analysisId, "gemini-test", now);
        usage.reserve(AiOperation.CHAT_VIDEO, member.id(), analysisId, "gemini-test", now.minus(Duration.ofHours(1)));
        UUID analysis = usage.reserve(AiOperation.ANALYSIS, null, analysisId, "gemini-test", now);
        usage.complete(analysis, "SUCCEEDED", new AiUsage(1_000, 2_000, 500, 300, 100, null), now);
        usage.reserve(AiOperation.ANALYSIS, null, analysisId, "gemini-test", now.minus(Duration.ofHours(1)));

        mvc.perform(get("/api/admin/metrics/overview").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.users.registered").value(2))
                .andExpect(jsonPath("$.users.newLast24Hours").value(2))
                .andExpect(jsonPath("$.users.activeLast24Hours").value(2))
                .andExpect(jsonPath("$.chat.requests").value(4))
                .andExpect(jsonPath("$.chat.users").value(1))
                .andExpect(jsonPath("$.chat.succeeded").value(1))
                .andExpect(jsonPath("$.chat.failed").value(1))
                .andExpect(jsonPath("$.chat.pending").value(1))
                .andExpect(jsonPath("$.chat.stale").value(1))
                .andExpect(jsonPath("$.chat.recordedTokens").value(390))
                .andExpect(jsonPath("$.chat.cachedTokens").value(50))
                .andExpect(jsonPath("$.allAi.requests").value(6))
                .andExpect(jsonPath("$.allAi.pending").value(1))
                .andExpect(jsonPath("$.allAi.stale").value(2))
                .andExpect(jsonPath("$.allAi.recordedTokens").value(3_790))
                .andExpect(jsonPath("$.allAi.cachedTokens").value(550));
    }
}
