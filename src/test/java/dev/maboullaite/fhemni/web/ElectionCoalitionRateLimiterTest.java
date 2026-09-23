package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ElectionCoalitionRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-23T12:00:00Z"));

    @Test
    void limitsEachRemoteAddressIndependently() {
        var limiter = limiter(2, 10, 10);

        limiter.check("192.0.2.10");
        limiter.check("192.0.2.10");
        limiter.check("192.0.2.11");

        assertThatThrownBy(() -> limiter.check("192.0.2.10"))
                .isInstanceOf(ElectionCoalitionRateLimitException.class)
                .hasMessageContaining("Too many");
    }

    @Test
    void capsAggregateWorkAcrossUniqueAddresses() {
        var limiter = limiter(10, 3, 10);

        limiter.check("192.0.2.1");
        limiter.check("192.0.2.2");
        limiter.check("192.0.2.3");

        assertThatThrownBy(() -> limiter.check("192.0.2.4"))
                .isInstanceOf(ElectionCoalitionRateLimitException.class);
    }

    @Test
    void groupsIpv6AddressesBySixtyFourBitPrefix() {
        var limiter = limiter(2, 10, 10);

        limiter.check("2001:db8:1234:5678::1");
        limiter.check("2001:db8:1234:5678::2");
        limiter.check("2001:db8:1234:5679::1");

        assertThatThrownBy(() -> limiter.check("2001:db8:1234:5678::3"))
                .isInstanceOf(ElectionCoalitionRateLimitException.class);
    }

    @Test
    void trackerSaturationDoesNotPutNewClientsInOneSharedBucket() {
        var limiter = limiter(2, 5, 1);

        limiter.check("192.0.2.1");
        limiter.check("192.0.2.2");
        limiter.check("192.0.2.3");
        limiter.check("192.0.2.4");

        assertThatCode(() -> limiter.check("192.0.2.5")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("192.0.2.6"))
                .isInstanceOf(ElectionCoalitionRateLimitException.class);
    }

    @Test
    void expiresClientAndGlobalWindowsUsingInjectedClock() {
        var limiter = limiter(1, 1, 10);
        limiter.check("192.0.2.1");

        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.check("192.0.2.1")).doesNotThrowAnyException();
    }

    private ElectionCoalitionRateLimiter limiter(int perClient, int globally, int maxClients) {
        return new ElectionCoalitionRateLimiter(
                perClient, globally, Duration.ofMinutes(1), maxClients, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
