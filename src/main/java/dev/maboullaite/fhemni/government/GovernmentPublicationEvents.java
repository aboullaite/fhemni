package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class GovernmentPublicationEvents {
    private final JdbcClient jdbc;
    private final GovernmentRepository repository;
    private final ObjectMapper mapper;
    public GovernmentPublicationEvents(JdbcClient jdbc,GovernmentRepository repository,ObjectMapper mapper){this.jdbc=jdbc;this.repository=repository;this.mapper=mapper;}
    public String materialHash(Object value) {
        return GovernmentValidation.sha256(mapper.writeValueAsString(material(mapper.convertValue(value,Object.class))).getBytes(StandardCharsets.UTF_8));
    }
    public static List<String> regionCodes(Object value) {
        var scopes=new java.util.ArrayList<Scope>();
        if(value instanceof CommitmentView commitment){scopes.add(commitment.geographicScope());commitment.observations().forEach(row->scopes.add(row.scope()));}
        if(value instanceof MeasureView measure)scopes.add(measure.scope());
        return scopes.stream().filter(scope->scope!=null&&"REGION".equals(scope.level())).map(Scope::code).distinct().sorted().toList();
    }
    private Object material(Object value) {
        if(value instanceof Map<?,?> map) {
            var result=new TreeMap<String,Object>();
            map.forEach((key,item)->{if(!List.of("verifiedAt","retrievedAt","reviewedAt","revisions","recentUpdates").contains(key.toString()))result.put(key.toString(),material(item));});
            return result;
        }
        if(value instanceof List<?> list)return list.stream().map(this::material).toList();
        return value;
    }
    public void insert(String mandate,java.util.UUID subjectId,PublishedUpdate update) {
        jdbc.sql("INSERT INTO government_publication_events(id,mandate_id,revision_id,subject_id,material_hash,payload,published_at) VALUES(:id,:mandate,:revision,:subject,:hash,:payload,:now)")
            .param("id",update.id()).param("mandate",mandate).param("revision",update.revisionId()).param("subject",subjectId)
            .param("hash",update.materialHash()).param("payload",repository.encode(update)).param("now",update.publishedAt().atOffset(ZoneOffset.UTC)).update();
    }
    public List<PublishedUpdate> latest(String mandate,int size) {
        return jdbc.sql("SELECT payload FROM government_publication_events WHERE mandate_id=:mandate ORDER BY published_at DESC,id LIMIT :limit")
            .param("mandate",mandate).param("limit",Math.clamp(size,1,100)).query(String.class).list().stream().map(json->repository.decode(json,PublishedUpdate.class)).toList();
    }
}
