package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reviewed discovery metadata. Never changes source snapshots or delivery assessments. */
@Service
public class ProgrammeDiscovery {
    private record Entry(String title, String anchor, List<String> topics, List<ProgrammePlace> places) {
        boolean matches(CommitmentView row) {
            return row.origin()==Origin.GOVERNMENT && row.title()!=null && title.equals(row.title().ar())
                    && anchor.equals(row.originalText()) && row.programmeContext()!=null;
        }
    }
    private final List<DiscoveryTopic> topics;
    private final Map<String,Entry> topicEntries;
    private final Map<String,Entry> placeEntries;

    @Autowired public ProgrammeDiscovery(ObjectMapper mapper) throws IOException {
        this(read(mapper,"programme-topics.json"),read(mapper,"programme-geography.json"));
    }
    private static JsonNode read(ObjectMapper mapper,String file) throws IOException {
        try(var stream=new ClassPathResource("government/"+file).getInputStream()){return mapper.readTree(stream);}
    }
    ProgrammeDiscovery(JsonNode topicIndex,JsonNode geographyIndex) {
        require(topicIndex.path("version").asInt()==1 && geographyIndex.path("version").asInt()==1,"Discovery version");
        var catalog=new ArrayList<DiscoveryTopic>();var codes=new HashSet<String>();
        for(var topic:topicIndex.path("topics")) {
            var code=required(topic,"code");require(codes.add(code),"Duplicate topic");
            catalog.add(new DiscoveryTopic(code,local(topic.path("label"))));
        }
        require(!catalog.isEmpty(),"Empty topic catalog");topics=List.copyOf(catalog);
        var tags=new LinkedHashMap<String,Entry>();
        for(var row:topicIndex.path("entries")) {
            var assigned=new ArrayList<String>();for(var code:row.path("topicCodes")) {
                require(codes.contains(code.asText())&&!assigned.contains(code.asText()),"Unknown or duplicate topic");assigned.add(code.asText());
            }
            require(!assigned.isEmpty(),"Empty assignment");
            require(tags.put(required(row,"slug"),new Entry(required(row,"titleAr"),required(row,"sourceAnchor"),List.copyOf(assigned),List.of()))==null,"Duplicate assignment");
        }
        topicEntries=Map.copyOf(tags);
        var locations=new LinkedHashMap<String,Entry>();
        for(var row:geographyIndex.path("entries")) {
            var places=new ArrayList<ProgrammePlace>();
            for(var place:row.path("places")) {
                int page=place.path("printedPage").asInt();require(page>0&&page<=127,"Invalid programme page");
                places.add(new ProgrammePlace(required(place,"regionCode"),local(place.path("place")),required(place,"sourceExcerpt"),page));
            }
            require(!places.isEmpty(),"Empty place assignment");
            require(locations.put(required(row,"slug"),new Entry(required(row,"titleAr"),required(row,"sourceAnchor"),List.of(),List.copyOf(places)))==null,"Duplicate geography entry");
        }
        placeEntries=Map.copyOf(locations);
    }
    private static String required(JsonNode node,String key){String value=node.path(key).asText("");require(!value.isBlank(),"Missing "+key);return value;}
    private static LocalText local(JsonNode node){return new LocalText(required(node,"ar"),required(node,"fr"),required(node,"en"));}
    private static void require(boolean valid,String message){if(!valid)throw new IllegalArgumentException(message);}
    public List<DiscoveryTopic> topics(){return topics;}
    public CommitmentView project(CommitmentView row) {
        var entry=topicEntries.get(row.slug());if(entry==null||!entry.matches(row))return row;
        return new CommitmentView(row.id(),row.slug(),row.origin(),row.title(),row.originalText(),row.originalLanguage(),row.author(),row.campaignPromiseId(),entry.topics(),row.geographicScope(),row.responsibleBody(),row.target(),row.deadline(),row.deliveryAssessment(),row.links(),row.evidence(),row.observations(),row.revisions(),row.programmeContext());
    }
    public List<ProgrammePlace> places(CommitmentView row) {
        var entry=placeEntries.get(row.slug());if(entry==null||!entry.matches(row))return List.of();
        var pages=row.programmeContext().printedPages();
        return pages!=null&&entry.places().stream().allMatch(place->pages.contains(place.printedPage()))?entry.places():List.of();
    }
}
