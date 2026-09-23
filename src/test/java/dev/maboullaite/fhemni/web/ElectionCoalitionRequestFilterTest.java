package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ElectionCoalitionRequestFilterTest {

    private static final String PATH = "/api/catalog/elections/2026/coalitions/evaluate";

    private final ElectionCoalitionRateLimiter rateLimiter = mock(ElectionCoalitionRateLimiter.class);
    private final ElectionCoalitionRequestFilter filter = new ElectionCoalitionRequestFilter(rateLimiter);

    @Test
    void rateLimitsBeforeReadingOrDeserializingTheBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public ServletInputStream getInputStream() {
                throw new AssertionError("The body must not be read after rate limiting");
            }
        };
        prepare(request, PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();
        doThrow(new ElectionCoalitionRateLimitException(23))
                .when(rateLimiter).check("192.0.2.10");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("23");
        assertThat(continued).isFalse();
    }

    @Test
    void rejectsAnOversizedBodyWithoutTrustingContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public String getHeader(String name) {
                return HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name) ? null : super.getHeader(name);
            }
        };
        prepare(request, PATH);
        request.setContent("x".repeat(ElectionCoalitionRequestFilter.MAX_REQUEST_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("16 KB or smaller");
        assertThat(continued).isFalse();
        verify(rateLimiter).check("192.0.2.10");
    }

    @Test
    void replaysAnAcceptedBodyToSpring() throws Exception {
        byte[] body = "{\"language\":\"ar\",\"partyCodes\":[\"RNI\",\"PAM\"]}"
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        prepare(request, PATH);
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> replayed = new AtomicReference<>();

        filter.doFilter(request, response, (wrapped, ignoredResponse) ->
                replayed.set(wrapped.getInputStream().readAllBytes()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(replayed.get()).isEqualTo(body);
    }

    @Test
    void matrixParametersCannotBypassTheGuard() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        prepare(request, PATH + ";pad=x");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new ElectionCoalitionRateLimitException(7))
                .when(rateLimiter).check("192.0.2.10");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(response.getStatus()).isEqualTo(429);
        verify(rateLimiter).check("192.0.2.10");
    }

    private static void prepare(MockHttpServletRequest request, String path) {
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setServletPath(path);
        request.setRemoteAddr("192.0.2.10");
    }
}
