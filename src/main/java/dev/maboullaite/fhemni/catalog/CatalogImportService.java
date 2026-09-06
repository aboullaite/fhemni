package dev.maboullaite.fhemni.catalog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import dev.maboullaite.fhemni.catalog.CatalogVideoRepository.ImportedCatalogVideo;
import dev.maboullaite.fhemni.catalog.CatalogVideoRepository.SaveResult;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import dev.maboullaite.fhemni.video.YouTubeUrlParser.ParsedYouTubeUrl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CatalogImportService {

    private static final int MAX_BATCH_SIZE = 20;
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ary", "fr", "en");

    private final YouTubeUrlParser urlParser;
    private final VideoMetadataGateway metadataGateway;
    private final VideoPublicationDateGateway publicationDates;
    private final CatalogVideoRepository repository;
    private final VideoSuggestionRepository suggestions;
    private final ExecutorService metadataExecutor;

    public CatalogImportService(
            YouTubeUrlParser urlParser,
            VideoMetadataGateway metadataGateway,
            VideoPublicationDateGateway publicationDates,
            CatalogVideoRepository repository,
            VideoSuggestionRepository suggestions,
            @Qualifier("catalogImportExecutor") ExecutorService metadataExecutor) {
        this.urlParser = urlParser;
        this.metadataGateway = metadataGateway;
        this.publicationDates = publicationDates;
        this.repository = repository;
        this.suggestions = suggestions;
        this.metadataExecutor = metadataExecutor;
    }

    public ImportSummary importVideos(List<ImportItem> items, String showName, String sourceLanguage) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one YouTube URL.");
        }
        if (items.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Import at most " + MAX_BATCH_SIZE + " videos at a time.");
        }
        String language = normalizeLanguage(sourceLanguage);
        String normalizedShowName = normalizeOptional(showName, 200, "Show name");
        List<CompletableFuture<PreparedImport>> metadataRequests = items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> prepare(item), metadataExecutor))
                .toList();
        List<ImportResult> results = new ArrayList<>(items.size());
        metadataRequests.stream()
                .map(CompletableFuture::join)
                .map(prepared -> save(prepared, normalizedShowName, language))
                .forEach(results::add);
        long imported = results.stream().filter(result -> result.outcome() == ImportOutcome.IMPORTED).count();
        long updated = results.stream().filter(result -> result.outcome() == ImportOutcome.UPDATED).count();
        long failed = results.stream().filter(result -> result.outcome() == ImportOutcome.FAILED).count();
        return new ImportSummary(imported, updated, failed, results);
    }

    private PreparedImport prepare(ImportItem item) {
        String submittedUrl = item == null ? null : item.youtubeUrl();
        try {
            var parsed = urlParser.parse(submittedUrl);
            var metadata = metadataGateway.fetch(parsed.canonicalUrl(), parsed.videoId());
            LocalDate publishedOn = item.publishedOn();
            if (publishedOn == null) {
                publishedOn = fetchDateOrNull(parsed.videoId());
            }
            return new PreparedImport(submittedUrl, item, parsed, metadata, publishedOn, null);
        } catch (RuntimeException exception) {
            return new PreparedImport(submittedUrl, item, null, null, null, safeMessage(exception));
        }
    }

    private ImportResult save(PreparedImport prepared, String showName, String language) {
        if (prepared.message() != null) {
            return new ImportResult(prepared.submittedUrl(), ImportOutcome.FAILED, null, null, prepared.message());
        }
        try {
            ParsedYouTubeUrl parsed = prepared.parsed();
            VideoMetadataGateway.VideoMetadata metadata = prepared.metadata();
            SaveResult saved = repository.saveImported(new ImportedCatalogVideo(
                    parsed.videoId(), parsed.canonicalUrl(), metadata.title(), metadata.authorName(),
                    metadata.thumbnailUrl(), showName, prepared.publishedOn(), language));
            suggestions.markAcceptedByYouTubeId(parsed.videoId());
            return new ImportResult(
                    prepared.submittedUrl(),
                    saved.created() ? ImportOutcome.IMPORTED : ImportOutcome.UPDATED,
                    saved.video().title(),
                    saved.video().slug(),
                    null);
        } catch (RuntimeException exception) {
            return new ImportResult(
                    prepared.submittedUrl(), ImportOutcome.FAILED, null, null, safeMessage(exception));
        }
    }

    private String normalizeLanguage(String value) {
        String language = value == null ? "ary" : value.strip().toLowerCase(Locale.ROOT);
        if (language.equals("ar")) {
            language = "ary";
        }
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("Choose Darija, French, or English as the source language.");
        }
        return language;
    }

    public DateRefreshSummary refreshMissingDates(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > 50) {
            throw new IllegalArgumentException("Refresh between 1 and 50 catalogue dates at a time.");
        }
        List<CatalogVideo> missing = repository.findMissingPublishedOn(requestedLimit);
        List<CompletableFuture<Boolean>> requests = missing.stream()
                .map(video -> CompletableFuture.supplyAsync(() -> refreshDate(video), metadataExecutor))
                .toList();
        long refreshed = requests.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        return new DateRefreshSummary(refreshed, missing.size() - refreshed);
    }

    private boolean refreshDate(CatalogVideo video) {
        LocalDate publishedOn = fetchDateOrNull(video.youtubeVideoId());
        if (publishedOn == null) {
            return false;
        }
        repository.updatePublishedOn(video.id(), publishedOn);
        return true;
    }

    private LocalDate fetchDateOrNull(String youtubeVideoId) {
        try {
            return publicationDates.fetch(youtubeVideoId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String normalizeOptional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long.");
        }
        return normalized;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "The video could not be imported." : message;
    }

    public record ImportItem(String youtubeUrl, LocalDate publishedOn) {
    }

    public record ImportSummary(long imported, long updated, long failed, List<ImportResult> results) {
    }

    public record DateRefreshSummary(long refreshed, long failed) {
    }

    public record ImportResult(
            String youtubeUrl,
            ImportOutcome outcome,
            String title,
            String slug,
            String message) {
    }

    private record PreparedImport(
            String submittedUrl,
            ImportItem item,
            ParsedYouTubeUrl parsed,
            VideoMetadataGateway.VideoMetadata metadata,
            LocalDate publishedOn,
            String message) {
    }

    public enum ImportOutcome {
        IMPORTED,
        UPDATED,
        FAILED
    }
}
