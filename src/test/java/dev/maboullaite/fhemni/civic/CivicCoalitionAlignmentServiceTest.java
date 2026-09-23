package dev.maboullaite.fhemni.civic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.AlignmentQuestionData;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.LocalizedText;
import org.junit.jupiter.api.Test;

class CivicCoalitionAlignmentServiceTest {

    private final CivicQuestionnaireRepository questionnaires = mock(CivicQuestionnaireRepository.class);
    private final CivicPartyPositionRepository positions = mock(CivicPartyPositionRepository.class);
    private final CivicCoalitionAlignmentService service = new CivicCoalitionAlignmentService(questionnaires, positions);

    @Test
    void asksForMorePartiesWithoutLoadingQuestionnaireData() {
        var alignment = service.evaluate(List.of("RNI"), "en");

        assertThat(alignment.status()).isEqualTo("NEEDS_MORE_PARTIES");
        verifyNoInteractions(questionnaires, positions);
    }

    @Test
    void supportsPublishedQuestionnairesWithMoreThanEighteenQuestions() {
        UUID editionId = UUID.randomUUID();
        List<AlignmentQuestionData> questions = IntStream.rangeClosed(1, 19)
                .mapToObj(index -> new AlignmentQuestionData(
                        editionId,
                        "question-" + index,
                        "theme",
                        index,
                        new LocalizedText("موضوع", "Thème", "Theme")))
                .toList();
        when(questionnaires.currentAlignmentQuestions()).thenReturn(questions);
        when(positions.publishedStances(editionId, Set.of("RNI", "PAM"))).thenReturn(List.of());

        var alignment = service.evaluate(List.of("RNI", "PAM"), "en");

        assertThat(alignment.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(alignment.possiblePairQuestions()).isEqualTo(19);
    }

    @Test
    void multiplePublishedEditionsDegradeWithoutLoadingPositions() {
        List<AlignmentQuestionData> questions = List.of(
                question(UUID.randomUUID(), "question-1"),
                question(UUID.randomUUID(), "question-2"));
        when(questionnaires.currentAlignmentQuestions()).thenReturn(questions);

        var alignment = service.evaluate(List.of("RNI", "PAM"), "en");

        assertThat(alignment.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(alignment.possiblePairQuestions()).isZero();
        verifyNoInteractions(positions);
    }

    private static AlignmentQuestionData question(UUID editionId, String key) {
        return new AlignmentQuestionData(
                editionId,
                key,
                "theme",
                1,
                new LocalizedText("موضوع", "Thème", "Theme"));
    }
}
