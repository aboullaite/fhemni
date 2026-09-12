package dev.maboullaite.fhemni.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MagicLinkRepository {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_LINKS_PER_HOUR = 5;

    private final JdbcClient jdbc;

    public MagicLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<String> create(String email, String returnTarget, Instant expiresAt) {
        Instant now = Instant.now();
        // Keep the count-and-insert decision atomic across application instances. The
        // transaction holds this row only for the few database statements below; email
        // delivery happens after this method commits.
        jdbc.sql("SELECT id FROM magic_link_rate_limit_lock WHERE id = 1 FOR UPDATE")
                .query(Integer.class)
                .single();
        int recent = jdbc.sql("""
                        SELECT COUNT(*) FROM magic_link_tokens
                         WHERE email = :email AND created_at >= :since
                        """)
                .param("email", email)
                .param("since", atUtc(now.minusSeconds(3600)))
                .query(Integer.class)
                .single();
        if (recent >= MAX_LINKS_PER_HOUR) {
            return Optional.empty();
        }

        String token = newToken();
        jdbc.sql("""
                        INSERT INTO magic_link_tokens
                            (token_hash, email, return_target, expires_at, created_at)
                        VALUES
                            (:tokenHash, :email, :returnTarget, :expiresAt, :createdAt)
                        """)
                .param("tokenHash", hash(token))
                .param("email", email)
                .param("returnTarget", returnTarget)
                .param("expiresAt", atUtc(expiresAt))
                .param("createdAt", atUtc(now))
                .update();
        jdbc.sql("DELETE FROM magic_link_tokens WHERE expires_at < :cutoff")
                .param("cutoff", atUtc(now.minusSeconds(86400)))
                .update();
        return Optional.of(token);
    }

    public boolean valid(String token) {
        return findValid(token).isPresent();
    }

    public Optional<VerifiedLink> findValid(String token) {
        if (token == null || token.length() > 256) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT email, return_target FROM magic_link_tokens
                         WHERE token_hash = :tokenHash
                           AND used_at IS NULL
                           AND expires_at > :now
                        """)
                .param("tokenHash", hash(token))
                .param("now", atUtc(Instant.now()))
                .query(VerifiedLink.class)
                .optional();
    }

    @Transactional
    public Optional<VerifiedLink> consume(String token) {
        if (token == null || token.length() > 256) {
            return Optional.empty();
        }
        String tokenHash = hash(token);
        Optional<VerifiedLink> link = jdbc.sql("""
                        SELECT email, return_target
                          FROM magic_link_tokens
                         WHERE token_hash = :tokenHash
                           AND used_at IS NULL
                           AND expires_at > :now
                         FOR UPDATE
                        """)
                .param("tokenHash", tokenHash)
                .param("now", atUtc(Instant.now()))
                .query(VerifiedLink.class)
                .optional();
        if (link.isEmpty()) {
            return Optional.empty();
        }
        jdbc.sql("UPDATE magic_link_tokens SET used_at = :usedAt WHERE token_hash = :tokenHash")
                .param("usedAt", atUtc(Instant.now()))
                .param("tokenHash", tokenHash)
                .update();
        return link;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public record VerifiedLink(String email, String returnTarget) {
    }
}
