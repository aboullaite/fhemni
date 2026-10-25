package dev.maboullaite.fhemni.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"fhemni.gemini.api-key=","fhemni.auth.admin-identities=test:editor","spring.datasource.url=jdbc:h2:mem:government-editorial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class GovernmentEditorialSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired dev.maboullaite.fhemni.identity.UserAccountRepository users;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @Test void publicCannotReadDraftsOrPublishAndPublicListsArePaged() throws Exception {
        mvc.perform(get("/api/admin/government/2026-2031/drafts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/government/2026-2031/drafts").with(user("member").roles("USER")).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/government/2026-2031/drafts").with(user("admin").roles("ADMIN")).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/catalog/government/2026-2031/commitments")).andExpect(status().isOk())
            .andExpect(jsonPath("$.pageSize").value(10)).andExpect(jsonPath("$.totalItems").value(0));
    }
    @Test void persistedEditorCanSaveReviewPublishAndInvalidPayloadsFailAsClientErrors() throws Exception {
        users.recordLogin(new dev.maboullaite.fhemni.identity.ExternalIdentityProfile("test","editor",null,"Editor",null,false,null),true);
        var auth=oauth2Login().attributes(a->a.put("sub","editor"));
        mvc.perform(get("/admin/government").with(auth)).andExpect(status().isOk());
        mvc.perform(get("/admin-government.html")).andExpect(status().is3xxRedirection());
        var pending=dev.maboullaite.fhemni.government.GovernmentViews.pending("2026-2031");
        var value=new dev.maboullaite.fhemni.government.GovernmentViews.MandateView(pending.id(),pending.label(),pending.lifecycle(),java.time.Instant.now().minusSeconds(2),java.util.List.of(),null,null,java.util.List.of());
        String draft=mapper.writeValueAsString(java.util.Map.of("type","MANDATE","slug",pending.id(),"payload",value,"reason","Editorial verification"));
        var saved=mvc.perform(post("/api/admin/government/2026-2031/drafts").with(auth).with(csrf()).contentType("application/json").content(draft))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        String id=mapper.readTree(saved.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/admin/government/2026-2031/drafts/"+id+"/review").with(auth).with(csrf()).contentType("application/json").content("{\"expectedVersion\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));
        mvc.perform(post("/api/admin/government/2026-2031/drafts/"+id+"/publish").with(auth).with(csrf()).contentType("application/json").content("{\"expectedVersion\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/api/admin/government/2026-2031/drafts").with(auth).with(csrf()).contentType("application/json").content(draft.replace("\"lifecycle\":","\"unexpected\":1,\"lifecycle\":")))
            .andExpect(status().isBadRequest());
    }
}
