package dev.maboullaite.fhemni.memberanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MemberAnalysisPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "false, false, false",
            "false, true, false",
            "true, false, false",
            "true, true, true"
    })
    void admissionsRequireBothFeatureAndWorkerEnabled(boolean enabled, boolean workerEnabled,
            boolean expectedAvailable) {
        policyContext()
                .withPropertyValues("fhemni.member-analysis.enabled=" + enabled,
                        "fhemni.member-analysis.worker-enabled=" + workerEnabled)
                .run(context -> {
                    var policy = context.getBean(MemberAnalysisPolicy.class).forUser(UUID.randomUUID());
                    assertThat(policy.enabled()).isEqualTo(expectedAvailable);
                    assertThat(policy.unavailableReason())
                            .isEqualTo(expectedAvailable ? null : "PROCESSING_UNAVAILABLE");
                });
    }

    @Test
    void missingWorkerSettingFailsClosedEvenWhenFeatureIsEnabled() {
        policyContext().withPropertyValues("fhemni.member-analysis.enabled=true")
                .run(context -> {
                    var policy = context.getBean(MemberAnalysisPolicy.class).forUser(UUID.randomUUID());
                    assertThat(policy.enabled()).isFalse();
                    assertThat(policy.unavailableReason()).isEqualTo("PROCESSING_UNAVAILABLE");
                });
    }

    @Test
    void missingFeatureSettingFailsClosedEvenWhenWorkerIsEnabled() {
        policyContext().withPropertyValues("fhemni.member-analysis.worker-enabled=true")
                .run(context -> {
                    var policy = context.getBean(MemberAnalysisPolicy.class).forUser(UUID.randomUUID());
                    assertThat(policy.enabled()).isFalse();
                    assertThat(policy.unavailableReason()).isEqualTo("PROCESSING_UNAVAILABLE");
                });
    }

    private ApplicationContextRunner policyContext() {
        var repository = mock(MemberAnalysisRepository.class);
        when(repository.role(any())).thenReturn("USER");
        var allowances = mock(MemberAllowanceRepository.class);
        var videos = mock(VideoEligibilityGateway.class);
        when(videos.configured()).thenReturn(true);
        var gateway = mock(VideoIntelligenceGateway.class);
        when(gateway.live()).thenReturn(true);
        var budget = mock(AiUsageGuard.class);
        when(budget.analysisEnabled()).thenReturn(true);

        return new ApplicationContextRunner()
                .withBean(MemberAnalysisRepository.class, () -> repository)
                .withBean(MemberAllowanceRepository.class, () -> allowances)
                .withBean(VideoEligibilityGateway.class, () -> videos)
                .withBean(VideoIntelligenceGateway.class, () -> gateway)
                .withBean(AiUsageGuard.class, () -> budget)
                .withBean(MemberAnalysisPolicy.class);
    }
}
