package dev.maboullaite.fhemni.web;

import java.io.IOException;
import java.time.Duration;

import dev.maboullaite.fhemni.programme.media.ProgrammeMedia;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaService;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaService.PublicProgrammeMedia;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/catalog/parties/{code}/programme/media")
public class PublicProgrammeMediaController {

    private static final Duration DELIVERY_URL_VALIDITY = Duration.ofHours(1);
    private final ProgrammeMediaService media;
    private final ProgrammeMediaStorage storage;

    public PublicProgrammeMediaController(ProgrammeMediaService media, ProgrammeMediaStorage storage) {
        this.media = media;
        this.storage = storage;
    }

    @GetMapping
    public ResponseEntity<PublicProgrammeMedia> metadata(@PathVariable String code) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(media.published(code));
    }

    @GetMapping("/{variant:audio|video|captions}")
    public ResponseEntity<StreamingResponseBody> asset(
            @PathVariable String code,
            @PathVariable String variant,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        ProgrammeMedia published = media.publishedRecord(code);
        String objectKey = switch (variant) {
            case "audio" -> published.audioObjectKey();
            case "video" -> published.videoObjectKey();
            case "captions" -> published.captionsObjectKey();
            default -> throw new IllegalArgumentException("Unknown programme media variant.");
        };
        // Keep captions same-origin: browsers apply stricter cross-origin rules to WebVTT tracks.
        // Audio and video stay private in GCS and are exposed with short-lived signed URLs.
        var delivery = "captions".equals(variant)
                ? java.util.Optional.<java.net.URI>empty()
                : storage.deliveryUri(objectKey, DELIVERY_URL_VALIDITY);
        if (delivery.isPresent()) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, delivery.get().toString())
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        ProgrammeMediaStorage.StoredObject stored = storage.open(objectKey);
        try {
            ByteRange range = ByteRange.parse(rangeHeader, stored.size());
            StreamingResponseBody body = output -> {
                try (stored) {
                    if (range == null) {
                        stored.content().transferTo(output);
                    } else {
                        stored.content().skipNBytes(range.start());
                        byte[] buffer = new byte[16 * 1024];
                        long remaining = range.length();
                        while (remaining > 0) {
                            int read = stored.content().read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (read < 0) break;
                            output.write(buffer, 0, read);
                            remaining -= read;
                        }
                    }
                }
            };
            MediaType type = stored.contentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(stored.contentType());
            ResponseEntity.BodyBuilder response = range == null
                    ? ResponseEntity.ok()
                    : ResponseEntity.status(HttpStatus.PARTIAL_CONTENT);
            response
                    .contentType(type)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic().immutable());
            if (range != null) {
                response.header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + range.start() + "-" + range.end() + "/" + stored.size());
            }
            if (stored.size() >= 0) {
                response.contentLength(range == null ? stored.size() : range.length());
            }
            return response.body(body);
        } catch (RuntimeException | Error failure) {
            try {
                stored.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private record ByteRange(long start, long end) {

        static ByteRange parse(String header, long size) {
            if (header == null || size <= 0 || !header.startsWith("bytes=") || header.contains(",")) {
                return null;
            }
            String value = header.substring("bytes=".length()).trim();
            int separator = value.indexOf('-');
            if (separator < 0) return null;
            try {
                String startValue = value.substring(0, separator).trim();
                String endValue = value.substring(separator + 1).trim();
                long start;
                long end;
                if (startValue.isEmpty()) {
                    long suffixLength = Long.parseLong(endValue);
                    if (suffixLength <= 0) return null;
                    start = Math.max(0, size - suffixLength);
                    end = size - 1;
                } else {
                    start = Long.parseLong(startValue);
                    end = endValue.isEmpty() ? size - 1 : Math.min(Long.parseLong(endValue), size - 1);
                }
                return start >= 0 && start <= end && start < size ? new ByteRange(start, end) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        long length() {
            return end - start + 1;
        }
    }
}
