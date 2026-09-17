package dev.maboullaite.fhemni.civic;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.civic.CivicPriorityShare.Metadata;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CivicPriorityShareRepository {

    private final JdbcClient jdbc;

    CivicPriorityShareRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<CivicPriorityShare> findByToken(String token) {
        return jdbc.sql("""
                        SELECT id, share_token, share_kind, language, image_object_key,
                               image_sha256, created_at
                          FROM civic_priority_shares
                         WHERE share_token = :token
                        """)
                .param("token", token)
                .query(this::map)
                .optional();
    }

    Optional<Metadata> findMetadataByToken(String token) {
        return jdbc.sql("""
                        SELECT share_token, share_kind, language, image_object_key, image_sha256, created_at
                          FROM civic_priority_shares
                         WHERE share_token = :token
                        """)
                .param("token", token)
                .query((result, rowNumber) -> new Metadata(
                        result.getString("share_token"),
                        CivicPriorityShare.Kind.valueOf(result.getString("share_kind")),
                        result.getString("language"),
                        result.getString("image_object_key"),
                        result.getString("image_sha256"),
                        result.getObject("created_at", Instant.class)))
                .optional();
    }

    Optional<CivicPriorityShare> findByImage(
            String imageSha256,
            CivicPriorityShare.Kind kind,
            String language) {
        return jdbc.sql("""
                        SELECT id, share_token, share_kind, language, image_object_key,
                               image_sha256, created_at
                          FROM civic_priority_shares
                         WHERE image_sha256 = :imageSha256
                           AND share_kind = :shareKind
                           AND language = :language
                        """)
                .param("imageSha256", imageSha256)
                .param("shareKind", kind.name())
                .param("language", language)
                .query(this::map)
                .optional();
    }

    int renew(String token, Instant createdAt) {
        return jdbc.sql("""
                        UPDATE civic_priority_shares
                           SET created_at = :createdAt
                         WHERE share_token = :token
                        """)
                .param("token", token)
                .param("createdAt", createdAt)
                .update();
    }

    boolean insertIfAbsent(CivicPriorityShare share) {
        int inserted = jdbc.sql("""
                        INSERT INTO civic_priority_shares (
                            id, share_token, share_kind, language, image_object_key,
                            image_sha256, created_at
                        ) VALUES (
                            :id, :shareToken, :shareKind, :language, :imageObjectKey,
                            :imageSha256, :createdAt
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", share.id())
                .param("shareToken", share.token())
                .param("shareKind", share.kind().name())
                .param("language", share.language())
                .param("imageObjectKey", share.imageObjectKey())
                .param("imageSha256", share.imageSha256())
                .param("createdAt", share.createdAt())
                .update();
        return inserted == 1;
    }

    int deleteByToken(String token) {
        return jdbc.sql("DELETE FROM civic_priority_shares WHERE share_token = :token")
                .param("token", token)
                .update();
    }

    int deleteByTokenIfCreatedBefore(String token, Instant cutoff) {
        return jdbc.sql("""
                        DELETE FROM civic_priority_shares
                         WHERE share_token = :token
                           AND created_at < :cutoff
                        """)
                .param("token", token)
                .param("cutoff", cutoff)
                .update();
    }

    java.util.List<Metadata> findCreatedBefore(Instant cutoff, int limit) {
        return jdbc.sql("""
                        SELECT share_token, share_kind, language, image_object_key, image_sha256, created_at
                          FROM civic_priority_shares
                         WHERE created_at < :cutoff
                         ORDER BY created_at
                         LIMIT :limit
                        """)
                .param("cutoff", cutoff)
                .param("limit", limit)
                .query((result, rowNumber) -> new Metadata(
                        result.getString("share_token"),
                        CivicPriorityShare.Kind.valueOf(result.getString("share_kind")),
                        result.getString("language"),
                        result.getString("image_object_key"),
                        result.getString("image_sha256"),
                        result.getObject("created_at", Instant.class)))
                .list();
    }

    private CivicPriorityShare map(ResultSet result, int rowNumber) throws SQLException {
        return new CivicPriorityShare(
                result.getObject("id", UUID.class),
                result.getString("share_token"),
                CivicPriorityShare.Kind.valueOf(result.getString("share_kind")),
                result.getString("language"),
                result.getString("image_object_key"),
                result.getString("image_sha256"),
                result.getObject("created_at", Instant.class));
    }
}
