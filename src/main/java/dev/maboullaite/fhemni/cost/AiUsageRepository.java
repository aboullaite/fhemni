package dev.maboullaite.fhemni.cost;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AiUsageRepository {

    private final JdbcClient jdbc;

    public AiUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countGlobal(Instant fromInclusive, Instant toExclusive, boolean analyses) {
        String operationFilter = analyses
                ? "operation = 'ANALYSIS'"
                : "operation IN ('CHAT_VIDEO', 'CHAT_CHECK', 'CHAT_PROGRAMME')";
        return jdbc.sql("""
                        SELECT COUNT(*)
                          FROM ai_usage_events
                         WHERE %s
                           AND requested_at >= :fromInclusive
                           AND requested_at < :toExclusive
                        """.formatted(operationFilter))
                .param("fromInclusive", utc(fromInclusive))
                .param("toExclusive", utc(toExclusive))
                .query(Long.class)
                .single();
    }

    public long countQuestionsForUser(UUID userId, Instant fromInclusive, Instant toExclusive) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                          FROM ai_usage_events
                         WHERE operation IN ('CHAT_VIDEO', 'CHAT_CHECK', 'CHAT_PROGRAMME')
                           AND user_id = :userId
                           AND requested_at >= :fromInclusive
                           AND requested_at < :toExclusive
                        """)
                .param("userId", userId)
                .param("fromInclusive", utc(fromInclusive))
                .param("toExclusive", utc(toExclusive))
                .query(Long.class)
                .single();
    }

    public long sumQuestionOutputTokensForUser(UUID userId, Instant fromInclusive, Instant toExclusive) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(output_tokens), 0)
                          FROM ai_usage_events
                         WHERE operation IN ('CHAT_VIDEO', 'CHAT_CHECK', 'CHAT_PROGRAMME')
                           AND user_id = :userId
                           AND requested_at >= :fromInclusive
                           AND requested_at < :toExclusive
                        """)
                .param("userId", userId)
                .param("fromInclusive", utc(fromInclusive))
                .param("toExclusive", utc(toExclusive))
                .query(Long.class)
                .single();
    }

    public UUID reserve(AiOperation operation, UUID userId, UUID analysisId, String model, Instant requestedAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO ai_usage_events (
                            id, operation, user_id, analysis_id, model, status, requested_at
                        ) VALUES (
                            :id, :operation, :userId, :analysisId, :model, 'RESERVED', :requestedAt
                        )
                        """)
                .param("id", id)
                .param("operation", operation.name())
                .param("userId", userId, java.sql.Types.OTHER)
                .param("analysisId", analysisId, java.sql.Types.OTHER)
                .param("model", model)
                .param("requestedAt", utc(requestedAt))
                .update();
        return id;
    }

    public void complete(UUID id, String status, AiUsage usage, Instant completedAt) {
        AiUsage safeUsage = usage == null ? AiUsage.empty() : usage;
        jdbc.sql("""
                        UPDATE ai_usage_events
                           SET status = :status,
                               completed_at = :completedAt,
                               input_tokens = :inputTokens,
                               output_tokens = :outputTokens,
                               cached_tokens = :cachedTokens,
                               thought_tokens = :thoughtTokens,
                               tool_use_tokens = :toolUseTokens,
                               grounding_queries = :groundingQueries
                         WHERE id = :id
                        """)
                .param("status", status)
                .param("completedAt", utc(completedAt))
                .param("inputTokens", safeUsage.inputTokens(), java.sql.Types.INTEGER)
                .param("outputTokens", safeUsage.outputTokens(), java.sql.Types.INTEGER)
                .param("cachedTokens", safeUsage.cachedTokens(), java.sql.Types.INTEGER)
                .param("thoughtTokens", safeUsage.thoughtTokens(), java.sql.Types.INTEGER)
                .param("toolUseTokens", safeUsage.toolUseTokens(), java.sql.Types.INTEGER)
                .param("groundingQueries", safeUsage.groundingQueries(), java.sql.Types.INTEGER)
                .param("id", id)
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
