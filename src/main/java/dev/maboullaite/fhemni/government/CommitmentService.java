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
    public CommitmentService(CommitmentRepository repository,CampaignPromiseAdapter campaigns){this.repository=repository;this.campaigns=campaigns;}
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
        return (slug.startsWith("campaign-")?campaigns.list(null,null):repository.published("2026-2031")).stream()
            .filter(row->row.slug().equals(slug)).findFirst().orElseThrow(()->new NoSuchElementException("Commitment not found."));
    }
    private String blank(String value){return value==null||value.isBlank()?null:value;}
}
