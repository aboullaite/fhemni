package dev.maboullaite.fhemni.following;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import dev.maboullaite.fhemni.identity.*;
import dev.maboullaite.fhemni.government.GovernmentViews.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties={"fhemni.gemini.api-key=","spring.datasource.url=jdbc:h2:mem:digest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","fhemni.digest.enabled=true","fhemni.digest.signing-key=test-only-secret-at-least-thirty-two-characters","fhemni.digest.base-url=https://example.org"})
class DigestIntegrationTest {
    @Autowired DigestService service;@Autowired DigestRepository repository;@Autowired UserAccountRepository users;@Autowired JdbcClient jdbc;
    @MockitoBean DigestEmailSender sender;@MockitoBean UpdateFeedService feed;
    UUID user;LocalDate week=LocalDate.of(2026,9,21);
    @BeforeEach void setup(){when(sender.configured()).thenReturn(true);user=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Test","test@example.org",true,null),false).id();when(feed.between(eq(user),any(),any())).thenReturn(List.of(event()));}
    @Test void consentIsSeparateAndVerificationRequired(){assertThat(service.preferences(user).enabled()).isFalse();service.update(user,true,"fr");assertThat(service.preferences(user).enabled()).isTrue();var unverified=users.recordLogin(new ExternalIdentityProfile("test",UUID.randomUUID().toString(),null,"Unknown",null,false,null),false).id();assertThatThrownBy(()->service.update(unverified,true,"en")).isInstanceOf(IllegalArgumentException.class);when(sender.configured()).thenReturn(false);assertThat(service.preferences(user).capable()).isFalse();}
    @Test void reservationsAreUniqueAndRevokedConsentPreventsSend(){service.update(user,true,"en");service.reserve(user,week);service.reserve(user,week);assertThat(repository.count(user,week)).isEqualTo(1);var claim=repository.claim(Instant.now()).orElseThrow();service.update(user,false,"en");service.deliver(claim);verify(sender,never()).send(any(),any(),any(),any());assertThat(repository.state(claim.id())).isEqualTo("CANCELLED");}
    @Test void emptyWeeksHaveNoOutboxAndSuccessfulDeliveryEscapesContent(){service.update(user,true,"en");when(feed.between(eq(user),any(),any())).thenReturn(List.of());service.reserve(user,week);assertThat(repository.count(user,week)).isZero();when(feed.between(eq(user),any(),any())).thenReturn(List.of(event()));service.reserve(user,week);var claim=repository.claim(Instant.now()).orElseThrow();service.deliver(claim);assertThat(repository.state(claim.id())).isEqualTo("SENT");verify(sender).send(eq("test@example.org"),any(),any(),argThat(html->html.contains("&lt;img")&&!html.contains("<img")));assertThat(repository.claim(Instant.now())).isEmpty();}
    @Test void ambiguousDeliveryNeverRetriesAndUnsubscribeIsScoped(){service.update(user,true,"ar");doThrow(new RuntimeException("timeout")).when(sender).send(any(),any(),any(),any());service.reserve(user,week);var claim=repository.claim(Instant.now()).orElseThrow();service.deliver(claim);assertThat(repository.state(claim.id())).isEqualTo("UNKNOWN");assertThat(repository.claim(Instant.now().plusSeconds(9999))).isEmpty();assertThat(feed.between(user,week.atStartOfDay().toInstant(ZoneOffset.UTC),week.plusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC))).hasSize(1);String token=service.unsubscribeToken(user,Instant.now().plusSeconds(60));assertThatThrownBy(()->service.unsubscribe(token+"x")).isInstanceOf(IllegalArgumentException.class);service.unsubscribe(token);assertThat(service.preferences(user).enabled()).isFalse();assertThatThrownBy(()->service.unsubscribe(service.unsubscribeToken(user,Instant.now().minusSeconds(1)))).isInstanceOf(IllegalArgumentException.class);}
    @Test void definiteRejectionIsNotReportedAsSent(){service.update(user,true,"fr");doThrow(new DigestEmailSender.Rejected()).when(sender).send(any(),any(),any(),any());service.reserve(user,week);var claim=repository.claim(Instant.now()).orElseThrow();service.deliver(claim);assertThat(repository.state(claim.id())).isEqualTo("FAILED");}
    @Test void twoWorkersCannotReserveOrClaimTheSameWeekTwice(){service.update(user,true,"en");var tasks=java.util.stream.IntStream.range(0,2).mapToObj(i->java.util.concurrent.CompletableFuture.runAsync(()->service.reserve(user,week))).toList();tasks.forEach(java.util.concurrent.CompletableFuture::join);assertThat(repository.count(user,week)).isEqualTo(1);var claims=java.util.stream.IntStream.range(0,2).mapToObj(i->java.util.concurrent.CompletableFuture.supplyAsync(()->repository.claim(Instant.now()))).toList();assertThat(claims.stream().map(java.util.concurrent.CompletableFuture::join).filter(Optional::isPresent)).hasSize(1);}
    @Test void lostSendingWorkerIsUnknownNotReclaimed(){service.update(user,true,"en");service.reserve(user,week);var claim=repository.claim(Instant.now()).orElseThrow();assertThat(repository.beginSend(claim)).isTrue();assertThat(repository.claim(Instant.now().plusSeconds(301))).isEmpty();assertThat(repository.state(claim.id())).isEqualTo("UNKNOWN");}
    @Test void productionAdapterIsAlwaysDisabled(){var disabled=new DisabledDigestEmailSender();assertThat(disabled.configured()).isFalse();assertThatThrownBy(()->disabled.send("nobody@example.org","subject","text","html")).isInstanceOf(DigestEmailSender.Rejected.class);}
    static PublishedUpdate event(){var text=new LocalText("<img> تحديث","<img> changement","<img> update");return new PublishedUpdate(UUID.randomUUID(),"COMMITMENT",UUID.randomUUID().toString(),UUID.randomUUID(),"hash",List.of("HEALTH"),List.of(),text,null,Instant.parse("2026-09-23T12:00:00Z"));}
}
