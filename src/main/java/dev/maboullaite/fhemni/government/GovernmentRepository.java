package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Repository
public class GovernmentRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    public GovernmentRepository(JdbcClient jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}
    public record Revision(UUID id,UUID subjectId,String mandateId,SubjectType subjectType,String slug,
            int revisionNumber,long rowVersion,RevisionState status,String payload,UUID authorId,
            UUID reviewerId,String reason,Instant createdAt,Instant reviewedAt,Instant publishedAt) {}

    public void ensureMandate(String id) {
        if(!"2026-2031".equals(id))throw new IllegalArgumentException("Unknown mandate.");
        jdbc.sql("INSERT INTO government_mandates(id,created_at) SELECT :id,:now WHERE NOT EXISTS (SELECT 1 FROM government_mandates WHERE id=:id)")
            .param("id",id).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();
    }
    @Transactional
    public Revision createDraft(String mandateId,SubjectType type,String slug,Object payload,UUID author,String reason) {
        if(author==null||type==null)throw new IllegalArgumentException("An author and subject type are required.");
        GovernmentValidation.text(reason,"revision reason",4000);
        if(slug==null||!slug.matches("[a-z0-9][a-z0-9-]{0,159}"))throw new IllegalArgumentException("Invalid subject slug.");
        if(type==SubjectType.MANDATE&&(!(payload instanceof MandateView mandate)||!mandateId.equals(mandate.id())||!slug.equals(mandateId)))
            throw new IllegalArgumentException("Mandate identifiers must agree.");
        if(type==SubjectType.COMMITMENT&&!(payload instanceof CommitmentView))throw new IllegalArgumentException("Expected a typed commitment.");
        ensureMandate(mandateId);
        // Serialize draft numbering for one mandate across application instances.
        jdbc.sql("SELECT id FROM government_mandates WHERE id=:id FOR UPDATE").param("id",mandateId).query(String.class).single();
        var subject=jdbc.sql("SELECT id FROM government_subjects WHERE subject_type=:type AND slug=:slug AND mandate_id=:mandate")
            .param("type",type.name()).param("slug",slug).param("mandate",mandateId).query(UUID.class).optional();
        UUID subjectId=subject.orElseGet(UUID::randomUUID);
        if(subject.isEmpty())jdbc.sql("INSERT INTO government_subjects(id,mandate_id,subject_type,slug) VALUES(:id,:mandate,:type,:slug)")
            .param("id",subjectId).param("mandate",mandateId).param("type",type.name()).param("slug",slug).update();
        int next=jdbc.sql("SELECT COALESCE(MAX(revision_number),0)+1 FROM government_revisions WHERE subject_id=:id")
            .param("id",subjectId).query(Integer.class).single();
        UUID id=UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO government_revisions(id,subject_id,revision_number,status,payload,author_id,reason,created_at)
            VALUES(:id,:subject,:version,'DRAFT',:payload,:author,:reason,:now)
            """).param("id",id).param("subject",subjectId).param("version",next)
            .param("payload",encode(payload)).param("author",author).param("reason",reason).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();
        return revision(id).orElseThrow();
    }
    public Optional<Revision> revision(UUID id) {
        return jdbc.sql("""
            SELECT r.id,r.subject_id,s.mandate_id,s.subject_type,s.slug,r.revision_number,r.row_version,r.status,
                   r.payload,r.author_id,r.reviewer_id,r.reason,r.created_at,r.reviewed_at,r.published_at
            FROM government_revisions r JOIN government_subjects s ON s.id=r.subject_id WHERE r.id=:id
            """).param("id",id).query(Revision.class).optional();
    }
    public <T> Optional<T> published(SubjectType type,String slug,Class<T> recordType) {
        return jdbc.sql("""
            SELECT r.payload FROM government_publications p
            JOIN government_revisions r ON r.id=p.revision_id AND r.subject_id=p.subject_id
            JOIN government_subjects s ON s.id=p.subject_id
            WHERE s.subject_type=:type AND s.slug=:slug AND r.status='PUBLISHED'
            """).param("type",type.name()).param("slug",slug).query(String.class).optional().map(json->decode(json,recordType));
    }
    public String encode(Object value) {return mapper.writeValueAsString(value);}
    public <T> T decode(String json,Class<T> type) {
        return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
    }
}
