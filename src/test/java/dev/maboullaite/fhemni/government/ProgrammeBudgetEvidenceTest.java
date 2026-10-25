package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProgrammeBudgetEvidenceTest {
    @Test void proposedFundingIsVisibleInListsAndDetailsWithoutBecomingDelivery() throws Exception {
        var budget = new ProgrammeBudgetEvidence(new JsonMapper());
        var repository = mock(CommitmentRepository.class);
        var relationships = mock(ProgrammeRelationships.class);
        when(relationships.project(any())).thenAnswer(call -> call.getArgument(0));
        var original = row("programme-p063-primary-care-1600", Origin.GOVERNMENT);
        when(repository.published("2026-2031")).thenReturn(List.of(original));
        var service = new CommitmentService(repository, mock(CampaignPromiseAdapter.class),
                mock(GovernmentRepository.class), relationships, budget);
        var list = service.list("2026-2031", "GOVERNMENT", null, null, 1);
        var proposal = list.items().getFirst().budgetProposals().getFirst();
        assertEquals("PROPOSED_FUNDING", proposal.kind());
        assertTrue(proposal.summary().en().contains("1.8 billion"));
        assertEquals(List.of(51), proposal.source().printedPages());
        assertEquals(List.of(61), proposal.source().pdfPages());
        assertTrue(proposal.source().url().endsWith("02-Note-de-presentation_Fr_0.pdf"));
        assertEquals("8b5653939c7f0ad6ffebd2e7f697b9a2620ec0f03598aae64828c53ceabc2459", proposal.source().sha256());
        assertEquals(list.items().getFirst().budgetProposals(), service.detail(original.slug()).budgetProposals());
        assertNull(service.detail(original.slug()).deliveryAssessment());
        assertTrue(service.detail(original.slug()).observations().isEmpty());
        assertTrue(original.budgetProposals().isEmpty(), "Published snapshots must not be mutated");
        var counts = service.insights("2026-2031", "GOVERNMENT", null, null, null, null).deliveryCounts();
        assertEquals(1L, counts.get("UNASSESSED"));
        assertEquals(0L, counts.get("PARTLY_DELIVERED"));
        assertEquals(0L, counts.get("DELIVERED"));
        var insights = service.insights("2026-2031", "GOVERNMENT", null, null, null, null);
        assertEquals(1, insights.budgetEntries().size());
        assertEquals(original.slug(), insights.budgetEntries().getFirst().slug());
        assertEquals(proposal.summary(), insights.budgetEntries().getFirst().summary());
        assertTrue(service.insights("2026-2031", "GOVERNMENT", null, null, "no match", null).budgetEntries().isEmpty());
    }

    @Test void reviewedMappingsAreLocalizedAndDoNotLeakIntoCampaignOrUnknownEntries() throws Exception {
        var budget = new ProgrammeBudgetEvidence(new JsonMapper());
        for (String suffix : List.of("p055-assistance-minimum", "p057-minimum-pensions", "p057-widow-pensions",
                "p052-electricity-bill-compensation", "p063-primary-care-1600", "p063-hospital-rehabilitation",
                "p052-ir-exempt-threshold", "p052-ir-brackets")) {
            var proposals = budget.project(row("programme-" + suffix, Origin.GOVERNMENT)).budgetProposals();
            assertEquals(1, proposals.size(), suffix);
            var proposal = proposals.getFirst();
            assertFalse(proposal.summary().ar().isBlank());
            assertFalse(proposal.summary().fr().isBlank());
            assertFalse(proposal.detail().en().isBlank());
            assertEquals("2026-10-25", proposal.reviewedOn().toString());
            assertFalse(proposal.source().printedPages().isEmpty());
            assertTrue(budget.project(row("programme-" + suffix, Origin.CAMPAIGN)).budgetProposals().isEmpty());
        }
        assertTrue(budget.project(row("unreviewed", Origin.GOVERNMENT)).budgetProposals().isEmpty());
        var assistance = budget.project(row("programme-p055-assistance-minimum", Origin.GOVERNMENT)).budgetProposals().getFirst();
        assertTrue(assistance.summary().en().contains("600"));
        assertTrue(assistance.detail().en().contains("1,000"));
        var tax = budget.project(row("programme-p052-ir-brackets", Origin.GOVERNMENT)).budgetProposals().getFirst();
        assertTrue(tax.detail().en().contains("2031"));
        assertEquals(List.of(8, 14, 24), tax.source().printedPages());
    }

    private CommitmentView row(String slug, Origin origin) {
        return new CommitmentView(UUID.randomUUID(), slug, origin, new LocalText("عنوان", "Titre", "Title"),
                "Original", "ar", null, null, List.of("HEALTH"), null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }
}
