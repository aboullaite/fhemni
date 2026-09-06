package dev.maboullaite.fhemni.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserAccountRepository {

    private static final String USER_BY_IDENTITY = """
            SELECT u.id, u.display_name, u.email, u.avatar_url, u.role,
                   u.created_at, u.updated_at, u.last_login_at
              FROM app_users u
              JOIN external_identities i ON i.user_id = u.id
             WHERE i.provider = :provider AND i.subject = :subject
            """;

    private final JdbcClient jdbc;

    public UserAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByIdentity(String provider, String subject) {
        return jdbc.sql(USER_BY_IDENTITY)
                .param("provider", provider)
                .param("subject", subject)
                .query(this::mapUser)
                .optional();
    }

    public boolean identityHasVerifiedEmail(String provider, String subject, String expectedEmail) {
        if (expectedEmail == null || expectedEmail.isBlank()) {
            return false;
        }
        return jdbc.sql("""
                        SELECT COUNT(*)
                          FROM external_identities i
                          JOIN app_users u ON u.id = i.user_id
                         WHERE i.provider = :provider
                           AND i.subject = :subject
                           AND i.email_verified = TRUE
                           AND LOWER(u.email) = :expectedEmail
                        """)
                .param("provider", provider)
                .param("subject", subject)
                .param("expectedEmail", expectedEmail.toLowerCase(Locale.ROOT))
                .query(Integer.class)
                .single() > 0;
    }

    @Transactional
    public Optional<AppUser> setRole(String provider, String subject, UserRole role) {
        jdbc.sql("""
                        UPDATE app_users
                           SET role = :role, updated_at = :updatedAt
                         WHERE id IN (
                               SELECT user_id
                                 FROM external_identities
                                WHERE provider = :provider AND subject = :subject
                           )
                        """)
                .param("role", role.name())
                .param("updatedAt", atUtc(Instant.now()))
                .param("provider", provider)
                .param("subject", subject)
                .update();
        return findByIdentity(provider, subject);
    }

    @Transactional
    public Optional<AppUser> promoteConfiguredIdentityToAdmin(String provider, String subject) {
        jdbc.sql("""
                        UPDATE app_users
                           SET role = 'ADMIN', updated_at = :updatedAt
                         WHERE role <> 'ADMIN'
                           AND id IN (
                               SELECT user_id
                                 FROM external_identities
                                WHERE provider = :provider AND subject = :subject
                           )
                        """)
                .param("updatedAt", atUtc(Instant.now()))
                .param("provider", provider)
                .param("subject", subject)
                .update();
        return findByIdentity(provider, subject);
    }

    @Transactional
    public Optional<AppUser> promoteVerifiedEmailToAdmin(
            String provider,
            String subject,
            String expectedEmail) {
        jdbc.sql("""
                        UPDATE app_users
                           SET role = 'ADMIN', updated_at = :updatedAt
                         WHERE role <> 'ADMIN'
                           AND LOWER(email) = :expectedEmail
                           AND id IN (
                               SELECT user_id
                                 FROM external_identities
                                WHERE provider = :provider
                                  AND subject = :subject
                                  AND email_verified = TRUE
                           )
                        """)
                .param("updatedAt", atUtc(Instant.now()))
                .param("expectedEmail", expectedEmail.toLowerCase(Locale.ROOT))
                .param("provider", provider)
                .param("subject", subject)
                .update();
        return findByIdentity(provider, subject);
    }

    @Transactional
    public AppUser recordLogin(ExternalIdentityProfile profile, boolean administrator) {
        Optional<AppUser> existing = findByIdentity(profile.provider(), profile.subject());
        if (existing.isPresent()) {
            return updateExisting(existing.get(), profile, administrator);
        }

        Instant now = databaseInstant();
        AppUser user = new AppUser(
                UUID.randomUUID(),
                profile.displayName(),
                profile.email(),
                profile.avatarUrl(),
                administrator ? UserRole.ADMIN : UserRole.USER,
                now,
                now,
                now);
        jdbc.sql("""
                        INSERT INTO app_users
                            (id, display_name, email, avatar_url, role, created_at, updated_at, last_login_at)
                        VALUES
                            (:id, :displayName, :email, :avatarUrl, :role, :createdAt, :updatedAt, :lastLoginAt)
                        """)
                .param("id", user.id())
                .param("displayName", user.displayName())
                .param("email", user.email())
                .param("avatarUrl", user.avatarUrl())
                .param("role", user.role().name())
                .param("createdAt", atUtc(user.createdAt()))
                .param("updatedAt", atUtc(user.updatedAt()))
                .param("lastLoginAt", atUtc(user.lastLoginAt()))
                .update();
        jdbc.sql("""
                        INSERT INTO external_identities
                            (provider, subject, user_id, provider_username, email_verified, created_at, last_login_at)
                        VALUES
                            (:provider, :subject, :userId, :providerUsername, :emailVerified, :createdAt, :lastLoginAt)
                        """)
                .param("provider", profile.provider())
                .param("subject", profile.subject())
                .param("userId", user.id())
                .param("providerUsername", profile.providerUsername())
                .param("emailVerified", profile.emailVerified())
                .param("createdAt", atUtc(now))
                .param("lastLoginAt", atUtc(now))
                .update();
        return user;
    }

    private AppUser updateExisting(
            AppUser existing,
            ExternalIdentityProfile profile,
            boolean administrator) {
        Instant now = databaseInstant();
        UserRole role = administrator
                ? UserRole.ADMIN
                : existing.role() == UserRole.ADMIN ? UserRole.USER : existing.role();
        jdbc.sql("""
                        UPDATE app_users
                           SET display_name = :displayName,
                               email = :email,
                               avatar_url = :avatarUrl,
                               role = :role,
                               updated_at = :updatedAt,
                               last_login_at = :lastLoginAt
                         WHERE id = :id
                        """)
                .param("displayName", profile.displayName())
                .param("email", profile.email())
                .param("avatarUrl", profile.avatarUrl())
                .param("role", role.name())
                .param("updatedAt", atUtc(now))
                .param("lastLoginAt", atUtc(now))
                .param("id", existing.id())
                .update();
        jdbc.sql("""
                        UPDATE external_identities
                           SET provider_username = :providerUsername,
                               email_verified = :emailVerified,
                               last_login_at = :lastLoginAt
                         WHERE provider = :provider AND subject = :subject
                        """)
                .param("providerUsername", profile.providerUsername())
                .param("emailVerified", profile.emailVerified())
                .param("lastLoginAt", atUtc(now))
                .param("provider", profile.provider())
                .param("subject", profile.subject())
                .update();
        return new AppUser(
                existing.id(), profile.displayName(), profile.email(), profile.avatarUrl(),
                role, existing.createdAt(), now, now);
    }

    private AppUser mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AppUser(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getString("email"),
                resultSet.getString("avatar_url"),
                UserRole.valueOf(resultSet.getString("role")),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                instant(resultSet, "last_login_at"));
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant databaseInstant() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
