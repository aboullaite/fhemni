package dev.maboullaite.fhemni.metrics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMetricsRepository {

    private final JdbcClient jdbc;

    public AdminMetricsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UserMetrics usersSince(Instant since) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS registered_users,
                               SUM(CASE WHEN created_at >= :since THEN 1 ELSE 0 END) AS new_users,
                               SUM(CASE WHEN last_login_at >= :since THEN 1 ELSE 0 END) AS active_users
                          FROM app_users
                        """)
                .param("since", utc(since))
                .query((result, rowNumber) -> new UserMetrics(
                        result.getLong("registered_users"),
                        result.getLong("new_users"),
                        result.getLong("active_users")))
                .single();
    }

    public UsageOverview usageOverview(Instant staleBefore) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS all_requests,
                               COUNT(DISTINCT user_id) AS all_users,
                               SUM(CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE 0 END) AS all_succeeded,
                               SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS all_failed,
                               SUM(CASE
                                   WHEN status = 'RESERVED' AND requested_at >= :staleBefore THEN 1 ELSE 0
                               END) AS all_pending,
                               SUM(CASE
                                   WHEN status = 'RESERVED' AND requested_at < :staleBefore THEN 1 ELSE 0
                               END) AS all_stale,
                               COALESCE(SUM(input_tokens), 0) AS all_input_tokens,
                               COALESCE(SUM(output_tokens), 0) AS all_output_tokens,
                               COALESCE(SUM(cached_tokens), 0) AS all_cached_tokens,
                               COALESCE(SUM(thought_tokens), 0) AS all_thought_tokens,
                               COALESCE(SUM(tool_use_tokens), 0) AS all_tool_use_tokens,
                               COALESCE(SUM(grounding_queries), 0) AS all_grounding_queries,
                               SUM(CASE WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN 1 ELSE 0 END)
                                   AS chat_requests,
                               COUNT(DISTINCT CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN user_id
                               END) AS chat_users,
                               SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') AND status = 'SUCCEEDED'
                                       THEN 1 ELSE 0
                               END) AS chat_succeeded,
                               SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') AND status = 'FAILED'
                                       THEN 1 ELSE 0
                               END) AS chat_failed,
                               SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK')
                                       AND status = 'RESERVED' AND requested_at >= :staleBefore
                                       THEN 1 ELSE 0
                               END) AS chat_pending,
                               SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK')
                                       AND status = 'RESERVED' AND requested_at < :staleBefore
                                       THEN 1 ELSE 0
                               END) AS chat_stale,
                               COALESCE(SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN input_tokens ELSE 0
                               END), 0) AS chat_input_tokens,
                               COALESCE(SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN output_tokens ELSE 0
                               END), 0) AS chat_output_tokens,
                               COALESCE(SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN cached_tokens ELSE 0
                               END), 0) AS chat_cached_tokens,
                               COALESCE(SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN thought_tokens ELSE 0
                               END), 0) AS chat_thought_tokens,
                               COALESCE(SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN tool_use_tokens ELSE 0
                               END), 0) AS chat_tool_use_tokens,
                               COALESCE(SUM(CASE
                                   WHEN operation IN ('CHAT_VIDEO', 'CHAT_CHECK') THEN grounding_queries ELSE 0
                               END), 0) AS chat_grounding_queries
                          FROM ai_usage_events
                        """)
                .param("staleBefore", utc(staleBefore))
                .query((result, rowNumber) -> new UsageOverview(
                        mapUsage(result, "chat_"),
                        mapUsage(result, "all_")))
                .single();
    }

    private UsageMetrics mapUsage(ResultSet result, String prefix) throws SQLException {
        long inputTokens = result.getLong(prefix + "input_tokens");
        long outputTokens = result.getLong(prefix + "output_tokens");
        long thoughtTokens = result.getLong(prefix + "thought_tokens");
        long toolUseTokens = result.getLong(prefix + "tool_use_tokens");
        return new UsageMetrics(
                result.getLong(prefix + "requests"),
                result.getLong(prefix + "users"),
                result.getLong(prefix + "succeeded"),
                result.getLong(prefix + "failed"),
                result.getLong(prefix + "pending"),
                result.getLong(prefix + "stale"),
                inputTokens,
                outputTokens,
                result.getLong(prefix + "cached_tokens"),
                thoughtTokens,
                toolUseTokens,
                result.getLong(prefix + "grounding_queries"),
                inputTokens + outputTokens + thoughtTokens + toolUseTokens);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public record UserMetrics(long registered, long newLast24Hours, long activeLast24Hours) {
    }

    public record UsageOverview(UsageMetrics chat, UsageMetrics allAi) {
    }

    public record UsageMetrics(
            long requests,
            long users,
            long succeeded,
            long failed,
            long pending,
            long stale,
            long inputTokens,
            long outputTokens,
            long cachedTokens,
            long thoughtTokens,
            long toolUseTokens,
            long groundingQueries,
            long recordedTokens) {
    }
}
