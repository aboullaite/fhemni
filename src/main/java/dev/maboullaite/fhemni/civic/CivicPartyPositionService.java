package dev.maboullaite.fhemni.civic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.catalog.PoliticalParty;
import dev.maboullaite.fhemni.catalog.PoliticalPartyRepository;
import dev.maboullaite.fhemni.civic.CivicPartyPositionRepository.EvidenceRow;
import dev.maboullaite.fhemni.civic.CivicPartyPositionRepository.PositionRow;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.LocalizedText;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.QuestionData;
import dev.maboullaite.fhemni.civic.PartyPosition.PositionEvidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CivicPartyPositionService {

    private final CivicQuestionnaireCatalogService catalog;
    private final CivicQuestionnaireRepository questionnaires;
    private final CivicPartyPositionRepository positions;
    private final PoliticalPartyRepository parties;

    CivicPartyPositionService(CivicQuestionnaireCatalogService catalog,
                              CivicQuestionnaireRepository questionnaires,
                              CivicPartyPositionRepository positions,
                              PoliticalPartyRepository parties) {
        this.catalog = catalog;
        this.questionnaires = questionnaires;
        this.positions = positions;
        this.parties = parties;
    }

    @Transactional(readOnly = true)
    public PositionMatrix matrix(String requestedLanguage) {
        String language = CivicQuestionnaireCatalogService.language(requestedLanguage);
        CivicQuestionnaire questionnaire = catalog.current(language);
        return buildMatrix(questionnaire, language);
    }

    @Transactional(readOnly = true)
    public List<AdminPositionRow> adminList(UUID editionId) {
        return positions.allPositions(editionId).stream()
                .map(row -> new AdminPositionRow(
                        row.id(), row.questionKey(), row.partyCode(),
                        row.stance(),
                        row.evidenceSummary().ar(), row.evidenceSummary().fr(), row.evidenceSummary().en()))
                .toList();
    }

    @Transactional
    public void upsert(UUID editionId, String questionKey, String partyCode,
                       PartyPositionStance stance,
                       String summaryAr, String summaryFr, String summaryEn,
                       String reviewerNote) {
        UUID questionId = resolveQuestion(editionId, questionKey);
        positions.upsert(UUID.randomUUID(), editionId, questionId, partyCode,
                stance, new LocalizedText(summaryAr, summaryFr, summaryEn), reviewerNote);
    }

    @Transactional
    public int batchUpsert(UUID editionId, List<PositionDraft> drafts) {
        int count = 0;
        for (PositionDraft draft : drafts) {
            upsert(editionId, draft.questionKey(), draft.partyCode(), draft.stance(),
                    draft.summaryAr(), draft.summaryFr(), draft.summaryEn(), draft.reviewerNote());
            count++;
        }
        return count;
    }

    @Transactional
    public void publish(UUID positionId) {
        positions.publish(positionId, Instant.now());
    }

    @Transactional
    public int publishAllDrafts(UUID editionId) {
        return positions.publishAllDrafts(editionId, Instant.now());
    }

    @Transactional
    public void addEvidence(UUID positionId, String labelAr, String labelFr, String labelEn,
                            String sourceUrl, String pageReference, UUID promiseId, int sortOrder) {
        positions.addEvidence(positionId, labelAr, labelFr, labelEn,
                sourceUrl, pageReference, promiseId, sortOrder);
    }

    private UUID resolveQuestion(UUID editionId, String questionKey) {
        return questionnaires.questions(editionId).stream()
                .filter(q -> q.key().equals(questionKey))
                .map(QuestionData::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Question key '" + questionKey + "' not found in edition " + editionId));
    }

    private PositionMatrix buildMatrix(CivicQuestionnaire questionnaire, String language) {
        List<PositionRow> rows = positions.publishedPositions(questionnaire.id());

        Set<String> partyCodes = new LinkedHashSet<>();
        for (PositionRow row : rows) {
            partyCodes.add(row.partyCode());
        }

        Map<String, PoliticalParty> partyLookup = parties.findAll().stream()
                .filter(PoliticalParty::visible)
                .collect(Collectors.toMap(PoliticalParty::code, p -> p, (a, b) -> a, LinkedHashMap::new));

        List<PartyColumn> columns = partyCodes.stream()
                .filter(partyLookup::containsKey)
                .map(code -> {
                    PoliticalParty party = partyLookup.get(code);
                    String name = language.equals("ar") ? party.nameAr() : party.nameFr();
                    return new PartyColumn(code, name, party.color(), party.symbolAsset());
                })
                .toList();

        Set<String> visiblePartyCodes = columns.stream()
                .map(PartyColumn::code)
                .collect(Collectors.toSet());

        Map<String, Map<String, PartyPosition>> byQuestion = new LinkedHashMap<>();
        for (PositionRow row : rows) {
            if (!visiblePartyCodes.contains(row.partyCode())) continue;
            byQuestion
                    .computeIfAbsent(row.questionKey(), k -> new LinkedHashMap<>())
                    .put(row.partyCode(), localize(row, language));
        }

        List<QuestionRow> questionRows = new ArrayList<>();
        Set<String> missingQuestions = new LinkedHashSet<>();
        for (CivicQuestionnaire.Question question : questionnaire.questions()) {
            Map<String, PartyPosition> partyPositions = byQuestion.getOrDefault(question.key(), Map.of());
            questionRows.add(new QuestionRow(
                    question.key(),
                    question.themeCode(),
                    question.themeLabel(),
                    question.prompt(),
                    partyPositions));
            Set<String> covered = partyPositions.keySet();
            for (String partyCode : visiblePartyCodes) {
                if (!covered.contains(partyCode)) {
                    missingQuestions.add(question.key());
                }
            }
        }

        int totalCells = questionnaire.questions().size() * visiblePartyCodes.size();
        int filledCells = (int) rows.stream()
                .filter(r -> visiblePartyCodes.contains(r.partyCode()))
                .count();
        int completenessPercent = totalCells == 0 ? 0 : Math.round(filledCells * 100f / totalCells);

        return new PositionMatrix(
                questionnaire.id().toString(),
                questionnaire.version(),
                language,
                columns,
                questionRows,
                completenessPercent,
                List.copyOf(missingQuestions));
    }

    private PartyPosition localize(PositionRow row, String language) {
        List<PositionEvidence> evidence = row.evidence().stream()
                .map(e -> localizeEvidence(e, language))
                .toList();
        return new PartyPosition(
                row.partyCode(),
                row.questionKey(),
                row.stance(),
                row.evidenceSummary().get(language),
                evidence);
    }

    private PositionEvidence localizeEvidence(EvidenceRow row, String language) {
        return new PositionEvidence(
                row.label().get(language),
                row.sourceUrl(),
                row.pageReference(),
                row.promiseSlug());
    }

    public record PositionMatrix(
            String editionId,
            String version,
            String language,
            List<PartyColumn> parties,
            List<QuestionRow> questions,
            int completenessPercent,
            List<String> incompleteQuestions) {
    }

    public record PartyColumn(String code, String name, String color, String symbolAsset) {
    }

    public record QuestionRow(
            String questionKey,
            String themeCode,
            String themeLabel,
            String prompt,
            Map<String, PartyPosition> positions) {
    }

    public record AdminPositionRow(
            UUID id,
            String questionKey,
            String partyCode,
            PartyPositionStance stance,
            String evidenceSummaryAr,
            String evidenceSummaryFr,
            String evidenceSummaryEn) {
    }

    public record PositionDraft(
            String questionKey,
            String partyCode,
            PartyPositionStance stance,
            String summaryAr,
            String summaryFr,
            String summaryEn,
            String reviewerNote) {
    }
}
