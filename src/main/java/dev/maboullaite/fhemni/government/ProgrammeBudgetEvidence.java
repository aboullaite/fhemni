package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Dated evidence annotations for current views. Does not rewrite publication history or delivery. */
@Component
public class ProgrammeBudgetEvidence {
    private final Map<String, BudgetProposal> entries;

    public ProgrammeBudgetEvidence(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("government/plf-2027-evidence.json").getInputStream()) {
            var document = mapper.readTree(input);
            if (document.path("version").asInt() != 1
                    || !"2026-2031".equals(document.path("mandate").asText())) {
                throw new IllegalArgumentException("Unsupported budget evidence index.");
            }
            var indexed = new HashMap<String, BudgetProposal>();
            for (var row : document.path("entries")) {
                String slug = row.path("slug").asText();
                var proposal = mapper.treeToValue(row.path("proposal"), BudgetProposal.class);
                if (!slug.startsWith("programme-") || proposal == null
                        || !Set.of("PROPOSED_MEASURE", "PROPOSED_FUNDING").contains(proposal.kind())
                        || proposal.reviewedOn() == null || !localized(proposal.summary())
                        || !localized(proposal.detail()) || proposal.source() == null
                        || !localized(proposal.source().title()) || proposal.source().publishedOn() == null
                        || !proposal.source().url().startsWith("https://medias24.com/content/uploads/2026/10/")
                        || !proposal.source().sha256().matches("[a-f0-9]{64}")
                        || !pages(proposal.source().printedPages()) || !pages(proposal.source().pdfPages())
                        || proposal.source().printedPages().size() != proposal.source().pdfPages().size()
                        || indexed.put(slug, proposal) != null) {
                    throw new IllegalArgumentException("Invalid budget evidence: " + slug);
                }
            }
            entries = Map.copyOf(indexed);
        }
    }

    public CommitmentView project(CommitmentView view) {
        var proposal = entries.get(view.slug());
        if (view.origin() != Origin.GOVERNMENT || proposal == null) return view;
        return new CommitmentView(view.id(), view.slug(), view.origin(), view.title(), view.originalText(),
                view.originalLanguage(), view.author(), view.campaignPromiseId(), view.topicCodes(),
                view.geographicScope(), view.responsibleBody(), view.target(), view.deadline(),
                view.deliveryAssessment(), view.links(), view.evidence(), view.observations(), view.revisions(),
                view.programmeContext(), List.of(proposal));
    }

    private static boolean localized(LocalText value) {
        return value != null && value.ar() != null && !value.ar().isBlank()
                && value.fr() != null && !value.fr().isBlank() && value.en() != null && !value.en().isBlank();
    }

    private static boolean pages(List<Integer> values) {
        return values != null && !values.isEmpty() && values.stream().allMatch(page -> page != null && page > 0);
    }
}
