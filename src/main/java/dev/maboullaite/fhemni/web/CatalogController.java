package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import dev.maboullaite.fhemni.catalog.CatalogPage;
import dev.maboullaite.fhemni.catalog.CatalogVideoRepository;
import dev.maboullaite.fhemni.catalog.CatalogVideoView;
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

    private final CatalogVideoRepository repository;

    public CatalogController(CatalogVideoRepository repository) {
        this.repository = repository;
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
        CatalogPage result = repository.findPublic(q, language, page, size);
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

    public record CatalogPageResponse(
            List<CatalogVideoView> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
