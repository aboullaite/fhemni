package dev.maboullaite.fhemni.web;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.media.ProgrammeMedia;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaService;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/admin/programmes/media/{mediaId}/asset")
public class AdminProgrammeMediaAssetController {

    private final ProgrammeMediaService media;
    private final ProgrammeMediaStorage storage;

    public AdminProgrammeMediaAssetController(ProgrammeMediaService media, ProgrammeMediaStorage storage) {
        this.media = media;
        this.storage = storage;
    }

    @GetMapping("/{variant:audio|video|captions}")
    public ResponseEntity<StreamingResponseBody> asset(
            @PathVariable UUID mediaId,
            @PathVariable String variant) throws IOException {
        ProgrammeMedia record = media.adminRecord(mediaId);
        String objectKey = switch (variant) {
            case "audio" -> record.audioObjectKey();
            case "video" -> record.videoObjectKey();
            case "captions" -> record.captionsObjectKey();
            default -> throw new IllegalArgumentException("Unknown programme media variant.");
        };
        if (objectKey == null) {
            throw new IllegalStateException("This programme media asset has not been generated yet.");
        }
        var delivery = storage.deliveryUri(objectKey, Duration.ofHours(1));
        if (delivery.isPresent()) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, delivery.get().toString())
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        ProgrammeMediaStorage.StoredObject stored = storage.open(objectKey);
        StreamingResponseBody body = output -> {
            try (stored) {
                stored.content().transferTo(output);
            }
        };
        MediaType type = stored.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(stored.contentType());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore());
        if (stored.size() >= 0) {
            response.contentLength(stored.size());
        }
        return response.body(body);
    }
}
