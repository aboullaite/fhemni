package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractionResult;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ProgrammeExtraction;
import dev.maboullaite.fhemni.programme.ProgrammeFactCheckService.FactCheckResult;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProgrammeIngestionServiceTest {

    @Test
    void normalizesAProgrammeUrlWithoutChangingItsEncodedPath() {
        assertThat(ProgrammeIngestionService.publicHttpsUrl(
                " HTTPS://PAM.MA:443/programme%20electoral/?year=2026#download "))
                .isEqualTo("https://pam.ma/programme%20electoral/?year=2026");
    }

    @Test
    void removesMarketingTrackingWithoutDroppingFunctionalQueryParameters() {
        assertThat(ProgrammeIngestionService.publicHttpsUrl(
                "https://pam.ma/programme-electoral/?utm_source=chatgpt.com&year=2026&fbclid=abc"))
                .isEqualTo("https://pam.ma/programme-electoral/?year=2026");
    }

    @Test
    void rejectsAFileThatOnlyPretendsToBeAPdf() {
        var service = new ProgrammeIngestionService(null, null, null, null);
        var document = new MockMultipartFile(
                "document", "programme.pdf", "application/pdf", "not a pdf".getBytes());

        assertThatThrownBy(() -> service.ingestPdf("https://party.ma/programme", document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid PDF");
    }

    @Test
    void rejectsNonPublicOrCredentialBearingUrlsBeforeGeminiSeesThem() {
        assertRejected("http://party.ma/programme");
        assertRejected("https://localhost/programme");
        assertRejected("https://127.0.0.1/programme");
        assertRejected("https://192.168.1.10/programme");
        assertRejected("https://admin:secret@party.ma/programme");
        assertRejected("https://party.local/programme");
    }

    @Test
    void explicitlyReplacesACachedDraftWhenTheAdminUploadsACorrectedPdf() {
        ProgrammeIntelligenceGateway gateway = mock(ProgrammeIntelligenceGateway.class);
        ProgrammeFactCheckService factChecks = mock(ProgrammeFactCheckService.class);
        PartyProgrammeService programmes = mock(PartyProgrammeService.class);
        AiUsageGuard usageGuard = mock(AiUsageGuard.class);
        ProgrammeIngestionService service = new ProgrammeIngestionService(
                gateway, factChecks, programmes, usageGuard);
        String sourceUrl = "https://party.ma/programme";
        AdminProgrammeView existing = programme(sourceUrl, "old");
        AdminProgrammeView replacement = programme(sourceUrl, "new");
        LocalizedText text = new LocalizedText("برنامج", "Programme", "Programme");
        ProgrammeExtraction extraction = new ProgrammeExtraction(
                "PJD", 2026, true, "Official programme", "ar", "corrected snapshot",
                text, text, List.of(), List.of());
        var document = new MockMultipartFile(
                "document", "programme.pdf", "application/pdf", "%PDF-corrected".getBytes());

        when(gateway.live()).thenReturn(true);
        when(factChecks.ready()).thenReturn(true);
        when(programmes.programmeBySourceUrl(sourceUrl)).thenReturn(Optional.of(existing));
        when(gateway.extractPdf(eq(sourceUrl), eq("programme.pdf"), any(), anyLong()))
                .thenReturn(new ExtractionResult(extraction, AiUsage.empty()));
        when(programmes.replaceGeneratedExtraction(existing.id(), sourceUrl, extraction, List.of()))
                .thenReturn(replacement);
        when(programmes.promisesAwaitingAssessment(replacement)).thenReturn(List.of());

        var result = service.ingestPdf(sourceUrl, document, true);

        assertThat(result.extracted()).isTrue();
        assertThat(result.cacheHit()).isFalse();
        verify(programmes).replaceGeneratedExtraction(existing.id(), sourceUrl, extraction, List.of());
    }

    @Test
    void keepsCompletedAssessmentBatchesWhenALaterBatchFails() {
        ProgrammeIntelligenceGateway gateway = mock(ProgrammeIntelligenceGateway.class);
        ProgrammeFactCheckService factChecks = mock(ProgrammeFactCheckService.class);
        PartyProgrammeService programmes = mock(PartyProgrammeService.class);
        AiUsageGuard usageGuard = mock(AiUsageGuard.class);
        ProgrammeIngestionService service = new ProgrammeIngestionService(
                gateway, factChecks, programmes, usageGuard);
        String sourceUrl = "https://party.ma/programme";
        AdminProgrammeView existing = programme(sourceUrl, "saved");
        AdminProgrammeView afterFirstBatch = programme(sourceUrl, "partially-assessed");
        LocalizedText text = new LocalizedText("وعد", "Promesse", "Promise");
        List<ExtractedPromise> pending = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new ExtractedPromise(
                        "pjd-promise-" + index, "topic", text, "promise", "page", "", ""))
                .toList();
        FactCheckResult generated = new FactCheckResult(List.of(), "consensus", "models", "method");

        when(gateway.live()).thenReturn(true);
        when(factChecks.ready()).thenReturn(true);
        when(programmes.programmeBySourceUrl(sourceUrl)).thenReturn(Optional.of(existing));
        when(programmes.promisesAwaitingAssessment(existing)).thenReturn(pending);
        when(factChecks.assess(eq(sourceUrl), any()))
                .thenReturn(generated)
                .thenThrow(new ProgrammeFactCheckException("temporary failure", null));
        when(programmes.saveGeneratedAssessments(
                eq(existing.id()), argThat(batch -> batch.size() == 6), eq(generated)))
                .thenReturn(afterFirstBatch);

        var result = service.ingest(sourceUrl);

        assertThat(result.programme()).isSameAs(afterFirstBatch);
        assertThat(result.assessed()).isTrue();
        assertThat(result.assessmentPending()).isTrue();
        verify(programmes).saveGeneratedAssessments(
                eq(existing.id()), argThat(batch -> batch.size() == 6), eq(generated));
    }

    private static AdminProgrammeView programme(String sourceUrl, String fingerprint) {
        LocalizedText text = new LocalizedText("برنامج", "Programme", "Programme");
        return new AdminProgrammeView(
                UUID.randomUUID(), "PJD", 2026, 2026, 2031, text, text,
                sourceUrl, "Official programme", "ar", List.of(), fingerprint,
                Instant.now(), false, EditorialStatus.DRAFT, null, List.of());
    }

    private void assertRejected(String value) {
        assertThatThrownBy(() -> ProgrammeIngestionService.publicHttpsUrl(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
