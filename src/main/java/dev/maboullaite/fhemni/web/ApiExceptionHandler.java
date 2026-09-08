package dev.maboullaite.fhemni.web;

import java.util.NoSuchElementException;

import dev.maboullaite.fhemni.cost.AiBudgetExceededException;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(GeminiApiException.class)
    ProblemDetail upstream(GeminiApiException exception) {
        ProblemDetail detail = problem(
                HttpStatus.BAD_GATEWAY,
                "The AI service could not complete this request. Please retry later.");
        detail.setProperty("code", geminiErrorCode(exception));
        return detail;
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    ProblemDetail unauthorized(AuthenticationCredentialsNotFoundException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(AiBudgetExceededException.class)
    ResponseEntity<ProblemDetail> aiBudgetExceeded(AiBudgetExceededException exception) {
        ProblemDetail detail = problem(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        detail.setProperty("code", exception.code());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> uploadTooLarge(MaxUploadSizeExceededException exception) {
        ProblemDetail detail = problem(HttpStatus.PAYLOAD_TOO_LARGE, "The programme PDF must be 25 MB or smaller.");
        detail.setProperty("code", "PROGRAMME_PDF_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(detail);
    }

    @ExceptionHandler(SuggestionRateLimitException.class)
    ResponseEntity<ProblemDetail> tooManySuggestions(SuggestionRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }

    private String geminiErrorCode(GeminiApiException exception) {
        Integer status = exception.upstreamStatus();
        if (status == null) {
            return "AI_RESULT_INVALID";
        }
        return switch (status) {
            case 400, 404, 422 -> "AI_SOURCE_OR_REQUEST_INVALID";
            case 401, 403 -> "AI_CREDENTIAL_REJECTED";
            case 429 -> "AI_RATE_LIMITED";
            default -> "AI_TEMPORARY_FAILURE";
        };
    }
}
