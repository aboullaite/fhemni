package dev.maboullaite.fhemni.memberanalysis;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import dev.maboullaite.fhemni.identity.*;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
@SpringBootTest(properties={"fhemni.gemini.api-key=","fhemni.member-analysis.enabled=true","fhemni.member-analysis.worker-enabled=true","fhemni.member-analysis.worker-initial-delay-ms=3600000","fhemni.cost-control.analysis-enabled=true","fhemni.cost-control.max-daily-analyses=100","spring.datasource.url=jdbc:h2:mem:member-admission;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class MemberAnalysisAdmissionIntegrationTest {
    @Autowired MemberAnalysisPolicy policy;
    @Test void oldMonthReceiptsDoNotConsumeNewMonthAndOtherAccountsStayIsolated(){
        var job=admission.submit(user,url,"en",UUID.randomUUID().toString());
        assertThat(policy.forUser(user).remaining()).isEqualTo(9);
        assertThat(policy.forUser(user).chatRemaining()).isEqualTo(20);
        jdbc.sql("UPDATE member_allowance_receipts SET requested_at=:old WHERE job_id=:id")
            .param("old",MemberAllowanceWindow.current(java.time.Clock.systemUTC()).start().minusSeconds(1).atOffset(java.time.ZoneOffset.UTC)).param("id",job.id()).update();
        assertThat(policy.forUser(user).remaining()).isEqualTo(10);
        var another=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Other",null,false,null),false).id();
        assertThat(policy.forUser(another).remaining()).isEqualTo(10);
    }
    @Autowired MemberAnalysisAdmission admission;@Autowired MemberAnalysisRepository repository;@Autowired UserAccountRepository users;@Autowired JdbcClient jdbc;
    @MockitoBean VideoEligibilityGateway videos;@MockitoBean VideoIntelligenceGateway gateway;
    UUID user;String url="https://www.youtube.com/watch?v=abcdefghijk";
    @BeforeEach void setup(){when(videos.configured()).thenReturn(true);when(videos.inspect(any())).thenReturn(new VideoEligibilityGateway.Eligibility(true,1800L,false,null));when(gateway.live()).thenReturn(true);when(gateway.model()).thenReturn("test-model");when(gateway.promptVersion()).thenReturn("v1");when(gateway.factCheckModel()).thenReturn("test-facts");when(gateway.factCheckPromptVersion()).thenReturn("v1");when(gateway.credentialVersion()).thenReturn("test");user=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Member",null,false,null),false).id();}
    @Test void exactLimitAndIdempotency(){var key=UUID.randomUUID().toString();var first=admission.submit(user,url,"en",key);assertThat(admission.submit(user,"https://youtu.be/abcdefghijk","en",key).id()).isEqualTo(first.id());assertThatThrownBy(()->admission.submit(user,url,"fr",key)).isInstanceOf(IllegalArgumentException.class);assertThat(repository.used(user,java.time.YearMonth.now(MemberAllowanceWindow.ZONE))).isEqualTo(1);}
    @Test void unknownOrTooLongNeverConsumesAllowance(){for(Long seconds:new Long[]{null,1801L}){when(videos.inspect(any())).thenReturn(new VideoEligibilityGateway.Eligibility(true,seconds,false,null));assertThatThrownBy(()->admission.submit(user,url,"en",UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);}assertThat(repository.used(user,java.time.YearMonth.now(java.time.ZoneOffset.UTC))).isZero();}
    @Test void concurrentAdmissionsHaveOneWinner(){var tasks=java.util.stream.IntStream.range(0,2).mapToObj(i->java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return admission.submit(user,url,"en",UUID.randomUUID().toString()).id();}catch(IllegalStateException expected){return null;}})).toList();assertThat(tasks.stream().map(java.util.concurrent.CompletableFuture::join).filter(Objects::nonNull)).hasSize(1);assertThat(repository.used(user,java.time.YearMonth.now(java.time.ZoneOffset.UTC))).isEqualTo(1);}
    @Test void bothRolesHaveTenAdmissions(){for(String role:List.of("USER","PREMIUM")){jdbc.sql("UPDATE app_users SET role=:role WHERE id=:id").param("role",role).param("id",user).update();for(int i=0;i<10;i++){var job=admission.submit(user,url,"en",UUID.randomUUID().toString());jdbc.sql("UPDATE member_analysis_jobs SET state='READY' WHERE id=:id").param("id",job.id()).update();jdbc.sql("DELETE FROM member_analysis_active WHERE job_id=:id").param("id",job.id()).update();}assertThatThrownBy(()->admission.submit(user,url,"en",UUID.randomUUID().toString())).isInstanceOf(IllegalStateException.class).hasMessage("MONTHLY_ALLOWANCE_REACHED");user=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Member",null,false,null),false).id();}}
    @Test void technicalFailureRefundsAllowanceOnlyOnceAndDoesNotEraseProviderBudget(){var job=admission.submit(user,url,"en",UUID.randomUUID().toString());long before=jdbc.sql("SELECT COUNT(*) FROM ai_usage_events").query(Long.class).single();repository.failQueued(job.id(),user);repository.failQueued(job.id(),user);assertThat(repository.used(user,java.time.YearMonth.now(java.time.ZoneOffset.UTC))).isZero();assertThat(jdbc.sql("SELECT COUNT(*) FROM ai_usage_events").query(Long.class).single()).isEqualTo(before);assertThat(admission.submit(user,url,"en",UUID.randomUUID().toString()).state()).isEqualTo("QUEUED");}
}
