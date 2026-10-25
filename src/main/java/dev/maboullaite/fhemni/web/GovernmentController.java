package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.government.GovernmentService;
import dev.maboullaite.fhemni.government.GovernmentViews.MandateView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import dev.maboullaite.fhemni.government.GovernmentViews.*;

@RestController
public class GovernmentController {
    private final GovernmentService service;
    public GovernmentController(GovernmentService service){this.service=service;}
    @GetMapping("/api/catalog/government/{mandateId}")
    public ResponseEntity<MandateView> mandate(@PathVariable String mandateId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.mandate(mandateId));
    }
    @GetMapping("/api/catalog/government/{mandateId}/sources/{id}")
    public ResponseEntity<dev.maboullaite.fhemni.government.GovernmentViews.Evidence> source(@PathVariable String mandateId,@PathVariable java.util.UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.source(mandateId,id));
    }
    @GetMapping("/api/catalog/government/{mandateId}/history")
    public ResponseEntity<Page<RevisionSummary>> history(@PathVariable String mandateId,@RequestParam(defaultValue="MANDATE") SubjectType type,
            @RequestParam(defaultValue="2026-2031") String slug,@RequestParam(defaultValue="1") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.history(mandateId,type,slug,page));
    }
    @GetMapping("/api/catalog/government/revisions/{id}")
    public ResponseEntity<PublicRevision> revision(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.revision(id));
    }
}
