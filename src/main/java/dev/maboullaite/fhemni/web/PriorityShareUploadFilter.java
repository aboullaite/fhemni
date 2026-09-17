package dev.maboullaite.fhemni.web;

import java.io.IOException;

import dev.maboullaite.fhemni.civic.CivicPriorityShareService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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
class PriorityShareUploadFilter extends OncePerRequestFilter {

    private static final String UPLOAD_PATH = "/api/catalog/questionnaires/current/shares";
    private static final long MAX_REQUEST_BYTES = CivicPriorityShareService.MAX_UPLOAD_BYTES;

    private final PriorityShareRateLimiter rateLimiter;

    PriorityShareUploadFilter(PriorityShareRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !UPLOAD_PATH.equals(uploadPath(request));
    }

    private static String uploadPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            String requestUri = request.getRequestURI();
            String contextPath = request.getContextPath();
            path = requestUri.substring(Math.min(contextPath.length(), requestUri.length()));
        }
        return stripMatrixParameters(path);
    }

    private static String stripMatrixParameters(String path) {
        StringBuilder clean = new StringBuilder(path.length());
        boolean parameter = false;
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == ';') {
                parameter = true;
            } else if (character == '/') {
                parameter = false;
                clean.append(character);
            } else if (!parameter) {
                clean.append(character);
            }
        }
        return clean.toString();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            rateLimiter.check(request.getRemoteAddr());
        } catch (PriorityShareRateLimitException exception) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
            writeProblem(response, HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
            return;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            contentLength = parseContentLength(request.getHeader(HttpHeaders.CONTENT_LENGTH));
        }
        if (contentLength > MAX_REQUEST_BYTES) {
            writeProblem(response, HttpStatus.PAYLOAD_TOO_LARGE, "The share card must be 2 MB or smaller.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static long parseContentLength(String value) {
        if (value == null) return -1;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }
}
