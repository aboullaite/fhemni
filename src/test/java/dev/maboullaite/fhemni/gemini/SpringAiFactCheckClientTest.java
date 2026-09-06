package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class SpringAiFactCheckClientTest {

    @Test
    void staysDisabledWithoutCreatingAProviderClientWhenTheKeyIsBlank() {
        var client = new SpringAiFactCheckClient(
                "",
                "gemini-3.8-flash",
                "https://generativelanguage.googleapis.com",
                "v1beta",
                Duration.ofSeconds(5));

        assertThat(client.configured()).isFalse();
        assertThat(client.model()).isEmpty();
        assertThatThrownBy(() -> client.check("system", "prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
        client.close();
    }

    @Test
    void rejectsABlankModelWhenFactCheckingIsEnabled() {
        assertThatThrownBy(() -> new SpringAiFactCheckClient(
                "test-key",
                "  ",
                "https://generativelanguage.googleapis.com",
                "v1beta",
                Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model must not be blank");
    }

    @Test
    void requestsGroundedProviderStructuredOutputThroughSpringAi() throws IOException {
        String result = """
                {"assessments":[{"claimId":"claim-1","verdict":"SUPPORTED","explanation":"Confirmed","evidenceStrength":"HIGH","sources":[{"title":"Official source","url":"https://example.gov/data","publishedDate":"2026-08-01"}]}]}
                """;
        byte[] response = ("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":%s}]},"finishReason":"STOP"}],"modelVersion":"gemini-3.8-flash","responseId":"test"}
                """).formatted(quote(result)).getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            var client = new SpringAiFactCheckClient(
                    "test-key",
                    "gemini-3.8-flash",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "v1beta",
                    Duration.ofSeconds(2));

            FactCheckResponse factChecks = client.check("Be neutral", "Check claim-1").response();

            assertThat(factChecks.assessments()).hasSize(1);
            assertThat(factChecks.assessments().getFirst().verdict()).isEqualTo("SUPPORTED");
            assertThat(client.model()).isEqualTo("gemini-3.8-flash");
            assertThat(requestPath.get()).contains("/models/gemini-3.8-flash:");
            assertThat(requestBody.get()).contains("googleSearch", "responseJsonSchema");
            client.close();
        } finally {
            server.stop(0);
            serverExecutor.close();
        }
    }

    private static String quote(String value) {
        return '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + '"';
    }
}
