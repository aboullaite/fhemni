package dev.maboullaite.fhemni.web;

import java.util.List;
import java.util.Map;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.RevisionSummary;
import dev.maboullaite.fhemni.catalog.CatalogImportService;
import dev.maboullaite.fhemni.catalog.CatalogImportService.ImportItem;
import dev.maboullaite.fhemni.catalog.CatalogImportService.ImportSummary;
import dev.maboullaite.fhemni.catalog.CatalogImportService.DateRefreshSummary;
import dev.maboullaite.fhemni.catalog.CatalogVideoRepository;
import dev.maboullaite.fhemni.catalog.CatalogVideoView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog/videos")
public class AdminCatalogController {

    private final CatalogImportService importer;
    private final CatalogVideoRepository repository;
    private final AnalysisRevisionRepository revisions;

    public AdminCatalogController(
            CatalogImportService importer,
            CatalogVideoRepository repository,
            AnalysisRevisionRepository revisions) {
        this.importer = importer;
        this.repository = repository;
        this.revisions = revisions;
    }

    @GetMapping
    public ResponseEntity<List<CatalogVideoView>> list() {
        Map<String, RevisionSummary> latest = revisions.latestByVideo();
        List<CatalogVideoView> videos = repository.findAll(100).stream()
                .map(video -> CatalogVideoView.fromAdmin(video, latest.get(video.youtubeVideoId())))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(videos);
    }

    @PostMapping("/import")
    public ResponseEntity<ImportSummary> importVideos(@RequestBody ImportRequest request) {
        ImportSummary result = importer.importVideos(request.items(), request.showName(), request.sourceLanguage());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping("/refresh-dates")
    public ResponseEntity<DateRefreshSummary> refreshDates() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(importer.refreshMissingDates(50));
    }

    public record ImportRequest(List<ImportItem> items, String showName, String sourceLanguage) {
    }
}
