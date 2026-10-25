package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProgrammeDiscoveryTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private ProgrammeDiscovery discovery(){return new ProgrammeDiscovery(mapper.readTree("""
        {"version":1,"topics":[{"code":"TRANSPORT_INFRASTRUCTURE","label":{"ar":"النقل","fr":"Transports","en":"Transport"}}],
        "entries":[{"slug":"programme-rail","titleAr":"السكك","sourceAnchor":"فاس ووجدة","topicCodes":["TRANSPORT_INFRASTRUCTURE"]}]}
        """),mapper.readTree("""
        {"version":1,"entries":[{"slug":"programme-rail","titleAr":"السكك","sourceAnchor":"فاس ووجدة","places":[
        {"regionCode":"fes-meknes","place":{"ar":"فاس","fr":"Fès","en":"Fez"},"sourceExcerpt":"فاس ووجدة","printedPage":109},
        {"regionCode":"oriental","place":{"ar":"وجدة","fr":"Oujda","en":"Oujda"},"sourceExcerpt":"فاس ووجدة","printedPage":109},
        {"regionCode":"oriental","place":{"ar":"الناظور","fr":"Nador","en":"Nador"},"sourceExcerpt":"الناظور","printedPage":109}]}]}
        """));}
    private CommitmentView row(String text){return new CommitmentView(UUID.randomUUID(),"programme-rail",Origin.GOVERNMENT,new LocalText("السكك","Rail","Rail"),text,"ar",null,null,List.of("OTHER"),new Scope("UNSPECIFIED","UNSPECIFIED",new LocalText("?","?","?")),null,null,null,null,List.of(),List.of(),List.of(),List.of(),new ProgrammeContext("MIRROR_UNAUTHENTICATED",List.of(109),null,null,null,null,null,List.of(),List.of()));}
    @Test void identityBoundDiscoveryChangesOnlyTagsAndNeverDeliveryOrGeographicScope(){
        var discovery=discovery();var raw=row("فاس ووجدة");var projected=discovery.project(raw);
        assertEquals(List.of("TRANSPORT_INFRASTRUCTURE"),projected.topicCodes());assertEquals(List.of("OTHER"),raw.topicCodes());
        assertNull(projected.deliveryAssessment());assertEquals("UNSPECIFIED",projected.geographicScope().level());
        assertEquals(3,discovery.places(projected).size());assertEquals(0,discovery.places(row("changed source")).size());
        assertEquals(List.of("OTHER"),discovery.project(row("changed source")).topicCodes());
    }
    @Test void regionalIndexCountsUniqueCommitmentsRatherThanPlacesAndNeverExpandsToAllRegions(){
        var discovery=discovery();var commitments=mock(CommitmentService.class);var geography=mock(GeographicDeliveryService.class);
        when(geography.regions()).thenReturn(List.of(scope("oriental"),scope("fes-meknes"),scope("souss-massa")));
        var records=new ArrayList<CommitmentView>();for(int i=0;i<12;i++)records.add(row("فاس ووجدة"));
        records.add(row("unmapped source"));
        when(commitments.selected("2026-2031","GOVERNMENT",null,null,null,null)).thenReturn(records);
        var service=new ProgrammeRegionalService(commitments,discovery,geography);
        var result=service.list("2026-2031","oriental",null,null,1);
        assertEquals(12,result.totalItems());assertEquals(10,result.items().size());assertEquals(13,result.programmeEntries());assertEquals(12,result.linkedEntries());
        assertEquals(12,result.regionCounts().getFirst().count());assertEquals(0,result.regionCounts().getLast().count());
        assertEquals(2,service.list("2026-2031","oriental",null,null,2).items().size());
        assertEquals(0,service.list("2026-2031","souss-massa",null,null,1).totalItems());
        assertThrows(IllegalArgumentException.class,()->service.list("2026-2031","invented",null,null,1));
    }
    private Scope scope(String code){return new Scope("REGION",code,new LocalText(code,code,code));}
    @Test void bundledIndexesAreCompleteAndRejectChangedIdentity() throws Exception {
        var discovery=new ProgrammeDiscovery(mapper);
        assertEquals(29,discovery.topics().size());
        var resource=new org.springframework.core.io.ClassPathResource("government/programme-topics.json");
        try(var stream=resource.getInputStream()){
            var index=mapper.readTree(stream);assertEquals(595,index.path("entries").size());
            for(var entry:index.path("entries"))assertFalse(entry.path("topicCodes").toString().contains("OTHER"));
        }
        var original=row("فاس ووجدة");
        var missingPages=new CommitmentView(original.id(),original.slug(),original.origin(),original.title(),original.originalText(),original.originalLanguage(),original.author(),null,original.topicCodes(),original.geographicScope(),null,null,null,null,List.of(),List.of(),List.of(),List.of(),new ProgrammeContext("MIRROR_UNAUTHENTICATED",List.of(110),null,null,null,null,null,List.of(),List.of()));
        assertTrue(discovery().places(missingPages).isEmpty());
    }
}
