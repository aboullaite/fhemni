package dev.maboullaite.fhemni.election;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.civic.CivicCoalitionAlignmentService;
import dev.maboullaite.fhemni.civic.CivicCoalitionAlignmentService.Alignment;
import dev.maboullaite.fhemni.election.ElectionResultService.ElectionResultSnapshot;
import dev.maboullaite.fhemni.election.ElectionResultService.PartyResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoalitionEvaluationService {

    private static final int MAXIMUM_PARTIES = 40;

    private final ElectionResultService elections;
    private final CivicCoalitionAlignmentService alignments;

    CoalitionEvaluationService(ElectionResultService elections,
                               CivicCoalitionAlignmentService alignments) {
        this.elections = elections;
        this.alignments = alignments;
    }

    @Transactional(readOnly = true)
    public CoalitionEvaluation evaluate(String electionSlug, CoalitionRequest request) {
        if (request == null) throw new IllegalArgumentException("A coalition request is required.");
        String language = ElectionResultService.language(request.language());
        List<String> submitted = request.partyCodes() == null ? List.of() : request.partyCodes();
        if (submitted.size() > MAXIMUM_PARTIES) {
            throw new IllegalArgumentException("A coalition contains too many parties.");
        }

        List<String> partyCodes = submitted.stream()
                .map(code -> code == null ? "" : code.strip().toUpperCase(Locale.ROOT))
                .toList();
        if (partyCodes.stream().anyMatch(code -> !code.matches("[A-Z0-9]{1,10}"))) {
            throw new IllegalArgumentException("Select only known result parties.");
        }
        Set<String> uniqueCodes = new LinkedHashSet<>(partyCodes);
        if (uniqueCodes.size() != partyCodes.size()) {
            throw new IllegalArgumentException("Select each party only once.");
        }

        ElectionResultSnapshot snapshot = elections.result(electionSlug, language);
        Map<String, PartyResult> available = snapshot.parties().stream()
                .collect(Collectors.toMap(PartyResult::code, Function.identity()));
        if (!available.keySet().containsAll(uniqueCodes)) {
            throw new IllegalArgumentException("Select only parties included in this election result.");
        }

        int selectedSeats = uniqueCodes.stream()
                .map(available::get)
                .mapToInt(PartyResult::totalSeats)
                .sum();
        int majoritySeats = snapshot.election().majoritySeats();
        boolean hasMajority = selectedSeats >= majoritySeats;
        Alignment alignment = alignments.evaluate(List.copyOf(uniqueCodes), language);
        return new CoalitionEvaluation(
                selectedSeats,
                snapshot.election().updatedAt(),
                majoritySeats,
                Math.max(0, majoritySeats - selectedSeats),
                Math.max(0, selectedSeats - majoritySeats),
                hasMajority,
                alignment);
    }

    public record CoalitionRequest(String language, List<String> partyCodes) {
    }

    public record CoalitionEvaluation(
            int selectedSeats,
            Instant resultUpdatedAt,
            int majoritySeats,
            int remainingSeats,
            int seatsAboveMajority,
            boolean hasMajority,
            Alignment alignment) {
    }
}
