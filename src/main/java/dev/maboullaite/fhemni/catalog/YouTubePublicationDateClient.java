package dev.maboullaite.fhemni.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class YouTubePublicationDateClient implements VideoPublicationDateGateway {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern META_DATE = Pattern.compile(
            "(?i)itemprop=[\"'](?:datePublished|uploadDate)[\"'][^>]*content=[\"']([^\"']+)[\"']");
    private static final Pattern JSON_DATE = Pattern.compile(
            "\"(?:publishDate|uploadDate)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIMPLE_TEXT_DATE = Pattern.compile(
            "\"(?:publishDate|uploadDate)\"\\s*:\\s*\\{\\s*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DISPLAY_DATE_PREFIX = Pattern.compile("(?i)^(?:Streamed live|Premiered) on\\s+");
    private static final Pattern RELATIVE_DISPLAY_DATE = Pattern.compile(
            "(?i)^(?:(?:Streamed live|Premiered)\\s+)?(\\d+)\\s+(minute|hour|day|week)s?\\s+ago$");
    private static final Pattern LIVE_START_DATE = Pattern.compile(
            "\"startTimestamp\"\\s*:\\s*\"([^\"]+)\"");
    private static final DateTimeFormatter ENGLISH_DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH);
    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String watchUrl;
    private final Clock clock;

    @Autowired
    public YouTubePublicationDateClient(
            @Value("${fhemni.catalog.watch-page-timeout:PT6S}") Duration requestTimeout,
            @Value("${fhemni.catalog.watch-page-url:https://www.youtube.com/watch?v=}") String watchUrl) {
        this(requestTimeout, watchUrl, Clock.systemUTC());
    }

    YouTubePublicationDateClient(Duration requestTimeout, String watchUrl, Clock clock) {
        this.requestTimeout = requestTimeout;
        this.watchUrl = watchUrl;
        this.clock = clock;
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
        var simpleText = SIMPLE_TEXT_DATE.matcher(html);
        if (simpleText.find()) {
            return parseDisplayDate(simpleText.group(1));
        }
        var liveStart = LIVE_START_DATE.matcher(html);
        if (liveStart.find()) {
            return parseDate(liveStart.group(1));
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

    private LocalDate parseDisplayDate(String value) {
        String normalized = DISPLAY_DATE_PREFIX.matcher(value.strip()).replaceFirst("");
        try {
            return LocalDate.parse(normalized, ENGLISH_DISPLAY_DATE);
        } catch (DateTimeParseException exception) {
            var relative = RELATIVE_DISPLAY_DATE.matcher(value.strip());
            if (!relative.matches()) {
                throw new IllegalStateException("YouTube returned an invalid publication date.", exception);
            }
            long amount = Long.parseLong(relative.group(1));
            Duration elapsed = switch (relative.group(2).toLowerCase(Locale.ROOT)) {
                case "minute" -> Duration.ofMinutes(amount);
                case "hour" -> Duration.ofHours(amount);
                case "day" -> Duration.ofDays(amount);
                case "week" -> Duration.ofDays(Math.multiplyExact(amount, 7));
                default -> throw new IllegalStateException("YouTube returned an invalid relative publication date.");
            };
            Instant publishedAt = clock.instant().minus(elapsed);
            return publishedAt.atZone(ZoneOffset.UTC).toLocalDate();
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
