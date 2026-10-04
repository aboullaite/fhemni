package dev.maboullaite.fhemni.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"fhemni.gemini.api-key=","spring.datasource.url=jdbc:h2:mem:government-public;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class GovernmentIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void unconfiguredMandateIsNeutralAndUnknownMandatesAreNotInvented() throws Exception {
        mvc.perform(get("/api/catalog/government/2026-2031")).andExpect(status().isOk())
            .andExpect(jsonPath("$.verifiedAt").value(nullValue()))
            .andExpect(jsonPath("$.membership.length()").value(0))
            .andExpect(jsonPath("$.programme").value(nullValue()));
        mvc.perform(get("/api/catalog/government/9999-9999")).andExpect(status().isNotFound());
        mvc.perform(get("/government/commitments/example")).andExpect(status().isOk()).andExpect(forwardedUrl("/commitment.html"));
        mvc.perform(get("/api/catalog/government/2026-2031/deadlines")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
