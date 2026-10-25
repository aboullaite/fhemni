package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

class ProgrammeBudgetEvidenceTest {
    @Test void budgetOnlyTextIsSearchableInEveryLanguageWithoutClaimingDelivery() throws Exception {
        var repository = mock(CommitmentRepository.class);
        var original = row("programme-p063-primary-care-1600", Origin.GOVERNMENT);
        var assistance = row("programme-p055-assistance-minimum", Origin.GOVERNMENT);
        when(repository.published("2026-2031")).thenReturn(List.of(original, assistance));
        var service = new CommitmentService(repository, mock(CampaignPromiseAdapter.class),
                mock(GovernmentRepository.class), mock(ProgrammeRelationships.class),
                new ProgrammeBudgetEvidence(new JsonMapper()));
        // These phrases occur only in the proposal summaries/details, not the original rows.
        for (String query : List.of("1.8 billion", "1,8 milliard", "مليار درهم مقترحة",
                "not completed renovations", "rénovations achevées", "ما كنجمعوش المرحلتين")) {
            var page = service.list("2026-2031", "GOVERNMENT", "HEALTH", null, query, null, 1);
            assertEquals(List.of(original.slug()), page.items().stream().map(CommitmentView::slug).toList(), query);
            var insights = service.insights("2026-2031", "GOVERNMENT", "HEALTH", null, query, null);
            assertEquals(List.of(original.slug()), insights.budgetEntries().stream().map(BudgetProgrammeEntry::slug).toList(), query);
            assertEquals(1L, insights.deliveryCounts().get("UNASSESSED"));
            assertEquals(0, service.list("2026-2031", "GOVERNMENT", null, null, query, "REVIEWED", 1).totalItems());
            assertEquals(0, service.list("2026-2031", "GOVERNMENT", "OTHER", null, query, null, 1).totalItems());
        }
        assertTrue(service.list("2026-2031", "GOVERNMENT", null, null, "600", null, 1).items()
                .stream().anyMatch(item -> item.slug().equals(assistance.slug())));
        assertTrue(original.budgetProposals().isEmpty());
    }

    @Test void everyEvidenceSlugResolvesToAnIndependentPublishedProgrammeRecord() throws Exception {
        var mapper = new JsonMapper();
        var repository = mock(CommitmentRepository.class);
        var relationships = mock(ProgrammeRelationships.class);
        when(relationships.project(any())).thenAnswer(call -> call.getArgument(0));
        var rows = new ArrayList<CommitmentView>();
        // Independent programme extract, not fixtures synthesized from evidence-file identifiers.
        try (var input = new ClassPathResource("government/budget-programme-records.json").getInputStream()) {
            for (var record : mapper.readTree(input).path("commitments")) {
                rows.add(ProgrammePreviewData.commitment(record, List.of(), List.of()));
            }
        }
        when(repository.published("2026-2031")).thenReturn(rows);
        var service = new CommitmentService(repository, mock(CampaignPromiseAdapter.class),
                mock(GovernmentRepository.class), relationships, new ProgrammeBudgetEvidence(mapper));
        try (var input = new ClassPathResource("government/plf-2027-evidence.json").getInputStream()) {
            var entries = mapper.readTree(input).path("entries");
            assertTrue(entries.size() > 0);
            for (var entry : entries) assertResolves(service, entry);
            // Prove this guard catches a typo even when that row otherwise remains valid.
            var typo = (ObjectNode) entries.get(0).deepCopy();
            typo.put("slug", "programme-p055-assistance-minimun");
            assertThrows(AssertionError.class, () -> assertResolves(service, typo));
        }
    }

    private void assertResolves(CommitmentService service, JsonNode entry) {
        String slug = entry.path("slug").asText();
        var detail = assertDoesNotThrow(() -> service.detail(slug), "Unresolved budget evidence: " + slug);
        assertEquals(Origin.GOVERNMENT, detail.origin());
        assertFalse(detail.budgetProposals().isEmpty(), slug);
    }

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
