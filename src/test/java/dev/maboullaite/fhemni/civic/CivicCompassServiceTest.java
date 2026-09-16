package dev.maboullaite.fhemni.civic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.catalog.PoliticalParty;
import dev.maboullaite.fhemni.catalog.PoliticalPartyRepository;
import dev.maboullaite.fhemni.civic.CivicPartyPositionRepository.PositionRow;
import dev.maboullaite.fhemni.civic.CivicProfileService.Answer;
import dev.maboullaite.fhemni.civic.CivicProfileService.ProfileRequest;
import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Question;
import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Theme;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.LocalizedText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CivicCompassServiceTest {

    private final CivicQuestionnaireCatalogService catalog = mock(CivicQuestionnaireCatalogService.class);
    private final CivicPartyPositionRepository positions = mock(CivicPartyPositionRepository.class);
    private final PoliticalPartyRepository parties = mock(PoliticalPartyRepository.class);
    private final CivicCompassService service = new CivicCompassService(catalog, positions, parties);

    private CivicQuestionnaire questionnaire;

    @BeforeEach
    void setUp() {
        UUID editionId = UUID.randomUUID();
        questionnaire = new CivicQuestionnaire(
                editionId,
                "2026-v1",
                "en",
                "ltr",
                "Priorities",
                "Intro",
                "Methodology",
                LocalDate.of(2026, 9, 16),
                List.of(new Theme("ECONOMY", "Economy", 1)),
                List.of(new Question("q1", "ECONOMY", "Economy", 1, "Question", "Context", List.of())));
        when(catalog.current("en")).thenReturn(questionnaire);
        when(positions.publishedPositions(editionId)).thenReturn(List.of(new PositionRow(
                UUID.randomUUID(), editionId, UUID.randomUUID(), "q1", "P1", PartyPositionStance.MIXED,
                new LocalizedText("مختلط", "Mixte", "Mixed"), List.of())));
        when(parties.findAll()).thenReturn(List.of(new PoliticalParty(
                "P1", "Parti 1", "الحزب 1", "#123456", "P1", "ح1",
                "/assets/parties/party.svg", true, "P1", true)));
    }

    @Test
    void scoresAnExplicitNeutralAnswerInsteadOfDiscardingIt() {
        var result = service.compass(new ProfileRequest("en", List.of(new Answer("q1", 0, false))));

        assertThat(result.answeredCount()).isEqualTo(1);
        assertThat(result.parties()).singleElement().satisfies(match -> {
            assertThat(match.compatibility()).isEqualTo(100);
            assertThat(match.answeredQuestions()).isEqualTo(1);
            assertThat(match.questions()).singleElement().satisfies(question ->
                    assertThat(question.userValue()).isZero());
        });
    }

    @Test
    void rejectsMalformedDuplicateUnknownAndOutOfRangeAnswers() {
        assertThatThrownBy(() -> service.compass(new ProfileRequest(
                "en", Collections.singletonList(null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.compass(new ProfileRequest(
                "en", List.of(new Answer("q1", 1, false), new Answer("q1", 2, false)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.compass(new ProfileRequest(
                "en", List.of(new Answer("unknown", 1, false)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.compass(new ProfileRequest(
                "en", List.of(new Answer("q1", 3, false)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
