package dev.maboullaite.fhemni.programme.media;

import java.time.Instant;
import java.util.UUID;

public record ProgrammeMedia(
        UUID id,
        UUID programmeId,
        String partyCode,
        String sourceSha256,
        ProgrammeMediaStatus status,
        int scriptRevision,
        ProgrammeMediaScript script,
        String scriptText,
        String scriptModel,
        String ttsModel,
        String ttsVoice,
        String imageModel,
        int illustrationCount,
        String pronunciationVersion,
        String audioObjectKey,
        String videoObjectKey,
        String captionsObjectKey,
        Long durationMs,
        boolean refreshRequired,
        int attemptCount,
        int maxAttempts,
        Instant availableAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant scriptReviewedAt,
        Instant mediaReviewedAt,
        Instant publishedAt) {
}
