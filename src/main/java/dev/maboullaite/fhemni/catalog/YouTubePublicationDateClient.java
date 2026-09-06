package dev.maboullaite.fhemni.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class YouTubePublicationDateClient implements VideoPublicationDateGateway {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern META_DATE = Pattern.compile(
            "(?i)itemprop=[\"'](?:datePublished|uploadDate)[\"'][^>]*content=[\"']([^\"']+)[\"']");
    private static final Pattern JSON_DATE = Pattern.compile(
            "\"(?:publishDate|uploadDate)\"\\s*:\\s*\"([^\"]+)\"");
    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String watchUrl;

    public YouTubePublicationDateClient(
            @Value("${fhemni.catalog.watch-page-timeout:PT6S}") Duration requestTimeout,
            @Value("${fhemni.catalog.watch-page-url:https://www.youtube.com/watch?v=}") String watchUrl) {
        this.requestTimeout = requestTimeout;
        this.watchUrl = watchUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public LocalDate fetch(String youtubeVideoId) {
        if (youtubeVideoId == null || !VIDEO_ID.matcher(youtubeVideoId).matches()) {
            throw new IllegalArgumentException("The YouTube video ID is invalid.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(watchUrl + youtubeVideoId))
                .timeout(requestTimeout)
                .header("Accept", "text/html")
                .header("Accept-Language", "en")
                .header("User-Agent", "Mozilla/5.0 (compatible; Fhemni/1.0)")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                close(response.body());
                throw new IllegalStateException("YouTube did not expose a publication date.");
            }
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES);
                return extractDate(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The YouTube date request was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("YouTube publication metadata is temporarily unavailable.", exception);
        }
    }

    private LocalDate extractDate(String html) {
        var metadata = META_DATE.matcher(html);
        if (metadata.find()) {
            return parseDate(metadata.group(1));
        }
        var json = JSON_DATE.matcher(html);
        if (json.find()) {
            return parseDate(json.group(1));
        }
        throw new IllegalStateException("YouTube did not expose a publication date.");
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("YouTube returned an invalid publication date.", exception);
        }
    }

    private void close(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // The response has already failed; closing is best-effort.
        }
    }
}
