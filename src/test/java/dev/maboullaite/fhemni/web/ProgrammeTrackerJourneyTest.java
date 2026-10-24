package dev.maboullaite.fhemni.web;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;
import dev.maboullaite.fhemni.government.*;
import dev.maboullaite.fhemni.identity.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"fhemni.gemini.api-key=","spring.datasource.url=jdbc:h2:mem:programme-journey;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class ProgrammeTrackerJourneyTest {
    @Autowired MockMvc mvc;
    @Autowired GovernmentRepository repository;
    @Autowired GovernmentEvidenceRepository evidence;
    @Autowired GovernmentEditorialService editorial;
    @Autowired UserAccountRepository users;
    @Test void regionalProgrammeDiscoveryUsesPublishedEntriesWithoutInventingMeasurements() throws Exception {
        UUID actor=UUID.randomUUID();repository.ensureMandate("2026-2031");
        var proof=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/test-discovery","مشروع التنمية القروية المندمجة لخنيفرة","page 26","ar",null,Instant.now().minusSeconds(2),null),null);
        publish(SubjectType.MANDATE,"2026-2031",new MandateView("2026-2031",text("Discovery fixture mandate"),Lifecycle.PROGRAMME_PUBLISHED,Instant.now().minusSeconds(1),List.of(new Membership(UUID.randomUUID(),text("Fixture minister"),text("Fixture role"),null,LocalDate.now().minusDays(2),null,List.of(proof.id()))),new ProgrammeVersion(UUID.randomUUID(),1,text("Programme"),LocalDate.now().minusDays(1),List.of(proof.id())),new ReviewCoverage(0,null,false),List.of()),actor);
        publish(SubjectType.COMMITMENT,"programme-p026-khenifra",new CommitmentView(UUID.randomUUID(),"programme-p026-khenifra",Origin.GOVERNMENT,text("تنمية قروية بخنيفرة"),proof.passage(),"ar",text("Programme"),null,List.of("OTHER"),new Scope("UNSPECIFIED","UNSPECIFIED",text("Unspecified")),null,null,null,null,List.of(),List.of(proof),List.of(),List.of(),new ProgrammeContext("MIRROR_UNAUTHENTICATED",List.of(26),"service",null,null,null,null,List.of(),List.of())),actor);
        mvc.perform(get("/api/catalog/government/2026-2031/programme-topics")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(29));
        mvc.perform(get("/api/catalog/government/2026-2031/regional-programme?region=beni-mellal-khenifra&q=خنيفرة"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].commitment.topicCodes[0]").value("REGIONAL_DEVELOPMENT"))
            .andExpect(jsonPath("$.items[0].places[0].printedPage").value(26)).andExpect(jsonPath("$.items[0].commitment.deliveryAssessment").value(nullValue()));
        mvc.perform(get("/api/catalog/government/2026-2031/observations?region=beni-mellal-khenifra")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/catalog/government/2026-2031/regional-programme?region=invented")).andExpect(status().isBadRequest());
    }
    @Test void sourcedProgrammeToSearchDetailsFollowAndUpdateFeed() throws Exception {
        UUID actor=UUID.randomUUID();
        repository.ensureMandate("2026-2031");
        var proof=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/test-only","Test-only source passage","page 60","en",null,Instant.now().minusSeconds(2),null),null);
        publish(SubjectType.MANDATE,"2026-2031",new MandateView("2026-2031",text("Fixture mandate"),Lifecycle.PROGRAMME_PUBLISHED,Instant.now().minusSeconds(1),
            List.of(new Membership(UUID.randomUUID(),text("Current minister"),text("Role"),null,LocalDate.now().minusDays(2),null,List.of(proof.id())),new Membership(UUID.randomUUID(),text("Former minister"),text("Former role"),null,LocalDate.now().minusDays(2),LocalDate.now().minusDays(1),List.of(proof.id()))),
            new ProgrammeVersion(UUID.randomUUID(),1,text("Programme"),LocalDate.now().minusDays(1),List.of(proof.id())),new ReviewCoverage(0,null,false),List.of()),actor);
        UUID first=null;
        for(int i=0;i<12;i++) {
            UUID id=UUID.randomUUID();if(i==0)first=id;
            publish(SubjectType.COMMITMENT,"journey-"+i,new CommitmentView(id,"journey-"+i,Origin.GOVERNMENT,text("Health entry "+i),proof.passage(),"en",text("Programme"),null,List.of("HEALTH"),new Scope("NATIONAL","MA",text("Morocco")),null,null,null,null,List.of(),List.of(proof),List.of(),List.of(),
                new ProgrammeContext("MIRROR_UNAUTHENTICATED",List.of(60),"service",null,"2031","at least 75 percent",null,List.of("Test extraction, not a verified delivery claim."),List.of())),actor);
        }
        mvc.perform(get("/api/catalog/government/2026-2031/commitments?origin=GOVERNMENT&q=health&assessment=UNASSESSED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(12)).andExpect(jsonPath("$.items.length()").value(10));
        mvc.perform(get("/api/catalog/government/2026-2031/commitments?origin=GOVERNMENT&topic=HEALTH&page=2"))
            .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/catalog/government/2026-2031/programme-insights?origin=GOVERNMENT&topic=HEALTH&q=health"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalEntries").value(12))
            .andExpect(jsonPath("$.topics[0].count").value(12)).andExpect(jsonPath("$.entries.length()").value(12));
        mvc.perform(get("/api/catalog/government/2026-2031/commitments?origin=GOVERNMENT&assessment=DELIVERED"))
            .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/catalog/government/commitments/journey-0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.programmeContext.deadlineText").value("2031"))
            .andExpect(jsonPath("$.target").value(nullValue())).andExpect(jsonPath("$.deadline").value(nullValue()))
            .andExpect(jsonPath("$.deliveryAssessment").value(nullValue())).andExpect(jsonPath("$.revisions.length()").value(1));
        mvc.perform(get("/api/catalog/government/2026-2031/sources/"+proof.id())).andExpect(status().isOk());
        users.recordLogin(new ExternalIdentityProfile("test","journey-reader",null,"Reader","reader@example.org",true,null),false);
        var auth=oauth2Login().attributes(values->values.put("sub","journey-reader"));
        mvc.perform(put("/api/account/follows/COMMITMENT/"+first).with(auth).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/account/follows").with(auth)).andExpect(jsonPath("$[0].title.en").value("Health entry 0"));
        mvc.perform(get("/api/account/updates").with(auth)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(delete("/api/account/follows/COMMITMENT/"+first).with(auth).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/account/follows").with(auth)).andExpect(jsonPath("$.length()").value(0));
    }
    private LocalText text(String value){return new LocalText(value,value,value);}
    private void publish(SubjectType type,String slug,Object value,UUID actor){var draft=repository.createDraft("2026-2031",type,slug,value,actor,"Test-only research journey");var reviewed=editorial.review(draft.id(),draft.rowVersion(),actor);editorial.publish(reviewed.id(),reviewed.rowVersion(),actor);}
}
