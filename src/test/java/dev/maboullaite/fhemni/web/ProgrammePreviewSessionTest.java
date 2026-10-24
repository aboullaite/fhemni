package dev.maboullaite.fhemni.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.maboullaite.fhemni.government.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"fhemni.gemini.api-key=","spring.datasource.url=jdbc:h2:mem:preview-session;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("programme-preview")
@AutoConfigureMockMvc
class ProgrammePreviewSessionTest {
    @Autowired MockMvc mvc;
    // Isolate HTTP safety from the opt-in, gitignored research files and its filesystem importer.
    @MockitoBean ProgrammePreview preview;
    @BeforeEach void fixture(){when(preview.snapshot()).thenReturn(new ProgrammePreviewData.Snapshot(new byte[]{1,2,3},null,List.of(),List.of(),List.of("PAM")));}
    @Test void onlyLocalCsrfProtectedRequestsCanStartTheFixedTestIdentityLogin() throws Exception {
        mvc.perform(get("/api/catalog/government/2026-2031/preview"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.localPreview").value(true))
            .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/catalog/government/2026-2031/preview/session")).andExpect(status().isForbidden());
        mvc.perform(post("/api/catalog/government/2026-2031/preview/session").with(csrf()).with(request->{request.setRemoteAddr("192.0.2.1");return request;})).andExpect(status().isForbidden());
        mvc.perform(post("/api/catalog/government/2026-2031/preview/session").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/auth/magic-link?token=")))
            .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/catalog/government/2026-2031/preview/pdf")).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf")).andExpect(content().bytes(new byte[]{1,2,3}));
    }
}
