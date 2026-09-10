package dev.maboullaite.fhemni.programme;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PromisePolicyTopic.Assignment;
import dev.maboullaite.fhemni.programme.PromisePolicyTopic.Relationship;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyTopicRepository {

    private final JdbcClient jdbc;

    public PolicyTopicRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PolicyTopic> findAllActive() {
        return jdbc.sql("""
                        SELECT topic.code, topic.parent_code,
                               topic.label_ar, topic.label_fr, topic.label_en,
                               topic.sort_order, topic.selectable
                          FROM policy_topics topic
                          LEFT JOIN policy_topics parent ON parent.code = topic.parent_code
                         WHERE topic.active = TRUE
                         ORDER BY COALESCE(parent.sort_order, topic.sort_order),
                                  CASE WHEN topic.parent_code IS NULL THEN 0 ELSE 1 END,
                                  topic.sort_order
                        """)
                .query(this::mapTopic)
                .list();
    }

    public Set<String> findActiveSelectableCodes(List<String> codes) {
        if (codes.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.sql("""
                        SELECT code FROM policy_topics
                         WHERE code IN (:codes) AND active = TRUE AND selectable = TRUE
                        """)
                .param("codes", codes)
                .query(String.class)
                .list());
    }

    public List<String> findUserPreferences(UUID userId) {
        return jdbc.sql("""
                        SELECT preference.topic_code
                          FROM user_policy_topic_preferences preference
                          JOIN policy_topics topic ON topic.code = preference.topic_code
                         WHERE preference.user_id = :userId
                           AND topic.active = TRUE
                           AND topic.selectable = TRUE
                         ORDER BY preference.position
                        """)
                .param("userId", userId)
                .query(String.class)
                .list();
    }

    public void replaceUserPreferences(UUID userId, List<String> topicCodes, Instant now) {
        jdbc.sql("DELETE FROM user_policy_topic_preferences WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        for (int index = 0; index < topicCodes.size(); index++) {
            jdbc.sql("""
                            INSERT INTO user_policy_topic_preferences (
                                user_id, topic_code, position, created_at, updated_at
                            ) VALUES (:userId, :topicCode, :position, :now, :now)
                            """)
                    .param("userId", userId)
                    .param("topicCode", topicCodes.get(index))
                    .param("position", index + 1)
                    .param("now", utc(now))
                    .update();
        }
    }

    public void replaceRuleAssignments(UUID promiseId, List<Assignment> assignments, Instant now) {
        jdbc.sql("""
                        DELETE FROM promise_policy_topics
                         WHERE promise_id = :promiseId AND mapping_source = 'RULE'
                        """)
                .param("promiseId", promiseId)
                .update();
        for (Assignment assignment : assignments) {
            jdbc.sql("""
                            INSERT INTO promise_policy_topics (
                                promise_id, topic_code, relationship, mapping_source, mapping_version, mapped_at
                            )
                            SELECT :promiseId, :topicCode, :relationship, 'RULE', :mappingVersion, :mappedAt
                             WHERE NOT EXISTS (
                                 SELECT 1 FROM promise_policy_topics existing
                                  WHERE existing.promise_id = :promiseId
                                    AND existing.topic_code = :topicCode
                                    AND existing.mapping_source = 'EDITORIAL'
                             )
                            """)
                    .param("promiseId", promiseId)
                    .param("topicCode", assignment.code())
                    .param("relationship", assignment.relationship().name())
                    .param("mappingVersion", PolicyTopicClassifier.VERSION)
                    .param("mappedAt", utc(now), Types.TIMESTAMP_WITH_TIMEZONE)
                    .update();
        }
    }

    public Map<UUID, List<PromisePolicyTopic>> findPromiseTopics(List<UUID> promiseIds) {
        if (promiseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<PromisePolicyTopic>> byPromise = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT link.promise_id, topic.code, COALESCE(topic.parent_code, topic.code) AS broad_code,
                               link.relationship
                          FROM promise_policy_topics link
                          JOIN policy_topics topic ON topic.code = link.topic_code AND topic.active = TRUE
                         WHERE link.promise_id IN (:promiseIds)
                           AND NOT (
                               topic.parent_code IS NULL
                               AND EXISTS (
                                   SELECT 1
                                     FROM promise_policy_topics child_link
                                     JOIN policy_topics child ON child.code = child_link.topic_code
                                    WHERE child_link.promise_id = link.promise_id
                                      AND child.parent_code = topic.code
                                      AND child.active = TRUE
                               )
                           )
                         ORDER BY link.promise_id, broad_code, topic.sort_order
                        """)
                .param("promiseIds", promiseIds)
                .query((rs, rowNumber) -> new PromiseTopicRow(
                        rs.getObject("promise_id", UUID.class),
                        new PromisePolicyTopic(
                                rs.getString("code"),
                                rs.getString("broad_code"),
                                Relationship.valueOf(rs.getString("relationship")))))
                .list()
                .forEach(row -> byPromise
                        .computeIfAbsent(row.promiseId(), ignored -> new ArrayList<>())
                        .add(row.topic()));
        return byPromise.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
    }

    private PolicyTopic mapTopic(ResultSet rs, int rowNumber) throws SQLException {
        return new PolicyTopic(
                rs.getString("code"),
                rs.getString("parent_code"),
                new LocalizedText(rs.getString("label_ar"), rs.getString("label_fr"), rs.getString("label_en")),
                rs.getInt("sort_order"),
                rs.getBoolean("selectable"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record PromiseTopicRow(UUID promiseId, PromisePolicyTopic topic) {
    }
}
