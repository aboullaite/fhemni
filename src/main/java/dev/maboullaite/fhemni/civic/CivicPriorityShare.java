package dev.maboullaite.fhemni.civic;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record CivicPriorityShare(
        UUID id,
        String token,
        Kind kind,
        String language,
        String imageObjectKey,
        String imageSha256,
        Instant createdAt) {

    public Metadata metadata() {
        return new Metadata(token, kind, language, imageObjectKey, imageSha256, createdAt);
    }

    public record Metadata(
            String token,
            Kind kind,
            String language,
            String imageObjectKey,
            String imageSha256,
            Instant createdAt) {
    }

    public enum Kind {
        COMPASS,
        PARTIES;

        static Kind parse(String value) {
            if (value == null) {
                throw new IllegalArgumentException("A share-card type is required.");
            }
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("The share-card type must be compass or parties.");
            }
        }
    }
}
