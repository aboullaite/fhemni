package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GovernmentEvidenceRepository {
    private final JdbcClient jdbc;
    public GovernmentEvidenceRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    public Evidence save(String mandateId,Evidence value,byte[] archive) {
        GovernmentValidation.evidence(value);
        String digest=GovernmentValidation.archiveDigest(archive,value.archivedSha256());
        jdbc.sql("""
            INSERT INTO government_evidence(id,mandate_id,url,passage,locator,original_language,published_at,retrieved_at,archive_bytes,archived_sha256)
            VALUES(:id,:mandate,:url,:passage,:locator,:language,:published,:retrieved,:bytes,:digest)
            """).param("id",value.id()).param("mandate",mandateId).param("url",value.url()).param("passage",value.passage())
            .param("locator",value.locator(),Types.VARCHAR).param("language",value.originalLanguage())
            .param("published",value.publishedAt()==null?null:value.publishedAt().atOffset(ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
            .param("retrieved",value.retrievedAt().atOffset(ZoneOffset.UTC))
            .param("bytes",archive,Types.BINARY).param("digest",digest,Types.VARCHAR).update();
        return find(mandateId,value.id()).orElseThrow();
    }
    public Optional<Evidence> find(String mandateId,UUID id) {
        return jdbc.sql("SELECT id,url,passage,locator,original_language,published_at,retrieved_at,archived_sha256 FROM government_evidence WHERE id=:id AND mandate_id=:mandate")
            .param("id",id).param("mandate",mandateId).query(Evidence.class).optional();
    }
    public void require(String mandateId,UUID id) {
        if(id==null||find(mandateId,id).isEmpty())throw new IllegalArgumentException("Evidence must belong to the same mandate.");
    }
}
