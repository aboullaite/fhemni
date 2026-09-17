package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PriorityShareRateLimiterTest {

    @Test
    void evictsTheLeastRecentlyUsedClientInsteadOfBlockingNewClients() {
        var limiter = new PriorityShareRateLimiter(1, Duration.ofHours(1), 2);

        limiter.check("192.0.2.1");
        limiter.check("192.0.2.2");

        assertThatCode(() -> limiter.check("192.0.2.3")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("192.0.2.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("192.0.2.1"))
                .isInstanceOf(PriorityShareRateLimitException.class);
    }
}
