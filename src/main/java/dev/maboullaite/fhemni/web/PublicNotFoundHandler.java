package dev.maboullaite.fhemni.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class PublicNotFoundHandler {

    private static final MediaType HTML_UTF_8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    private final PublicPageRequestMatcher publicPages = new PublicPageRequestMatcher();
    private final Resource notFoundPage = new ClassPathResource("static/404.html");

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Resource> noResourceFound(HttpServletRequest request) {
        if (!publicPages.matches(request)) {
            return ResponseEntity.notFound().build();
        }
        return notFoundPage();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Resource> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        if (exception.getStatusCode().value() != HttpStatus.NOT_FOUND.value()
                || !publicPages.matches(request)) {
            throw exception;
        }
        return notFoundPage();
    }

    private ResponseEntity<Resource> notFoundPage() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(HTML_UTF_8)
                .cacheControl(CacheControl.noStore())
                .body(notFoundPage);
    }
}
