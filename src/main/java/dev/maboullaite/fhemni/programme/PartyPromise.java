package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;

public record PartyPromise(
        UUID id,
        UUID programmeId,
        String slug,
        String topic,
        LocalizedText title,
        String promiseText,
        String sourceLocator,
        String mechanism,
        String financing,
        EditorialStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt) {
}
