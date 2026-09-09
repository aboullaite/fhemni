package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.programme.ProgrammeChatBasis;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProgrammeChatGatewayTest {

    @Test
    void parsesTheStructuredSourceLockedAnswer() {
        GeminiInteractionsClient client = mock(GeminiInteractionsClient.class);
        AiUsage usage = new AiUsage(300, 70, null, 10, null, null);
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("gemini-3.8-flash");
        when(client.createQuestion(any())).thenReturn(new InteractionResponse(
                "", """
                {"answer":"The programme proposes it.","basis":"PROGRAMME","citationIds":["PROMISE_1"]}
                """, List.of(), usage));
        ProgrammeChatGateway gateway = new ProgrammeChatGateway(client, new ObjectMapper(), 2_048);

        ProgrammeChatGateway.Result result = gateway.answer(
                "What does it propose?", OutputLanguage.ENGLISH, "PJD-only dossier");

        assertThat(result.answer()).isEqualTo("The programme proposes it.");
        assertThat(result.basis()).isEqualTo(ProgrammeChatBasis.PROGRAMME);
        assertThat(result.citationIds()).containsExactly("PROMISE_1");
        assertThat(result.usage()).isEqualTo(usage);
        verify(client).createQuestion(any());
    }

    @Test
    void preservesBilledUsageWhenTheStructuredAnswerIsInvalid() {
        GeminiInteractionsClient client = mock(GeminiInteractionsClient.class);
        AiUsage usage = new AiUsage(300, 70, null, 10, null, null);
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("gemini-3.8-flash");
        when(client.createQuestion(any())).thenReturn(new InteractionResponse(
                "", "not-json", List.of(), usage));
        ProgrammeChatGateway gateway = new ProgrammeChatGateway(client, new ObjectMapper(), 2_048);

        assertThatThrownBy(() -> gateway.answer("Question", OutputLanguage.ENGLISH, "Material"))
                .isInstanceOf(GeminiApiException.class)
                .satisfies(error -> assertThat(((GeminiApiException) error).usage()).isEqualTo(usage));
    }

    @Test
    void preservesBilledUsageWhenTheAnswerBasisIsNull() {
        GeminiInteractionsClient client = mock(GeminiInteractionsClient.class);
        AiUsage usage = new AiUsage(420, 90, null, 12, null, null);
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("gemini-3.8-flash");
        when(client.createQuestion(any())).thenReturn(new InteractionResponse(
                "", """
                {"answer":"The programme proposes it.","basis":null,"citationIds":["PROGRAMME"]}
                """, List.of(), usage));
        ProgrammeChatGateway gateway = new ProgrammeChatGateway(client, new ObjectMapper(), 2_048);

        assertThatThrownBy(() -> gateway.answer("Question", OutputLanguage.ENGLISH, "Material"))
                .isInstanceOf(GeminiApiException.class)
                .hasMessageContaining("invalid structured programme answer")
                .satisfies(error -> assertThat(((GeminiApiException) error).usage()).isEqualTo(usage));
    }

    @Test
    void removesInternalCitationIdsAndLocalizesVerdictCodesFromVisibleAnswer() {
        GeminiInteractionsClient client = mock(GeminiInteractionsClient.class);
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("gemini-3.8-flash");
        when(client.createQuestion(any())).thenReturn(new InteractionResponse(
                "", """
                {"answer":"هاد الوعد HARD [ASSESSMENT_1]، والمعطيات ديال وعد آخر INSUFFICIENT_DATA [PROMISE_2_E1].","basis":"FEASIBILITY","citationIds":["ASSESSMENT_1","PROMISE_2_E1"]}
                """, List.of(), AiUsage.empty()));
        ProgrammeChatGateway gateway = new ProgrammeChatGateway(client, new ObjectMapper(), 2_048);

        ProgrammeChatGateway.Result result = gateway.answer(
                "واش ساهل؟", OutputLanguage.DARIJA, "PJD-only dossier");

        assertThat(result.answer())
                .isEqualTo("هاد الوعد صعيب ولكن ممكن، والمعطيات ديال وعد آخر ما كايناش معطيات كافية.")
                .doesNotContain("ASSESSMENT", "PROMISE_", "HARD", "INSUFFICIENT_DATA");
        assertThat(result.citationIds()).containsExactly("ASSESSMENT_1", "PROMISE_2_E1");
    }
}
