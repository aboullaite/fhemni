package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest(properties="fhemni.gemini.api-key=")
class MeasureIntegrationTest {
    @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);}
    @Autowired GovernmentRepository repository;
    @Autowired GovernmentEditorialService editorial;
    @Autowired GovernmentEvidenceRepository evidence;
    @Autowired MeasureService measures;
    @Autowired GeographicDeliveryService geography;
    @Autowired GovernmentService government;
    final UUID actor=UUID.randomUUID();
    static LocalText text(String s){return new LocalText(s,s,s);}
    @Test void practicalAvailabilityIsReviewedSeparateFromDatesAndSuspensionRemovesApply() {
        repository.ensureMandate("2026-2031");
        var source=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/measure","Official test-only measure passage",null,"en",null,Instant.now().minusSeconds(1),null),null);
        UUID id=UUID.randomUUID();
        for(var status:List.of(OperationalStatus.PENDING,OperationalStatus.AVAILABLE,OperationalStatus.SUSPENDED)){
            var value=new MeasureView(id,"test-measure",text("Measure"),LocalDate.now().minusDays(2),LocalDate.now().minusDays(1),status,text("Eligibility"),text("Steps"),"https://example.org/apply",new Scope("NATIONAL","MA",text("Morocco")),text("Responsible agency"),List.of(source));
            var draft=repository.createDraft("2026-2031",SubjectType.MEASURE,value.slug(),value,actor,"Test-only revision");
            if(status==OperationalStatus.PENDING)assertThat(measures.list("2026-2031",1).items()).isEmpty();
            var reviewed=editorial.review(draft.id(),draft.rowVersion(),actor);editorial.publish(reviewed.id(),reviewed.rowVersion(),actor);
            assertThat(measures.detail(value.slug()).operationalStatus()).isEqualTo(status);
            assertThat(MeasureService.canApply(measures.detail(id.toString()))).isEqualTo(status==OperationalStatus.AVAILABLE);
            assertThat(government.source("2026-2031",source.id())).isEqualTo(source);
        }
        assertThat(repository.publicHistory(SubjectType.MEASURE,"test-measure")).hasSize(3);
        var invalid=new MeasureView(UUID.randomUUID(),"invalid",text("Missing evidence"),null,null,OperationalStatus.AVAILABLE,null,null,null,new Scope("REGION","invented",text("Unknown")),null,List.of());
        var draft=repository.createDraft("2026-2031",SubjectType.MEASURE,invalid.slug(),invalid,actor,"Invalid fixture");
        assertThatThrownBy(()->editorial.review(draft.id(),draft.rowVersion(),actor)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->geography.list("2026-2031","invented",1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nationalEvidenceIsNotReplicatedIntoRegionalTotals() {
        var national=new Observation(UUID.randomUUID(),"homes","homes",java.math.BigDecimal.TEN,LocalDate.of(2026,1,1),LocalDate.of(2026,9,30),new Scope("NATIONAL","MA",text("Morocco")),"v1",List.of(UUID.randomUUID()),Instant.now().minusSeconds(1));
        assertThat(GeographicDeliveryService.matchesRegion(national,"01")).isFalse();
        assertThat(GeographicDeliveryService.matchesRegion(national,null)).isTrue();
        assertThat(geography.regions()).hasSize(12);
    }
}
