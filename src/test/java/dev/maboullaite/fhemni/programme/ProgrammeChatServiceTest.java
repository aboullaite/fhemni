package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.maboullaite.fhemni.cost.AiOperation;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.ProgrammeChatGateway;
import dev.maboullaite.fhemni.model.SourceReference;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.ProgrammeChatDossier;
import org.junit.jupiter.api.Test;

class ProgrammeChatServiceTest {

    @Test
    void reservesSharedChatQuotaAndReturnsOnlyServerOwnedSources() {
        Fixture fixture = fixture();
        AiUsage usage = new AiUsage(900, 120, 0, 20, 0, 0);
        when(fixture.gateway.answer(any(), any(), any())).thenReturn(new ProgrammeChatGateway.Result(
                "The programme promises jobs; the review says delivery is difficult.",
                ProgrammeChatBasis.BOTH,
                List.of("PROMISE_1", "ASSESSMENT_1", "PROMISE_1_E1", "INVENTED"),
                usage));

        ProgrammeChatAnswer answer = fixture.service.ask("PJD", fixture.userId, "What about jobs?", "en");

        assertThat(answer.basis()).isEqualTo(ProgrammeChatBasis.BOTH);
        assertThat(answer.sources()).containsExactly(
                fixture.programmeSource, fixture.assessmentSource, fixture.evidenceSource);
        verify(fixture.usageGuard).reserveQuestion(
                null, fixture.userId, AiOperation.CHAT_PROGRAMME, "gemini-3.8-flash");
        verify(fixture.usageGuard).succeeded(fixture.reservation, usage);
    }

    @Test
    void rejectsAFeasibilityAnswerThatDoesNotCitePublishedEvidenceAndKeepsBilledUsage() {
        Fixture fixture = fixture();
        AiUsage usage = new AiUsage(500, 80, null, null, null, null);
        when(fixture.gateway.answer(any(), any(), any())).thenReturn(new ProgrammeChatGateway.Result(
                "It is difficult.", ProgrammeChatBasis.FEASIBILITY, List.of("PROMISE_1"), usage));

        assertThatThrownBy(() -> fixture.service.ask("PJD", fixture.userId, "Is it feasible?", "en"))
                .isInstanceOf(GeminiApiException.class)
                .hasMessageContaining("evidence did not match");
        verify(fixture.usageGuard).failed(fixture.reservation, usage);
    }

    @Test
    void acceptsAFeasibilityAnswerThatCitesThePublishedAssessment() {
        Fixture fixture = fixture();
        AiUsage usage = new AiUsage(500, 80, null, null, null, null);
        when(fixture.gateway.answer(any(), any(), any())).thenReturn(new ProgrammeChatGateway.Result(
                "It is difficult.", ProgrammeChatBasis.FEASIBILITY, List.of("ASSESSMENT_1"), usage));

        ProgrammeChatAnswer answer = fixture.service.ask("PJD", fixture.userId, "Is it feasible?", "en");

        assertThat(answer.sources()).containsExactly(fixture.assessmentSource);
        verify(fixture.usageGuard).succeeded(fixture.reservation, usage);
    }

    @Test
    void keepsConversationHistoryPrivateToTheUserAndProgramme() {
        Fixture fixture = fixture();
        when(fixture.gateway.answer(any(), any(), any())).thenReturn(new ProgrammeChatGateway.Result(
                "First answer", ProgrammeChatBasis.PROGRAMME, List.of("PROGRAMME"), AiUsage.empty()),
                new ProgrammeChatGateway.Result(
                        "Second answer", ProgrammeChatBasis.PROGRAMME, List.of("PROGRAMME"), AiUsage.empty()));
        when(fixture.usageGuard.reserveQuestion(any(), any(), any(), any())).thenReturn(fixture.reservation);

        fixture.service.ask("PJD", fixture.userId, "First question", "en");
        fixture.service.ask("PJD", UUID.randomUUID(), "Other user", "en");

        verify(fixture.contexts).build(eq(fixture.dossier), eq("First question"), any(), eq(List.of()));
        verify(fixture.contexts).build(eq(fixture.dossier), eq("Other user"), any(), eq(List.of()));
    }

    private static Fixture fixture() {
        PartyProgrammeService programmes = mock(PartyProgrammeService.class);
        ProgrammeChatContextBuilder contexts = mock(ProgrammeChatContextBuilder.class);
        ProgrammeChatGateway gateway = mock(ProgrammeChatGateway.class);
        AiUsageGuard usageGuard = mock(AiUsageGuard.class);
        UUID userId = UUID.randomUUID();
        Reservation reservation = new Reservation(UUID.randomUUID());
        PartyProgramme programme = new PartyProgramme(
                UUID.randomUUID(), "PJD", 2026, 2026, 2031,
                text("برنامج", "Programme", "Programme"),
                text("ملخص", "Résumé", "Summary"),
                "https://party.example/programme.pdf", "Official programme", "ar", "snapshot", List.of(),
                "abc", Instant.now(), true, EditorialStatus.PUBLISHED,
                Instant.now(), Instant.now(), Instant.now());
        ProgrammeChatDossier dossier = new ProgrammeChatDossier(programme, List.of());
        SourceReference programmeSource = new SourceReference(
                "Official programme · page 1", programme.sourceUrl(), "");
        SourceReference evidenceSource = new SourceReference(
                "HCP · Jobs report", "https://hcp.example/jobs", "2026-01-01");
        SourceReference assessmentSource = new SourceReference(
                "Fhemni five-year feasibility review · Jobs", "/promises/jobs", "2026-01-01");
        ProgrammeChatContext context = new ProgrammeChatContext(
                "PJD-only material", Map.of(
                        "PROGRAMME", new SourceReference("Official programme", programme.sourceUrl(), ""),
                        "PROMISE_1", programmeSource,
                        "ASSESSMENT_1", assessmentSource,
                        "PROMISE_1_E1", evidenceSource));

        when(programmes.publishedChatDossier("PJD")).thenReturn(dossier);
        when(contexts.build(eq(dossier), any(), any(), any())).thenReturn(context);
        when(gateway.live()).thenReturn(true);
        when(gateway.model()).thenReturn("gemini-3.8-flash");
        when(usageGuard.chatEnabled()).thenReturn(true);
        when(usageGuard.reserveQuestion(null, userId, AiOperation.CHAT_PROGRAMME, "gemini-3.8-flash"))
                .thenReturn(reservation);
        ProgrammeChatService service = new ProgrammeChatService(
                programmes, contexts, gateway, usageGuard, 8, 100, Duration.ofHours(6));
        return new Fixture(service, contexts, gateway, usageGuard, dossier, userId, reservation,
                programmeSource, assessmentSource, evidenceSource);
    }

    private static LocalizedText text(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }

    private record Fixture(
            ProgrammeChatService service,
            ProgrammeChatContextBuilder contexts,
            ProgrammeChatGateway gateway,
            AiUsageGuard usageGuard,
            ProgrammeChatDossier dossier,
            UUID userId,
            Reservation reservation,
            SourceReference programmeSource,
            SourceReference assessmentSource,
            SourceReference evidenceSource) {
    }
}
