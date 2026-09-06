package dev.maboullaite.fhemni.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CatalogVideo(
        UUID id,
        String youtubeVideoId,
        String slug,
        String canonicalUrl,
        String title,
        String authorName,
        String thumbnailUrl,
        String showName,
        LocalDate publishedOn,
        String sourceLanguage,
        String category,
        String shortSummary,
        CatalogStatus status,
        UUID publishedAnalysisId,
        boolean listed,
        Instant createdAt,
        Instant updatedAt) {
}
