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
        if(!referenced)referenced=repository.publishedVersions(mandate,SubjectType.MEASURE).stream().map(json->repository.decode(json,MeasureView.class)).anyMatch(view->view.evidence().stream().anyMatch(source->source.id().equals(id)));
        if(!referenced)referenced=repository.publishedVersions(mandate,SubjectType.LINK).stream().map(json->repository.decode(json,RelationshipView.class)).anyMatch(view->view.evidence().stream().anyMatch(source->source.id().equals(id)));
        if(!referenced)throw new java.util.NoSuchElementException("Published source not found.");
        return evidence.find(mandate,id).orElseThrow();
    }
    public Page<RevisionSummary> history(String mandate,SubjectType type,String slug,int page) {
        if(!"2026-2031".equals(mandate))throw new java.util.NoSuchElementException("Unknown mandate.");
        if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");
        var rows=repository.publicHistory(type,slug);int start=Math.min((page-1)*10,rows.size());
        return new Page<>(rows.subList(start,Math.min(start+10,rows.size())),page,10,rows.size());
    }
    public PublicRevision revision(java.util.UUID id) {
        var revision=repository.revision(id).filter(row->row.publishedAt()!=null&&java.util.List.of(RevisionState.PUBLISHED,RevisionState.SUPERSEDED).contains(row.status())).orElseThrow(()->new java.util.NoSuchElementException("Published version not found."));
        Object snapshot=switch(revision.subjectType()) {
            case MANDATE->repository.decode(revision.payload(),MandateView.class);
            case COMMITMENT->repository.decode(revision.payload(),CommitmentView.class);
            case MEASURE->repository.decode(revision.payload(),MeasureView.class);
            case LINK->repository.decode(revision.payload(),RelationshipView.class);
        };
        java.util.List<Evidence> sources;
        if(snapshot instanceof MandateView mandate) {
            var ids=new java.util.LinkedHashSet<java.util.UUID>();mandate.membership().forEach(member->ids.addAll(member.evidenceIds()));
            if(mandate.programme()!=null)ids.addAll(mandate.programme().evidenceIds());
            sources=ids.stream().map(sourceId->evidence.find(revision.mandateId(),sourceId).orElseThrow()).toList();
        } else if(snapshot instanceof CommitmentView commitment)sources=commitment.evidence();
        else if(snapshot instanceof MeasureView measure)sources=measure.evidence();
        else sources=((RelationshipView)snapshot).evidence();
        var explanation=repository.history(revision.subjectId()).stream().filter(row->row.id().equals(id)).findFirst().orElseThrow().explanation();
        return new PublicRevision(id,revision.subjectType(),revision.slug(),revision.revisionNumber(),revision.publishedAt(),explanation,snapshot,sources);
    }
}
