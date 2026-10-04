package dev.maboullaite.fhemni.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import dev.maboullaite.fhemni.government.*;
import dev.maboullaite.fhemni.identity.*;

@SpringBootTest(properties={"fhemni.gemini.api-key=","fhemni.auth.admin-identities=test:editor","spring.datasource.url=jdbc:h2:mem:following;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"}) @AutoConfigureMockMvc
class FollowingIntegrationTest {
    @Autowired MockMvc mvc;@Autowired UserAccountRepository users;@Autowired GovernmentRepository repository;@Autowired GovernmentEditorialService editorial;@Autowired GovernmentEvidenceRepository evidence;@Autowired tools.jackson.databind.ObjectMapper mapper;
    String a,b;
    @BeforeEach void users(){a="member-"+UUID.randomUUID();b="member-"+UUID.randomUUID();for(String name:List.of(a,b,"editor"))users.recordLogin(new ExternalIdentityProfile("test",name,null,name,null,false,null),name.equals("editor"));}
    @Test void followsArePrivateIdempotentAndTopicPreferencesHaveOneSourceOfTruth()throws Exception {
        mvc.perform(put("/api/account/follows/TOPIC/HEALTH").with(csrf())).andExpect(status().isUnauthorized());
        var auth=oauth2Login().attributes(v->v.put("sub",a));
        mvc.perform(put("/api/account/follows/TOPIC/HEALTH").with(auth)).andExpect(status().isForbidden());
        for(int i=0;i<2;i++)mvc.perform(put("/api/account/follows/TOPIC/HEALTH").with(auth).with(csrf())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/account/follows").with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/account/policy-topics").with(auth)).andExpect(jsonPath("$.topicCodes[0]").value("HEALTH"));
        mvc.perform(get("/api/account/follows").with(oauth2Login().attributes(v->v.put("sub",b)))).andExpect(jsonPath("$.length()").value(0));
        for(String code:List.of("EDUCATION","EMPLOYMENT"))mvc.perform(put("/api/account/follows/TOPIC/"+code).with(auth).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/account/follows/TOPIC/HOUSING").with(auth).with(csrf())).andExpect(status().isBadRequest());
        for(int i=0;i<2;i++)mvc.perform(delete("/api/account/follows/TOPIC/HEALTH").with(auth).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/account/follows/TOPIC/HEALTH").with(auth)).andExpect(jsonPath("$.following").value(false));
        mvc.perform(put("/api/account/follows/REGION/invented").with(auth).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/account/corrections").with(auth).with(csrf()).contentType("application/json").content("{\"targetId\":\""+UUID.randomUUID()+"\",\"text\":\"Missing type\"}")).andExpect(status().isBadRequest());
    }
    @Test void materialFeedIsPagedAndCorrectionsRemainPrivateWithoutMutatingThePublishedTarget()throws Exception {
        UUID id=UUID.randomUUID(),actor=users.findByIdentity("test","editor").orElseThrow().id();repository.ensureMandate("2026-2031");
        var proof=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/source","Test-only source",null,"en",null,Instant.now().minusSeconds(3),null),null);
        publish(new MandateView("2026-2031",text("Mandate"),Lifecycle.PROGRAMME_PUBLISHED,Instant.now().minusSeconds(2),List.of(new Membership(UUID.randomUUID(),text("Fixture"),text("Role"),null,LocalDate.now().minusDays(1),null,List.of(proof.id()))),new ProgrammeVersion(UUID.randomUUID(),1,text("Programme"),LocalDate.now().minusDays(1),List.of(proof.id())),new ReviewCoverage(0,null,false),List.of()),SubjectType.MANDATE,"2026-2031",actor);
        for(int i=0;i<12;i++)publish(new CommitmentView(id,"feed-fixture",Origin.GOVERNMENT,text("Revision "+i),proof.passage(),"en",text("Author"),null,List.of("HEALTH"),new Scope("NATIONAL","MA",text("Morocco")),null,null,null,null,List.of(),List.of(proof),List.of(),List.of()),SubjectType.COMMITMENT,"feed-fixture",actor);
        var auth=oauth2Login().attributes(v->v.put("sub",a));
        mvc.perform(put("/api/account/follows/COMMITMENT/"+id).with(auth).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/account/updates").with(auth)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items.length()").value(10)).andExpect(jsonPath("$.totalItems").value(12));
        mvc.perform(get("/api/account/updates?page=2").with(auth)).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/account/updates").with(oauth2Login().attributes(v->v.put("sub",b)))).andExpect(jsonPath("$.totalItems").value(0));
        String body=mapper.writeValueAsString(Map.of("type","COMMITMENT","targetId",id.toString(),"evidenceUrl","https://example.org/correction","text","<img src=x onerror=bad()>"));
        var sent=mvc.perform(post("/api/account/corrections").with(auth).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
        String correction=mapper.readTree(sent.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/account/corrections/"+correction).with(oauth2Login().attributes(v->v.put("sub",b)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/catalog/government/commitments/"+id)).andExpect(jsonPath("$.title.en").value("Revision 11"));
        mvc.perform(get("/api/admin/government/corrections").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/government/corrections").with(oauth2Login().attributes(v->v.put("sub","editor")))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].text").value("<img src=x onerror=bad()>"));
        mvc.perform(post("/api/admin/government/corrections/"+correction+"/review").with(oauth2Login().attributes(v->v.put("sub","editor"))).with(csrf()).contentType("application/json").content("{\"note\":\"Missing outcome\"}")).andExpect(status().isBadRequest());
    }
    static LocalText text(String s){return new LocalText(s,s,s);}
    void publish(Object value,SubjectType type,String slug,UUID actor){var draft=repository.createDraft("2026-2031",type,slug,value,actor,"Fixture revision");var checked=editorial.review(draft.id(),1,actor);editorial.publish(checked.id(),checked.rowVersion(),actor);}
}
