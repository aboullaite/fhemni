package dev.maboullaite.fhemni.catalog;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class YouTubeOEmbedClient implements VideoMetadataGateway {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final String endpoint;

    public YouTubeOEmbedClient(
            ObjectMapper objectMapper,
            @Value("${fhemni.catalog.oembed-timeout:PT5S}") Duration requestTimeout,
            @Value("${fhemni.catalog.oembed-url:https://www.youtube.com/oembed}") String endpoint) {
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public VideoMetadata fetch(String canonicalUrl, String youtubeVideoId) {
        URI requestUri = URI.create(endpoint + "?format=json&url="
                + URLEncoder.encode(canonicalUrl, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("YouTube could not find a public video at this URL.");
            }
            JsonNode payload = objectMapper.readTree(response.body());
            String title = requiredText(payload, "title");
            String author = requiredText(payload, "author_name");
            String thumbnail = payload.path("thumbnail_url").asText("").strip();
            if (!isYouTubeThumbnail(thumbnail)) {
                thumbnail = "https://i.ytimg.com/vi/" + youtubeVideoId + "/hqdefault.jpg";
            }
            return new VideoMetadata(title, author, thumbnail);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The YouTube metadata request was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("YouTube metadata is temporarily unavailable.", exception);
        }
    }

    private String requiredText(JsonNode payload, String name) {
        String value = payload.path(name).asText("").strip();
        if (value.isBlank()) {
            throw new IllegalStateException("YouTube returned incomplete video metadata.");
        }
        return value;
    }

    private boolean isYouTubeThumbnail(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && (host.equals("i.ytimg.com") || host.endsWith(".ytimg.com"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
