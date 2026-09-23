package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ElectionCoalitionRateLimiterTest {

    @Test
    void limitsEachRemoteAddressIndependently() {
        var limiter = new ElectionCoalitionRateLimiter(2, Duration.ofMinutes(1), 10);

        limiter.check("192.0.2.10");
        limiter.check("192.0.2.10");
        limiter.check("192.0.2.11");

        assertThatThrownBy(() -> limiter.check("192.0.2.10"))
                .isInstanceOf(ElectionCoalitionRateLimitException.class)
                .hasMessageContaining("Too many");
    }
}
