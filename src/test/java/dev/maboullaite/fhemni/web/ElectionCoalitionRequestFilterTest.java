package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ElectionCoalitionRequestFilterTest {

    private static final String PATH = "/api/catalog/elections/2026/coalitions/evaluate";

    private final ElectionCoalitionRequestFilter filter = new ElectionCoalitionRequestFilter();

    @Test
    void letsBoundedRequestsContinueToSecurityAndMvc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        prepare(request, PATH);
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(continued).isTrue();
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
        request.setContent("x".repeat(ElectionCoalitionRequestFilter.MAX_REQUEST_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void encodedFallbackPathCannotBypassTheGuard() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public String getServletPath() {
                return "";
            }
        };
        prepare(request, "/api/catalog/elections/2026/coalitions/%65valuate");
        request.setContent("x".repeat(ElectionCoalitionRequestFilter.MAX_REQUEST_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(response.getStatus()).isEqualTo(413);
    }

    private static void prepare(MockHttpServletRequest request, String path) {
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setServletPath(path);
        request.setRemoteAddr("192.0.2.10");
    }
}
