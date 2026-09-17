package dev.maboullaite.fhemni.web;

final class PriorityShareRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    PriorityShareRateLimitException(long retryAfterSeconds) {
        super("Too many share links were created. Please retry later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

