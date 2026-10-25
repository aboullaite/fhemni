package dev.maboullaite.fhemni.government;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class PublicationScopeTest {
    @Test void regionalMeasurementsRemainDiscoverableUnderANationalCommitment(){
        var label=new LocalText("Test","Test","Test");
        var observation=new Observation(UUID.randomUUID(),"homes","homes",java.math.BigDecimal.TEN,LocalDate.now().minusDays(1),LocalDate.now(),new Scope("REGION","region-code",label),"v1",List.of(UUID.randomUUID()),Instant.now());
        var commitment=new CommitmentView(UUID.randomUUID(),"test",Origin.GOVERNMENT,label,"Quote","en",label,null,List.of(),new Scope("NATIONAL","MA",label),null,null,null,null,List.of(),List.of(),List.of(observation,observation),List.of());
        assertThat(GovernmentPublicationEvents.regionCodes(commitment)).containsExactly("region-code");
    }
}
