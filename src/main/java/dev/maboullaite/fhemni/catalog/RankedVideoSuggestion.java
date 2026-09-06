package dev.maboullaite.fhemni.catalog;

import java.time.Instant;
import java.util.UUID;

public record RankedVideoSuggestion(
        UUID id,
        String youtubeVideoId,
        String canonicalUrl,
        String title,
        String authorName,
        String thumbnailUrl,
        SuggestionModerationStatus moderationStatus,
        String moderationReason,
        int submissionCount,
        int voteScore,
        int upvotes,
        int downvotes,
        int viewerVote,
        String suggestedByFirstName,
        Instant lastSuggestedAt) {
}
