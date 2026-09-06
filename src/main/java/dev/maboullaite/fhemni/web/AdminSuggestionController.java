package dev.maboullaite.fhemni.web;

import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.catalog.RankedVideoSuggestion;
import dev.maboullaite.fhemni.catalog.VideoSuggestionRepository;
import dev.maboullaite.fhemni.catalog.VideoSuggestionService;
import dev.maboullaite.fhemni.catalog.VideoSuggestionService.MetadataRefreshSummary;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/suggestions")
public class AdminSuggestionController {

    private static final int MAX_QUEUE_SIZE = 100;

    private final VideoSuggestionRepository suggestions;
    private final VideoSuggestionService service;

    public AdminSuggestionController(
            VideoSuggestionRepository suggestions,
            VideoSuggestionService service) {
        this.suggestions = suggestions;
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RankedVideoSuggestion>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(suggestions.findPending(MAX_QUEUE_SIZE));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable UUID id) {
        suggestions.dismiss(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        suggestions.approve(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/refresh-metadata")
    public ResponseEntity<MetadataRefreshSummary> refreshMetadata() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.enrichPending(50));
    }
}
