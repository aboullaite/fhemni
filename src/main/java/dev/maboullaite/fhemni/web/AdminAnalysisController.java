package dev.maboullaite.fhemni.web;

import java.util.UUID;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.PublicationState;
import dev.maboullaite.fhemni.analysis.AnalysisService;
import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analyses")
public class AdminAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalysisController.class);

    private final AnalysisService analyses;
    private final CurrentUserService currentUser;

    public AdminAnalysisController(AnalysisService analyses, CurrentUserService currentUser) {
        this.analyses = analyses;
        this.currentUser = currentUser;
    }

    @PostMapping("/reprocess")
    public ResponseEntity<AnalysisSnapshot> reprocess(@RequestBody ReprocessRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(analyses.reprocess(request.youtubeUrl(), request.language()));
    }

    @GetMapping("/{analysisId}/publication")
    public ResponseEntity<PublicationState> publication(@PathVariable UUID analysisId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(analyses.publication(analysisId));
    }

    @PostMapping("/{analysisId}/publish")
    public ResponseEntity<PublicationState> publish(
            @PathVariable UUID analysisId,
            Authentication authentication) {
        AppUser administrator = currentUser.find(authentication)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "A persisted administrator account is required to publish."));
        log.info("Administrator {} is publishing analysis {}", administrator.id(), analysisId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(analyses.publish(analysisId, administrator.id()));
    }

    public record ReprocessRequest(String youtubeUrl, String language) {
    }
}
