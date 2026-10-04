package dev.maboullaite.fhemni.memberanalysis;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.time.*;
import dev.maboullaite.fhemni.identity.*;
import dev.maboullaite.fhemni.gemini.*;
import dev.maboullaite.fhemni.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@SpringBootTest(properties={"fhemni.gemini.api-key=","fhemni.member-analysis.enabled=true","fhemni.cost-control.chat-enabled=true","fhemni.cost-control.max-daily-analyses=100","spring.datasource.url=jdbc:h2:mem:member-worker;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class MemberAnalysisWorkerIntegrationTest {
    @Autowired MemberAnalysisAdmission admission;@Autowired MemberAnalysisRepository repository;@Autowired MemberAnalysisService service;@Autowired UserAccountRepository users;
    @Autowired MemberQuestionService questions;@Autowired dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository revisions;@Autowired dev.maboullaite.fhemni.catalog.CatalogVideoRepository catalog;
    @MockitoBean VideoEligibilityGateway videos;@MockitoBean VideoIntelligenceGateway gateway;
    UUID user;String url="https://youtu.be/abcdefghijk";
    @BeforeEach void setup(){user=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Member",null,false,null),false).id();when(videos.configured()).thenReturn(true);when(videos.inspect(any())).thenReturn(new VideoEligibilityGateway.Eligibility(true,100L,false,null));when(gateway.live()).thenReturn(true);when(gateway.model()).thenReturn("fixture");when(gateway.promptVersion()).thenReturn("v1");when(gateway.factCheckModel()).thenReturn("fixture");when(gateway.factCheckPromptVersion()).thenReturn("v1");when(gateway.credentialVersion()).thenReturn("fixture");when(gateway.analyze(any(),any(),any())).thenReturn(new GatewayAnalysisResult("private-interaction",new VideoReport("Fixture","Summary","Details",List.of(),List.of(),List.of(),List.of())));when(gateway.factCheck(any(),any())).thenReturn(new GatewayFactCheckResult(List.of(),null));}
    @Test void resultIsDurablePrivateAndExplicitPublicationRequestDoesNotPublish(){var job=admission.submit(user,url,"en",UUID.randomUUID().toString());var lease=repository.claim(Instant.now()).orElseThrow();service.process(lease);assertThat(repository.owner(job.id(),user).state()).isEqualTo("READY");assertThat(repository.owner(job.id(),user).report().title()).isEqualTo("Fixture");service.requestPublication(job.id(),user,true);assertThat(service.publicationStatus(job.id(),user)).isEqualTo("REQUESTED");verify(gateway,times(1)).analyze(any(),any(),any());assertThat(repository.claim(Instant.now())).isEmpty();}
    @Test void healthyLeaseIsNotReclaimedAndUncertainExpiryFailsWithoutRerun(){var job=admission.submit(user,url,"en",UUID.randomUUID().toString());var lease=repository.claim(Instant.now()).orElseThrow();assertThat(repository.claim(Instant.now())).isEmpty();repository.expire(Instant.now().plus(Duration.ofHours(2)));service.process(lease);assertThat(repository.owner(job.id(),user).state()).isEqualTo("FAILED");verify(gateway,never()).analyze(any(),any(),any());assertThat(repository.used(user,YearMonth.now(ZoneOffset.UTC))).isZero();}
    @Test void credentialMismatchFailsClosed(){var job=admission.submit(user,url,"en",UUID.randomUUID().toString());var lease=repository.claim(Instant.now()).orElseThrow();when(gateway.credentialVersion()).thenReturn("changed");service.process(lease);assertThat(repository.owner(job.id(),user).errorCode()).isEqualTo("CONFIGURATION_CHANGED");verify(gateway,never()).analyze(any(),any(),any());}
    @Test void questionHistoryIsOwnerPrivateAndApprovedPublicationCreatesSeparateDerivative(){
        var job=admission.submit(user,url,"en",UUID.randomUUID().toString());service.process(repository.claim(Instant.now()).orElseThrow());
        when(gateway.ask(any(),any(),any(),any(),any())).thenReturn(new GatewayAnswerResult("private-next","Private answer",List.of()));questions.ask(job.id(),user,"Why?",QuestionMode.VIDEO);
        assertThat(questions.answers(job.id(),user)).hasSize(1);assertThatThrownBy(()->questions.answers(job.id(),UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
        service.requestPublication(job.id(),user,true);var request=service.requests(1).items().stream().filter(row->row.jobId().equals(job.id())).findFirst().orElseThrow();
        assertThatThrownBy(()->service.publish(request.id(),user)).isInstanceOf(IllegalStateException.class);service.review(request.id(),user,true,"Reviewed source and report");
        catalog.saveImported(new dev.maboullaite.fhemni.catalog.CatalogVideoRepository.ImportedCatalogVideo("abcdefghijk","https://www.youtube.com/watch?v=abcdefghijk","Fixture","Fixture channel","https://example.org/fixture.png",null,null,"en"));
        UUID derivative=service.publish(request.id(),user);assertThat(derivative).isNotEqualTo(job.id());assertThat(revisions.findPublished(job.id())).isEmpty();assertThat(revisions.findPublished(derivative)).isPresent();assertThat(revisions.find(derivative).orElseThrow().interactionId()).isNull();assertThat(questions.answers(job.id(),user)).hasSize(1);
    }
}
