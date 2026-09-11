package dev.maboullaite.fhemni.programme.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ProgrammeMediaRepository {

    private static final String COLUMNS = """
            id, programme_id, party_code, source_sha256, status, script_revision,
            script_json, script_text, script_model, tts_model, tts_voice, image_model,
            illustration_count, pronunciation_version,
            audio_object_key, video_object_key, captions_object_key, duration_ms,
            attempt_count, max_attempts, available_at, last_error_code, last_error_message,
            created_at, updated_at, script_reviewed_at, media_reviewed_at, published_at
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public ProgrammeMediaRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public ProgrammeMedia create(
            UUID programmeId,
            String partyCode,
            String sourceSha256,
            String pronunciationVersion,
            int maxAttempts,
            Instant now) {
        staleWorking(programmeId, now);
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO programme_media (
                            id, programme_id, party_code, source_sha256, status,
                            working_marker, pronunciation_version, max_attempts,
                            available_at, created_at, updated_at
                        ) VALUES (
                            :id, :programmeId, :partyCode, :sourceSha256, 'QUEUED_SCRIPT',
                            TRUE, :pronunciationVersion, :maxAttempts,
                            :now, :now, :now
                        )
                        """)
                .param("id", id)
                .param("programmeId", programmeId)
                .param("partyCode", partyCode)
                .param("sourceSha256", sourceSha256)
                .param("pronunciationVersion", pronunciationVersion)
                .param("maxAttempts", maxAttempts)
                .param("now", utc(now))
                .update();
        return find(id).orElseThrow();
    }

    public Optional<ProgrammeMedia> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM programme_media WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public Optional<ProgrammeMedia> working(UUID programmeId) {
        return jdbc.sql("""
                        SELECT %s FROM programme_media
                         WHERE programme_id = :programmeId AND working_marker = TRUE
                        """.formatted(COLUMNS))
                .param("programmeId", programmeId)
                .query(this::map)
                .optional();
    }

    public Optional<ProgrammeMedia> published(UUID programmeId) {
        return jdbc.sql("""
                        SELECT %s FROM programme_media
                         WHERE programme_id = :programmeId AND published_marker = TRUE
                        """.formatted(COLUMNS))
                .param("programmeId", programmeId)
                .query(this::map)
                .optional();
    }

    public Optional<ProgrammeMedia> publishedByParty(String partyCode) {
        return jdbc.sql("""
                        SELECT %s FROM programme_media
                         WHERE party_code = :partyCode AND published_marker = TRUE
                         ORDER BY published_at DESC LIMIT 1
                        """.formatted(COLUMNS))
                .param("partyCode", partyCode)
                .query(this::map)
                .optional();
    }

    public List<ProgrammeMedia> history(UUID programmeId) {
        return jdbc.sql("""
                        SELECT %s FROM programme_media
                         WHERE programme_id = :programmeId
                         ORDER BY created_at DESC
                        """.formatted(COLUMNS))
                .param("programmeId", programmeId)
                .query(this::map)
                .list();
    }

    public Map<UUID, ProgrammeMedia> latestByProgramme() {
        List<ProgrammeMedia> media = jdbc.sql(
                        "SELECT " + COLUMNS + " FROM programme_media ORDER BY created_at DESC")
                .query(this::map)
                .list();
        Map<UUID, ProgrammeMedia> latest = new LinkedHashMap<>();
        media.forEach(item -> latest.putIfAbsent(item.programmeId(), item));
        return Map.copyOf(latest);
    }

    public List<UUID> dispatchable(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT id FROM programme_media
                         WHERE working_marker = TRUE
                           AND status IN ('QUEUED_SCRIPT', 'QUEUED_MEDIA')
                           AND available_at <= :now
                           AND (lease_until IS NULL OR lease_until <= :now)
                         ORDER BY created_at
                         LIMIT :limit
                        """)
                .param("now", utc(now))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    @Transactional
    public void recoverExpired(Instant now) {
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET status = CASE status
                               WHEN 'GENERATING_SCRIPT' THEN 'QUEUED_SCRIPT'
                               WHEN 'RENDERING_MEDIA' THEN 'QUEUED_MEDIA'
                               ELSE status END,
                               lock_owner = NULL, lease_until = NULL, available_at = :now,
                               last_error_code = 'WORKER_INTERRUPTED',
                               last_error_message = 'The media worker stopped; generation will resume.',
                               updated_at = :now
                         WHERE working_marker = TRUE
                           AND status IN ('GENERATING_SCRIPT', 'RENDERING_MEDIA')
                           AND lease_until IS NOT NULL AND lease_until <= :now
                        """)
                .param("now", utc(now))
                .update();
    }

    @Transactional
    public Optional<Lease> claim(UUID id, String owner, Instant now, Instant leaseUntil) {
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET status = CASE status
                               WHEN 'QUEUED_SCRIPT' THEN 'GENERATING_SCRIPT'
                               WHEN 'QUEUED_MEDIA' THEN 'RENDERING_MEDIA'
                               ELSE status END,
                               lock_owner = :owner, lease_until = :leaseUntil,
                               lease_token = lease_token + 1, attempt_count = attempt_count + 1,
                               last_error_code = NULL, last_error_message = NULL, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE
                           AND status IN ('QUEUED_SCRIPT', 'QUEUED_MEDIA')
                           AND available_at <= :now
                           AND (lease_until IS NULL OR lease_until <= :now)
                        """)
                .param("owner", owner)
                .param("leaseUntil", utc(leaseUntil))
                .param("now", utc(now))
                .param("id", id)
                .update();
        if (updated != 1) {
            return Optional.empty();
        }
        long token = jdbc.sql("SELECT lease_token FROM programme_media WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
        return Optional.of(new Lease(id, owner, token));
    }

    @Transactional
    public boolean renew(Lease lease, Instant now, Instant leaseUntil) {
        return jdbc.sql("""
                        UPDATE programme_media SET lease_until = :leaseUntil, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE
                           AND status IN ('GENERATING_SCRIPT', 'RENDERING_MEDIA')
                           AND lock_owner = :owner AND lease_token = :token AND lease_until > :now
                        """)
                .param("leaseUntil", utc(leaseUntil))
                .param("now", utc(now))
                .param("id", lease.id())
                .param("owner", lease.owner())
                .param("token", lease.token())
                .update() == 1;
    }

    @Transactional
    public ProgrammeMedia completeScript(
            Lease lease,
            ProgrammeMediaScript script,
            String scriptText,
            String model,
            Instant now) {
        requireOwned(lease, "GENERATING_SCRIPT", now);
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET status = 'SCRIPT_REVIEW', script_json = :scriptJson,
                               script_text = :scriptText, script_model = :model,
                               lock_owner = NULL, lease_until = NULL, available_at = NULL,
                               updated_at = :now
                         WHERE id = :id AND working_marker = TRUE
                           AND status = 'GENERATING_SCRIPT'
                           AND lock_owner = :owner AND lease_token = :token
                        """)
                .param("scriptJson", writeScript(script), Types.LONGVARCHAR)
                .param("scriptText", scriptText, Types.LONGVARCHAR)
                .param("model", model)
                .param("now", utc(now))
                .param("id", lease.id())
                .param("owner", lease.owner())
                .param("token", lease.token())
                .update();
        if (updated != 1) {
            throw new ProgrammeMediaLeaseLostException();
        }
        return find(lease.id()).orElseThrow();
    }

    @Transactional
    public ProgrammeMedia saveReviewedScript(UUID id, ProgrammeMediaScript script, String scriptText, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET script_json = :scriptJson, script_text = :scriptText,
                               script_revision = script_revision + 1,
                               audio_object_key = NULL, video_object_key = NULL,
                               captions_object_key = NULL, duration_ms = NULL, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE AND status = 'SCRIPT_REVIEW'
                        """)
                .param("scriptJson", writeScript(script), Types.LONGVARCHAR)
                .param("scriptText", scriptText, Types.LONGVARCHAR)
                .param("now", utc(now))
                .param("id", id)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Only a script awaiting review can be edited.");
        }
        return find(id).orElseThrow();
    }

    @Transactional
    public ProgrammeMedia queueMedia(UUID id, ProgrammeMediaScript script, String scriptText, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET status = 'QUEUED_MEDIA', script_json = :scriptJson,
                               script_text = :scriptText, script_revision = script_revision + 1,
                               script_reviewed_at = :now, attempt_count = 0, available_at = :now,
                               last_error_code = NULL, last_error_message = NULL, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE AND status = 'SCRIPT_REVIEW'
                        """)
                .param("scriptJson", writeScript(script), Types.LONGVARCHAR)
                .param("scriptText", scriptText, Types.LONGVARCHAR)
                .param("now", utc(now))
                .param("id", id)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Only a reviewed script can be sent to the media worker.");
        }
        return find(id).orElseThrow();
    }

    @Transactional
    public ProgrammeMedia completeMedia(
            Lease lease,
            String ttsModel,
            String voice,
            String imageModel,
            int illustrationCount,
            String audioKey,
            String videoKey,
            String captionsKey,
            long durationMs,
            Instant now) {
        requireOwned(lease, "RENDERING_MEDIA", now);
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET status = 'MEDIA_REVIEW', tts_model = :ttsModel, tts_voice = :voice,
                               image_model = :imageModel, illustration_count = :illustrationCount,
                               audio_object_key = :audioKey, video_object_key = :videoKey,
                               captions_object_key = :captionsKey, duration_ms = :durationMs,
                               lock_owner = NULL, lease_until = NULL, available_at = NULL,
                               updated_at = :now
                         WHERE id = :id AND working_marker = TRUE AND status = 'RENDERING_MEDIA'
                           AND lock_owner = :owner AND lease_token = :token
                        """)
                .param("ttsModel", ttsModel)
                .param("voice", voice)
                .param("imageModel", imageModel, Types.VARCHAR)
                .param("illustrationCount", illustrationCount)
                .param("audioKey", audioKey)
                .param("videoKey", videoKey)
                .param("captionsKey", captionsKey)
                .param("durationMs", durationMs)
                .param("now", utc(now))
                .param("id", lease.id())
                .param("owner", lease.owner())
                .param("token", lease.token())
                .update();
        if (updated != 1) {
            throw new ProgrammeMediaLeaseLostException();
        }
        return find(lease.id()).orElseThrow();
    }

    @Transactional
    public ProgrammeMedia publish(UUID id, Instant now) {
        ProgrammeMedia media = find(id).orElseThrow();
        if (media.status() != ProgrammeMediaStatus.MEDIA_REVIEW) {
            throw new IllegalStateException("Only reviewed media can be published.");
        }
        jdbc.sql("""
                        UPDATE programme_media
                           SET status = 'STALE', published_marker = NULL, updated_at = :now
                         WHERE programme_id = :programmeId AND published_marker = TRUE
                        """)
                .param("programmeId", media.programmeId())
                .param("now", utc(now))
                .update();
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET status = 'PUBLISHED', working_marker = NULL, published_marker = TRUE,
                               media_reviewed_at = :now, published_at = :now, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE AND status = 'MEDIA_REVIEW'
                        """)
                .param("id", id)
                .param("now", utc(now))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("The reviewed programme media changed before it could be published.");
        }
        return find(id).orElseThrow();
    }

    @Transactional
    public ProgrammeMedia failOrRetry(
            Lease lease,
            String errorCode,
            String message,
            Instant retryAt,
            Instant now) {
        ProgrammeMedia media = find(lease.id()).orElseThrow();
        String retryStatus = switch (media.status()) {
            case GENERATING_SCRIPT -> "QUEUED_SCRIPT";
            case RENDERING_MEDIA -> "QUEUED_MEDIA";
            default -> throw new ProgrammeMediaLeaseLostException();
        };
        requireOwned(lease, media.status().name(), now);
        boolean exhausted = media.attemptCount() >= media.maxAttempts();
        jdbc.sql("""
                        UPDATE programme_media
                           SET status = :status, available_at = :availableAt,
                               last_error_code = :errorCode, last_error_message = :message,
                               lock_owner = NULL, lease_until = NULL, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE
                           AND lock_owner = :owner AND lease_token = :token
                        """)
                .param("status", exhausted ? "FAILED" : retryStatus)
                .param("availableAt", exhausted ? null : utc(retryAt), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("errorCode", errorCode)
                .param("message", message)
                .param("now", utc(now))
                .param("id", lease.id())
                .param("owner", lease.owner())
                .param("token", lease.token())
                .update();
        return find(lease.id()).orElseThrow();
    }

    @Transactional
    public ProgrammeMedia retryFailed(UUID id, Instant now) {
        ProgrammeMedia media = find(id).orElseThrow();
        if (media.status() != ProgrammeMediaStatus.FAILED) {
            throw new IllegalStateException("Only failed media can be retried.");
        }
        String status = media.script() == null ? "QUEUED_SCRIPT" : "QUEUED_MEDIA";
        jdbc.sql("""
                        UPDATE programme_media
                           SET status = :status, attempt_count = 0, available_at = :now,
                               last_error_code = NULL, last_error_message = NULL, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE AND status = 'FAILED'
                        """)
                .param("status", status)
                .param("now", utc(now))
                .param("id", id)
                .update();
        return find(id).orElseThrow();
    }

    @Transactional
    public void staleOwned(Lease lease, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE programme_media
                           SET status = 'STALE', working_marker = NULL,
                               lock_owner = NULL, lease_until = NULL, updated_at = :now
                         WHERE id = :id AND working_marker = TRUE
                           AND status IN ('GENERATING_SCRIPT', 'RENDERING_MEDIA')
                           AND lock_owner = :owner AND lease_token = :token
                        """)
                .param("now", utc(now))
                .param("id", lease.id())
                .param("owner", lease.owner())
                .param("token", lease.token())
                .update();
        if (updated != 1) {
            throw new ProgrammeMediaLeaseLostException();
        }
    }

    @Transactional
    public void staleWorking(UUID programmeId, Instant now) {
        jdbc.sql("""
                        UPDATE programme_media
                           SET status = 'STALE', working_marker = NULL,
                               lock_owner = NULL, lease_until = NULL,
                               lease_token = lease_token + 1, updated_at = :now
                         WHERE programme_id = :programmeId AND working_marker = TRUE
                        """)
                .param("programmeId", programmeId)
                .param("now", utc(now))
                .update();
    }

    private void requireOwned(Lease lease, String status, Instant now) {
        boolean owned = jdbc.sql("""
                        SELECT id FROM programme_media
                         WHERE id = :id AND working_marker = TRUE AND status = :status
                           AND lock_owner = :owner AND lease_token = :token AND lease_until > :now
                         FOR UPDATE
                        """)
                .param("id", lease.id())
                .param("status", status)
                .param("owner", lease.owner())
                .param("token", lease.token())
                .param("now", utc(now))
                .query(UUID.class)
                .optional()
                .isPresent();
        if (!owned) {
            throw new ProgrammeMediaLeaseLostException();
        }
    }

    private ProgrammeMedia map(ResultSet rs, int rowNumber) throws SQLException {
        return new ProgrammeMedia(
                rs.getObject("id", UUID.class), rs.getObject("programme_id", UUID.class),
                rs.getString("party_code"), rs.getString("source_sha256"),
                ProgrammeMediaStatus.valueOf(rs.getString("status")), rs.getInt("script_revision"),
                readScript(rs.getString("script_json")), rs.getString("script_text"),
                rs.getString("script_model"), rs.getString("tts_model"), rs.getString("tts_voice"),
                rs.getString("image_model"), rs.getInt("illustration_count"),
                rs.getString("pronunciation_version"), rs.getString("audio_object_key"),
                rs.getString("video_object_key"), rs.getString("captions_object_key"),
                nullableLong(rs, "duration_ms"), rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                nullableInstant(rs, "available_at"), rs.getString("last_error_code"),
                rs.getString("last_error_message"), instant(rs, "created_at"), instant(rs, "updated_at"),
                nullableInstant(rs, "script_reviewed_at"), nullableInstant(rs, "media_reviewed_at"),
                nullableInstant(rs, "published_at"));
    }

    private String writeScript(ProgrammeMediaScript script) {
        try {
            return mapper.writeValueAsString(script);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Programme media script could not be stored.", exception);
        }
    }

    private ProgrammeMediaScript readScript(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, ProgrammeMediaScript.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored programme media script could not be read.", exception);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record Lease(UUID id, String owner, long token) {
    }
}
