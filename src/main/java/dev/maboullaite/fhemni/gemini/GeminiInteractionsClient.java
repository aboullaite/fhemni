package dev.maboullaite.fhemni.gemini;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionStatus;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.interactions.URLCitation;
import com.google.genai.gaos.models.interactions.Usage;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;
import com.google.genai.gaos.utils.Options;
import com.google.genai.gaos.utils.RetryConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.UploadFileConfig;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.FactCheckEvidencePolicy;
import dev.maboullaite.fhemni.model.SourceReference;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class GeminiInteractionsClient {

    private static final Options NO_RETRIES = Options.builder()
            .retryConfig(RetryConfig.noRetries())
            .build();

    private final Client analysisClient;
    private final Client questionClient;
    private final String apiKey;
    private final String model;

    GeminiInteractionsClient(
            @Value("${fhemni.gemini.api-key:}") String apiKey,
            @Value("${fhemni.gemini.model:gemini-3.8-flash}") String model,
            @Value("${fhemni.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${fhemni.gemini.api-version:v1beta}") String apiVersion,
            @Value("${fhemni.gemini.analysis-read-timeout:PT12M}") Duration analysisReadTimeout,
            @Value("${fhemni.gemini.question-read-timeout:PT30S}") Duration questionReadTimeout) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null ? "" : model.strip();
        validateTimeout(analysisReadTimeout);
        validateTimeout(questionReadTimeout);

        if (configured()) {
            if (this.model.isBlank()) {
                throw new IllegalArgumentException("Gemini model must not be blank");
            }
            this.analysisClient = googleClient(baseUrl, apiVersion, analysisReadTimeout);
            this.questionClient = googleClient(baseUrl, apiVersion, questionReadTimeout);
        } else {
            this.analysisClient = null;
            this.questionClient = null;
        }
    }

    boolean configured() {
        return !apiKey.isBlank();
    }

    String model() {
        return model;
    }

    InteractionResponse create(CreateModelInteraction request) {
        return create(analysisClient, request, null);
    }

    InteractionResponse createQuestion(CreateModelInteraction request) {
        return create(questionClient, request, NO_RETRIES);
    }

    private InteractionResponse create(Client client, CreateModelInteraction request, Options options) {
        if (!configured() || client == null) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        try {
            Interaction interaction = client.interactions
                    .create(null, CreateInteractionRequestBody.of(request), options)
                    .interaction()
                    .orElseThrow(() -> new GeminiApiException("Gemini returned an empty response"));
            InteractionStatus status = interaction.status().orElse(null);
            if (!InteractionStatus.COMPLETED.equals(status)) {
                String value = status == null ? "unknown" : status.value();
                throw new GeminiApiException(
                        "Gemini interaction did not complete (status: " + value + incompleteDetails(interaction) + ")",
                        extractUsage(interaction));
            }
            String output = interaction.outputText()
                    .filter(text -> !text.isBlank())
                    .orElseGet(() -> extractOutputText(interaction));
            if (output.isBlank()) {
                throw new GeminiApiException("Gemini completed without a text result");
            }
            return new InteractionResponse(
                    interaction.id().orElse(""),
                    output,
                    extractCitations(interaction),
                    extractUsage(interaction));
        } catch (ApiException exception) {
            throw new GeminiApiException(
                    "Gemini request failed (HTTP " + exception.code() + ")",
                    exception,
                    exception.code());
        } catch (GeminiApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeminiApiException("Could not reach the Gemini API", exception);
        }
    }

    UploadedFile uploadPdf(InputStream input, long size, String displayName) {
        if (!configured() || analysisClient == null) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        try {
            var file = analysisClient.files.upload(input, size, UploadFileConfig.builder()
                    .mimeType("application/pdf")
                    .displayName(displayName)
                    .build());
            return new UploadedFile(
                    file.name().orElseThrow(() -> new GeminiApiException("Gemini uploaded a PDF without a file name")),
                    file.uri().orElseThrow(() -> new GeminiApiException("Gemini uploaded a PDF without a file URI")));
        } catch (ApiException exception) {
            throw new GeminiApiException(
                    "Gemini PDF upload failed (HTTP " + exception.code() + ")",
                    exception,
                    exception.code());
        } catch (GeminiApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeminiApiException("Could not upload the PDF to Gemini", exception);
        }
    }

    void deleteFile(String name) {
        if (analysisClient == null || name == null || name.isBlank()) {
            return;
        }
        analysisClient.files.delete(name, null);
    }

    private Client googleClient(String baseUrl, String apiVersion, Duration timeout) {
        HttpOptions.Builder http = HttpOptions.builder()
                .apiVersion(apiVersion)
                .timeout(Math.toIntExact(timeout.toMillis()));
        if (baseUrl != null && !baseUrl.isBlank()) {
            http.baseUrl(baseUrl.strip());
        }
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(http.build())
                .build();
    }

    private List<SourceReference> extractCitations(Interaction interaction) {
        Map<String, SourceReference> citations = new LinkedHashMap<>();
        interaction.steps().orElse(List.of()).stream()
                .filter(ModelOutputStep.class::isInstance)
                .map(ModelOutputStep.class::cast)
                .flatMap(step -> step.content().orElse(List.of()).stream())
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .flatMap(content -> content.annotations().orElse(List.of()).stream())
                .filter(URLCitation.class::isInstance)
                .map(URLCitation.class::cast)
                .forEach(citation -> citation.url().filter(FactCheckEvidencePolicy::isSafeWebUrl).ifPresent(url ->
                        citations.putIfAbsent(url, new SourceReference(citation.title().orElse("Source"), url, ""))));
        return new ArrayList<>(citations.values());
    }

    private AiUsage extractUsage(Interaction interaction) {
        Usage usage = interaction.usage().orElse(null);
        if (usage == null) {
            return AiUsage.empty();
        }
        int groundingQueries = usage.groundingToolCount().orElse(List.of()).stream()
                .mapToInt(item -> item.count().orElse(0))
                .sum();
        return new AiUsage(
                usage.totalInputTokens().orElse(null),
                usage.totalOutputTokens().orElse(null),
                usage.totalCachedTokens().orElse(null),
                usage.totalThoughtTokens().orElse(null),
                usage.totalToolUseTokens().orElse(null),
                groundingQueries == 0 ? null : groundingQueries);
    }

    private String incompleteDetails(Interaction interaction) {
        List<String> details = new ArrayList<>();
        interaction.errors().orElse(List.of()).forEach(error -> {
            String code = error.code().orElse("").strip();
            String message = error.message().orElse("").strip();
            if (!code.isBlank() || !message.isBlank()) {
                details.add((code + (code.isBlank() || message.isBlank() ? "" : ": ") + message).strip());
            }
        });
        interaction.usage().ifPresent(usage -> {
            usage.totalOutputTokens().ifPresent(value -> details.add("output_tokens=" + value));
            usage.totalThoughtTokens().ifPresent(value -> details.add("thought_tokens=" + value));
        });
        return details.isEmpty() ? "" : "; " + String.join("; ", details);
    }

    private String extractOutputText(Interaction interaction) {
        StringBuilder output = new StringBuilder();
        interaction.steps().orElse(List.of()).stream()
                .filter(ModelOutputStep.class::isInstance)
                .map(ModelOutputStep.class::cast)
                .flatMap(step -> step.content().orElse(List.of()).stream())
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .flatMap(content -> content.text().stream())
                .forEach(text -> {
                    if (!output.isEmpty()) {
                        output.append('\n');
                    }
                    output.append(text);
                });
        return output.toString();
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Gemini read timeouts must be positive and fit in milliseconds");
        }
    }

    @PreDestroy
    void close() {
        if (analysisClient != null) {
            analysisClient.close();
        }
        if (questionClient != null) {
            questionClient.close();
        }
    }

    record UploadedFile(String name, String uri) {
    }
}
