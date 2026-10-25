package dev.maboullaite.fhemni.government;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
@Service
public class GeographicDeliveryService {
    private final CommitmentRepository commitments;private final JdbcClient jdbc;
    public GeographicDeliveryService(CommitmentRepository commitments,JdbcClient jdbc){this.commitments=commitments;this.jdbc=jdbc;}
    public List<Scope> regions(){return jdbc.sql("SELECT DISTINCT code,name_ar,name_fr,name_en FROM election_regions ORDER BY code").query((rs,n)->new Scope("REGION",rs.getString("code"),new LocalText(rs.getString("name_ar"),rs.getString("name_fr"),rs.getString("name_en")))).list();}
    public static boolean matchesRegion(Observation row,String region){return region==null||region.isBlank()||"REGION".equals(row.scope().level())&&region.equals(row.scope().code());}
    public Page<GeographicObservation> list(String mandate,String region,int page){
        if(!"2026-2031".equals(mandate))throw new NoSuchElementException("Unknown mandate.");
        if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");
        if(region!=null&&!region.isBlank()&&regions().stream().noneMatch(row->row.code().equals(region)))throw new IllegalArgumentException("Unknown canonical region.");
        // National evidence is returned once in the all-scope view; never expanded into twelve regional achievements.
        var rows=commitments.published(mandate).stream().flatMap(commitment->commitment.observations().stream().filter(row->matchesRegion(row,region)).map(row->new GeographicObservation(commitment.id(),commitment.slug(),commitment.title(),commitment.responsibleBody(),row,commitment.evidence().stream().filter(source->row.evidenceIds().contains(source.id())).toList())))
            .sorted(Comparator.comparing((GeographicObservation row)->row.observation().periodEnd()).reversed().thenComparing(row->row.observation().id())).toList();
        int start=Math.min((page-1)*10,rows.size());return new Page<>(rows.subList(start,Math.min(start+10,rows.size())),page,10,rows.size());
    }
}
