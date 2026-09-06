package dev.maboullaite.fhemni.video;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class YouTubeUrlParser {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public ParsedYouTubeUrl parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Paste a YouTube URL to continue.");
        }

        String candidate = value.strip();
        if (!candidate.contains("://")) {
            candidate = "https://" + candidate;
        }

        try {
            URI uri = URI.create(candidate);
            String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
            String videoId;

            if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
                videoId = firstPathSegment(uri.getPath());
            } else if (isYouTubeHost(host)) {
                videoId = extractFromYouTubePath(uri);
            } else {
                throw invalid();
            }

            if (!VIDEO_ID.matcher(videoId).matches()) {
                throw invalid();
            }

            return new ParsedYouTubeUrl(
                    videoId,
                    "https://www.youtube.com/watch?v=" + videoId,
                    "https://www.youtube.com/embed/" + videoId);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Use a public")) {
                throw exception;
            }
            throw invalid();
        }
    }

    private String extractFromYouTubePath(URI uri) {
        String path = Optional.ofNullable(uri.getPath()).orElse("");
        if (path.equals("/watch")) {
            return queryParameter(uri.getRawQuery(), "v").orElseThrow(this::invalid);
        }

        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length >= 2 && (segments[0].equals("embed")
                || segments[0].equals("shorts")
                || segments[0].equals("live"))) {
            return segments[1];
        }
        throw invalid();
    }

    private Optional<String> queryParameter(String query, String expectedName) {
        if (query == null) {
            return Optional.empty();
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(expectedName))
                .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                .findFirst();
    }

    private String firstPathSegment(String path) {
        return Arrays.stream(Optional.ofNullable(path).orElse("").split("/"))
                .filter(segment -> !segment.isBlank())
                .findFirst()
                .orElseThrow(this::invalid);
    }

    private boolean isYouTubeHost(String host) {
        return host.equals("youtube.com")
                || host.equals("www.youtube.com")
                || host.equals("m.youtube.com")
                || host.equals("music.youtube.com");
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Use a public youtube.com or youtu.be video URL.");
    }

    public record ParsedYouTubeUrl(String videoId, String canonicalUrl, String embedUrl) {
    }
}
