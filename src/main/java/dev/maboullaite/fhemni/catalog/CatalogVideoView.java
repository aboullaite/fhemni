package dev.maboullaite.fhemni.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.RevisionSummary;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;

public record CatalogVideoView(
        String youtubeVideoId,
        String slug,
        String canonicalUrl,
        String embedUrl,
        String title,
        String authorName,
        String thumbnailUrl,
        String showName,
        LocalDate publishedOn,
        String sourceLanguage,
        String category,
        String shortSummary,
        CatalogStatus status,
        java.util.UUID publishedAnalysisId,
        java.util.UUID latestAnalysisId,
        AnalysisStatus latestAnalysisStatus,
        OutputLanguage latestAnalysisLanguage,
        Instant cataloguedAt) {

    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public static CatalogVideoView from(CatalogVideo video) {
        String videoId = validatedVideoId(video.youtubeVideoId());
        return new CatalogVideoView(
                videoId,
                video.slug(),
                video.canonicalUrl(),
                "https://www.youtube-nocookie.com/embed/" + videoId,
                video.title(),
                video.authorName(),
                video.thumbnailUrl(),
                video.showName(),
                video.publishedOn(),
                video.sourceLanguage(),
                video.category(),
                video.shortSummary(),
                video.status(),
                video.status() == CatalogStatus.PUBLISHED ? video.publishedAnalysisId() : null,
                null,
                null,
                null,
                video.createdAt());
    }

    public static CatalogVideoView fromAdmin(CatalogVideo video, RevisionSummary latest) {
        String videoId = validatedVideoId(video.youtubeVideoId());
        return new CatalogVideoView(
                videoId,
                video.slug(),
                video.canonicalUrl(),
                "https://www.youtube-nocookie.com/embed/" + videoId,
                video.title(),
                video.authorName(),
                video.thumbnailUrl(),
                video.showName(),
                video.publishedOn(),
                video.sourceLanguage(),
                video.category(),
                video.shortSummary(),
                video.status(),
                video.publishedAnalysisId(),
                latest == null ? null : latest.id(),
                latest == null ? null : latest.status(),
                latest == null ? null : latest.language(),
                video.createdAt());
    }

    private static String validatedVideoId(String videoId) {
        if (videoId == null || !YOUTUBE_VIDEO_ID.matcher(videoId).matches()) {
            throw new IllegalStateException("Stored catalogue video has an invalid YouTube ID");
        }
        return videoId;
    }
}
