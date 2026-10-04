package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
@Service
public class CommitmentService {
    private final CommitmentRepository repository;
    private final CampaignPromiseAdapter campaigns;
    private final GovernmentRepository revisions;
    private final ProgrammeRelationships relationships;
    public CommitmentService(CommitmentRepository repository,CampaignPromiseAdapter campaigns,GovernmentRepository revisions,ProgrammeRelationships relationships){this.repository=repository;this.campaigns=campaigns;this.revisions=revisions;this.relationships=relationships;}
    public Page<CommitmentView> list(String mandate,String origin,String topic,String party,int page) {
        if(!"2026-2031".equals(mandate))throw new NoSuchElementException("Unknown mandate.");
        if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");
        Origin filter=origin==null||origin.isBlank()?null:Origin.valueOf(origin);
        var rows=new ArrayList<CommitmentView>();
        if(filter!=Origin.GOVERNMENT)rows.addAll(campaigns.list(blank(topic),blank(party)));
        if(filter!=Origin.CAMPAIGN&&blank(party)==null)rows.addAll(repository.published(mandate).stream().filter(row->blank(topic)==null||row.topicCodes().contains(topic)).toList());
        rows.sort(Comparator.comparing(CommitmentView::slug));
        int start=Math.min((page-1)*10,rows.size());
        return new Page<>(rows.subList(start,Math.min(start+10,rows.size())),page,10,rows.size());
    }
    public CommitmentView detail(String slug) {
        var view=repository.published("2026-2031").stream()
            .filter(row->row.slug().equals(slug)||row.id().toString().equals(slug)).findFirst()
            .or(()->campaigns.list(null,null).stream().filter(row->row.slug().equals(slug)||row.id().toString().equals(slug)).findFirst())
            .orElseThrow(()->new NoSuchElementException("Commitment not found."));
        view=relationships.project(view);
        return new CommitmentView(view.id(),view.slug(),view.origin(),view.title(),view.originalText(),view.originalLanguage(),view.author(),view.campaignPromiseId(),view.topicCodes(),view.geographicScope(),view.responsibleBody(),view.target(),view.deadline(),view.deliveryAssessment(),view.links(),view.evidence(),view.observations(),revisions.publicHistory(SubjectType.COMMITMENT,view.slug()));
    }
    public List<CommitmentView> upcoming(String mandate) {
        if(!"2026-2031".equals(mandate))throw new NoSuchElementException("Unknown mandate.");
        var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        return repository.published(mandate).stream().filter(row->row.deadline()!=null&&!row.deadline().isBefore(today))
            .filter(row->row.deliveryAssessment()==null||!List.of(DeliveryState.DELIVERED,DeliveryState.WITHDRAWN).contains(row.deliveryAssessment().state()))
            .sorted(Comparator.comparing(CommitmentView::deadline).thenComparing(CommitmentView::slug)).limit(3).toList();
    }
    private String blank(String value){return value==null||value.isBlank()?null:value;}
}
