package dev.maboullaite.fhemni.civic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.catalog.PoliticalParty;
import dev.maboullaite.fhemni.catalog.PoliticalPartyRepository;
import dev.maboullaite.fhemni.civic.CivicPartyPositionRepository.PositionRow;
import dev.maboullaite.fhemni.civic.CivicProfileService.Answer;
import dev.maboullaite.fhemni.civic.CivicProfileService.ProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CivicCompassService {

    private final CivicQuestionnaireCatalogService catalog;
    private final CivicPartyPositionRepository positions;
    private final PoliticalPartyRepository parties;

    CivicCompassService(CivicQuestionnaireCatalogService catalog,
                        CivicPartyPositionRepository positions,
                        PoliticalPartyRepository parties) {
        this.catalog = catalog;
        this.positions = positions;
        this.parties = parties;
    }

    @Transactional(readOnly = true)
    public CompassResult compass(ProfileRequest request) {
        String language = CivicQuestionnaireCatalogService.language(request.language());
        CivicQuestionnaire questionnaire = catalog.current(language);
        List<Answer> answers = request.answers() == null ? List.of() : List.copyOf(request.answers());
        if (answers.isEmpty()) {
            throw new IllegalArgumentException("Answer at least one question to compute the compass.");
        }

        Map<String, Answer> answersByKey = new LinkedHashMap<>();
        for (Answer answer : answers) {
            if (answer.questionKey() != null && !answer.questionKey().isBlank()) {
                answersByKey.put(answer.questionKey(), answer);
            }
        }

        List<PositionRow> published = positions.publishedPositions(questionnaire.id());
        Map<String, List<PositionRow>> byParty = published.stream()
                .collect(Collectors.groupingBy(PositionRow::partyCode, LinkedHashMap::new, Collectors.toList()));

        Map<String, PoliticalParty> partyLookup = parties.findAll().stream()
                .filter(PoliticalParty::visible)
                .collect(Collectors.toMap(PoliticalParty::code, p -> p, (a, b) -> a, LinkedHashMap::new));

        Set<String> answeredKeys = answersByKey.keySet();

        Map<String, CivicQuestionnaire.Question> questionsByKey = questionnaire.questions().stream()
                .collect(Collectors.toMap(CivicQuestionnaire.Question::key, q -> q, (a, b) -> a));

        List<PartyMatch> matches = new ArrayList<>();
        for (var entry : byParty.entrySet()) {
            String partyCode = entry.getKey();
            PoliticalParty party = partyLookup.get(partyCode);
            if (party == null) continue;

            List<PositionRow> partyPositions = entry.getValue();
            Map<String, PositionRow> positionsByKey = partyPositions.stream()
                    .collect(Collectors.toMap(PositionRow::questionKey, r -> r, (a, b) -> a));

            double totalWeight = 0;
            double totalScore = 0;
            List<QuestionMatch> questionMatches = new ArrayList<>();

            for (Answer answer : answers) {
                if (answer.value() == 0) continue;
                double weight = answer.important() ? 2.0 : 1.0;
                totalWeight += weight;

                PositionRow pos = positionsByKey.get(answer.questionKey());
                boolean covered = pos != null && pos.stance() != PartyPositionStance.NO_POSITION;
                double alignment = covered ? alignment(answer.value(), pos.stance()) : 0.5;
                totalScore += weight * alignment;

                if (covered) {
                    CivicQuestionnaire.Question question = questionsByKey.get(answer.questionKey());
                    questionMatches.add(new QuestionMatch(
                            answer.questionKey(),
                            question != null ? question.prompt() : answer.questionKey(),
                            question != null ? question.themeCode() : "",
                            answer.value(),
                            pos.stance(),
                            pos.evidenceSummary().get(language) != null ? pos.evidenceSummary().get(language) : "",
                            Math.round((float) (alignment * 100)),
                            answer.important()));
                }
            }

            int compatibility = totalWeight == 0 ? 0 : (int) Math.round(totalScore * 100 / totalWeight);
            int coveredCount = (int) partyPositions.stream()
                    .filter(p -> answeredKeys.contains(p.questionKey()) && p.stance() != PartyPositionStance.NO_POSITION)
                    .count();

            String name = language.equals("ar") ? party.nameAr() : party.nameFr();
            matches.add(new PartyMatch(
                    partyCode, name, party.color(), party.symbolAsset(),
                    compatibility, coveredCount, answers.size(),
                    questionMatches));
        }

        matches.sort(Comparator.comparingInt(PartyMatch::compatibility).reversed());

        return new CompassResult(
                questionnaire.id().toString(),
                questionnaire.version(),
                language,
                answers.size(),
                (int) published.size(),
                matches);
    }

    private double alignment(int userValue, PartyPositionStance stance) {
        int partyValue = switch (stance) {
            case SUPPORTS -> 2;
            case OPPOSES -> -2;
            case MIXED -> 0;
            case NO_POSITION -> 0;
        };
        int maxDistance = 4;
        int distance = Math.abs(userValue - partyValue);
        return 1.0 - ((double) distance / maxDistance);
    }

    public record CompassResult(
            String editionId,
            String version,
            String language,
            int answeredCount,
            int totalPositionsReviewed,
            List<PartyMatch> parties) {
    }

    public record PartyMatch(
            String code,
            String name,
            String color,
            String symbolAsset,
            int compatibility,
            int coveredQuestions,
            int answeredQuestions,
            List<QuestionMatch> questions) {
    }

    public record QuestionMatch(
            String questionKey,
            String prompt,
            String themeCode,
            int userValue,
            PartyPositionStance partyStance,
            String evidenceSummary,
            int alignment,
            boolean important) {
    }
}
