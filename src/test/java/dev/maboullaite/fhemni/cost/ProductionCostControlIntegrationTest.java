package dev.maboullaite.fhemni.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "JDBC_DATABASE_URL=jdbc:h2:mem:production-cost-control;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "JDBC_DATABASE_USERNAME=sa",
        "JDBC_DATABASE_PASSWORD=",
        "fhemni.gemini.api-key="
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionCostControlIntegrationTest {

    @Autowired
    private AiUsageGuard guard;

    @Autowired
    private MockMvc mvc;

    @Test
    void productionDisablesPaidAiUnlessExplicitlyEnabled() {
        assertThat(guard.analysisEnabled()).isFalse();
        assertThat(guard.chatEnabled()).isFalse();
    }

    @Test
    void productionLoginCookieIsSecureAndHttpOnly() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("FHEMNI_SESSION="),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"))));
    }
}
