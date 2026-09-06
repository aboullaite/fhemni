package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.catalog.PublishedAnalysisMigrationService;
import dev.maboullaite.fhemni.catalog.PublishedAnalysisMigrationService.MigrationOverview;
import dev.maboullaite.fhemni.catalog.PublishedAnalysisMigrationService.MigrationSnapshot;
import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog/context-migration")
public class AdminPublishedAnalysisMigrationController {

    private final PublishedAnalysisMigrationService migrations;
    private final CurrentUserService currentUser;

    public AdminPublishedAnalysisMigrationController(
            PublishedAnalysisMigrationService migrations,
            CurrentUserService currentUser) {
        this.migrations = migrations;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<MigrationOverview> overview() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(migrations.overview());
    }

    @PostMapping
    public ResponseEntity<MigrationSnapshot> start(
            @RequestBody MigrationRequest request,
            Authentication authentication) {
        AppUser administrator = currentUser.find(authentication)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "A persisted administrator account is required to start this migration."));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(migrations.start(administrator.id(), request.maxItems()));
    }

    public record MigrationRequest(Integer maxItems) {
    }
}
