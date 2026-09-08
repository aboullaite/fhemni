package dev.maboullaite.fhemni.gemini;

import dev.maboullaite.fhemni.cost.AiUsage;

public class GeminiApiException extends RuntimeException {

    private final AiUsage usage;
    private final Integer upstreamStatus;

    public GeminiApiException(String message) {
        this(message, null, AiUsage.empty(), null);
    }

    public GeminiApiException(String message, Throwable cause) {
        this(message, cause, AiUsage.empty(), null);
    }

    public GeminiApiException(String message, Throwable cause, int upstreamStatus) {
        this(message, cause, AiUsage.empty(), upstreamStatus);
    }

    public GeminiApiException(String message, AiUsage usage) {
        this(message, null, usage, null);
    }

    public GeminiApiException(String message, Throwable cause, AiUsage usage) {
        this(message, cause, usage, null);
    }

    public GeminiApiException(String message, Throwable cause, AiUsage usage, Integer upstreamStatus) {
        super(message, cause);
        this.usage = usage == null ? AiUsage.empty() : usage;
        this.upstreamStatus = upstreamStatus;
    }

    public AiUsage usage() {
        return usage;
    }

    public Integer upstreamStatus() {
        return upstreamStatus;
    }
}
