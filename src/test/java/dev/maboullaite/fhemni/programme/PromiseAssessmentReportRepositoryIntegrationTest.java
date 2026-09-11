package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftAssessment;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftProgramme;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftPromise;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.EvidenceDraft;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Category;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:assessment-report-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class PromiseAssessmentReportRepositoryIntegrationTest {

    @Autowired
    private PartyProgrammeService programmes;

    @Autowired
    private PromiseAssessmentReportRepository reports;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM party_programmes").update();
        jdbc.sql("DELETE FROM app_users").update();
    }

    @Test
    void resubmissionUpdatesAndReopensTheSameAssessmentReport() {
        var promise = publishedPromise();
        UUID userId = user();
        Instant firstAt = Instant.parse("2026-09-11T18:00:00Z");
        PromiseAssessmentReport first = reports.save(
                promise.id(), promise.assessment().id(), userId,
                Category.FACTUAL_OR_LEGAL_ERROR,
                "The interpretation of the competition law may be incomplete.",
                "https://adala.justice.gov.ma/first.pdf", firstAt);
        reports.close(first.id(), Status.DISMISSED, firstAt.plusSeconds(60));

        PromiseAssessmentReport updated = reports.save(
                promise.id(), promise.assessment().id(), userId,
                Category.OUTDATED_OR_MISSING_SOURCE,
                "Please review Articles 2, 3 and 63 of the consolidated act.",
                "https://adala.justice.gov.ma/consolidated.pdf", firstAt.plusSeconds(120));

        assertThat(updated.id()).isEqualTo(first.id());
        assertThat(updated.status()).isEqualTo(Status.OPEN);
        assertThat(updated.category()).isEqualTo(Category.OUTDATED_OR_MISSING_SOURCE);
        assertThat(updated.sourceUrl()).endsWith("consolidated.pdf");
        assertThat(reports.openReports(promise.id())).containsExactly(updated);

        reports.resolveForPromise(promise.id(), firstAt.plusSeconds(180));
        assertThat(reports.openReports(promise.id())).isEmpty();
    }

    private PartyProgrammeService.PublicPromiseView publishedPromise() {
        var programme = programmes.createProgramme(new DraftProgramme(
                "PJD", text("برنامج", "Programme", "Programme"),
                text("الخلاصة", "Résumé", "Summary"),
                "https://example.org/pjd-2026.pdf", "Official programme", "ar",
                "Frozen official source snapshot.", true, List.of()));
        var promise = programmes.createPromise(programme.id(), new DraftPromise(
                "pjd-report-promise", "economy", text("وعد", "Promesse", "Promise"),
                "Cap fuel distribution margins.", "Page 10", "Regulation", "None"));
        programmes.createAssessment(promise.promise().id(), new DraftAssessment(
                FeasibilityVerdict.HARD,
                text("صعيب", "Difficile", "Hard"),
                text("شروط", "Conditions", "Conditions"),
                text("فرضيات", "Hypothèses", "Assumptions"),
                text("حساب", "Calcul", "Calculation"),
                "method-v1", LocalDate.of(2026, 9, 1),
                List.of(new EvidenceDraft(
                        "Official source", "Indicator", "https://example.org/indicator",
                        LocalDate.of(2026, 8, 1), "Official baseline"))));
        programmes.publishAll(programme.id());
        return programmes.publishedPromise(promise.promise().slug());
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-11T17:00:00Z");
        jdbc.sql("""
                        INSERT INTO app_users (
                            id, display_name, role, created_at, updated_at, last_login_at
                        ) VALUES (:id, 'Reader', 'USER', :now, :now, :now)
                        """)
                .param("id", id)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
        return id;
    }

    private static LocalizedText text(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
