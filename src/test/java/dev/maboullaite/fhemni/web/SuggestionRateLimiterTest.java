package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SuggestionRateLimiterTest {

    @Test
    void limitsEachRemoteAddressIndependently() {
        SuggestionRateLimiter limiter = new SuggestionRateLimiter(2, Duration.ofHours(1), 10);

        limiter.check("192.0.2.10");
        limiter.check("192.0.2.10");
        limiter.check("192.0.2.11");

        assertThatThrownBy(() -> limiter.check("192.0.2.10"))
                .isInstanceOf(SuggestionRateLimitException.class)
                .hasMessageContaining("Too many");
    }
}
