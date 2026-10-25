package dev.maboullaite.fhemni.memberanalysis;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import dev.maboullaite.fhemni.analysis.*;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.model.*;
import dev.maboullaite.fhemni.government.GovernmentViews.Page;

@Service
public class MemberAnalysisService {
    private final MemberAnalysisRepository repository;private final MemberAnalysisAccess access;private final VideoAnalysisRunner runner;private final VideoIntelligenceGateway gateway;private final AiUsageGuard usage;private final JdbcClient jdbc;private final AnalysisRevisionRepository revisions;
    public MemberAnalysisService(MemberAnalysisRepository repository,MemberAnalysisAccess access,VideoAnalysisRunner runner,VideoIntelligenceGateway gateway,AiUsageGuard usage,JdbcClient jdbc,AnalysisRevisionRepository revisions){this.repository=repository;this.access=access;this.runner=runner;this.gateway=gateway;this.usage=usage;this.jdbc=jdbc;this.revisions=revisions;}
    public boolean compatible(MemberAnalysisRepository.Claim claim){return gateway.live()&&Objects.equals(claim.model(),gateway.model())&&Objects.equals(claim.promptVersion(),gateway.promptVersion())&&Objects.equals(claim.factCheckModel(),gateway.factCheckModel())&&Objects.equals(claim.factCheckPromptVersion(),gateway.factCheckPromptVersion())&&Objects.equals(claim.credentialVersion(),gateway.credentialVersion());}
    public void process(MemberAnalysisRepository.Claim claim){if(!repository.leaseValid(claim))return;if(!compatible(claim)){usage.failed(new AiUsageGuard.Reservation(claim.usageReservationId()));repository.finish(claim,null,null,"CONFIGURATION_CHANGED");return;}var job=repository.owner(claim.id(),claim.userId());try{var result=runner.run(new VideoAnalysisRunner.RunnerInput(job.id(),job.videoUrl(),job.videoId(),OutputLanguage.fromCode(job.language())),event->{if(!repository.leaseValid(claim))throw new IllegalStateException("Lease ended");},new AiUsageGuard.Reservation(claim.usageReservationId()));repository.finish(claim,result.report(),result.providerInteractionId(),null);}catch(RuntimeException failure){repository.finish(claim,null,null,"PROCESSING_FAILED");}}
    @Transactional public void requestPublication(UUID id,UUID user,boolean confirmed){repository.lockOwner(user);var job=access.requireOwner(id,user);if(!confirmed)throw new IllegalArgumentException("Confirm the separate editorial publication request.");if(!job.state().equals("READY"))throw new IllegalStateException("Wait for a completed report.");if(!publicationStatus(id,user).equals("PRIVATE"))return;jdbc.sql("INSERT INTO member_analysis_publication_requests(id,job_id,state,requested_at) VALUES(:id,:job,'REQUESTED',:now)").param("id",UUID.randomUUID()).param("job",id).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();}
    public String publicationStatus(UUID id,UUID user){access.requireOwner(id,user);return jdbc.sql("SELECT state FROM member_analysis_publication_requests WHERE job_id=:id").param("id",id).query(String.class).optional().orElse("PRIVATE");}
    public record PublicationRequest(UUID id,UUID jobId,String state,Instant requestedAt,String reviewNote,UUID derivativeId) {}
    public record PublicationQueueEntry(UUID id,UUID jobId,String state,Instant requestedAt,String reviewNote,UUID derivativeId,String title,String videoUrl,String publicPath) {}
    public record ReviewView(PublicationRequest request,MemberJob report) {}
    public Page<PublicationQueueEntry> requests(int page){
        if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");
        var now=Instant.now().atOffset(ZoneOffset.UTC);
        String visible=" FROM member_analysis_publication_requests r WHERE EXISTS (SELECT 1 FROM member_analysis_jobs j WHERE j.id=r.job_id AND j.expires_at>:now)";
        long total=jdbc.sql("SELECT COUNT(*)"+visible).param("now",now).query(Long.class).single();
        var requests=jdbc.sql("SELECT id,job_id,state,requested_at,review_note,derivative_id"+visible+" ORDER BY requested_at DESC,id LIMIT 10 OFFSET :offset")
            .param("now",now).param("offset",(page-1)*10).query(PublicationRequest.class).list();
        var entries=requests.stream().map(value->{
            var identity=repository.identity(value.jobId()).orElseThrow();
            var job=repository.owner(value.jobId(),identity.userId());
            String title=job.report()!=null&&job.report().title()!=null?job.report().title():job.videoId();
            String publicPath=value.derivativeId()==null?null:jdbc.sql("SELECT slug FROM catalog_videos WHERE published_analysis_id=:id AND status='PUBLISHED'")
                .param("id",value.derivativeId()).query(String.class).optional().map(slug->"/videos/"+slug).orElse(null);
            return new PublicationQueueEntry(value.id(),value.jobId(),value.state(),value.requestedAt(),value.reviewNote(),value.derivativeId(),title,job.videoUrl(),publicPath);
        }).toList();
        return new Page<>(entries,page,10,total);
    }
    public ReviewView reviewView(UUID requestId){var request=request(requestId,false);var identity=repository.identity(request.jobId()).orElseThrow();return new ReviewView(request,repository.owner(request.jobId(),identity.userId()));}
    private PublicationRequest request(UUID id,boolean lock){return jdbc.sql("SELECT id,job_id,state,requested_at,review_note,derivative_id FROM member_analysis_publication_requests WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query(PublicationRequest.class).optional().orElseThrow(()->new NoSuchElementException("Publication request not found."));}
    @Transactional public void review(UUID id,UUID actor,boolean accept,String note){var value=request(id,true);if(!value.state().equals("REQUESTED"))throw new IllegalStateException("Request is no longer awaiting review.");if(note==null||note.isBlank()||note.length()>4000)throw new IllegalArgumentException("Write a review note under 4000 characters.");jdbc.sql("UPDATE member_analysis_publication_requests SET state=:state,reviewer_id=:actor,review_note=:note,reviewed_at=:now WHERE id=:id").param("state",accept?"REVIEWED":"REJECTED").param("actor",actor).param("note",note).param("now",Instant.now().atOffset(ZoneOffset.UTC)).param("id",id).update();}
    @Transactional
    public UUID publish(UUID id, UUID actor) {
        var value = request(id, true);
        if (!value.state().equals("REVIEWED")) {
            throw new IllegalStateException("Review the requested report before publication.");
        }
        var identity = repository.identity(value.jobId()).orElseThrow();
        var job = repository.owner(value.jobId(), identity.userId());
        if (!job.state().equals("READY") || job.report() == null) {
            throw new IllegalStateException("Completed report unavailable.");
        }
        UUID derivative = UUID.randomUUID();
        var snapshot = new AnalysisSnapshot(
                derivative, job.videoUrl(), job.videoId(), OutputLanguage.fromCode(job.language()),
                AnalysisStatus.QUEUED, 0, "Editorially reviewed member submission", false,
                Instant.now(), null, null, List.of(), false, null);
        revisions.create(snapshot, identity.model(), identity.promptVersion(), identity.factCheckModel(),
                identity.factCheckPromptVersion(), identity.credentialVersion());
        if (revisions.complete(derivative, job.report(), null) != 1) {
            throw new IllegalStateException("Could not persist the reviewed report.");
        }
        revisions.publish(derivative, actor);
        jdbc.sql("UPDATE member_analysis_publication_requests SET state='PUBLISHED',derivative_id=:derivative WHERE id=:id")
                .param("id", id)
                .param("derivative", derivative)
                .update();
        return derivative;
    }
}
