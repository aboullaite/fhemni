package dev.maboullaite.fhemni.web;

final class ElectionCoalitionRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    ElectionCoalitionRateLimitException(long retryAfterSeconds) {
        super("Too many coalition evaluations. Please retry later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
