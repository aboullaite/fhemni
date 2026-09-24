package dev.maboullaite.fhemni.election;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.maboullaite.fhemni.civic.CivicCoalitionAlignmentService;
import dev.maboullaite.fhemni.civic.CivicCoalitionAlignmentService.Alignment;
import dev.maboullaite.fhemni.election.ElectionResultService.CoalitionResultData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoalitionEvaluationService {

    private static final int MAXIMUM_PARTIES = 5;

    private final ElectionResultService elections;
    private final CivicCoalitionAlignmentService alignments;

    CoalitionEvaluationService(ElectionResultService elections,
                               CivicCoalitionAlignmentService alignments) {
        this.elections = elections;
        this.alignments = alignments;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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

        CoalitionResultData result = elections.coalitionResult(electionSlug);
        Map<String, Integer> seatsByParty = result.seatsByParty();
        if (!seatsByParty.keySet().containsAll(uniqueCodes)) {
            throw new IllegalArgumentException("Select only parties included in this election result.");
        }
        int leadingSeats = seatsByParty.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> leadingPartyCodes = seatsByParty.entrySet().stream()
                .filter(entry -> entry.getValue() == leadingSeats)
                .map(Map.Entry::getKey)
                .toList();
        if (leadingPartyCodes.size() == 1 && !uniqueCodes.contains(leadingPartyCodes.getFirst())) {
            throw new IllegalArgumentException("A government coalition must include the leading party.");
        }

        int selectedSeats = uniqueCodes.stream()
                .mapToInt(seatsByParty::get)
                .sum();
        int majoritySeats = result.majoritySeats();
        boolean hasMajority = selectedSeats >= majoritySeats;
        Alignment alignment = alignments.evaluate(List.copyOf(uniqueCodes), language);
        return new CoalitionEvaluation(
                selectedSeats,
                result.updatedAt(),
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
