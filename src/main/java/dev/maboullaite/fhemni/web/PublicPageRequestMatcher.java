package dev.maboullaite.fhemni.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Lets unknown document navigations reach MVC's 404 handling without making
 * APIs, administration, authentication, or static-resource paths public.
 */
public final class PublicPageRequestMatcher implements RequestMatcher {

    private static final List<String> RESERVED_ROOTS = List.of(
            "/api", "/admin", "/auth", "/oauth2", "/login", "/error",
            "/css", "/js", "/assets", "/webjars", "/actuator");

    @Override
    public boolean matches(HttpServletRequest request) {
        String method = request.getMethod();
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
            return false;
        }

        String path = withoutMatrixParameters(request.getServletPath());
        if (path == null || isReserved(path) || looksLikeAFile(path)) {
            return false;
        }

        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.TEXT_HTML));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isReserved(String path) {
        if (path.equals("/favicon.ico") || path.equals("/healthz")) {
            return true;
        }
        return RESERVED_ROOTS.stream()
                .anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    private boolean looksLikeAFile(String path) {
        int finalSlash = path.lastIndexOf('/');
        String finalSegment = path.substring(finalSlash + 1).toLowerCase();
        return finalSegment.contains(".") && !finalSegment.endsWith(".html");
    }

    private String withoutMatrixParameters(String path) {
        if (path == null || path.indexOf(';') < 0) {
            return path;
        }
        return path.replaceAll(";[^/]*", "");
    }
}
