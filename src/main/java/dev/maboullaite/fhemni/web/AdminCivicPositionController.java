package dev.maboullaite.fhemni.web;

import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.civic.CivicPartyPositionService;
import dev.maboullaite.fhemni.civic.CivicPartyPositionService.AdminPositionRow;
import dev.maboullaite.fhemni.civic.CivicPartyPositionService.PositionDraft;
import dev.maboullaite.fhemni.civic.PartyPositionStance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/civic-positions")
public class AdminCivicPositionController {

    private final CivicPartyPositionService positionService;

    AdminCivicPositionController(CivicPartyPositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping("/{editionId}")
    public ResponseEntity<List<AdminPositionRow>> list(@PathVariable UUID editionId) {
        return ResponseEntity.ok(positionService.adminList(editionId));
    }

    @PutMapping("/{editionId}")
    public ResponseEntity<Void> upsert(@PathVariable UUID editionId,
                                       @RequestBody PositionRequest request) {
        positionService.upsert(editionId, request.questionKey(), request.partyCode(),
                request.stance(),
                request.evidenceSummaryAr(), request.evidenceSummaryFr(), request.evidenceSummaryEn(),
                request.reviewerNote());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{editionId}/batch")
    public ResponseEntity<BatchResult> batchUpsert(@PathVariable UUID editionId,
                                                   @RequestBody List<PositionRequest> requests) {
        List<PositionDraft> drafts = requests.stream()
                .map(r -> new PositionDraft(r.questionKey(), r.partyCode(), r.stance(),
                        r.evidenceSummaryAr(), r.evidenceSummaryFr(), r.evidenceSummaryEn(),
                        r.reviewerNote()))
                .toList();
        int created = positionService.batchUpsert(editionId, drafts);
        return ResponseEntity.ok(new BatchResult(created));
    }

    @PostMapping("/{editionId}/publish-all")
    public ResponseEntity<BatchResult> publishAll(@PathVariable UUID editionId) {
        int published = positionService.publishAllDrafts(editionId);
        return ResponseEntity.ok(new BatchResult(published));
    }

    @PostMapping("/{positionId}/publish")
    public ResponseEntity<Void> publish(@PathVariable UUID positionId) {
        positionService.publish(positionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{positionId}/evidence")
    public ResponseEntity<Void> addEvidence(@PathVariable UUID positionId,
                                            @RequestBody EvidenceRequest request) {
        positionService.addEvidence(positionId,
                request.labelAr(), request.labelFr(), request.labelEn(),
                request.sourceUrl(), request.pageReference(), request.promiseId(),
                request.sortOrder());
        return ResponseEntity.ok().build();
    }

    public record PositionRequest(
            String questionKey,
            String partyCode,
            PartyPositionStance stance,
            String evidenceSummaryAr,
            String evidenceSummaryFr,
            String evidenceSummaryEn,
            String reviewerNote) {
    }

    record EvidenceRequest(
            String labelAr,
            String labelFr,
            String labelEn,
            String sourceUrl,
            String pageReference,
            UUID promiseId,
            int sortOrder) {
    }

    record BatchResult(int created) {
    }
}
