package dev.maboullaite.fhemni.memberanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.member-analysis.enabled=true",
        "fhemni.member-analysis.worker-enabled=false",
        "fhemni.cost-control.analysis-enabled=true",
        "fhemni.cost-control.max-daily-analyses=100",
        "spring.datasource.url=jdbc:h2:mem:member-availability;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class MemberAnalysisAvailabilityIntegrationTest {

    @Autowired MemberAnalysisAdmission admission;
    @Autowired MemberAnalysisPolicy policy;
    @Autowired UserAccountRepository users;
    @Autowired JdbcClient jdbc;
    @MockitoBean VideoEligibilityGateway videos;
    @MockitoBean VideoIntelligenceGateway gateway;

    @Test
    void disabledWorkerRejectsSubmissionWithoutConsumingAllowanceOrCreatingAJob() {
        var user = users.recordLogin(new ExternalIdentityProfile(
                "test", UUID.randomUUID().toString(), null, "Member", null, false, null), false).id();
        when(videos.configured()).thenReturn(true);
        when(videos.inspect(any())).thenReturn(
                new VideoEligibilityGateway.Eligibility(true, 100L, false, null));
        when(gateway.live()).thenReturn(true);
        when(gateway.model()).thenReturn("fixture");
        when(gateway.promptVersion()).thenReturn("v1");
        when(gateway.factCheckModel()).thenReturn("fixture");
        when(gateway.factCheckPromptVersion()).thenReturn("v1");
        when(gateway.credentialVersion()).thenReturn("fixture");
        long reservationsBefore = jdbc.sql("SELECT COUNT(*) FROM ai_usage_events")
                .query(Long.class).single();

        assertThatThrownBy(() -> admission.submit(user, "https://youtu.be/abcdefghijk", "en",
                UUID.randomUUID().toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROCESSING_UNAVAILABLE");

        assertThat(policy.forUser(user).enabled()).isFalse();
        assertThat(policy.forUser(user).remaining()).isEqualTo(10);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM member_analysis_jobs WHERE user_id=:user")
                .param("user", user).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM member_analysis_active WHERE user_id=:user")
                .param("user", user).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM member_allowance_receipts WHERE user_id=:user")
                .param("user", user).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ai_usage_events")
                .query(Long.class).single()).isEqualTo(reservationsBefore);
    }
}
