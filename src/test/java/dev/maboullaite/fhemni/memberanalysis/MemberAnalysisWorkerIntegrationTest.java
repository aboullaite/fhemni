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
    @Test void competingQuestionsForLastAccountSlotHaveOneWinner(){
        var first=admission.submit(user,url,"en",UUID.randomUUID().toString());service.process(repository.claim(Instant.now()).orElseThrow());
        var second=admission.submit(user,url,"en",UUID.randomUUID().toString());service.process(repository.claim(Instant.now()).orElseThrow());
        for(int i=0;i<19;i++)jdbc.sql("INSERT INTO member_allowance_receipts(id,job_id,user_id,kind,charged,requested_at) VALUES(:id,:job,:user,'QUESTION',TRUE,:now)")
            .param("id",UUID.randomUUID()).param("job",first.id()).param("user",user).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();
        when(gateway.ask(any(),any(),any(),any(),any())).thenReturn(new GatewayAnswerResult("answer","Answer",List.of()));
        var calls=List.of(first.id(),second.id()).stream().map(id->java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{questions.ask(id,user,"Last?",QuestionMode.VIDEO);return true;}catch(IllegalStateException exhausted){assertThat(exhausted).hasMessage("MONTHLY_CHAT_ALLOWANCE_REACHED");return false;}
        })).toList();
        assertThat(calls.stream().map(java.util.concurrent.CompletableFuture::join).filter(Boolean::booleanValue)).hasSize(1);
        verify(gateway,times(1)).ask(any(),any(),any(),any(),any());
    }
    @Test void twentyQuestionsAreSharedAcrossReportsNotLimitedToTenPerVideo(){
        var first=admission.submit(user,url,"en",UUID.randomUUID().toString());service.process(repository.claim(Instant.now()).orElseThrow());
        var second=admission.submit(user,url,"en",UUID.randomUUID().toString());service.process(repository.claim(Instant.now()).orElseThrow());
        when(gateway.ask(any(),any(),any(),any(),any())).thenReturn(new GatewayAnswerResult("answer","Answer",List.of()));
        for(int i=0;i<20;i++)questions.ask(i<12?first.id():second.id(),user,"Question "+i,QuestionMode.VIDEO);
        assertThat(questions.answers(first.id(),user)).hasSize(12);
        assertThat(questions.answers(second.id(),user)).hasSize(8);
        assertThatThrownBy(()->questions.ask(second.id(),user,"One too many",QuestionMode.VIDEO)).hasMessage("MONTHLY_CHAT_ALLOWANCE_REACHED");
        verify(gateway,times(20)).ask(any(),any(),any(),any(),any());
    }
    @Autowired MemberAnalysisAdmission admission;@Autowired MemberAnalysisRepository repository;@Autowired MemberAnalysisService service;@Autowired UserAccountRepository users;
    @Autowired MemberQuestionService questions;@Autowired dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository revisions;@Autowired dev.maboullaite.fhemni.catalog.CatalogVideoRepository catalog;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
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
    @Test void failedOrUncertainQuestionAllowsOnlyAnExplicitNewAttempt(){
        for(var failure:List.of(new GeminiApiException("rejected",null,400),new GeminiApiException("timeout"))){
            var account=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Member",null,false,null),false).id();
            var job=admission.submit(account,url,"en",UUID.randomUUID().toString());service.process(repository.claim(Instant.now()).orElseThrow());
            when(gateway.ask(any(),any(),any(),any(),any())).thenThrow(failure).thenReturn(new GatewayAnswerResult("fresh-answer","New answer",List.of()));
            assertThatThrownBy(()->questions.ask(job.id(),account,"First?",QuestionMode.VIDEO)).isInstanceOf(IllegalStateException.class);
            assertThat(questions.status(job.id(),account)).isEqualTo(failure.upstreamStatus()==null?"UNCERTAIN":"FAILED");
            assertThat(questions.answers(job.id(),account)).isEmpty();
            questions.ask(job.id(),account,"A new question?",QuestionMode.VIDEO);
            assertThat(questions.answers(job.id(),account)).hasSize(1);assertThat(questions.status(job.id(),account)).isEqualTo("ANSWERED");
        }
    }
    @Test void expiredQuestionCompletionCannotAppendButDoesNotFreezeFutureQuestions(){
        var job=admission.submit(user,url,"en",UUID.randomUUID().toString());service.process(repository.claim(Instant.now()).orElseThrow());
        when(gateway.ask(any(),any(),any(),any(),any())).thenAnswer(call->{jdbc.sql("UPDATE member_analysis_conversations SET lease_until=:until WHERE job_id=:id").param("id",job.id()).param("until",Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC)).update();return new GatewayAnswerResult("late","Late answer",List.of());});
        assertThatThrownBy(()->questions.ask(job.id(),user,"First?",QuestionMode.VIDEO)).isInstanceOf(IllegalStateException.class);
        assertThat(questions.answers(job.id(),user)).isEmpty();assertThat(questions.status(job.id(),user)).isEqualTo("UNCERTAIN");
        when(gateway.ask(any(),any(),any(),any(),any())).thenReturn(new GatewayAnswerResult("fresh","Fresh answer",List.of()));
        questions.ask(job.id(),user,"Next?",QuestionMode.VIDEO);assertThat(questions.answers(job.id(),user)).extracting(FollowUpAnswer::answer).containsExactly("Fresh answer");
    }
}
