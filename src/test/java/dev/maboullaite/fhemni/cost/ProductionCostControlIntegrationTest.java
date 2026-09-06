package dev.maboullaite.fhemni.cost;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "JDBC_DATABASE_URL=jdbc:h2:mem:production-cost-control;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "JDBC_DATABASE_USERNAME=sa",
        "JDBC_DATABASE_PASSWORD=",
        "fhemni.gemini.api-key="
})
@ActiveProfiles("prod")
class ProductionCostControlIntegrationTest {

    @Autowired
    private AiUsageGuard guard;

    @Test
    void productionDisablesPaidAiUnlessExplicitlyEnabled() {
        assertThat(guard.analysisEnabled()).isFalse();
        assertThat(guard.chatEnabled()).isFalse();
    }
}
