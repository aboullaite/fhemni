package dev.maboullaite.fhemni.gemini;

import java.time.Duration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import dev.maboullaite.fhemni.cost.AiUsage;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SpringAiFactCheckClient {

    private final Client googleClient;
    private final ChatClient chatClient;
    private final String model;
    private final boolean configured;
    private final int maxOutputTokens;

    @Autowired
    SpringAiFactCheckClient(
            @Value("${fhemni.gemini.api-key:}") String apiKey,
            @Value("${fhemni.gemini.fact-check-model:gemini-3.8-flash}") String model,
            @Value("${fhemni.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${fhemni.gemini.api-version:v1beta}") String apiVersion,
            @Value("${fhemni.gemini.fact-check-read-timeout:PT90S}") Duration readTimeout,
            @Value("${fhemni.gemini.fact-check-max-output-tokens:8192}") int maxOutputTokens) {
        String normalizedKey = apiKey == null ? "" : apiKey.strip();
        validateTimeout(readTimeout);
        if (maxOutputTokens < 256) {
            throw new IllegalArgumentException("Gemini fact-check output limit must be at least 256 tokens");
        }
        this.configured = !normalizedKey.isBlank();
        this.maxOutputTokens = maxOutputTokens;

        if (!configured) {
            this.googleClient = null;
            this.chatClient = null;
            this.model = null;
            return;
        }

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Gemini fact-check model must not be blank");
        }
        this.model = model.strip();
        HttpOptions.Builder http = HttpOptions.builder()
                .apiVersion(apiVersion)
                .timeout(Math.toIntExact(readTimeout.toMillis()));
        if (baseUrl != null && !baseUrl.isBlank()) {
            http.baseUrl(baseUrl.strip());
        }
        this.googleClient = Client.builder()
                .apiKey(normalizedKey)
                .httpOptions(http.build())
                .build();

        var chatModel = GoogleGenAiChatModel.builder()
                .genAiClient(googleClient)
                .options(GoogleGenAiChatOptions.builder().model(this.model).build())
                .build();
        this.chatClient = ChatClient.create(chatModel);
    }

    SpringAiFactCheckClient(
            String apiKey,
            String model,
            String baseUrl,
            String apiVersion,
            Duration readTimeout) {
        this(apiKey, model, baseUrl, apiVersion, readTimeout, 8192);
    }

    FactCheckResult check(String systemInstruction, String prompt) {
        if (!configured || chatClient == null) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        try {
            var result = chatClient.prompt()
                    .system(systemInstruction)
                    .user(prompt)
                    .options(GoogleGenAiChatOptions.builder()
                            .model(model)
                            .maxOutputTokens(maxOutputTokens)
                            .includeExtendedUsageMetadata(true)
                            .googleSearchRetrieval(true)
                            .includeServerSideToolInvocations(true))
                    .call()
                    .responseEntity(FactCheckResponse.class, spec -> spec
                            .useProviderStructuredOutput()
                            .validateSchema());
            FactCheckResponse response = result.entity();
            if (response == null) {
                throw new GeminiApiException("Gemini returned an empty fact-check response");
            }
            Usage usage = result.response().getMetadata().getUsage();
            return new FactCheckResult(response, usage == null
                    ? AiUsage.empty()
                    : new AiUsage(
                            usage.getPromptTokens(),
                            usage.getCompletionTokens(),
                            integer(usage.getCacheReadInputTokens()),
                            null,
                            null,
                            null));
        } catch (GeminiApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeminiApiException("Spring AI fact-checking failed", exception);
        }
    }

    private Integer integer(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    record FactCheckResult(FactCheckResponse response, AiUsage usage) {
    }

    boolean configured() {
        return configured;
    }

    String model() {
        return model == null ? "" : model;
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Gemini read timeouts must be positive and fit in milliseconds");
        }
    }

    @PreDestroy
    void close() {
        if (googleClient != null) {
            googleClient.close();
        }
    }
}
