package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class YouTubeOEmbedClientTest {

    @Test
    void readsOnlyThePublicMetadataNeededByTheCatalogue() throws IOException {
        byte[] response = """
                {
                  "title": "ساعة الصراحة",
                  "author_name": "2MTV",
                  "thumbnail_url": "https://i.ytimg.com/vi/n5B3boj2MFM/hqdefault.jpg",
                  "html": "<iframe>ignored</iframe>"
                }
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/oembed", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            var client = new YouTubeOEmbedClient(
                    new ObjectMapper(),
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/oembed");

            var metadata = client.fetch(
                    "https://www.youtube.com/watch?v=n5B3boj2MFM",
                    "n5B3boj2MFM");

            assertThat(metadata.title()).isEqualTo("ساعة الصراحة");
            assertThat(metadata.authorName()).isEqualTo("2MTV");
            assertThat(metadata.thumbnailUrl()).isEqualTo(
                    "https://i.ytimg.com/vi/n5B3boj2MFM/hqdefault.jpg");
        } finally {
            server.stop(0);
            executor.close();
        }
    }

    @Test
    void rejectsVideosThatAreNotPubliclyAvailable() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oembed", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new YouTubeOEmbedClient(
                    new ObjectMapper(),
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/oembed");

            assertThatThrownBy(() -> client.fetch(
                    "https://www.youtube.com/watch?v=n5B3boj2MFM",
                    "n5B3boj2MFM"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("public video");
        } finally {
            server.stop(0);
        }
    }
}
