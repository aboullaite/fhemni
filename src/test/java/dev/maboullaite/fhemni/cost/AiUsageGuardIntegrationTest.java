package dev.maboullaite.fhemni.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.cost-control.analysis-enabled=true",
        "fhemni.cost-control.chat-enabled=true",
        "fhemni.cost-control.max-daily-analyses=2",
        "fhemni.cost-control.max-hourly-chat-rounds=10",
        "fhemni.cost-control.max-daily-chat-rounds=100",
        "fhemni.cost-control.max-daily-chat-rounds-per-user=1",
        "fhemni.cost-control.max-weekly-chat-rounds-per-user=5",
        "spring.datasource.url=jdbc:h2:mem:cost-guard-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class AiUsageGuardIntegrationTest {

    @Autowired
    private AiUsageGuard guard;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void durablyLimitsAttemptsAndRecordsReturnedUsage() {
        UUID firstAnalysis = UUID.randomUUID();
        var first = guard.reserveAnalysis(firstAnalysis, "test-model");
        guard.succeeded(first, new AiUsage(100, 20, 40, 5, 0, 2));
        var factCheck = guard.reserveFactCheck(firstAnalysis, "fact-check-model");
        guard.succeeded(factCheck, new AiUsage(80, 10, 0, 0, 0, 1));
        guard.reserveAnalysis(UUID.randomUUID(), "test-model");

        assertThatThrownBy(() -> guard.reserveAnalysis(UUID.randomUUID(), "test-model"))
                .isInstanceOf(AiBudgetExceededException.class)
                .hasMessageContaining("daily analysis budget");
        assertThat(jdbc.sql("SELECT status FROM ai_usage_events WHERE id = :id")
                .param("id", first.id()).query(String.class).single()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.sql("SELECT input_tokens FROM ai_usage_events WHERE id = :id")
                .param("id", first.id()).query(Integer.class).single()).isEqualTo(100);
        assertThat(jdbc.sql("SELECT operation || ':' || model FROM ai_usage_events WHERE id = :id")
                .param("id", factCheck.id()).query(String.class).single())
                .isEqualTo("FACT_CHECK:fact-check-model");

        var user = users.recordLogin(new ExternalIdentityProfile(
                "test", "cost-user", null, "Cost User", null, false, null), false);
        guard.reserveQuestion(firstAnalysis, user.id(), AiOperation.CHAT_PROGRAMME, "test-model");
        assertThatThrownBy(() -> guard.reserveQuestion(
                firstAnalysis, user.id(), AiOperation.CHAT_CHECK, "test-model"))
                .isInstanceOf(AiBudgetExceededException.class)
                .hasMessageContaining("daily chat allowance");
    }
}
