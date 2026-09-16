package dev.maboullaite.fhemni.civic;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CivicQuestionnaireRepository {

    private final JdbcClient jdbc;

    CivicQuestionnaireRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    EditionData currentPublished() {
        return jdbc.sql("""
                        SELECT id, version, title_ar, title_fr, title_en,
                               intro_ar, intro_fr, intro_en,
                               methodology_ar, methodology_fr, methodology_en,
                               data_cutoff
                          FROM civic_questionnaire_editions
                         WHERE status = 'PUBLISHED'
                        """)
                .query(this::mapEdition)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("No published priority questionnaire is available."));
    }

    EditionData published(UUID editionId) {
        return jdbc.sql("""
                        SELECT id, version, title_ar, title_fr, title_en,
                               intro_ar, intro_fr, intro_en,
                               methodology_ar, methodology_fr, methodology_en,
                               data_cutoff
                          FROM civic_questionnaire_editions
                         WHERE id = :editionId AND status IN ('PUBLISHED', 'SUPERSEDED')
                        """)
                .param("editionId", editionId)
                .query(this::mapEdition)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("Priority questionnaire edition not found."));
    }

    List<ThemeData> themes(UUID editionId) {
        return jdbc.sql("""
                        SELECT code, label_ar, label_fr, label_en, sort_order
                          FROM civic_questionnaire_themes
                         WHERE edition_id = :editionId
                         ORDER BY sort_order
                        """)
                .param("editionId", editionId)
                .query((rs, rowNumber) -> new ThemeData(
                        rs.getString("code"), localized(rs, "label"), rs.getInt("sort_order")))
                .list();
    }

    List<QuestionData> questions(UUID editionId) {
        return jdbc.sql("""
                        SELECT id, question_key, theme_code, sort_order,
                               prompt_ar, prompt_fr, prompt_en,
                               context_ar, context_fr, context_en
                          FROM civic_questions
                         WHERE edition_id = :editionId AND status = 'PUBLISHED'
                         ORDER BY sort_order
                        """)
                .param("editionId", editionId)
                .query((rs, rowNumber) -> {
                    UUID questionId = rs.getObject("id", UUID.class);
                    return new QuestionData(
                            questionId,
                            rs.getString("question_key"),
                            rs.getString("theme_code"),
                            rs.getInt("sort_order"),
                            localized(rs, "prompt"),
                            localized(rs, "context"),
                            sources(questionId));
                })
                .list();
    }

    private List<SourceData> sources(UUID questionId) {
        return jdbc.sql("""
                        SELECT label_ar, label_fr, label_en, source_url, source_date
                          FROM civic_question_sources
                         WHERE question_id = :questionId
                         ORDER BY sort_order, id
                        """)
                .param("questionId", questionId)
                .query((rs, rowNumber) -> new SourceData(
                        localized(rs, "label"),
                        rs.getString("source_url"),
                        rs.getObject("source_date", LocalDate.class)))
                .list();
    }

    private EditionData mapEdition(ResultSet rs, int rowNumber) throws SQLException {
        return new EditionData(
                rs.getObject("id", UUID.class),
                rs.getString("version"),
                localized(rs, "title"),
                localized(rs, "intro"),
                localized(rs, "methodology"),
                rs.getObject("data_cutoff", LocalDate.class));
    }

    private LocalizedText localized(ResultSet rs, String prefix) throws SQLException {
        return new LocalizedText(
                rs.getString(prefix + "_ar"),
                rs.getString(prefix + "_fr"),
                rs.getString(prefix + "_en"));
    }

    record LocalizedText(String ar, String fr, String en) {
        String get(String language) {
            return switch (language) {
                case "ar" -> ar;
                case "fr" -> fr;
                case "en" -> en;
                default -> throw new IllegalArgumentException("Supported languages are ar, fr and en.");
            };
        }
    }

    record EditionData(
            UUID id,
            String version,
            LocalizedText title,
            LocalizedText intro,
            LocalizedText methodology,
            LocalDate dataCutoff) {
    }

    record ThemeData(String code, LocalizedText label, int order) {
    }

    record QuestionData(
            UUID id,
            String key,
            String themeCode,
            int order,
            LocalizedText prompt,
            LocalizedText context,
            List<SourceData> sources) {
    }

    record SourceData(LocalizedText label, String url, LocalDate date) {
    }
}
