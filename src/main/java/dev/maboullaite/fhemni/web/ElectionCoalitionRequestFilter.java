package dev.maboullaite.fhemni.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ElectionCoalitionRequestFilter extends OncePerRequestFilter {

    static final int MAX_REQUEST_BYTES = 16_384;

    private final ElectionCoalitionRateLimiter rateLimiter;

    ElectionCoalitionRequestFilter(ElectionCoalitionRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !isCoalitionEvaluationPath(requestPath(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            rateLimiter.check(request.getRemoteAddr());
        } catch (ElectionCoalitionRateLimitException exception) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
            writeProblem(response, HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
            return;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            contentLength = parseContentLength(request.getHeader(HttpHeaders.CONTENT_LENGTH));
        }
        if (contentLength > MAX_REQUEST_BYTES) {
            writeTooLarge(response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            writeTooLarge(response);
            return;
        }
        filterChain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private static boolean isCoalitionEvaluationPath(String path) {
        String[] segments = path.split("/", -1);
        return segments.length == 7
                && segments[0].isEmpty()
                && "api".equals(segments[1])
                && "catalog".equals(segments[2])
                && "elections".equals(segments[3])
                && !segments[4].isEmpty()
                && "coalitions".equals(segments[5])
                && "evaluate".equals(segments[6]);
    }

    private static String requestPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            String requestUri = request.getRequestURI();
            String contextPath = request.getContextPath();
            path = requestUri.substring(Math.min(contextPath.length(), requestUri.length()));
        }
        StringBuilder clean = new StringBuilder(path.length());
        boolean matrixParameter = false;
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == ';') {
                matrixParameter = true;
            } else if (character == '/') {
                matrixParameter = false;
                clean.append(character);
            } else if (!matrixParameter) {
                clean.append(character);
            }
        }
        return clean.toString();
    }

    private static long parseContentLength(String value) {
        if (value == null) return -1;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    private static void writeTooLarge(HttpServletResponse response) throws IOException {
        writeProblem(
                response,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "The coalition request must be 16 KB or smaller.");
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }

    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not supported");
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
