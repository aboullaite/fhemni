package dev.maboullaite.fhemni.government;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CommitmentSearchTest {
    @Test void deliveryCountsCoverAllPublishedGovernmentRowsAndKeepUnknownSeparate() throws Exception {
        var repository=mock(CommitmentRepository.class);
        var rows=new ArrayList<CommitmentView>();
        for(int i=0;i<14;i++) {
            var assessment=i<3?new DeliveryAssessment(DeliveryState.DELIVERED,null,null,List.of(),null)
                    :i==3?new DeliveryAssessment(DeliveryState.UNDERWAY,null,null,List.of(),null):null;
            rows.add(new CommitmentView(UUID.randomUUID(),"entry-"+i,Origin.GOVERNMENT,new LocalText("عنوان","Titre","Title"),"text","ar",null,null,List.of("HEALTH"),null,null,null,null,assessment,List.of(),List.of(),List.of(),List.of()));
        }
        when(repository.published("2026-2031")).thenReturn(rows);
        var service=new CommitmentService(repository,mock(CampaignPromiseAdapter.class),mock(GovernmentRepository.class),mock(ProgrammeRelationships.class));
        var json=new tools.jackson.databind.json.JsonMapper().valueToTree(service.insights("2026-2031","GOVERNMENT",null,null,null,null));
        assertEquals(3,json.path("deliveryCounts").path("DELIVERED").asInt());
        assertEquals(1,json.path("deliveryCounts").path("UNDERWAY").asInt());
        assertEquals(10,json.path("deliveryCounts").path("UNASSESSED").asInt());
        assertEquals(0,json.path("deliveryCounts").path("NO_EVIDENCE_FOUND").asInt());
    }
    @Test void filtersBeforePaginationAndPreservesProgrammeContextOnDetail() {
        var repository=mock(CommitmentRepository.class);var campaigns=mock(CampaignPromiseAdapter.class);
        var revisions=mock(GovernmentRepository.class);var relationships=mock(ProgrammeRelationships.class);
        var context=new ProgrammeContext("MIRROR_UNAUTHENTICATED",List.of(60),"service","health coverage",null,null,null,List.of(),List.of());
        var rows=new ArrayList<CommitmentView>();
        for(int i=0;i<15;i++)rows.add(new CommitmentView(UUID.randomUUID(),"health-"+i,Origin.GOVERNMENT,new LocalText("الصحة","Santé","Health"),"original","ar",null,null,List.of("HEALTH"),null,null,null,null,null,List.of(),List.of(),List.of(),List.of(),context));
        when(repository.published("2026-2031")).thenReturn(rows);
        when(relationships.project(any())).thenAnswer(call->call.getArgument(0));
        var service=new CommitmentService(repository,campaigns,revisions,relationships);
        var first=service.list("2026-2031","GOVERNMENT",null,null,"الصحة","UNASSESSED",1);
        assertEquals(15,first.totalItems());assertEquals(10,first.items().size());
        assertEquals(5,service.list("2026-2031","GOVERNMENT",null,null,"health","UNASSESSED",2).items().size());
        assertEquals(0,service.list("2026-2031","GOVERNMENT",null,null,"health","DELIVERED",1).totalItems());
        assertEquals(0,service.list("2026-2031","GOVERNMENT",null,null,"nonexistent",null,1).totalItems());
        assertThrows(IllegalArgumentException.class,()->service.list("2026-2031","GOVERNMENT",null,null,null,"FAKE",1));
        assertThrows(IllegalArgumentException.class,()->service.list("2026-2031","GOVERNMENT",null,null,"x".repeat(201),null,1));
        assertEquals(context,service.detail("health-0").programmeContext());
        var insights=service.insights("2026-2031","GOVERNMENT",null,null,"health","UNASSESSED");
        assertEquals(15,insights.totalEntries());
        assertEquals(15,insights.topics().getFirst().count());
        assertEquals("HEALTH",insights.topics().getFirst().code());
        assertEquals(15,insights.entries().size());
        assertEquals(context,insights.entries().getFirst().programmeContext());
        assertEquals(0,service.insights("2026-2031","GOVERNMENT","OTHER",null,null,null).totalEntries());
        assertEquals(0,service.insights("2026-2031","GOVERNMENT",null,null,null,"DELIVERED").totalEntries());
        assertThrows(NoSuchElementException.class,()->service.insights("unknown",null,null,null,null,null));
    }
}
