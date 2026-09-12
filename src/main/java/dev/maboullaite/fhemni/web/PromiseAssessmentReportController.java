package dev.maboullaite.fhemni.web;

import java.util.UUID;

import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Category;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/promises")
public class PromiseAssessmentReportController {

    private final PromiseAssessmentReportService reports;
    private final CurrentUserService currentUser;

    public PromiseAssessmentReportController(
            PromiseAssessmentReportService reports,
            CurrentUserService currentUser) {
        this.reports = reports;
        this.currentUser = currentUser;
    }

    @PostMapping("/{slug}/reports")
    public ResponseEntity<PromiseAssessmentReport> report(
            @PathVariable String slug,
            @RequestBody ReportRequest request,
            Authentication authentication) {
        AppUser user = currentUser.find(authentication).orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException("Sign in to report an assessment issue."));
        PromiseAssessmentReport saved = reports.submit(
                slug,
                request == null ? null : request.assessmentId(),
                user.id(),
                request == null ? null : request.category(),
                request == null ? null : request.details(),
                request == null ? null : request.sourceUrl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(saved);
    }

    public record ReportRequest(UUID assessmentId, Category category, String details, String sourceUrl) {
    }
}
