package dev.maboullaite.fhemni.civic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.maboullaite.fhemni.civic.CivicPartyPositionRepository.StanceRow;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.AlignmentQuestionData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CivicCoalitionAlignmentService {

    private static final int MINIMUM_COMPARABLE_QUESTIONS = 6;
    private static final int MINIMUM_COVERAGE_PERCENT = 33;

    private final CivicQuestionnaireRepository questionnaires;
    private final CivicPartyPositionRepository positions;

    CivicCoalitionAlignmentService(CivicQuestionnaireRepository questionnaires,
                                    CivicPartyPositionRepository positions) {
        this.questionnaires = questionnaires;
        this.positions = positions;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Alignment evaluate(List<String> partyCodes, String requestedLanguage) {
        String language = CivicQuestionnaireCatalogService.language(requestedLanguage);
        List<String> selected = partyCodes == null ? List.of() : partyCodes.stream().distinct().toList();
        List<AlignmentQuestionData> questions = questionnaires.currentAlignmentQuestions();
        if (questions.size() != 18) {
            throw new IllegalStateException("A published priority questionnaire must contain exactly 18 questions.");
        }
        var editionId = questions.getFirst().editionId();
        if (questions.stream().anyMatch(question -> !question.editionId().equals(editionId))) {
            throw new IllegalStateException("Only one priority questionnaire edition can be published.");
        }
        int pairCount = selected.size() * (selected.size() - 1) / 2;
        int possiblePairQuestions = pairCount * questions.size();
        if (selected.size() < 2) {
            return new Alignment(
                    "NEEDS_MORE_PARTIES", null, null, 0, 0,
                    possiblePairQuestions, 0, List.of(), List.of());
        }

        Map<String, Map<String, PartyPositionStance>> byParty = new LinkedHashMap<>();
        Set<String> selectedCodes = Set.copyOf(selected);
        for (StanceRow row : positions.publishedStances(editionId, selectedCodes)) {
            byParty.computeIfAbsent(row.partyCode(), ignored -> new HashMap<>())
                    .put(row.questionKey(), row.stance());
        }

        double scoreTotal = 0;
        int comparedPairQuestions = 0;
        Set<String> comparableQuestions = new HashSet<>();
        Map<String, ThemeAccumulator> themeScores = new HashMap<>();

        for (int leftIndex = 0; leftIndex < selected.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < selected.size(); rightIndex++) {
                Map<String, PartyPositionStance> left = byParty.getOrDefault(selected.get(leftIndex), Map.of());
                Map<String, PartyPositionStance> right = byParty.getOrDefault(selected.get(rightIndex), Map.of());
                for (AlignmentQuestionData question : questions) {
                    PartyPositionStance leftStance = left.get(question.key());
                    PartyPositionStance rightStance = right.get(question.key());
                    if (!comparable(leftStance) || !comparable(rightStance)) continue;

                    double questionScore = alignment(leftStance, rightStance);
                    scoreTotal += questionScore;
                    comparedPairQuestions++;
                    comparableQuestions.add(question.key());
                    themeScores.computeIfAbsent(
                                    question.themeCode(),
                                    ignored -> new ThemeAccumulator(
                                            question.themeCode(), question.themeLabel().get(language)))
                            .add(questionScore);
                }
            }
        }

        int coveragePercent = possiblePairQuestions == 0
                ? 0
                : Math.round(comparedPairQuestions * 100f / possiblePairQuestions);
        if (comparableQuestions.size() < MINIMUM_COMPARABLE_QUESTIONS
                || coveragePercent < MINIMUM_COVERAGE_PERCENT) {
            return new Alignment(
                    "INSUFFICIENT_DATA", null, null,
                    comparableQuestions.size(), comparedPairQuestions,
                    possiblePairQuestions, coveragePercent, List.of(), List.of());
        }

        int score = (int) Math.round(scoreTotal * 100 / comparedPairQuestions);
        String level = score >= 75 ? "STRONG" : score >= 50 ? "MEDIUM" : "WEAK";
        List<ThemeAlignment> themes = themeScores.values().stream()
                .map(ThemeAccumulator::result)
                .toList();
        List<ThemeAlignment> agreements = themes.stream()
                .filter(theme -> theme.score() >= 75)
                .sorted(Comparator.comparingInt(ThemeAlignment::comparedPairQuestions).reversed()
                        .thenComparing(Comparator.comparingInt(ThemeAlignment::score).reversed()))
                .limit(2)
                .toList();
        List<ThemeAlignment> tensions = themes.stream()
                .filter(theme -> theme.score() < 50)
                .sorted(Comparator.comparingInt(ThemeAlignment::comparedPairQuestions).reversed()
                        .thenComparingInt(ThemeAlignment::score))
                .limit(2)
                .toList();
        return new Alignment(
                "AVAILABLE", score, level,
                comparableQuestions.size(), comparedPairQuestions,
                possiblePairQuestions, coveragePercent,
                agreements, tensions);
    }

    private static boolean comparable(PartyPositionStance stance) {
        return stance != null && stance != PartyPositionStance.NO_POSITION;
    }

    private static double alignment(PartyPositionStance left, PartyPositionStance right) {
        return 1.0 - (Math.abs(value(left) - value(right)) / 4.0);
    }

    private static int value(PartyPositionStance stance) {
        return switch (stance) {
            case SUPPORTS -> 2;
            case MIXED -> 0;
            case OPPOSES -> -2;
            case NO_POSITION -> throw new IllegalArgumentException("No-position rows are not comparable.");
        };
    }

    public record Alignment(
            String status,
            Integer score,
            String level,
            int comparableQuestions,
            int comparedPairQuestions,
            int possiblePairQuestions,
            int coveragePercent,
            List<ThemeAlignment> strongestAgreements,
            List<ThemeAlignment> strongestTensions) {
    }

    public record ThemeAlignment(
            String themeCode,
            String themeLabel,
            int score,
            int comparedPairQuestions) {
    }

    private static final class ThemeAccumulator {
        private final String code;
        private final String label;
        private final List<Double> scores = new ArrayList<>();

        private ThemeAccumulator(String code, String label) {
            this.code = code;
            this.label = label;
        }

        private void add(double score) {
            scores.add(score);
        }

        private ThemeAlignment result() {
            int score = (int) Math.round(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100);
            return new ThemeAlignment(code, label, score, scores.size());
        }
    }
}
