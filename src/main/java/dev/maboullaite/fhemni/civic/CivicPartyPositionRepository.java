package dev.maboullaite.fhemni.civic;

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
import java.util.UUID;

import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.LocalizedText;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CivicPartyPositionRepository {

    private final JdbcClient jdbc;

    CivicPartyPositionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<PositionRow> publishedPositions(UUID editionId) {
        List<PositionRow> positions = jdbc.sql("""
                        SELECT p.id, p.edition_id, p.question_id, p.party_code, p.stance,
                               p.evidence_summary_ar, p.evidence_summary_fr, p.evidence_summary_en,
                               q.question_key
                          FROM civic_party_positions p
                          JOIN civic_questions q ON q.id = p.question_id
                         WHERE p.edition_id = :editionId AND p.editorial_status = 'PUBLISHED'
                         ORDER BY q.sort_order, p.party_code
                        """)
                .param("editionId", editionId)
                .query(this::mapPosition)
                .list();
        return withEvidence(positions);
    }

    List<PositionRow> allPositions(UUID editionId) {
        List<PositionRow> positions = jdbc.sql("""
                        SELECT p.id, p.edition_id, p.question_id, p.party_code, p.stance,
                               p.evidence_summary_ar, p.evidence_summary_fr, p.evidence_summary_en,
                               q.question_key
                          FROM civic_party_positions p
                          JOIN civic_questions q ON q.id = p.question_id
                         WHERE p.edition_id = :editionId
                         ORDER BY q.sort_order, p.party_code
                        """)
                .param("editionId", editionId)
                .query(this::mapPosition)
                .list();
        return withEvidence(positions);
    }

    void upsert(UUID id, UUID editionId, UUID questionId, String partyCode,
                PartyPositionStance stance, LocalizedText evidenceSummary,
                String reviewerNote) {
        jdbc.sql("""
                        MERGE INTO civic_party_positions (
                            id, edition_id, question_id, party_code, stance,
                            evidence_summary_ar, evidence_summary_fr, evidence_summary_en,
                            editorial_status, reviewer_note, created_at
                        ) KEY (edition_id, question_id, party_code)
                        VALUES (
                            :id, :editionId, :questionId, :partyCode, :stance,
                            :summaryAr, :summaryFr, :summaryEn,
                            'DRAFT', :reviewerNote, CURRENT_TIMESTAMP
                        )
                        """)
                .param("id", id)
                .param("editionId", editionId)
                .param("questionId", questionId)
                .param("partyCode", partyCode)
                .param("stance", stance.name())
                .param("summaryAr", evidenceSummary.ar())
                .param("summaryFr", evidenceSummary.fr())
                .param("summaryEn", evidenceSummary.en())
                .param("reviewerNote", reviewerNote, Types.VARCHAR)
                .update();
    }

    void publish(UUID positionId, Instant publishedAt) {
        int updated = jdbc.sql("""
                        UPDATE civic_party_positions
                           SET editorial_status = 'PUBLISHED', published_at = :publishedAt
                         WHERE id = :positionId AND editorial_status IN ('DRAFT', 'REVIEWED')
                        """)
                .param("positionId", positionId)
                .param("publishedAt", publishedAt.atOffset(ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Only a draft or reviewed position can be published.");
        }
    }

    int publishAllDrafts(UUID editionId, Instant publishedAt) {
        return jdbc.sql("""
                        UPDATE civic_party_positions
                           SET editorial_status = 'PUBLISHED', published_at = :publishedAt
                         WHERE edition_id = :editionId AND editorial_status IN ('DRAFT', 'REVIEWED')
                        """)
                .param("editionId", editionId)
                .param("publishedAt", publishedAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    void addEvidence(UUID positionId, String labelAr, String labelFr, String labelEn,
                     String sourceUrl, String pageReference, UUID promiseId, int sortOrder) {
        jdbc.sql("""
                        INSERT INTO civic_position_evidence (
                            position_id, label_ar, label_fr, label_en,
                            source_url, page_reference, promise_id, sort_order
                        ) VALUES (
                            :positionId, :labelAr, :labelFr, :labelEn,
                            :sourceUrl, :pageReference, :promiseId, :sortOrder
                        )
                        """)
                .param("positionId", positionId)
                .param("labelAr", labelAr)
                .param("labelFr", labelFr)
                .param("labelEn", labelEn)
                .param("sourceUrl", sourceUrl, Types.VARCHAR)
                .param("pageReference", pageReference, Types.VARCHAR)
                .param("promiseId", promiseId, Types.OTHER)
                .param("sortOrder", sortOrder)
                .update();
    }

    void clearEvidence(UUID positionId) {
        jdbc.sql("DELETE FROM civic_position_evidence WHERE position_id = :positionId")
                .param("positionId", positionId)
                .update();
    }

    int positionCount(UUID editionId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM civic_party_positions
                         WHERE edition_id = :editionId AND editorial_status = 'PUBLISHED'
                        """)
                .param("editionId", editionId)
                .query(Integer.class)
                .single();
    }

    private List<PositionRow> withEvidence(List<PositionRow> positions) {
        if (positions.isEmpty()) return List.of();
        List<UUID> positionIds = positions.stream().map(PositionRow::id).toList();
        Map<UUID, List<EvidenceRow>> evidenceByPosition = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT e.position_id, e.label_ar, e.label_fr, e.label_en,
                               e.source_url, e.page_reference, p.slug AS promise_slug
                          FROM civic_position_evidence e
                          LEFT JOIN party_promises p ON p.id = e.promise_id
                         WHERE e.position_id IN (:positionIds)
                         ORDER BY e.position_id, e.sort_order
                        """)
                .param("positionIds", positionIds)
                .query((rs, rowNumber) -> {
                    UUID posId = rs.getObject("position_id", UUID.class);
                    EvidenceRow row = new EvidenceRow(
                            new LocalizedText(rs.getString("label_ar"), rs.getString("label_fr"), rs.getString("label_en")),
                            rs.getString("source_url"),
                            rs.getString("page_reference"),
                            rs.getString("promise_slug"));
                    evidenceByPosition.computeIfAbsent(posId, ignored -> new ArrayList<>()).add(row);
                    return null;
                })
                .list();
        return positions.stream()
                .map(p -> new PositionRow(p.id(), p.editionId(), p.questionId(), p.questionKey(),
                        p.partyCode(), p.stance(), p.evidenceSummary(),
                        List.copyOf(evidenceByPosition.getOrDefault(p.id(), List.of()))))
                .toList();
    }

    private PositionRow mapPosition(ResultSet rs, int rowNumber) throws SQLException {
        return new PositionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getObject("question_id", UUID.class),
                rs.getString("question_key"),
                rs.getString("party_code"),
                PartyPositionStance.valueOf(rs.getString("stance")),
                new LocalizedText(
                        rs.getString("evidence_summary_ar"),
                        rs.getString("evidence_summary_fr"),
                        rs.getString("evidence_summary_en")),
                List.of());
    }

    record PositionRow(
            UUID id,
            UUID editionId,
            UUID questionId,
            String questionKey,
            String partyCode,
            PartyPositionStance stance,
            LocalizedText evidenceSummary,
            List<EvidenceRow> evidence) {
    }

    record EvidenceRow(
            LocalizedText label,
            String sourceUrl,
            String pageReference,
            String promiseSlug) {
    }
}
