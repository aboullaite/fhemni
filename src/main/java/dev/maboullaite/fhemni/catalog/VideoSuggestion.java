package dev.maboullaite.fhemni.catalog;

import java.time.Instant;
import java.util.UUID;

public record VideoSuggestion(
        UUID id,
        String youtubeVideoId,
        String canonicalUrl,
        String title,
        String authorName,
        String thumbnailUrl,
        VideoSuggestionStatus status,
        SuggestionModerationStatus moderationStatus,
        String moderationReason,
        int submissionCount,
        UUID suggestedByUserId,
        Instant metadataCheckedAt,
        Instant firstSuggestedAt,
        Instant lastSuggestedAt) {
}
