package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import dev.maboullaite.fhemni.catalog.CatalogPage;
import dev.maboullaite.fhemni.catalog.CatalogVideoRepository;
import dev.maboullaite.fhemni.catalog.CatalogVideoView;
import dev.maboullaite.fhemni.catalog.PersonCatalogService;
import dev.maboullaite.fhemni.catalog.PersonDirectory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/videos")
public class CatalogController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();
    private static final int MAX_PERSON_PATTERNS = 100;

    private final CatalogVideoRepository repository;
    private final PersonCatalogService people;

    public CatalogController(CatalogVideoRepository repository, PersonCatalogService people) {
        this.repository = repository;
        this.people = people;
    }

    @GetMapping
    public ResponseEntity<CatalogPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String language) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater.");
        }
        if (size < 1 || size > 24) {
            throw new IllegalArgumentException("Page size must be between 1 and 24.");
        }
        CatalogPage result = repository.findPublic(q, language, page, size, personPatterns(q));
        List<CatalogVideoView> items = result.items().stream().map(CatalogVideoView::from).toList();
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .body(new CatalogPageResponse(items, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CatalogVideoView> detail(@PathVariable String slug) {
        CatalogVideoView video = repository.findPublicBySlug(slug)
                .map(CatalogVideoView::from)
                .orElseThrow(() -> new NoSuchElementException("This catalogue video was not found."));
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(video);
    }

    /**
     * Bilingual person search: expands the query into every known spelling of
     * the matching guests so a French query also hits Arabic briefing text
     * (and vice versa). SQL LIKE cannot transliterate on its own.
     */
    private List<String> personPatterns(String query) {
        // A query made only of punctuation normalizes to nothing: expanding it
        // would match every known guest and therefore every published episode.
        if (query == null || query.isBlank() || PersonDirectory.normalize(query).isEmpty()) {
            return List.of();
        }
        return people.searchPeople(query, null).stream()
                .flatMap(summary -> summary.spellings().stream())
                .distinct()
                .limit(MAX_PERSON_PATTERNS)
                .toList();
    }

    public record CatalogPageResponse(
            List<CatalogVideoView> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
