package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class YouTubePublicationDateClientTest {

    @Test
    void readsThePublicationDateFromPublicWatchPageMetadata() throws IOException {
        byte[] response = """
                <html><head>
                <meta itemprop="datePublished" content="2026-09-02T15:00:30-07:00">
                <meta itemprop="uploadDate" content="2026-09-02T15:00:30-07:00">
                </head></html>
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(response, 200);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
        try {
            var client = new YouTubePublicationDateClient(
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/watch?v=");

            assertThat(client.fetch("n5B3boj2MFM")).isEqualTo(LocalDate.of(2026, 9, 2));
        } finally {
            server.stop(0);
            executor.close();
        }
    }

    @Test
    void failsWhenYoutubeDoesNotExposeADate() throws IOException {
        HttpServer server = server("<html></html>".getBytes(StandardCharsets.UTF_8), 200);
        server.start();
        try {
            var client = new YouTubePublicationDateClient(
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/watch?v=");

            assertThatThrownBy(() -> client.fetch("n5B3boj2MFM"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("publication date");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsTheDisplayDateShapeReturnedToTheProductionHost() throws IOException {
        byte[] response = """
                <script>
                {"publishDate":{"simpleText":"Streamed live on Sep 6, 2026"}}
                </script>
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(response, 200);
        server.start();
        try {
            var client = new YouTubePublicationDateClient(
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/watch?v=");

            assertThat(client.fetch("2prDGFPxFrU")).isEqualTo(LocalDate.of(2026, 9, 6));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void continuesToReadABareDisplayDate() throws IOException {
        byte[] response = """
                <script>
                {"publishDate":{"simpleText":"Sep 6, 2026"}}
                </script>
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(response, 200);
        server.start();
        try {
            var client = new YouTubePublicationDateClient(
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/watch?v=");

            assertThat(client.fetch("2prDGFPxFrU")).isEqualTo(LocalDate.of(2026, 9, 6));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsTheRelativeLiveDateReturnedForRecentStreams() throws IOException {
        byte[] response = """
                <script>
                {"publishDate":{"simpleText":"Streamed live 20 hours ago"}}
                </script>
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(response, 200);
        server.start();
        try {
            var client = new YouTubePublicationDateClient(
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/watch?v=",
                    Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC));

            assertThat(client.fetch("P6ZsBNWCXYY")).isEqualTo(LocalDate.of(2026, 9, 11));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToTheLiveBroadcastStartDate() throws IOException {
        byte[] response = """
                <script>
                {"dateText":{"simpleText":"Streamed live on Sep 4, 2026"},
                "liveBroadcastDetails":{"startTimestamp":"2026-09-04T18:30:02Z"}}
                </script>
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(response, 200);
        server.start();
        try {
            var client = new YouTubePublicationDateClient(
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/watch?v=");

            assertThat(client.fetch("cfM0dKXkuhU")).isEqualTo(LocalDate.of(2026, 9, 4));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(byte[] response, int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/watch", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }
}
