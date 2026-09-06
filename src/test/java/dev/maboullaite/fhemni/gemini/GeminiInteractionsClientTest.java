package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class GeminiInteractionsClientTest {

    @Test
    void rejectsIncompleteInteractionsInsteadOfReturningTruncatedText() throws IOException {
        byte[] response = """
                {
                  "id":"test",
                  "status":"incomplete",
                  "steps":[{"type":"model_output","content":[{"type":"text","text":"partial answer"}]}],
                  "usage":{"total_output_tokens":1024,"total_thought_tokens":220}
                }
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            GeminiInteractionsClient client = new GeminiInteractionsClient(
                    "test-key",
                    "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "v1beta",
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2));
            var request = CreateModelInteraction.builder()
                    .model("test-model")
                    .input(InteractionsInput.of("test"))
                    .build();

            assertThatThrownBy(() -> client.createQuestion(request))
                    .isInstanceOf(GeminiApiException.class)
                    .hasMessageContaining("status: incomplete")
                    .satisfies(exception -> assertThat(((GeminiApiException) exception).usage().outputTokens())
                            .isEqualTo(1024));
            client.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesTheShortTimeoutOnlyForQuestionInteractions() throws IOException {
        byte[] response = """
                {"id":"test","status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"ok"}]}]}
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(200);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The short-timeout client is expected to disconnect before this response is written.
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            GeminiInteractionsClient client = new GeminiInteractionsClient(
                    "test-key",
                    "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "v1beta",
                    Duration.ofSeconds(2),
                    Duration.ofMillis(50));
            var request = CreateModelInteraction.builder()
                    .model("test-model")
                    .input(InteractionsInput.of("test"))
                    .build();

            assertThat(client.create(request).outputText()).isEqualTo("ok");
            assertThatThrownBy(() -> client.createQuestion(request))
                    .isInstanceOf(GeminiApiException.class)
                    .hasMessageContaining("Could not reach the Gemini API");
            client.close();
        } finally {
            server.stop(0);
            serverExecutor.close();
        }
    }
}
