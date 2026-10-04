package dev.maboullaite.fhemni.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"fhemni.gemini.api-key=", "spring.datasource.url=jdbc:h2:mem:accountability-pages;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AccountabilityPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void newPagesArePublicDocumentsWithoutGrantingNewApiWrites() throws Exception {
        mvc.perform(get("/government")).andExpect(status().isOk()).andExpect(forwardedUrl("/government.html"));
        mvc.perform(get("/analyse")).andExpect(status().isOk()).andExpect(forwardedUrl("/analyse.html"));
        mvc.perform(get("/api/admin/government/drafts")).andExpect(status().isUnauthorized());
    }
}
