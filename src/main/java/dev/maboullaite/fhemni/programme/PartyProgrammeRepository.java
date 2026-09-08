package dev.maboullaite.fhemni.programme;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PromiseAssessment.Evidence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PartyProgrammeRepository {

    private static final String PROGRAMME_COLUMNS = """
            id, party_code, election_year, term_start_year, term_end_year,
            title_ar, title_fr, title_en, summary_ar, summary_fr, summary_en,
            source_url, source_label, source_language, source_snapshot, extraction_warnings, source_sha256,
            source_retrieved_at, source_verified, editorial_status,
            created_at, updated_at, published_at
            """;
    private static final String PROGRAMME_PUBLIC_COLUMNS = """
            id, party_code, election_year, term_start_year, term_end_year,
            title_ar, title_fr, title_en, summary_ar, summary_fr, summary_en,
            source_url, source_label, source_language, NULL AS source_snapshot, NULL AS extraction_warnings, source_sha256,
            source_retrieved_at, source_verified, editorial_status,
            created_at, updated_at, published_at
            """;
    private static final String PROGRAMME_ADMIN_LIST_COLUMNS = """
            id, party_code, election_year, term_start_year, term_end_year,
            title_ar, title_fr, title_en, summary_ar, summary_fr, summary_en,
            source_url, source_label, source_language, NULL AS source_snapshot, extraction_warnings, source_sha256,
            source_retrieved_at, source_verified, editorial_status,
            created_at, updated_at, published_at
            """;
    private static final String PROMISE_COLUMNS = """
            id, programme_id, slug, topic, title_ar, title_fr, title_en,
            promise_text, source_locator, mechanism, financing, editorial_status,
            created_at, updated_at, published_at
            """;
    private static final String ASSESSMENT_COLUMNS = """
            id, promise_id, revision_number, horizon_years, verdict,
            summary_ar, summary_fr, summary_en,
            requirements_ar, requirements_fr, requirements_en,
            assumptions_ar, assumptions_fr, assumptions_en,
            calculation_notes_ar, calculation_notes_fr, calculation_notes_en,
            methodology_version, provider_mode, model_names, data_cutoff,
            editorial_status, created_at, published_at
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public PartyProgrammeRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public boolean visiblePartyExists(String partyCode) {
        return jdbc.sql("SELECT COUNT(*) FROM political_parties WHERE code = :code AND visible = TRUE")
                .param("code", partyCode)
                .query(Integer.class)
                .single() == 1;
    }

    public List<PartyProgramme> findAll() {
        return jdbc.sql("SELECT " + PROGRAMME_COLUMNS + " FROM party_programmes ORDER BY party_code")
                .query(this::mapProgramme)
                .list();
    }

    public List<PartyProgramme> findAllForAdminList() {
        return jdbc.sql("SELECT " + PROGRAMME_ADMIN_LIST_COLUMNS + " FROM party_programmes ORDER BY party_code")
                .query(this::mapProgramme)
                .list();
    }

    public Optional<PartyProgramme> findById(UUID id) {
        return jdbc.sql("SELECT " + PROGRAMME_COLUMNS + " FROM party_programmes WHERE id = :id")
                .param("id", id)
                .query(this::mapProgramme)
                .optional();
    }

    public Optional<PartyProgramme> findSummaryById(UUID id) {
        return jdbc.sql("SELECT " + PROGRAMME_PUBLIC_COLUMNS + " FROM party_programmes WHERE id = :id")
                .param("id", id)
                .query(this::mapProgramme)
                .optional();
    }

    public Optional<PartyProgramme> findByParty(String partyCode, int electionYear) {
        return jdbc.sql("""
                        SELECT %s FROM party_programmes
                         WHERE party_code = :partyCode AND election_year = :electionYear
                        """.formatted(PROGRAMME_COLUMNS))
                .param("partyCode", partyCode)
                .param("electionYear", electionYear)
                .query(this::mapProgramme)
                .optional();
    }

    public Optional<PartyProgramme> findBySourceUrl(String sourceUrl) {
        return jdbc.sql("SELECT " + PROGRAMME_COLUMNS + " FROM party_programmes WHERE source_url = :sourceUrl")
                .param("sourceUrl", sourceUrl)
                .query(this::mapProgramme)
                .optional();
    }

    public Optional<PartyProgramme> findPublishedByParty(String partyCode, int electionYear) {
        return jdbc.sql("""
                        SELECT %s FROM party_programmes
                         WHERE party_code = :partyCode
                           AND election_year = :electionYear
                           AND editorial_status = 'PUBLISHED'
                        """.formatted(PROGRAMME_PUBLIC_COLUMNS))
                .param("partyCode", partyCode)
                .param("electionYear", electionYear)
                .query(this::mapProgramme)
                .optional();
    }

    public void insertProgramme(PartyProgramme programme) {
        jdbc.sql("""
                        INSERT INTO party_programmes (
                            id, party_code, election_year, term_start_year, term_end_year,
                            title_ar, title_fr, title_en, summary_ar, summary_fr, summary_en,
                            source_url, source_label, source_language, source_snapshot, extraction_warnings, source_sha256,
                            source_retrieved_at, source_verified, editorial_status,
                            created_at, updated_at, published_at
                        ) VALUES (
                            :id, :partyCode, :electionYear, :termStartYear, :termEndYear,
                            :titleAr, :titleFr, :titleEn, :summaryAr, :summaryFr, :summaryEn,
                            :sourceUrl, :sourceLabel, :sourceLanguage, :sourceSnapshot, :extractionWarnings, :sourceSha256,
                            :sourceRetrievedAt, :sourceVerified, :status,
                            :createdAt, :updatedAt, :publishedAt
                        )
                        """)
                .param("id", programme.id())
                .param("partyCode", programme.partyCode())
                .param("electionYear", programme.electionYear())
                .param("termStartYear", programme.termStartYear())
                .param("termEndYear", programme.termEndYear())
                .param("titleAr", programme.title().ar())
                .param("titleFr", programme.title().fr())
                .param("titleEn", programme.title().en())
                .param("summaryAr", programme.summary().ar())
                .param("summaryFr", programme.summary().fr())
                .param("summaryEn", programme.summary().en())
                .param("sourceUrl", programme.sourceUrl())
                .param("sourceLabel", programme.sourceLabel())
                .param("sourceLanguage", programme.sourceLanguage())
                .param("sourceSnapshot", programme.sourceSnapshot(), Types.LONGVARCHAR)
                .param("extractionWarnings", writeWarnings(programme.extractionWarnings()), Types.LONGVARCHAR)
                .param("sourceSha256", programme.sourceSha256())
                .param("sourceRetrievedAt", utc(programme.sourceRetrievedAt()))
                .param("sourceVerified", programme.sourceVerified())
                .param("status", programme.status().name())
                .param("createdAt", utc(programme.createdAt()))
                .param("updatedAt", utc(programme.updatedAt()))
                .param("publishedAt", null, Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    public List<PartyPromise> findPromises(UUID programmeId, boolean publishedOnly) {
        String publication = publishedOnly ? " AND editorial_status = 'PUBLISHED'" : "";
        return jdbc.sql("SELECT " + PROMISE_COLUMNS
                        + " FROM party_promises WHERE programme_id = :programmeId"
                        + publication + " ORDER BY created_at")
                .param("programmeId", programmeId)
                .query(this::mapPromise)
                .list();
    }

    public Optional<PartyPromise> findPromise(UUID id) {
        return jdbc.sql("SELECT " + PROMISE_COLUMNS + " FROM party_promises WHERE id = :id")
                .param("id", id)
                .query(this::mapPromise)
                .optional();
    }

    public boolean promiseSlugExists(String slug) {
        return jdbc.sql("SELECT COUNT(*) FROM party_promises WHERE slug = :slug")
                .param("slug", slug)
                .query(Integer.class)
                .single() > 0;
    }

    public Optional<PartyPromise> findPublishedPromiseBySlug(String slug) {
        return jdbc.sql("""
                        SELECT p.id, p.programme_id, p.slug, p.topic,
                               p.title_ar, p.title_fr, p.title_en,
                               p.promise_text, p.source_locator, p.mechanism, p.financing,
                               p.editorial_status, p.created_at, p.updated_at, p.published_at
                          FROM party_promises p
                          JOIN party_programmes programme ON programme.id = p.programme_id
                         WHERE p.slug = :slug
                           AND p.editorial_status = 'PUBLISHED'
                           AND programme.editorial_status = 'PUBLISHED'
                        """)
                .param("slug", slug)
                .query(this::mapPromise)
                .optional();
    }

    public List<PublishedPromise> findFeaturedPublishedPromises(int limit) {
        return jdbc.sql("""
                        WITH eligible AS (
                            SELECT p.id, p.programme_id, p.slug, p.topic,
                                   p.title_ar, p.title_fr, p.title_en,
                                   p.promise_text, p.source_locator, p.mechanism, p.financing,
                                   p.editorial_status, p.created_at, p.updated_at, p.published_at,
                                   programme.party_code,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY programme.party_code ORDER BY RANDOM()
                                   ) AS promise_rank
                              FROM party_promises p
                              JOIN party_programmes programme ON programme.id = p.programme_id
                             WHERE p.editorial_status = 'PUBLISHED'
                               AND programme.editorial_status = 'PUBLISHED'
                               AND EXISTS (
                                   SELECT 1 FROM promise_assessments assessment
                                    WHERE assessment.promise_id = p.id
                                      AND assessment.editorial_status = 'PUBLISHED'
                               )
                        )
                        SELECT id, programme_id, slug, topic,
                               title_ar, title_fr, title_en,
                               promise_text, source_locator, mechanism, financing,
                               editorial_status, created_at, updated_at, published_at,
                               party_code
                          FROM eligible
                         WHERE promise_rank = 1
                         ORDER BY RANDOM()
                         LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, rowNumber) -> new PublishedPromise(
                        rs.getString("party_code"), mapPromise(rs, rowNumber)))
                .list();
    }

    public void insertPromise(PartyPromise promise) {
        jdbc.sql("""
                        INSERT INTO party_promises (
                            id, programme_id, slug, topic, title_ar, title_fr, title_en,
                            promise_text, source_locator, mechanism, financing, editorial_status,
                            created_at, updated_at, published_at
                        ) VALUES (
                            :id, :programmeId, :slug, :topic, :titleAr, :titleFr, :titleEn,
                            :promiseText, :sourceLocator, :mechanism, :financing, :status,
                            :createdAt, :updatedAt, :publishedAt
                        )
                        """)
                .param("id", promise.id())
                .param("programmeId", promise.programmeId())
                .param("slug", promise.slug())
                .param("topic", promise.topic())
                .param("titleAr", promise.title().ar())
                .param("titleFr", promise.title().fr())
                .param("titleEn", promise.title().en())
                .param("promiseText", promise.promiseText(), Types.LONGVARCHAR)
                .param("sourceLocator", promise.sourceLocator())
                .param("mechanism", promise.mechanism(), Types.LONGVARCHAR)
                .param("financing", promise.financing(), Types.LONGVARCHAR)
                .param("status", promise.status().name())
                .param("createdAt", utc(promise.createdAt()))
                .param("updatedAt", utc(promise.updatedAt()))
                .param("publishedAt", null, Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    public int nextAssessmentRevision(UUID promiseId) {
        return jdbc.sql("""
                        SELECT COALESCE(MAX(revision_number), 0) + 1
                          FROM promise_assessments WHERE promise_id = :promiseId
                        """)
                .param("promiseId", promiseId)
                .query(Integer.class)
                .single();
    }

    public void insertAssessment(PromiseAssessment assessment) {
        jdbc.sql("""
                        INSERT INTO promise_assessments (
                            id, promise_id, revision_number, horizon_years, verdict,
                            summary_ar, summary_fr, summary_en,
                            requirements_ar, requirements_fr, requirements_en,
                            assumptions_ar, assumptions_fr, assumptions_en,
                            calculation_notes_ar, calculation_notes_fr, calculation_notes_en,
                            methodology_version, provider_mode, model_names, data_cutoff,
                            editorial_status, created_at, published_at
                        ) VALUES (
                            :id, :promiseId, :revisionNumber, :horizonYears, :verdict,
                            :summaryAr, :summaryFr, :summaryEn,
                            :requirementsAr, :requirementsFr, :requirementsEn,
                            :assumptionsAr, :assumptionsFr, :assumptionsEn,
                            :calculationNotesAr, :calculationNotesFr, :calculationNotesEn,
                            :methodologyVersion, :providerMode, :modelNames, :dataCutoff,
                            :status, :createdAt, :publishedAt
                        )
                        """)
                .param("id", assessment.id())
                .param("promiseId", assessment.promiseId())
                .param("revisionNumber", assessment.revisionNumber())
                .param("horizonYears", assessment.horizonYears())
                .param("verdict", assessment.verdict().name())
                .param("summaryAr", assessment.summary().ar(), Types.LONGVARCHAR)
                .param("summaryFr", assessment.summary().fr(), Types.LONGVARCHAR)
                .param("summaryEn", assessment.summary().en(), Types.LONGVARCHAR)
                .param("requirementsAr", assessment.requirements().ar(), Types.LONGVARCHAR)
                .param("requirementsFr", assessment.requirements().fr(), Types.LONGVARCHAR)
                .param("requirementsEn", assessment.requirements().en(), Types.LONGVARCHAR)
                .param("assumptionsAr", assessment.assumptions().ar(), Types.LONGVARCHAR)
                .param("assumptionsFr", assessment.assumptions().fr(), Types.LONGVARCHAR)
                .param("assumptionsEn", assessment.assumptions().en(), Types.LONGVARCHAR)
                .param("calculationNotesAr", assessment.calculationNotes().ar(), Types.LONGVARCHAR)
                .param("calculationNotesFr", assessment.calculationNotes().fr(), Types.LONGVARCHAR)
                .param("calculationNotesEn", assessment.calculationNotes().en(), Types.LONGVARCHAR)
                .param("methodologyVersion", assessment.methodologyVersion())
                .param("providerMode", assessment.providerMode())
                .param("modelNames", assessment.modelNames())
                .param("dataCutoff", assessment.dataCutoff())
                .param("status", assessment.status().name())
                .param("createdAt", utc(assessment.createdAt()))
                .param("publishedAt", null, Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
        for (Evidence evidence : assessment.evidence()) {
            insertEvidence(assessment.id(), evidence);
        }
    }

    public Optional<PromiseAssessment> findAssessment(UUID assessmentId) {
        return jdbc.sql("SELECT " + ASSESSMENT_COLUMNS + " FROM promise_assessments WHERE id = :id")
                .param("id", assessmentId)
                .query(this::mapAssessment)
                .optional()
                .map(this::withEvidence);
    }

    public List<PromiseAssessment> findAssessments(UUID promiseId, boolean publishedOnly) {
        return findAssessments(List.of(promiseId), publishedOnly).getOrDefault(promiseId, List.of());
    }

    public Map<UUID, List<PromiseAssessment>> findAssessments(
            List<UUID> promiseIds,
            boolean publishedOnly) {
        if (promiseIds == null || promiseIds.isEmpty()) {
            return Map.of();
        }
        String publication = publishedOnly ? " AND editorial_status = 'PUBLISHED'" : "";
        List<PromiseAssessment> assessments = jdbc.sql("SELECT " + ASSESSMENT_COLUMNS
                        + " FROM promise_assessments WHERE promise_id IN (:promiseIds)"
                        + publication + " ORDER BY revision_number DESC")
                .param("promiseIds", promiseIds)
                .query(this::mapAssessment)
                .list();
        Map<UUID, List<PromiseAssessment>> grouped = new LinkedHashMap<>();
        promiseIds.forEach(id -> grouped.put(id, new ArrayList<>()));
        for (PromiseAssessment assessment : withEvidence(assessments)) {
            grouped.computeIfAbsent(assessment.promiseId(), ignored -> new ArrayList<>()).add(assessment);
        }
        grouped.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(grouped);
    }

    public void publishAssessment(UUID assessmentId, UUID promiseId, Instant publishedAt) {
        lockPromise(promiseId);
        jdbc.sql("""
                        UPDATE promise_assessments
                           SET editorial_status = 'SUPERSEDED'
                         WHERE promise_id = :promiseId AND editorial_status = 'PUBLISHED'
                        """)
                .param("promiseId", promiseId)
                .update();
        int updated = jdbc.sql("""
                        UPDATE promise_assessments
                           SET editorial_status = 'PUBLISHED', published_at = :publishedAt
                         WHERE id = :assessmentId AND editorial_status = 'DRAFT'
                        """)
                .param("publishedAt", utc(publishedAt))
                .param("assessmentId", assessmentId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Only a draft assessment can be published.");
        }
    }

    public void lockPromise(UUID promiseId) {
        jdbc.sql("SELECT id FROM party_promises WHERE id = :promiseId FOR UPDATE")
                .param("promiseId", promiseId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new java.util.NoSuchElementException("Promise not found."));
    }

    public void publishPromise(UUID promiseId, Instant publishedAt) {
        int updated = jdbc.sql("""
                        UPDATE party_promises
                           SET editorial_status = 'PUBLISHED', published_at = :publishedAt, updated_at = :publishedAt
                         WHERE id = :promiseId AND editorial_status = 'DRAFT'
                           AND EXISTS (
                               SELECT 1 FROM promise_assessments assessment
                                WHERE assessment.promise_id = party_promises.id
                                  AND assessment.editorial_status = 'PUBLISHED'
                           )
                        """)
                .param("publishedAt", utc(publishedAt))
                .param("promiseId", promiseId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("A draft promise needs a published assessment before publication.");
        }
    }

    public void publishProgramme(UUID programmeId, Instant publishedAt) {
        int updated = jdbc.sql("""
                        UPDATE party_programmes
                           SET editorial_status = 'PUBLISHED', published_at = :publishedAt, updated_at = :publishedAt
                         WHERE id = :programmeId AND editorial_status = 'DRAFT'
                           AND source_verified = TRUE
                           AND EXISTS (
                               SELECT 1 FROM party_promises promise
                                WHERE promise.programme_id = party_programmes.id
                                  AND promise.editorial_status = 'PUBLISHED'
                           )
                        """)
                .param("publishedAt", utc(publishedAt))
                .param("programmeId", programmeId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException(
                    "A draft programme needs a verified 2026 source and a published promise before publication.");
        }
    }

    public void verifySource(UUID programmeId, Instant verifiedAt) {
        int updated = jdbc.sql("""
                        UPDATE party_programmes
                           SET source_verified = TRUE, updated_at = :verifiedAt
                         WHERE id = :programmeId AND editorial_status = 'DRAFT'
                        """)
                .param("verifiedAt", utc(verifiedAt))
                .param("programmeId", programmeId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Only a draft programme source can be verified.");
        }
    }

    public void deleteDraftAssessment(UUID assessmentId) {
        int deleted = jdbc.sql("DELETE FROM promise_assessments WHERE id = :id AND editorial_status = 'DRAFT'")
                .param("id", assessmentId)
                .update();
        if (deleted != 1) {
            throw new IllegalStateException("Only a draft assessment can be discarded.");
        }
    }

    public void deleteDraftPromise(UUID promiseId) {
        int deleted = jdbc.sql("DELETE FROM party_promises WHERE id = :id AND editorial_status = 'DRAFT'")
                .param("id", promiseId)
                .update();
        if (deleted != 1) {
            throw new IllegalStateException("Only a draft promise can be discarded.");
        }
    }

    public void deleteDraftProgramme(UUID programmeId) {
        int deleted = jdbc.sql("DELETE FROM party_programmes WHERE id = :id AND editorial_status = 'DRAFT'")
                .param("id", programmeId)
                .update();
        if (deleted != 1) {
            throw new IllegalStateException("Only a draft programme can be discarded.");
        }
    }

    private void insertEvidence(UUID assessmentId, Evidence evidence) {
        jdbc.sql("""
                        INSERT INTO promise_evidence (
                            id, assessment_id, publisher, title, url, published_on, note, sort_order
                        ) VALUES (
                            :id, :assessmentId, :publisher, :title, :url, :publishedOn, :note, :sortOrder
                        )
                        """)
                .param("id", evidence.id())
                .param("assessmentId", assessmentId)
                .param("publisher", evidence.publisher())
                .param("title", evidence.title())
                .param("url", evidence.url())
                .param("publishedOn", evidence.publishedOn(), Types.DATE)
                .param("note", evidence.note(), Types.LONGVARCHAR)
                .param("sortOrder", evidence.sortOrder())
                .update();
    }

    private PromiseAssessment withEvidence(PromiseAssessment assessment) {
        return withEvidence(List.of(assessment)).getFirst();
    }

    private List<PromiseAssessment> withEvidence(List<PromiseAssessment> assessments) {
        if (assessments.isEmpty()) {
            return List.of();
        }
        List<UUID> assessmentIds = assessments.stream().map(PromiseAssessment::id).toList();
        Map<UUID, List<Evidence>> evidenceByAssessment = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT assessment_id, id, publisher, title, url, published_on, note, sort_order
                          FROM promise_evidence WHERE assessment_id IN (:assessmentIds)
                         ORDER BY assessment_id, sort_order
                        """)
                .param("assessmentIds", assessmentIds)
                .query((rs, rowNumber) -> new AssessmentEvidence(
                        rs.getObject("assessment_id", UUID.class), mapEvidence(rs, rowNumber)))
                .list()
                .forEach(row -> evidenceByAssessment
                        .computeIfAbsent(row.assessmentId(), ignored -> new ArrayList<>())
                        .add(row.evidence()));
        return assessments.stream().map(assessment -> new PromiseAssessment(
                        assessment.id(), assessment.promiseId(), assessment.revisionNumber(), assessment.horizonYears(),
                        assessment.verdict(), assessment.summary(), assessment.requirements(), assessment.assumptions(),
                        assessment.calculationNotes(), assessment.methodologyVersion(), assessment.providerMode(),
                        assessment.modelNames(), assessment.dataCutoff(), assessment.status(), assessment.createdAt(),
                        assessment.publishedAt(), List.copyOf(evidenceByAssessment.getOrDefault(
                                assessment.id(), List.of()))))
                .toList();
    }

    private PartyProgramme mapProgramme(ResultSet rs, int rowNumber) throws SQLException {
        return new PartyProgramme(
                rs.getObject("id", UUID.class), rs.getString("party_code"), rs.getInt("election_year"),
                rs.getInt("term_start_year"), rs.getInt("term_end_year"),
                localized(rs, "title"), localized(rs, "summary"),
                rs.getString("source_url"), rs.getString("source_label"), rs.getString("source_language"),
                rs.getString("source_snapshot"), readWarnings(rs.getString("extraction_warnings")),
                rs.getString("source_sha256"),
                instant(rs, "source_retrieved_at"), rs.getBoolean("source_verified"),
                EditorialStatus.valueOf(rs.getString("editorial_status")),
                instant(rs, "created_at"), instant(rs, "updated_at"), nullableInstant(rs, "published_at"));
    }

    public record PublishedPromise(String partyCode, PartyPromise promise) {
    }

    private record AssessmentEvidence(UUID assessmentId, Evidence evidence) {
    }

    private PartyPromise mapPromise(ResultSet rs, int rowNumber) throws SQLException {
        return new PartyPromise(
                rs.getObject("id", UUID.class), rs.getObject("programme_id", UUID.class),
                rs.getString("slug"), rs.getString("topic"), localized(rs, "title"),
                rs.getString("promise_text"), rs.getString("source_locator"),
                rs.getString("mechanism"), rs.getString("financing"),
                EditorialStatus.valueOf(rs.getString("editorial_status")),
                instant(rs, "created_at"), instant(rs, "updated_at"), nullableInstant(rs, "published_at"));
    }

    private PromiseAssessment mapAssessment(ResultSet rs, int rowNumber) throws SQLException {
        return new PromiseAssessment(
                rs.getObject("id", UUID.class), rs.getObject("promise_id", UUID.class),
                rs.getInt("revision_number"), rs.getInt("horizon_years"),
                FeasibilityVerdict.valueOf(rs.getString("verdict")), localized(rs, "summary"),
                localized(rs, "requirements"), localized(rs, "assumptions"),
                localized(rs, "calculation_notes"), rs.getString("methodology_version"),
                rs.getString("provider_mode"), rs.getString("model_names"),
                rs.getObject("data_cutoff", LocalDate.class),
                EditorialStatus.valueOf(rs.getString("editorial_status")),
                instant(rs, "created_at"), nullableInstant(rs, "published_at"), List.of());
    }

    private Evidence mapEvidence(ResultSet rs, int rowNumber) throws SQLException {
        return new Evidence(
                rs.getObject("id", UUID.class), rs.getString("publisher"), rs.getString("title"),
                rs.getString("url"), rs.getObject("published_on", LocalDate.class),
                rs.getString("note"), rs.getInt("sort_order"));
    }

    private static LocalizedText localized(ResultSet rs, String prefix) throws SQLException {
        return new LocalizedText(
                rs.getString(prefix + "_ar"), rs.getString(prefix + "_fr"), rs.getString(prefix + "_en"));
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

    private String writeWarnings(List<String> warnings) {
        try {
            return mapper.writeValueAsString(warnings == null ? List.of() : warnings);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Programme extraction warnings could not be stored.", exception);
        }
    }

    private List<String> readWarnings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(mapper.readValue(json, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored programme extraction warnings could not be read.", exception);
        }
    }
}
