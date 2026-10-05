package dev.maboullaite.fhemni.memberanalysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import dev.maboullaite.fhemni.identity.*;
import dev.maboullaite.fhemni.gemini.*;
import dev.maboullaite.fhemni.model.*;

@Testcontainers
@SpringBootTest(properties={"fhemni.gemini.api-key=","fhemni.member-analysis.enabled=true","fhemni.member-analysis.worker-enabled=false","fhemni.cost-control.chat-enabled=true","fhemni.cost-control.max-daily-analyses=100"})
class MemberRetentionIntegrationTest {
    @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);}
    @Autowired MemberAnalysisAdmission admission; @Autowired MemberAnalysisRepository repository;
    @Autowired MemberAnalysisService service; @Autowired MemberQuestionService questions;
    @Autowired UserAccountRepository users; @Autowired JdbcClient jdbc; @Autowired MemberAnalysisPolicy policy;
    @Autowired MemberAnalysisRetention retention;
    @Autowired dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository revisions;
    @Autowired dev.maboullaite.fhemni.catalog.CatalogVideoRepository catalog;
    @MockitoBean VideoEligibilityGateway videos; @MockitoBean VideoIntelligenceGateway gateway;
    UUID user;
    @BeforeEach void setup(){
        user=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Member",null,false,null),false).id();
        when(videos.configured()).thenReturn(true);when(videos.inspect(any())).thenReturn(new VideoEligibilityGateway.Eligibility(true,100L,false,null));
        when(gateway.live()).thenReturn(true);when(gateway.model()).thenReturn("fixture");when(gateway.promptVersion()).thenReturn("v1");when(gateway.factCheckModel()).thenReturn("fixture");when(gateway.factCheckPromptVersion()).thenReturn("v1");when(gateway.credentialVersion()).thenReturn("fixture");
        when(gateway.analyze(any(),any(),any())).thenReturn(new GatewayAnalysisResult("private-interaction",new VideoReport("Private title","Summary","Details",List.of(),List.of(),List.of(),List.of())));
        when(gateway.factCheck(any(),any())).thenReturn(new GatewayFactCheckResult(List.of(),null));
        when(gateway.ask(any(),any(),any(),any(),any())).thenReturn(new GatewayAnswerResult("next","Private answer",List.of()));
    }
    MemberJob completed(){var job=admission.submit(user,"https://youtu.be/abcdefghijk","en",UUID.randomUUID().toString());assertThat(job.expiresAt()).isNull();service.process(repository.claim(Instant.now()).orElseThrow());return repository.owner(job.id(),user);}
    void expire(UUID id){jdbc.sql("UPDATE member_analysis_jobs SET expires_at=:now WHERE id=:id").param("id",id).param("now",Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC)).update();}
    @Test void terminalReportsExpireThirtyDaysAfterCompletionAndAccessStopsBeforeCleanup(){
        var job=completed();assertThat(job.expiresAt()).isEqualTo(job.updatedAt().plus(Duration.ofDays(30)));
        questions.ask(job.id(),user,"Why?",QuestionMode.VIDEO);service.requestPublication(job.id(),user,true);expire(job.id());
        assertThat(repository.history(user,1).items()).isEmpty();
        assertThatThrownBy(()->repository.owner(job.id(),user)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->questions.answers(job.id(),user)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->service.requestPublication(job.id(),user,true)).isInstanceOf(NoSuchElementException.class);
        assertThat(service.requests(1).items()).noneMatch(row->row.jobId().equals(job.id()));
        assertThat(retention.purgeExpired(Instant.now())).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM member_analysis_conversations WHERE job_id=:id").param("id",job.id()).query(Long.class).single()).isZero();
        assertThat(policy.forUser(user).remaining()).isEqualTo(9);assertThat(policy.forUser(user).chatRemaining()).isEqualTo(19);
    }
    @Test void purgingPrivateOriginalDoesNotDeleteIndependentPublishedReport(){
        catalog.saveImported(new dev.maboullaite.fhemni.catalog.CatalogVideoRepository.ImportedCatalogVideo("abcdefghijk","https://www.youtube.com/watch?v=abcdefghijk","Fixture","Fixture channel","https://example.org/fixture.png",null,null,"en"));
        var job=completed();service.requestPublication(job.id(),user,true);
        var request=service.requests(1).items().stream().filter(row->row.jobId().equals(job.id())).findFirst().orElseThrow();
        service.review(request.id(),user,true,"Reviewed for publication");UUID derivative=service.publish(request.id(),user);
        expire(job.id());retention.purgeExpired(Instant.now());assertThat(revisions.findPublished(derivative)).isPresent();
    }
    @Test void answerFinishingAfterExpiryCannotRestorePrivateContent(){
        var job=completed();when(gateway.ask(any(),any(),any(),any(),any())).thenAnswer(call->{expire(job.id());retention.purgeExpired(Instant.now());return new GatewayAnswerResult("late","Must not persist",List.of());});
        assertThatThrownBy(()->questions.ask(job.id(),user,"Too late?",QuestionMode.VIDEO)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM member_analysis_conversations WHERE job_id=:id").param("id",job.id()).query(Long.class).single()).isZero();
    }
    @Test void purgeBoundaryIsInclusiveAndFailedJobsAlsoExpire(){
        var failed=admission.submit(user,"https://youtu.be/abcdefghijk","en",UUID.randomUUID().toString());repository.failQueued(failed.id(),user);
        var job=repository.owner(failed.id(),user);assertThat(job.expiresAt()).isEqualTo(job.updatedAt().plus(Duration.ofDays(30)));
        assertThat(retention.purgeExpired(job.expiresAt().minusNanos(1000))).isZero();
        assertThat(retention.purgeExpired(job.expiresAt())).isEqualTo(1);
        assertThat(policy.forUser(user).remaining()).isEqualTo(10);
    }
}
