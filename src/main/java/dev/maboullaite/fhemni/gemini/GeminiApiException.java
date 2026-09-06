package dev.maboullaite.fhemni.gemini;

import dev.maboullaite.fhemni.cost.AiUsage;

public class GeminiApiException extends RuntimeException {

    private final AiUsage usage;

    public GeminiApiException(String message) {
        this(message, null, AiUsage.empty());
    }

    public GeminiApiException(String message, Throwable cause) {
        this(message, cause, AiUsage.empty());
    }

    public GeminiApiException(String message, AiUsage usage) {
        this(message, null, usage);
    }

    private GeminiApiException(String message, Throwable cause, AiUsage usage) {
        super(message, cause);
        this.usage = usage == null ? AiUsage.empty() : usage;
    }

    public AiUsage usage() {
        return usage;
    }
}
