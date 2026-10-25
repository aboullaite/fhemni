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
    private final ProgrammeBudgetEvidence budgetEvidence;
    public CommitmentService(CommitmentRepository repository,CampaignPromiseAdapter campaigns,GovernmentRepository revisions,ProgrammeRelationships relationships,ProgrammeBudgetEvidence budgetEvidence){this.repository=repository;this.campaigns=campaigns;this.revisions=revisions;this.relationships=relationships;this.budgetEvidence=budgetEvidence;}
    public Page<CommitmentView> list(String mandate,String origin,String topic,String party,int page) {
        return list(mandate,origin,topic,party,null,null,page);
    }
    public Page<CommitmentView> list(String mandate,String origin,String topic,String party,String query,String assessment,int page) {
        if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");
        var rows=selected(mandate,origin,topic,party,query,assessment);
        int start=Math.min((page-1)*10,rows.size());
        return new Page<>(rows.subList(start,Math.min(start+10,rows.size())),page,10,rows.size());
    }
    public ProgrammeInsights insights(String mandate,String origin,String topic,String party,String query,String assessment) {
        var rows=selected(mandate,origin,topic,party,query,assessment);
        var counts=new java.util.TreeMap<String,Long>();
        for(var row:rows)for(String code:new java.util.HashSet<>(row.topicCodes()))counts.merge(code,1L,Long::sum);
        var topics=counts.entrySet().stream().map(e->new TopicCount(e.getKey(),e.getValue()))
                .sorted(Comparator.comparingLong(TopicCount::count).reversed().thenComparing(TopicCount::code)).toList();
        var entries=rows.stream().filter(row->row.origin()==Origin.GOVERNMENT&&row.programmeContext()!=null)
                .map(row->new ProgrammeGraphicEntry(row.slug(),row.title(),row.programmeContext())).toList();
        var deliveryCounts=new java.util.TreeMap<String,Long>();
        deliveryCounts.put("UNASSESSED",0L);
        for(var state:DeliveryState.values())deliveryCounts.put(state.name(),0L);
        for(var row:rows)if(row.origin()==Origin.GOVERNMENT) {
            String state=row.deliveryAssessment()==null?"UNASSESSED":row.deliveryAssessment().state().name();
            deliveryCounts.merge(state,1L,Long::sum);
        }
        var budgetEntries=rows.stream().filter(row->row.origin()==Origin.GOVERNMENT&&!row.budgetProposals().isEmpty())
                .map(row->new BudgetProgrammeEntry(row.slug(),row.title(),row.budgetProposals().getFirst().summary()))
                .distinct().toList();
        return new ProgrammeInsights(rows.size(),topics,entries,java.util.Map.copyOf(deliveryCounts),budgetEntries);
    }
    List<CommitmentView> selected(String mandate,String origin,String topic,String party,String query,String assessment) {
        if(!"2026-2031".equals(mandate))throw new NoSuchElementException("Unknown mandate.");
        if(query!=null&&query.length()>200)throw new IllegalArgumentException("Search must be at most 200 characters.");
        if(blank(assessment)!=null&&!List.of("UNASSESSED","REVIEWED").contains(assessment))DeliveryState.valueOf(assessment);
        Origin filter=origin==null||origin.isBlank()?null:Origin.valueOf(origin);
        var rows=new ArrayList<CommitmentView>();
        if(filter!=Origin.GOVERNMENT)rows.addAll(campaigns.list(blank(topic),blank(party)));
        if(filter!=Origin.CAMPAIGN&&blank(party)==null)rows.addAll(repository.published(mandate).stream().filter(row->blank(topic)==null||row.topicCodes().contains(topic)).toList());
        String needle=normalize(query);
        rows.removeIf(row->!needle.isEmpty()&&!searchText(row).contains(needle)
                ||blank(assessment)!=null&&("REVIEWED".equals(assessment)?row.deliveryAssessment()==null
                    :!assessment.equals(row.deliveryAssessment()==null?"UNASSESSED":row.deliveryAssessment().state().name())));
        rows.sort(Comparator.comparing(CommitmentView::slug));
        rows.replaceAll(budgetEvidence::project);
        return rows;
    }
    public CommitmentView detail(String slug) {
        var view=repository.published("2026-2031").stream()
            .filter(row->row.slug().equals(slug)||row.id().toString().equals(slug)).findFirst()
            .or(()->campaigns.list(null,null).stream().filter(row->row.slug().equals(slug)||row.id().toString().equals(slug)).findFirst())
            .orElseThrow(()->new NoSuchElementException("Commitment not found."));
        view=relationships.project(view);
        return budgetEvidence.project(new CommitmentView(view.id(),view.slug(),view.origin(),view.title(),view.originalText(),view.originalLanguage(),view.author(),view.campaignPromiseId(),view.topicCodes(),view.geographicScope(),view.responsibleBody(),view.target(),view.deadline(),view.deliveryAssessment(),view.links(),view.evidence(),view.observations(),revisions.publicHistory(SubjectType.COMMITMENT,view.slug()),view.programmeContext()));
    }
    public List<CommitmentView> upcoming(String mandate) {
        if(!"2026-2031".equals(mandate))throw new NoSuchElementException("Unknown mandate.");
        var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        return repository.published(mandate).stream().filter(row->row.deadline()!=null&&!row.deadline().isBefore(today))
            .filter(row->row.deliveryAssessment()==null||!List.of(DeliveryState.DELIVERED,DeliveryState.WITHDRAWN).contains(row.deliveryAssessment().state()))
            .sorted(Comparator.comparing(CommitmentView::deadline).thenComparing(CommitmentView::slug)).limit(3).map(budgetEvidence::project).toList();
    }
    private String blank(String value){return value==null||value.isBlank()?null:value;}
    private String searchText(CommitmentView row){return normalize(row.slug()+" "+row.title().ar()+" "+row.title().fr()+" "+row.title().en()+" "+row.originalText()+" "+(row.programmeContext()==null?"":row.programmeContext().summaryEn()));}
    private String normalize(String value){return value==null?"":java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}","").replace("ـ","").toLowerCase(java.util.Locale.ROOT).strip();}
}
