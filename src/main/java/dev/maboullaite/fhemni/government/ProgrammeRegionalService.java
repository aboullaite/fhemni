package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ProgrammeRegionalService {
    private final CommitmentService commitments;
    private final ProgrammeDiscovery discovery;
    private final GeographicDeliveryService geography;
    public ProgrammeRegionalService(CommitmentService commitments,ProgrammeDiscovery discovery,GeographicDeliveryService geography) {
        this.commitments=commitments;this.discovery=discovery;this.geography=geography;
    }
    public RegionalProgrammePage list(String mandate,String region,String topic,String query,int page) {
        if(page<1||page>100000)throw new IllegalArgumentException("Invalid page");
        var regions=geography.regions();var codes=new HashSet<String>();regions.forEach(row->codes.add(row.code()));
        if(region!=null&&!region.isBlank()&&!codes.contains(region))throw new IllegalArgumentException("Unknown region");
        var all=commitments.selected(mandate,"GOVERNMENT",topic,null,query,null);
        var linked=new ArrayList<RegionalProgrammeEntry>();var counts=new HashMap<String,Long>();
        for(var row:all) {
            var places=discovery.places(row);
            if(places.isEmpty())continue;
            if(places.stream().anyMatch(place->!codes.contains(place.regionCode())))throw new IllegalStateException("Unknown discovery region");
            linked.add(new RegionalProgrammeEntry(row,places));
            places.stream().map(ProgrammePlace::regionCode).distinct().forEach(code->counts.merge(code,1L,Long::sum));
        }
        var selected=linked.stream().filter(row->region==null||region.isBlank()||row.places().stream().anyMatch(place->region.equals(place.regionCode()))).toList();
        int start=Math.min((page-1)*10,selected.size());
        return new RegionalProgrammePage(selected.subList(start,Math.min(start+10,selected.size())),page,10,selected.size(),
                regions.stream().map(row->new RegionCoverage(row,counts.getOrDefault(row.code(),0L))).toList(),linked.size(),all.size());
    }
}
