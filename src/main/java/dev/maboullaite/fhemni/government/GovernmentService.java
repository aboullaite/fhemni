package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GovernmentService {
    private final GovernmentRepository repository;
    private final GovernmentPublicationEvents events;
    private final GovernmentEvidenceRepository evidence;
    public GovernmentService(GovernmentRepository repository,GovernmentPublicationEvents events,GovernmentEvidenceRepository evidence){this.repository=repository;this.events=events;this.evidence=evidence;}
    public MandateView mandate(String id) {
        if(!"2026-2031".equals(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var view=repository.published(SubjectType.MANDATE,id,MandateView.class).orElseGet(()->GovernmentViews.pending(id));
        return new MandateView(view.id(),view.label(),view.lifecycle(),view.verifiedAt(),view.membership(),view.programme(),view.reviewCoverage(),events.latest(id,10));
    }
    public Evidence source(String mandate,java.util.UUID id) {
        boolean referenced=repository.publishedVersions(mandate,SubjectType.MANDATE).stream().map(json->repository.decode(json,MandateView.class))
            .anyMatch(view->view.membership().stream().anyMatch(member->member.evidenceIds().contains(id))||view.programme()!=null&&view.programme().evidenceIds().contains(id));
        if(!referenced)referenced=repository.publishedVersions(mandate,SubjectType.COMMITMENT).stream().map(json->repository.decode(json,CommitmentView.class))
            .anyMatch(view->view.evidence().stream().anyMatch(source->source.id().equals(id)));
        if(!referenced)throw new java.util.NoSuchElementException("Published source not found.");
        return evidence.find(mandate,id).orElseThrow();
    }
}
