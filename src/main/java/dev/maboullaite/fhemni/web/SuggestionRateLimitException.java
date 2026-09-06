package dev.maboullaite.fhemni.web;

public class SuggestionRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    SuggestionRateLimitException(long retryAfterSeconds) {
        super("Too many video suggestions. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
