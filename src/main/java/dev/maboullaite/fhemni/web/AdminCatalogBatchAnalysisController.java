package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.catalog.CatalogBatchAnalysisService;
import dev.maboullaite.fhemni.catalog.CatalogBatchAnalysisService.BatchSnapshot;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog/analysis-batches")
public class AdminCatalogBatchAnalysisController {

    private final CatalogBatchAnalysisService batches;

    public AdminCatalogBatchAnalysisController(CatalogBatchAnalysisService batches) {
        this.batches = batches;
    }

    @PostMapping
    public ResponseEntity<BatchSnapshot> start(@RequestBody BatchRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(batches.start(request.language(), request.maxItems()));
    }

    @GetMapping("/latest")
    public ResponseEntity<BatchSnapshot> latest() {
        BatchSnapshot latest = batches.latest();
        if (latest == null) {
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(latest);
    }

    public record BatchRequest(String language, Integer maxItems) {
    }
}
