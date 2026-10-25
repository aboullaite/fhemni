package dev.maboullaite.fhemni.following;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.maboullaite.fhemni.government.GovernmentViews.LocalText;
import dev.maboullaite.fhemni.government.GovernmentViews.PublishedUpdate;
import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:digest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "fhemni.digest.enabled=true",
        "fhemni.digest.signing-key=test-only-secret-at-least-thirty-two-characters",
        "fhemni.digest.base-url=https://example.org"
})
class DigestIntegrationTest {
    @Autowired
    DigestService service;

    @Autowired
    DigestRepository repository;

    @Autowired
    UserAccountRepository users;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    DigestEmailSender sender;

    @MockitoBean
    UpdateFeedService feed;

    UUID user;
    final LocalDate week = LocalDate.of(2026, 9, 21);

    @BeforeEach
    void setup() {
        when(sender.configured()).thenReturn(true);
        var profile = new ExternalIdentityProfile(
                "test", UUID.randomUUID().toString(), null, "Test", "test@example.org", true, null);
        user = users.recordLogin(profile, false).id();
        when(feed.between(eq(user), any(), any())).thenReturn(List.of(event()));
    }

    @Test
    void consentIsSeparateAndVerificationRequired() {
        assertThat(service.preferences(user).enabled()).isFalse();
        service.update(user, true, "fr");
        assertThat(service.preferences(user).enabled()).isTrue();
        var profile = new ExternalIdentityProfile(
                "test", UUID.randomUUID().toString(), null, "Unknown", null, false, null);
        var unverified = users.recordLogin(profile, false).id();
        assertThatThrownBy(() -> service.update(unverified, true, "en"))
                .isInstanceOf(IllegalArgumentException.class);
        when(sender.configured()).thenReturn(false);
        assertThat(service.preferences(user).capable()).isFalse();
    }

    @Test
    void reservationsAreUniqueAndRevokedConsentPreventsSend() {
        service.update(user, true, "en");
        service.reserve(user, week);
        service.reserve(user, week);
        assertThat(repository.count(user, week)).isEqualTo(1);
        var claim = repository.claim(Instant.now()).orElseThrow();

        service.update(user, false, "en");
        service.deliver(claim);

        verify(sender, never()).send(any(), any(), any(), any());
        assertThat(repository.state(claim.id())).isEqualTo("CANCELLED");
    }

    @Test
    void emptyWeeksHaveNoOutboxAndSuccessfulDeliveryEscapesContent() {
        service.update(user, true, "en");
        when(feed.between(eq(user), any(), any())).thenReturn(List.of());
        service.reserve(user, week);
        assertThat(repository.count(user, week)).isZero();
        when(feed.between(eq(user), any(), any())).thenReturn(List.of(event()));
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();

        service.deliver(claim);

        assertThat(repository.state(claim.id())).isEqualTo("SENT");
        verify(sender).send(
                eq("test@example.org"), any(), any(),
                argThat(html -> html.contains("&lt;img") && !html.contains("<img")));
        assertThat(repository.claim(Instant.now())).isEmpty();
    }

