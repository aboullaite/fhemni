package dev.maboullaite.fhemni.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.catalog.VideoSuggestionRepository.SaveResult;
import dev.maboullaite.fhemni.catalog.VideoMetadataGateway.VideoMetadata;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class VideoSuggestionService {

    private final YouTubeUrlParser urlParser;
    private final CatalogVideoRepository catalog;
    private final VideoSuggestionRepository suggestions;
    private final VideoMetadataGateway metadataGateway;
    private final SuggestionSafetyPolicy safetyPolicy;
    private final ExecutorService metadataExecutor;

    public VideoSuggestionService(
            YouTubeUrlParser urlParser,
            CatalogVideoRepository catalog,
            VideoSuggestionRepository suggestions,
            VideoMetadataGateway metadataGateway,
            SuggestionSafetyPolicy safetyPolicy,
            @Qualifier("catalogImportExecutor") ExecutorService metadataExecutor) {
        this.urlParser = urlParser;
        this.catalog = catalog;
        this.suggestions = suggestions;
        this.metadataGateway = metadataGateway;
        this.safetyPolicy = safetyPolicy;
        this.metadataExecutor = metadataExecutor;
    }

    public SuggestionResult suggest(String youtubeUrl, UUID suggestedByUserId) {
        Objects.requireNonNull(suggestedByUserId, "A signed-in user is required to suggest a video");
        var parsed = urlParser.parse(youtubeUrl);
        if (catalog.findByYouTubeId(parsed.videoId()).isPresent()) {
            suggestions.markAcceptedByYouTubeId(parsed.videoId());
            return new SuggestionResult(SuggestionOutcome.ALREADY_CATALOGUED, 0, null);
        }

        VideoSuggestion existing = suggestions.findByYouTubeId(parsed.videoId()).orElse(null);
        VideoMetadata metadata = existing != null && existing.metadataCheckedAt() != null
                ? new VideoMetadata(existing.title(), existing.authorName(), existing.thumbnailUrl())
                : metadataGateway.fetch(parsed.canonicalUrl(), parsed.videoId());
        SuggestionSafetyPolicy.Assessment assessment = existing != null && existing.metadataCheckedAt() != null
                ? new SuggestionSafetyPolicy.Assessment(
                        existing.moderationStatus(), existing.moderationReason())
                : safetyPolicy.assess(metadata);
        SaveResult saved = suggestions.record(
                parsed.videoId(), parsed.canonicalUrl(), metadata, assessment, suggestedByUserId);
        SuggestionOutcome outcome = saved.created()
                ? assessment.status() == SuggestionModerationStatus.REVIEW_REQUIRED
                        ? SuggestionOutcome.HELD_FOR_REVIEW
                        : SuggestionOutcome.CREATED
                : SuggestionOutcome.ALREADY_SUGGESTED;
        return new SuggestionResult(
                outcome,
                saved.suggestion().submissionCount(),
                saved.suggestion().title());
    }

    public List<RankedVideoSuggestion> rankedFor(UUID viewerId, int limit) {
        return viewerId == null
                ? suggestions.findPendingPublic(limit)
                : suggestions.findPendingForViewer(viewerId, limit);
    }

    public void vote(UUID suggestionId, UUID userId, int value) {
        suggestions.setVote(suggestionId, userId, value);
    }

    public MetadataRefreshSummary enrichPending(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > 50) {
            throw new IllegalArgumentException("Refresh between 1 and 50 suggestions at a time.");
        }
        List<VideoSuggestion> pending = suggestions.findPendingWithoutMetadata(requestedLimit);
        List<CompletableFuture<MetadataRefreshResult>> requests = pending.stream()
                .map(suggestion -> CompletableFuture.supplyAsync(() -> enrich(suggestion), metadataExecutor))
                .toList();
        List<MetadataRefreshResult> results = new ArrayList<>(requests.size());
        requests.stream().map(CompletableFuture::join).forEach(results::add);
        long refreshed = results.stream().filter(result -> result.outcome() == MetadataRefreshOutcome.REFRESHED).count();
        long held = results.stream().filter(result -> result.outcome() == MetadataRefreshOutcome.HELD).count();
        long failed = results.stream().filter(result -> result.outcome() == MetadataRefreshOutcome.FAILED).count();
        return new MetadataRefreshSummary(refreshed, held, failed);
    }

    private MetadataRefreshResult enrich(VideoSuggestion suggestion) {
        try {
            VideoMetadata metadata = metadataGateway.fetch(
                    suggestion.canonicalUrl(), suggestion.youtubeVideoId());
            SuggestionSafetyPolicy.Assessment assessment = safetyPolicy.assess(metadata);
            suggestions.updateMetadata(suggestion.id(), metadata, assessment);
            return new MetadataRefreshResult(
                    assessment.status() == SuggestionModerationStatus.REVIEW_REQUIRED
                            ? MetadataRefreshOutcome.HELD
                            : MetadataRefreshOutcome.REFRESHED);
        } catch (RuntimeException exception) {
            return new MetadataRefreshResult(MetadataRefreshOutcome.FAILED);
        }
    }

    public record SuggestionResult(SuggestionOutcome outcome, int submissionCount, String title) {
    }

    public enum SuggestionOutcome {
        CREATED,
        HELD_FOR_REVIEW,
        ALREADY_SUGGESTED,
        ALREADY_CATALOGUED
    }

    public record MetadataRefreshSummary(long refreshed, long heldForReview, long failed) {
    }

    private record MetadataRefreshResult(MetadataRefreshOutcome outcome) {
    }

    private enum MetadataRefreshOutcome {
        REFRESHED,
        HELD,
        FAILED
    }
}
