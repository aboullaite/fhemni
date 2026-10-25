package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernmentEditorialService {
    private final GovernmentRepository repository;
    private final GovernmentEvidenceRepository evidence;
    private final GovernmentPublicationEvents events;
    private final JdbcClient jdbc;
    public GovernmentEditorialService(GovernmentRepository repository,GovernmentEvidenceRepository evidence,GovernmentPublicationEvents events,JdbcClient jdbc) {
        this.repository=repository;this.evidence=evidence;this.events=events;this.jdbc=jdbc;
    }
    @Transactional
    public GovernmentRepository.Revision review(UUID id,long expectedVersion,UUID administratorId) {
        requireActor(administratorId);
        var revision=repository.locked(id,expectedVersion,RevisionState.DRAFT);
        validate(revision);
        return repository.markReviewed(revision,administratorId,repository.mandateHash(revision.mandateId()));
    }
    @Transactional
    public GovernmentRepository.Revision publish(UUID id,long expectedVersion,UUID administratorId) {
        requireActor(administratorId);
        var revision=repository.locked(id,expectedVersion,RevisionState.REVIEWED);
        if(!repository.reviewContext(id).equals(repository.mandateHash(revision.mandateId())))throw new IllegalStateException("The mandate changed after review. Prepare and review a fresh revision.");
        Object value=validate(revision); // Repeat under the mandate lock: coverage or references may have changed.
        var hash=events.materialHash(value);
        boolean changed=repository.currentHash(revision.subjectId()).filter(hash::equals).isEmpty();
        var now=Instant.now();
        var result=repository.markPublished(revision,administratorId,hash,now);
        if(value instanceof MandateView mandate)repository.retainMandateHistory(revision,mandate);
        if(changed) {
            LocalText title=value instanceof MandateView mandate?mandate.label():value instanceof MeasureView measure?measure.title():value instanceof RelationshipView link?link.title():((CommitmentView)value).title();
            List<String> topics=value instanceof CommitmentView commitment?commitment.topicCodes():List.of();
            List<String> regions=GovernmentPublicationEvents.regionCodes(value);
            String publicId=value instanceof MandateView mandate?mandate.id():value instanceof MeasureView measure?measure.id().toString():value instanceof RelationshipView link?link.relationship().campaignPromiseId().toString():((CommitmentView)value).id().toString();
            events.insert(revision.mandateId(),revision.subjectId(),new PublishedUpdate(UUID.randomUUID(),revision.subjectType().name(),publicId,revision.id(),hash,topics,regions,title,null,now));
        }
        return result;
    }
    private Object validate(GovernmentRepository.Revision revision) {
        String mandateId=revision.mandateId();
        var current=repository.published(SubjectType.MANDATE,mandateId,MandateView.class).orElse(null);
        if(revision.subjectType()==SubjectType.MANDATE) {
            var value=repository.decode(revision.payload(),MandateView.class);
            GovernmentValidation.mandate(value,current);
            for(var member:value.membership()) {
                member.evidenceIds().forEach(id->evidence.require(mandateId,id));
                if(member.partyCode()!=null&&!exists("SELECT COUNT(*) FROM political_parties WHERE code=:id",member.partyCode()))throw new IllegalArgumentException("Unknown party code.");
            }
            if(value.programme()!=null) {
                value.programme().evidenceIds().forEach(id->evidence.require(mandateId,id));
                if(value.programme().publishedAt()==null||value.programme().publishedAt().isAfter(java.time.LocalDate.now(java.time.ZoneOffset.UTC)))throw new IllegalArgumentException("A source publication date is required.");
                if(current!=null&&current.programme()!=null) {
                    var before=current.programme();var after=value.programme();
                    if(before.id().equals(after.id())&&!before.equals(after)||!before.id().equals(after.id())&&after.version()<=before.version())throw new IllegalArgumentException("Changed programme documents require a new, increasing version.");
                }
            }
            return value;
        }
        if(revision.subjectType()==SubjectType.MEASURE){
            var measure=repository.decode(revision.payload(),MeasureView.class);GovernmentValidation.measure(measure);validateScope(measure.scope());
            var previous=repository.published(SubjectType.MEASURE,revision.slug(),MeasureView.class);
            if(previous.isPresent()&&!previous.get().id().equals(measure.id()))throw new IllegalArgumentException("A measure keeps its stable identifier.");
            if(repository.publishedPayloads(mandateId,SubjectType.MEASURE).stream().map(json->repository.decode(json,MeasureView.class)).anyMatch(other->other.id().equals(measure.id())&&!other.slug().equals(measure.slug())))throw new IllegalArgumentException("Measure identifier already published.");
            for(var source:measure.evidence())if(!evidence.find(mandateId,source.id()).filter(source::equals).isPresent())throw new IllegalArgumentException("Save the exact source evidence first.");
            return measure;
        }
        if(revision.subjectType()==SubjectType.LINK) {
            var value=repository.decode(revision.payload(),RelationshipView.class);
            if(value.id()==null)throw new IllegalArgumentException("A relationship needs a stable identifier.");
            GovernmentValidation.translations(value.title());
            if(current==null||current.programme()==null||current.lifecycle()!=Lifecycle.PROGRAMME_PUBLISHED)throw new IllegalArgumentException("Publish the sourced programme before reviewing its links.");
            var link=value.relationship();GovernmentValidation.link(link,current.reviewCoverage());
            String canonical="link-"+link.campaignPromiseId()+"-"+link.programmeVersionId();
            if(!canonical.equals(value.slug()))throw new IllegalArgumentException("One relationship per campaign promise and programme version is required.");
            if(!link.programmeVersionId().equals(current.programme().id()))throw new IllegalArgumentException("Review links against the current programme version.");
            if(!exists("SELECT COUNT(*) FROM party_promises p JOIN party_programmes g ON p.programme_id=g.id WHERE p.id=:id AND p.editorial_status='PUBLISHED' AND g.editorial_status='PUBLISHED'",link.campaignPromiseId()))throw new IllegalArgumentException("Link only published campaign promises.");
            if(link.relation()==Relation.NOT_IDENTIFIED&&link.governmentCommitmentId()!=null)throw new IllegalArgumentException("An absent promise has no government commitment target.");
            if(link.governmentCommitmentId()!=null&&!repository.publishedPayloads(mandateId,SubjectType.COMMITMENT).stream().map(json->repository.decode(json,CommitmentView.class)).anyMatch(row->row.id().equals(link.governmentCommitmentId())))throw new IllegalArgumentException("Link only published government commitments.");
            var previous=repository.published(SubjectType.LINK,revision.slug(),RelationshipView.class);
            if(previous.isPresent()&&!previous.get().id().equals(value.id()))throw new IllegalArgumentException("A relationship keeps its stable identifier.");
            if(value.evidence()==null||!value.evidence().stream().map(Evidence::id).toList().containsAll(link.evidenceIds()))throw new IllegalArgumentException("Attach supporting relationship evidence.");
            for(var source:value.evidence())if(!evidence.find(mandateId,source.id()).filter(source::equals).isPresent())throw new IllegalArgumentException("Save the exact source evidence first.");
            return value;
        }
        if(revision.subjectType()!=SubjectType.COMMITMENT)throw new IllegalArgumentException("Unsupported publication type.");
        var value=repository.decode(revision.payload(),CommitmentView.class);
        GovernmentValidation.commitment(value);
        if(current==null||current.lifecycle()!=Lifecycle.PROGRAMME_PUBLISHED)throw new IllegalArgumentException("Publish the sourced government programme before its commitments.");
        var previous=repository.published(SubjectType.COMMITMENT,revision.slug(),CommitmentView.class);
        if(previous.isPresent()&&!previous.get().id().equals(value.id()))throw new IllegalArgumentException("A commitment keeps its stable identifier.");
        if(repository.publishedPayloads(mandateId,SubjectType.COMMITMENT).stream().map(json->repository.decode(json,CommitmentView.class)).anyMatch(other->other.id().equals(value.id())&&!other.slug().equals(value.slug())))throw new IllegalArgumentException("A commitment identifier is already published under another slug.");
        for(var source:value.evidence()) {
            var saved=evidence.find(mandateId,source.id()).orElseThrow(()->new IllegalArgumentException("Save the source evidence first."));
            if(!saved.equals(source))throw new IllegalArgumentException("Evidence must match its immutable saved record.");
        }
        for(var topic:value.topicCodes())if(!exists("SELECT COUNT(*) FROM policy_topics WHERE code=:id AND active=TRUE",topic))throw new IllegalArgumentException("Unknown policy topic.");
        validateScope(value.geographicScope());
        if(value.deliveryAssessment()!=null)requireAttached(value,value.deliveryAssessment().evidenceIds());
        for(var observation:value.observations()){requireAttached(value,observation.evidenceIds());validateScope(observation.scope());}
        for(var link:value.links()) {
            GovernmentValidation.link(link,current.reviewCoverage());
            if(!link.programmeVersionId().equals(current.programme().id()))throw new IllegalArgumentException("Review links against the current programme version.");
            if(link.governmentCommitmentId()!=null&&!link.governmentCommitmentId().equals(value.id()))throw new IllegalArgumentException("This link must refer to this government commitment.");
            if(!exists("SELECT COUNT(*) FROM party_promises p JOIN party_programmes g ON p.programme_id=g.id WHERE p.id=:id AND p.editorial_status='PUBLISHED' AND g.editorial_status='PUBLISHED'",link.campaignPromiseId()))throw new IllegalArgumentException("Link only published campaign promises.");
            requireAttached(value,link.evidenceIds());
        }
        return value;
    }
    private boolean exists(String sql,Object id){return jdbc.sql(sql).param("id",id).query(Long.class).single()>0;}
    private void validateScope(Scope scope) {
        if(scope.level().equals("REGION")&&!exists("SELECT COUNT(*) FROM election_regions WHERE code=:id",scope.code()))throw new IllegalArgumentException("Unknown canonical region.");
    }
    private void requireAttached(CommitmentView value,List<UUID> ids) {
        if(!value.evidence().stream().map(Evidence::id).toList().containsAll(ids))throw new IllegalArgumentException("Assessment and observation evidence must be attached to the commitment.");
    }
    private void requireActor(UUID actor){if(actor==null)throw new IllegalArgumentException("An authenticated editor is required.");}
}
