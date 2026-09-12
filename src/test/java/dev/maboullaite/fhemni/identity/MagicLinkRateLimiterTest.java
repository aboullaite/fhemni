package dev.maboullaite.fhemni.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MagicLinkRateLimiterTest {

    @Test
    void limitsEachClientWithoutBlockingOtherClients() {
        MagicLinkRateLimiter limiter = new MagicLinkRateLimiter(2, Duration.ofHours(1), 10);

        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.1")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.1")).isFalse();
        assertThat(limiter.tryAcquire("192.0.2.2")).isTrue();
    }
}
