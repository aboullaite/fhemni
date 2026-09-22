package dev.maboullaite.fhemni.civic;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.civic.CivicPriorityShare.Metadata;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class CivicPriorityShareRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    CivicPriorityShareRepository(JdbcClient jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
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
                        instant(result, "created_at")))
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
                .param("createdAt", utc(createdAt))
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
                .param("createdAt", utc(share.createdAt()))
                .update();
        return inserted == 1;
    }

    int deleteByToken(String token) {
        return jdbc.sql("DELETE FROM civic_priority_shares WHERE share_token = :token")
                .param("token", token)
                .update();
    }

    List<Metadata> findCreatedBefore(Instant cutoff, int limit) {
        return jdbc.sql("""
                        SELECT share_token, share_kind, language, image_object_key, image_sha256, created_at
                          FROM civic_priority_shares
                         WHERE created_at < :cutoff
                         ORDER BY created_at
                         LIMIT :limit
                        """)
                .param("cutoff", utc(cutoff))
                .param("limit", limit)
                .query((result, rowNumber) -> new Metadata(
                        result.getString("share_token"),
                        CivicPriorityShare.Kind.valueOf(result.getString("share_kind")),
                        result.getString("language"),
                        result.getString("image_object_key"),
                        result.getString("image_sha256"),
                        instant(result, "created_at")))
                .list();
    }

    Optional<PendingDeletion> moveToDeletionQueueIfExpired(
            String token,
            Instant cutoff,
            Instant queuedAt) {
        return transactions.execute(status -> {
            var source = jdbc.sql("""
                            SELECT share_token, image_object_key
                              FROM civic_priority_shares
                             WHERE share_token = :token
                               AND created_at < :cutoff
                             FOR UPDATE
                            """)
                    .param("token", token)
                    .param("cutoff", utc(cutoff))
                    .query((result, rowNumber) -> new PendingDeletion(
                            result.getString("share_token"),
                            result.getString("image_object_key"),
                            queuedAt))
                    .optional();
            if (source.isEmpty()) return Optional.empty();

            PendingDeletion deletion = source.get();
            jdbc.sql("""
                            INSERT INTO civic_priority_share_deletions (
                                share_token, image_object_key, queued_at, next_attempt_at
                            ) VALUES (
                                :token, :objectKey, :queuedAt, :queuedAt
                            )
                            """)
                    .param("token", deletion.token())
                    .param("objectKey", deletion.objectKey())
                    .param("queuedAt", utc(queuedAt))
                    .update();
            int deleted = jdbc.sql("DELETE FROM civic_priority_shares WHERE share_token = :token")
                    .param("token", deletion.token())
                    .update();
            if (deleted != 1) {
                throw new IllegalStateException("Could not queue expired civic-priority share for deletion.");
            }
            return Optional.of(deletion);
        });
    }

    List<PendingDeletion> findPendingDeletions(Instant readyAt, int limit) {
        return jdbc.sql("""
                        SELECT share_token, image_object_key, next_attempt_at
                          FROM civic_priority_share_deletions
                         WHERE next_attempt_at <= :readyAt
                         ORDER BY next_attempt_at, queued_at
                         LIMIT :limit
                        """)
                .param("readyAt", utc(readyAt))
                .param("limit", limit)
                .query((result, rowNumber) -> new PendingDeletion(
                        result.getString("share_token"),
                        result.getString("image_object_key"),
                        instant(result, "next_attempt_at")))
                .list();
    }

    int claimPendingDeletion(
            PendingDeletion deletion,
            UUID claimToken,
            Instant attemptedAt,
            Instant retryAt) {
        return jdbc.sql("""
                        UPDATE civic_priority_share_deletions
                           SET last_attempted_at = :attemptedAt,
                               claim_token = :claimToken,
                               next_attempt_at = :retryAt
                         WHERE share_token = :token
                           AND next_attempt_at = :observedNextAttemptAt
                           AND next_attempt_at <= :attemptedAt
                        """)
                .param("attemptedAt", utc(attemptedAt))
                .param("claimToken", claimToken)
                .param("retryAt", utc(retryAt))
                .param("token", deletion.token())
                .param("observedNextAttemptAt", utc(deletion.nextAttemptAt()))
                .update();
    }

    int deleteClaimedDeletion(String token, UUID claimToken) {
        return jdbc.sql("""
                        DELETE FROM civic_priority_share_deletions
                         WHERE share_token = :token
                           AND claim_token = :claimToken
                        """)
                .param("token", token)
                .param("claimToken", claimToken)
                .update();
    }

    private CivicPriorityShare map(ResultSet result, int rowNumber) throws SQLException {
        return new CivicPriorityShare(
                result.getObject("id", UUID.class),
                result.getString("share_token"),
                CivicPriorityShare.Kind.valueOf(result.getString("share_kind")),
                result.getString("language"),
                result.getString("image_object_key"),
                result.getString("image_sha256"),
                instant(result, "created_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    record PendingDeletion(String token, String objectKey, Instant nextAttemptAt) {
    }
}