    @Test
    void ambiguousDeliveryNeverRetriesAndUnsubscribeIsScoped() {
        service.update(user, true, "ar");
        doThrow(new RuntimeException("timeout")).when(sender).send(any(), any(), any(), any());
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();

        service.deliver(claim);

        assertThat(repository.state(claim.id())).isEqualTo("UNKNOWN");
        assertThat(repository.claim(Instant.now().plusSeconds(9999))).isEmpty();
        assertThat(feed.between(
                user,
                week.atStartOfDay().toInstant(ZoneOffset.UTC),
                week.plusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC))).hasSize(1);
        String token = service.unsubscribeToken(user, Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> service.unsubscribe(token + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        service.unsubscribe(token);
        assertThat(service.preferences(user).enabled()).isFalse();
        assertThatThrownBy(() -> service.unsubscribe(
                service.unsubscribeToken(user, Instant.now().minusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definiteRejectionIsNotReportedAsSent() {
        service.update(user, true, "fr");
        doThrow(new DigestEmailSender.Rejected()).when(sender).send(any(), any(), any(), any());
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();

        service.deliver(claim);

        assertThat(repository.state(claim.id())).isEqualTo("FAILED");
    }

    @Test
    void twoWorkersCannotReserveOrClaimTheSameWeekTwice() {
        service.update(user, true, "en");
        var tasks = IntStream.range(0, 2)
                .mapToObj(i -> CompletableFuture.runAsync(() -> service.reserve(user, week)))
                .toList();
        tasks.forEach(CompletableFuture::join);
        assertThat(repository.count(user, week)).isEqualTo(1);
        var claims = IntStream.range(0, 2)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> repository.claim(Instant.now())))
                .toList();
        assertThat(claims.stream().map(CompletableFuture::join).filter(Optional::isPresent)).hasSize(1);
    }

    @Test
    void lostSendingWorkerIsUnknownNotReclaimed() {
        service.update(user, true, "en");
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();

        assertThat(repository.beginSend(claim)).isTrue();

        assertThat(repository.claim(Instant.now().plusSeconds(301))).isEmpty();
        assertThat(repository.state(claim.id())).isEqualTo("UNKNOWN");
    }

    @Test
    void transportRunsWithoutAnOpenDatabaseTransaction() {
        service.update(user, true, "en");
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("email transport must not hold a database transaction")
                    .isFalse();
            assertThat(repository.state(claim.id())).isEqualTo("SENDING");
            return null;
        }).when(sender).send(any(), any(), any(), any());

        service.deliver(claim);

        assertThat(repository.state(claim.id())).isEqualTo("SENT");
    }

    @Test
    void transportSuspendsAnAmbientDatabaseTransaction() {
        service.update(user, true, "en");
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("email transport must also suspend a caller's transaction")
                    .isFalse();
            return null;
        }).when(sender).send(any(), any(), any(), any());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.deliver(claim);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });

        assertThat(repository.state(claim.id())).isEqualTo("SENT");
    }

    @Test
    void resubscribingDoesNotAuthorizeAnEarlierConsentClaim() {
        service.update(user, true, "en");
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();
        service.update(user, false, "en");
        service.update(user, true, "en");

        service.deliver(claim);

        assertThat(repository.state(claim.id())).isEqualTo("CANCELLED");
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void aDuplicateWorkerCannotCancelAnInFlightSendAfterUnsubscribe() {
        service.update(user, true, "en");
        String token = service.unsubscribeToken(user, Instant.now().plusSeconds(60));
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();
        doAnswer(invocation -> {
            service.unsubscribe(token);

            service.deliver(claim);

            assertThat(repository.state(claim.id()))
                    .as("revocation cannot turn an already in-flight send into a cancellation")
                    .isEqualTo("SENDING");
            return null;
        }).when(sender).send(any(), any(), any(), any());

        service.deliver(claim);

        assertThat(service.preferences(user).enabled()).isFalse();
        assertThat(repository.state(claim.id())).isEqualTo("SENT");
        verify(sender, times(1)).send(any(), any(), any(), any());
    }

    @Test
    void aReplacedLeaseCannotSendOrCancelTheCurrentWorkersClaim() {
        service.update(user, true, "en");
        service.reserve(user, week);
        var staleClaim = repository.claim(Instant.now()).orElseThrow();
        jdbc.sql("UPDATE accountability_digest_outbox SET lease_until=:expired WHERE id=:id")
                .param("expired", Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC))
                .param("id", staleClaim.id())
                .update();
        var currentClaim = repository.claim(Instant.now()).orElseThrow();

        service.deliver(staleClaim);

        assertThat(repository.state(currentClaim.id())).isEqualTo("LEASED");
        verify(sender, never()).send(any(), any(), any(), any());

        service.deliver(currentClaim);
        service.deliver(currentClaim);

        assertThat(repository.state(currentClaim.id())).isEqualTo("SENT");
        verify(sender, times(1)).send(any(), any(), any(), any());
    }

    @Test
    void unsubscribeCompletesWhileTransportIsStillSending() throws Exception {
        service.update(user, true, "en");
        String token = service.unsubscribeToken(user, Instant.now().plusSeconds(60));
        service.reserve(user, week);
        var claim = repository.claim(Instant.now()).orElseThrow();
        var transportStarted = new CountDownLatch(1);
        var releaseTransport = new CountDownLatch(1);
        doAnswer(invocation -> {
            transportStarted.countDown();
            if (!releaseTransport.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release email transport");
            }
            return null;
        }).when(sender).send(any(), any(), any(), any());

        var delivery = CompletableFuture.runAsync(() -> service.deliver(claim));
        CompletableFuture<Void> revocation = null;
        try {
            assertThat(transportStarted.await(5, TimeUnit.SECONDS)).isTrue();
            revocation = CompletableFuture.runAsync(() -> service.unsubscribe(token));
            assertThat(revocation).succeedsWithin(Duration.ofSeconds(1));
            assertThat(service.preferences(user).enabled()).isFalse();
            assertThat(repository.state(claim.id())).isEqualTo("SENDING");
        } finally {
            releaseTransport.countDown();
            delivery.get(5, TimeUnit.SECONDS);
            if (revocation != null) {
                revocation.get(5, TimeUnit.SECONDS);
            }
        }

        assertThat(repository.state(claim.id())).isEqualTo("SENT");
    }

    @Test
    void productionAdapterIsAlwaysDisabled() {
        var disabled = new DisabledDigestEmailSender();
        assertThat(disabled.configured()).isFalse();
        assertThatThrownBy(() -> disabled.send("nobody@example.org", "subject", "text", "html"))
                .isInstanceOf(DigestEmailSender.Rejected.class);
    }

    static PublishedUpdate event() {
        var text = new LocalText("<img> تحديث", "<img> changement", "<img> update");
        return new PublishedUpdate(
                UUID.randomUUID(), "COMMITMENT", UUID.randomUUID().toString(), UUID.randomUUID(),
                "hash", List.of("HEALTH"), List.of(), text, null, Instant.parse("2026-09-23T12:00:00Z"));
    }
}
