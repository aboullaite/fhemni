package dev.maboullaite.fhemni.civic;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
                        SELECT id, share_token, share_kind, language, image_png, image_sha256, created_at
                          FROM civic_priority_shares
                         WHERE share_token = :token
                        """)
                .param("token", token)
                .query(this::map)
                .optional();
    }

    Optional<CivicPriorityShare> findByImage(
            String imageSha256,
            CivicPriorityShare.Kind kind,
            String language) {
        return jdbc.sql("""
                        SELECT id, share_token, share_kind, language, image_png, image_sha256, created_at
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

    void insert(CivicPriorityShare share) {
        jdbc.sql("""
                        INSERT INTO civic_priority_shares (
                            id, share_token, share_kind, language, image_png, image_sha256, created_at
                        ) VALUES (
                            :id, :shareToken, :shareKind, :language, :imagePng, :imageSha256, :createdAt
                        )
                        """)
                .param("id", share.id())
                .param("shareToken", share.token())
                .param("shareKind", share.kind().name())
                .param("language", share.language())
                .param("imagePng", share.imagePng())
                .param("imageSha256", share.imageSha256())
                .param("createdAt", share.createdAt())
                .update();
    }

    private CivicPriorityShare map(ResultSet result, int rowNumber) throws SQLException {
        return new CivicPriorityShare(
                result.getObject("id", UUID.class),
                result.getString("share_token"),
                CivicPriorityShare.Kind.valueOf(result.getString("share_kind")),
                result.getString("language"),
                result.getBytes("image_png"),
                result.getString("image_sha256"),
                result.getObject("created_at", Instant.class));
    }
}

