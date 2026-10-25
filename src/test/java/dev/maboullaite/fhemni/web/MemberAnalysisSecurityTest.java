package dev.maboullaite.fhemni.web;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
import java.util.*;
import dev.maboullaite.fhemni.identity.*;
import dev.maboullaite.fhemni.memberanalysis.*;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties={"fhemni.gemini.api-key=","fhemni.member-analysis.enabled=true","fhemni.member-analysis.worker-enabled=true","fhemni.member-analysis.worker-initial-delay-ms=3600000","fhemni.auth.admin-identities=test:editor","spring.datasource.url=jdbc:h2:mem:member-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"}) @AutoConfigureMockMvc
class MemberAnalysisSecurityTest {
    @Autowired MockMvc mvc;@Autowired UserAccountRepository users;@Autowired MemberAnalysisAdmission admission;
    @MockitoBean VideoEligibilityGateway videos;@MockitoBean VideoIntelligenceGateway gateway;
    @Test void everyMemberEndpointIsOwnerOnlyIncludingForAnAdministrator()throws Exception{
        String owner="owner-"+UUID.randomUUID(),other="other-"+UUID.randomUUID();for(String name:List.of(owner,other,"editor"))users.recordLogin(new ExternalIdentityProfile("test",name,null,name,null,false,null),name.equals("editor"));
        when(videos.configured()).thenReturn(true);when(videos.inspect(any())).thenReturn(new VideoEligibilityGateway.Eligibility(true,100L,false,null));when(gateway.live()).thenReturn(true);when(gateway.model()).thenReturn("fixture");when(gateway.promptVersion()).thenReturn("v1");when(gateway.factCheckModel()).thenReturn("fixture");when(gateway.factCheckPromptVersion()).thenReturn("v1");when(gateway.credentialVersion()).thenReturn("fixture");
        var id=admission.submit(users.findByIdentity("test",owner).orElseThrow().id(),"https://youtu.be/abcdefghijk","en",UUID.randomUUID().toString()).id();String path="/api/account/analyses/"+id;
        for(String suffix:List.of("","/events","/questions","/question-status","/publication-request")){
            boolean post=suffix.equals("/questions")||suffix.equals("/publication-request");
            mvc.perform(post?post(path+suffix).with(csrf()).contentType("application/json").content("{\"question\":\"Why?\",\"confirmed\":true}"):get(path+suffix)).andExpect(status().isUnauthorized());
            for(String identity:List.of(other,"editor"))mvc.perform((post?post(path+suffix).with(csrf()).contentType("application/json").content("{\"question\":\"Why?\",\"confirmed\":true}"):get(path+suffix)).with(oauth2Login().attributes(a->a.put("sub",identity)))).andExpect(status().isNotFound());
        }
        var auth=oauth2Login().attributes(a->a.put("sub",owner));mvc.perform(get(path).with(auth)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("QUEUED")).andExpect(jsonPath("$.providerInteractionId").doesNotExist());
        mvc.perform(get(path+"/question-status").with(auth)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("READY"));
        mvc.perform(post(path+"/questions").with(auth).contentType("application/json").content("{\"question\":\"Why?\"}")).andExpect(status().isForbidden());mvc.perform(get("/api/analyses/"+id)).andExpect(status().isNotFound());mvc.perform(get("/api/analyses/"+id).with(oauth2Login().attributes(a->a.put("sub","editor")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/account/analyses").with(oauth2Login().attributes(a->a.put("sub",other)))).andExpect(jsonPath("$.totalItems").value(0));
    }
}
