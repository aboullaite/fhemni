package dev.maboullaite.fhemni.following;
import static dev.maboullaite.fhemni.following.FollowRepository.*;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import dev.maboullaite.fhemni.government.*;
import dev.maboullaite.fhemni.programme.PolicyInterestService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class FollowService {
    private final FollowRepository repository;private final PolicyInterestService topics;private final CommitmentService commitments;private final GeographicDeliveryService geography;
    public FollowService(FollowRepository repository,PolicyInterestService topics,CommitmentService commitments,GeographicDeliveryService geography){this.repository=repository;this.topics=topics;this.commitments=commitments;this.geography=geography;}
    public record FollowEntry(String type,String id,LocalText title,String url) {}
    public List<FollowTarget> targets(UUID user){var all=new ArrayList<>(repository.list(user));topics.preferences(user).forEach(code->all.add(new FollowTarget("TOPIC",code)));return List.copyOf(all);}
    public List<FollowEntry> list(UUID user){return targets(user).stream().map(this::entry).toList();}
    public boolean following(UUID user,FollowTarget target){checkType(target);return targets(user).contains(target);}
    @Transactional public boolean change(UUID user,FollowTarget target,boolean enabled){
        checkType(target);repository.lock(user);
        if(enabled)entry(target); // Only real published targets may be followed.
        if(target.type().equals("TOPIC")){var values=new ArrayList<>(topics.preferences(user));if(enabled&&!values.contains(target.id()))values.add(target.id());else if(!enabled)values.remove(target.id());topics.replacePreferences(user,values);}
        else {if(enabled&&!repository.list(user).contains(target)&&repository.list(user).size()>=100)throw new IllegalArgumentException("Follow at most 100 commitments or regions.");repository.change(user,target,enabled);}
        return following(user,target);
    }
    private void checkType(FollowTarget target){if(!List.of("TOPIC","COMMITMENT","REGION").contains(target.type())||target.id()==null||target.id().length()>160)throw new IllegalArgumentException("Invalid follow target.");}
    private FollowEntry entry(FollowTarget target){return switch(target.type()){
        case "TOPIC"->{var row=topics.topics().stream().filter(t->t.selectable()&&t.code().equals(target.id())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown selectable topic."));yield new FollowEntry(target.type(),target.id(),new LocalText(row.label().ar(),row.label().fr(),row.label().en()),"/government?tab=programme&origin=GOVERNMENT&topic="+row.code());}
        case "REGION"->{var row=geography.regions().stream().filter(r->r.code().equals(target.id())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown canonical region."));yield new FollowEntry(target.type(),target.id(),row.label(),"/government?tab=regions&region="+row.code());}
        case "COMMITMENT"->{var row=commitments.detail(target.id());if(row.origin()!=Origin.GOVERNMENT||!row.id().toString().equals(target.id()))throw new IllegalArgumentException("Follow a published government commitment identifier.");yield new FollowEntry(target.type(),target.id(),row.title(),"/government/commitments/"+row.slug());}
        default->throw new IllegalArgumentException("Invalid follow type.");};}
}
