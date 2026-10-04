package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
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
        if(type==SubjectType.COMMITMENT&&!slug.equals(((CommitmentView)payload).slug()))throw new IllegalArgumentException("Commitment slug must agree.");
        if(type==SubjectType.MEASURE&&(!(payload instanceof MeasureView measure)||!slug.equals(measure.slug())))throw new IllegalArgumentException("Expected a measure with a matching slug.");
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
    public List<Revision> drafts(String mandateId) {
        return jdbc.sql("""
            SELECT r.id,r.subject_id,s.mandate_id,s.subject_type,s.slug,r.revision_number,r.row_version,r.status,
                   r.payload,r.author_id,r.reviewer_id,r.reason,r.created_at,r.reviewed_at,r.published_at
            FROM government_revisions r JOIN government_subjects s ON s.id=r.subject_id
            WHERE s.mandate_id=:mandate ORDER BY r.created_at DESC LIMIT 100
            """).param("mandate",mandateId).query(Revision.class).list();
    }
    public Revision locked(UUID id,long expected,RevisionState state) {
        var initial=revision(id).orElseThrow();
        jdbc.sql("SELECT id FROM government_mandates WHERE id=:id FOR UPDATE").param("id",initial.mandateId()).query(String.class).single();
        var current=revision(id).orElseThrow();
        int latest=jdbc.sql("SELECT MAX(revision_number) FROM government_revisions WHERE subject_id=:id")
            .param("id",current.subjectId()).query(Integer.class).single();
        if(current.rowVersion()!=expected||current.status()!=state||current.revisionNumber()!=latest)
            throw new IllegalStateException("This revision is stale or no longer in the required state. Reload before continuing.");
        return current;
    }
    public Revision markReviewed(Revision revision,UUID actor,String contextHash) {
        jdbc.sql("UPDATE government_revisions SET status='REVIEWED',row_version=row_version+1,reviewer_id=:actor,reviewed_at=:now,review_context_hash=:context WHERE id=:id")
            .param("context",contextHash).param("actor",actor).param("now",Instant.now().atOffset(ZoneOffset.UTC)).param("id",revision.id()).update();
        return revision(revision.id()).orElseThrow();
    }
    public String mandateHash(String mandate) {
        return jdbc.sql("SELECT r.material_hash FROM government_publications p JOIN government_revisions r ON r.id=p.revision_id JOIN government_subjects s ON s.id=p.subject_id WHERE s.mandate_id=:mandate AND s.subject_type='MANDATE'")
            .param("mandate",mandate).query(String.class).optional().orElse("");
    }
    public String reviewContext(UUID revision) {
        return jdbc.sql("SELECT review_context_hash FROM government_revisions WHERE id=:id").param("id",revision).query(String.class).optional().orElse("");
    }
    public Optional<String> currentHash(UUID subject) {
        return jdbc.sql("SELECT r.material_hash FROM government_publications p JOIN government_revisions r ON r.id=p.revision_id WHERE p.subject_id=:id")
            .param("id",subject).query(String.class).optional();
    }
    public Revision markPublished(Revision revision,UUID actor,String hash,Instant time) {
        jdbc.sql("UPDATE government_revisions SET status='SUPERSEDED',row_version=row_version+1 WHERE subject_id=:subject AND status='PUBLISHED'")
            .param("subject",revision.subjectId()).update();
        jdbc.sql("UPDATE government_revisions SET status='PUBLISHED',row_version=row_version+1,publisher_id=:actor,published_at=:now,material_hash=:hash WHERE id=:id")
            .param("actor",actor).param("now",time.atOffset(ZoneOffset.UTC)).param("hash",hash).param("id",revision.id()).update();
        int updated=jdbc.sql("UPDATE government_publications SET revision_id=:revision WHERE subject_id=:subject")
            .param("revision",revision.id()).param("subject",revision.subjectId()).update();
        if(updated==0)jdbc.sql("INSERT INTO government_publications(subject_id,revision_id) VALUES(:subject,:revision)")
            .param("subject",revision.subjectId()).param("revision",revision.id()).update();
        return revision(revision.id()).orElseThrow();
    }
    public void retainMandateHistory(Revision revision,MandateView view) {
        for(var member:view.membership())jdbc.sql("INSERT INTO government_membership_history(revision_id,membership_id,effective_from,effective_until,payload) VALUES(:revision,:id,:fromDate,:untilDate,:payload)")
            .param("revision",revision.id()).param("id",member.id()).param("fromDate",member.effectiveFrom())
            .param("untilDate",member.effectiveUntil(),java.sql.Types.DATE).param("payload",encode(member)).update();
        if(view.programme()!=null) {
            var programme=view.programme();
            jdbc.sql("""
                INSERT INTO government_programme_versions(id,mandate_id,version,revision_id,published_at)
                SELECT :id,:mandate,:version,:revision,:date WHERE NOT EXISTS(SELECT 1 FROM government_programme_versions WHERE id=:id)
                """).param("id",programme.id()).param("mandate",view.id()).param("version",programme.version())
                .param("revision",revision.id()).param("date",programme.publishedAt(),java.sql.Types.DATE).update();
        }
    }
    public List<String> publishedPayloads(String mandate,SubjectType type) {
        return jdbc.sql("""
            SELECT r.payload FROM government_publications p JOIN government_revisions r ON r.id=p.revision_id
            JOIN government_subjects s ON s.id=p.subject_id
            WHERE s.mandate_id=:mandate AND s.subject_type=:type AND r.status='PUBLISHED' ORDER BY s.slug
            """).param("mandate",mandate).param("type",type.name()).query(String.class).list();
    }
    public List<RevisionSummary> history(UUID subject) {
        return jdbc.sql("SELECT id,revision_number,published_at FROM government_revisions WHERE subject_id=:subject AND published_at IS NOT NULL ORDER BY revision_number DESC")
            .param("subject",subject).query((rs,n)->new RevisionSummary(rs.getObject("id",UUID.class),rs.getInt("revision_number"),rs.getObject("published_at",java.time.OffsetDateTime.class).toInstant(),null)).list();
    }
    public List<RevisionSummary> publicHistory(SubjectType type,String slug) {
        return jdbc.sql("SELECT id FROM government_subjects WHERE subject_type=:type AND slug=:slug")
            .param("type",type.name()).param("slug",slug).query(UUID.class).optional().map(this::history).orElse(List.of());
    }
    public List<String> publishedVersions(String mandate,SubjectType type) {
        return jdbc.sql("SELECT r.payload FROM government_revisions r JOIN government_subjects s ON s.id=r.subject_id WHERE s.mandate_id=:mandate AND s.subject_type=:type AND r.published_at IS NOT NULL")
            .param("mandate",mandate).param("type",type.name()).query(String.class).list();
    }
    public <T> T decode(String json,Class<T> type) {
        return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
    }
}
