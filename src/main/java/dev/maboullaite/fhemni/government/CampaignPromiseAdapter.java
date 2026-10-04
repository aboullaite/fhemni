package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import dev.maboullaite.fhemni.programme.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Read-only projection: campaign feasibility is deliberately not a delivery assessment. */
@Component
public class CampaignPromiseAdapter {
    private final PartyProgrammeRepository programmes;
    private final PolicyTopicRepository topics;
    private final ProgrammeRelationships relationships;
    public CampaignPromiseAdapter(PartyProgrammeRepository programmes,PolicyTopicRepository topics,ProgrammeRelationships relationships){this.programmes=programmes;this.topics=topics;this.relationships=relationships;}
    public List<CommitmentView> list(String topic,String party) {
        var result=new ArrayList<CommitmentView>();
        for(var programme:programmes.findAll()) {
            if(programme.status()!=EditorialStatus.PUBLISHED||party!=null&&!party.equals(programme.partyCode()))continue;
            var promises=programmes.findPromises(programme.id(),true);
            var mappings=topics.findPromiseTopics(promises.stream().map(PartyPromise::id).toList());
            for(var promise:promises) {
                var codes=mappings.getOrDefault(promise.id(),List.of()).stream().flatMap(mapping->java.util.stream.Stream.of(mapping.code(),mapping.broadCode())).filter(java.util.Objects::nonNull).distinct().toList();
                if(topic!=null&&!codes.contains(topic))continue;
                var source=new Evidence(programme.id(),programme.sourceUrl(),promise.promiseText(),promise.sourceLocator(),programme.sourceLanguage(),null,programme.sourceRetrievedAt(),programme.sourceSha256());
                result.add(new CommitmentView(promise.id(),"campaign-"+promise.slug(),Origin.CAMPAIGN,
                    new LocalText(promise.title().ar(),promise.title().fr(),promise.title().en()),promise.promiseText(),programme.sourceLanguage(),
                    new LocalText(programme.partyCode(),programme.partyCode(),programme.partyCode()),promise.id(),codes,null,null,null,null,null,List.of(),List.of(source),List.of(),List.of()));
            }
        }
        return relationships.projectAll(result);
    }
}
