package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Read-only reverse projection. Later independent reviews supersede embedded legacy links. */
@Component
public class ProgrammeRelationships {
    private final GovernmentRepository repository;
    public ProgrammeRelationships(GovernmentRepository repository){this.repository=repository;}
    public record ReviewedLink(CommitmentLink link,List<Evidence> evidence) {}
    public List<ReviewedLink> all() {
        var rows=new LinkedHashMap<String,ReviewedLink>();
        for(var json:repository.publishedPayloads("2026-2031",SubjectType.COMMITMENT)) {
            var value=repository.decode(json,CommitmentView.class);
            for(var link:value.links())rows.put(key(link),new ReviewedLink(link,value.evidence()));
        }
        for(var json:repository.publishedPayloads("2026-2031",SubjectType.LINK)) {
            var value=repository.decode(json,RelationshipView.class);
            rows.put(key(value.relationship()),new ReviewedLink(value.relationship(),value.evidence()));
        }
        return List.copyOf(rows.values());
    }
    private String key(CommitmentLink link){return link.campaignPromiseId()+":"+link.programmeVersionId();}
    public CommitmentView project(CommitmentView view) {
        return project(view,all());
    }
    public List<CommitmentView> projectAll(List<CommitmentView> views) {
        var links=all();return views.stream().map(view->project(view,links)).toList();
    }
    private CommitmentView project(CommitmentView view,List<ReviewedLink> links) {
        var matching=links.stream().filter(row->view.origin()==Origin.CAMPAIGN?row.link().campaignPromiseId().equals(view.id()):view.id().equals(row.link().governmentCommitmentId())).toList();
        var sources=new LinkedHashMap<UUID,Evidence>();view.evidence().forEach(source->sources.put(source.id(),source));
        matching.forEach(row->row.evidence().stream().filter(source->row.link().evidenceIds().contains(source.id())).forEach(source->sources.put(source.id(),source)));
        return new CommitmentView(view.id(),view.slug(),view.origin(),view.title(),view.originalText(),view.originalLanguage(),view.author(),view.campaignPromiseId(),view.topicCodes(),view.geographicScope(),view.responsibleBody(),view.target(),view.deadline(),view.deliveryAssessment(),matching.stream().map(ReviewedLink::link).toList(),List.copyOf(sources.values()),view.observations(),view.revisions(),view.programmeContext());
    }
}
