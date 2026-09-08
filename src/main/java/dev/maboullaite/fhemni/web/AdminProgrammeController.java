package dev.maboullaite.fhemni.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.ProgrammeAssessmentJob;
import dev.maboullaite.fhemni.programme.ProgrammeAssessmentJobService;
import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.ProgrammeIngestionService;
import dev.maboullaite.fhemni.programme.ProgrammeIngestionService.IngestionRequest;
import dev.maboullaite.fhemni.programme.ProgrammeIngestionService.IngestionResult;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminPromiseView;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftAssessment;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftProgramme;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftPromise;
import dev.maboullaite.fhemni.programme.PromiseAssessment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/programmes")
public class AdminProgrammeController {

    private final PartyProgrammeService programmes;
    private final ProgrammeIngestionService ingestion;
    private final ProgrammeAssessmentJobService assessmentJobs;

    public AdminProgrammeController(
            PartyProgrammeService programmes,
            ProgrammeIngestionService ingestion,
            ProgrammeAssessmentJobService assessmentJobs) {
        this.programmes = programmes;
        this.ingestion = ingestion;
        this.assessmentJobs = assessmentJobs;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestionJobResult> ingest(@RequestBody IngestionRequest request) {
        IngestionResult result = ingestion.ingest(request.sourceUrl());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(withJob(result));
    }

    @PostMapping(value = "/ingest-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionJobResult> ingestPdf(
            @RequestParam String sourceUrl,
            @RequestParam("document") MultipartFile document,
            @RequestParam(defaultValue = "false") boolean replaceExistingDraft) {
        IngestionResult result = ingestion.ingestPdf(sourceUrl, document, replaceExistingDraft);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(withJob(result));
    }

    @GetMapping
    public ResponseEntity<List<AdminProgrammeView>> list() {
        return noStore(programmes.adminProgrammes());
    }

    @GetMapping("/assessment-jobs")
    public ResponseEntity<Map<UUID, ProgrammeAssessmentJob>> assessmentJobs() {
        return noStore(assessmentJobs.latest());
    }

    @GetMapping("/{programmeId}/assessment-jobs/latest")
    public ResponseEntity<ProgrammeAssessmentJob> latestAssessmentJob(@PathVariable UUID programmeId) {
        return noStore(assessmentJobs.latest(programmeId));
    }

    @PostMapping("/{programmeId}/assessment-jobs")
    public ResponseEntity<ProgrammeAssessmentJob> startAssessmentJob(@PathVariable UUID programmeId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(assessmentJobs.start(programmeId));
    }

    @PostMapping
    public ResponseEntity<AdminProgrammeView> create(@RequestBody DraftProgramme request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(programmes.createProgramme(request));
    }

    @PostMapping("/{programmeId}/promises")
    public ResponseEntity<AdminPromiseView> createPromise(
            @PathVariable UUID programmeId,
            @RequestBody DraftPromise request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(programmes.createPromise(programmeId, request));
    }

    @PostMapping("/promises/{promiseId}/assessments")
    public ResponseEntity<PromiseAssessment> createAssessment(
            @PathVariable UUID promiseId,
            @RequestBody DraftAssessment request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(programmes.createAssessment(promiseId, request));
    }

    @PostMapping("/assessments/{assessmentId}/publish")
    public ResponseEntity<PromiseAssessment> publishAssessment(@PathVariable UUID assessmentId) {
        return noStore(programmes.publishAssessment(assessmentId));
    }

    @PostMapping("/promises/{promiseId}/publish")
    public ResponseEntity<AdminPromiseView> publishPromise(@PathVariable UUID promiseId) {
        return noStore(programmes.publishPromise(promiseId));
    }

    @PostMapping("/{programmeId}/publish")
    public ResponseEntity<AdminProgrammeView> publish(@PathVariable UUID programmeId) {
        return noStore(programmes.publishProgramme(programmeId));
    }

    @PostMapping("/{programmeId}/publish-all")
    public ResponseEntity<AdminProgrammeView> publishAll(@PathVariable UUID programmeId) {
        return noStore(programmes.publishAll(programmeId));
    }

    @PostMapping("/{programmeId}/verify-source")
    public ResponseEntity<AdminProgrammeView> verifySource(@PathVariable UUID programmeId) {
        return noStore(programmes.verifySource(programmeId));
    }

    @DeleteMapping("/assessments/{assessmentId}")
    public ResponseEntity<Void> discardAssessment(@PathVariable UUID assessmentId) {
        programmes.discardAssessment(assessmentId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/promises/{promiseId}")
    public ResponseEntity<Void> discardPromise(@PathVariable UUID promiseId) {
        programmes.discardPromise(promiseId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/{programmeId}")
    public ResponseEntity<Void> discardProgramme(@PathVariable UUID programmeId) {
        programmes.discardProgramme(programmeId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private IngestionJobResult withJob(IngestionResult result) {
        ProgrammeAssessmentJob job = assessmentJobs.start(result.programme().id());
        return new IngestionJobResult(
                result.programme(), result.cacheHit(), result.extracted(), result.warnings(), job);
    }

    public record IngestionJobResult(
            AdminProgrammeView programme,
            boolean cacheHit,
            boolean extracted,
            List<String> warnings,
            ProgrammeAssessmentJob job) {
    }
}
