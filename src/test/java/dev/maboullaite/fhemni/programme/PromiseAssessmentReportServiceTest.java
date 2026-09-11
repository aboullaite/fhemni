package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgrammeService.PublicPromiseView;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Category;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReport.Status;
import org.junit.jupiter.api.Test;

class PromiseAssessmentReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T18:00:00Z");

    private final PromiseAssessmentReportRepository reports = mock(PromiseAssessmentReportRepository.class);
    private final PartyProgrammeService programmes = mock(PartyProgrammeService.class);
    private final PromiseAssessmentReportService service = new PromiseAssessmentReportService(
            reports, programmes, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void attachesAReaderReportToTheCurrentlyPublishedAssessment() {
        UUID promiseId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PublicPromiseView promise = mock(PublicPromiseView.class);
        PromiseAssessment assessment = mock(PromiseAssessment.class);
        when(promise.id()).thenReturn(promiseId);
        when(promise.assessment()).thenReturn(assessment);
        when(assessment.id()).thenReturn(assessmentId);
        when(programmes.publishedPromise("fuel-margin-cap")).thenReturn(promise);
        PromiseAssessmentReport saved = report(
                promiseId, assessmentId, userId,
                "Articles 2, 3 and 63 appear to permit a permanent margin cap.",
                "https://adala.justice.gov.ma/law.pdf");
        when(reports.save(
                promiseId, assessmentId, userId, Category.FACTUAL_OR_LEGAL_ERROR,
                saved.details(), saved.sourceUrl(), NOW)).thenReturn(saved);

        PromiseAssessmentReport actual = service.submit(
                "fuel-margin-cap", userId, Category.FACTUAL_OR_LEGAL_ERROR,
                "  " + saved.details() + "  ", saved.sourceUrl());

        assertThat(actual).isSameAs(saved);
        verify(reports).save(
                promiseId, assessmentId, userId, Category.FACTUAL_OR_LEGAL_ERROR,
                saved.details(), saved.sourceUrl(), NOW);
    }

    @Test
    void labelsReaderClaimsAsUntrustedContextForFocusedReanalysis() {
        UUID promiseId = UUID.randomUUID();
        PromiseAssessmentReport report = report(
                promiseId, UUID.randomUUID(), UUID.randomUUID(),
                "The legal mechanism in the published assessment may be incomplete.",
                "https://adala.justice.gov.ma/law.pdf");
        when(reports.openReports(promiseId)).thenReturn(List.of(report));

        String context = service.reviewContext(
                promiseId, "Check the consolidated act and its implementing decree.");

        assertThat(context)
                .contains("untrusted claim to investigate")
                .contains(report.details())
                .contains(report.sourceUrl())
                .contains("Check the consolidated act");
    }

    @Test
    void rejectsNonHttpsSourcesBeforeSavingAReport() {
        assertThatThrownBy(() -> service.submit(
                "fuel-margin-cap", UUID.randomUUID(), Category.OTHER,
                "This report contains enough detail to be reviewed.",
                "http://example.org/source"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");

        verifyNoInteractions(reports);
    }

    private static PromiseAssessmentReport report(
            UUID promiseId,
            UUID assessmentId,
            UUID userId,
            String details,
            String sourceUrl) {
        return new PromiseAssessmentReport(
                UUID.randomUUID(), promiseId, assessmentId, userId,
                Category.FACTUAL_OR_LEGAL_ERROR, details, sourceUrl,
                Status.OPEN, NOW, NOW, null);
    }
}
