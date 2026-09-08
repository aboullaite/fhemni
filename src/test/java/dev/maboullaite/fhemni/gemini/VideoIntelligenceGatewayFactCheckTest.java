package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimKind;
import dev.maboullaite.fhemni.model.ClaimVerdict;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.SourceReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class VideoIntelligenceGatewayFactCheckTest {

    @Test
    void downgradesDecisiveVerdictsWhenNoSafeSourceRemains() {
        SpringAiFactCheckClient factCheckClient = mock(SpringAiFactCheckClient.class);
        when(factCheckClient.configured()).thenReturn(true);
        when(factCheckClient.check(anyString(), anyString())).thenReturn(new SpringAiFactCheckClient.FactCheckResult(
                new FactCheckResponse(List.of(
                        new FactCheckResponse.Item(
                                "claim-1", "SUPPORTED", "Confirmed", "HIGH", List.of()),
                        new FactCheckResponse.Item(
                                "claim-2", "CONTRADICTED", "Refuted", "HIGH",
                                List.of(new SourceReference("Unsafe", "javascript:alert(1)", ""))))),
                AiUsage.empty()));

        GatewayFactCheckResult result = gateway(factCheckClient).factCheck(
                List.of(factualClaim("claim-1"), factualClaim("claim-2")),
                OutputLanguage.DARIJA);

        assertThat(result.assessments()).allSatisfy(assessment -> {
            assertThat(assessment.verdict()).isEqualTo(ClaimVerdict.UNVERIFIABLE);
            assertThat(assessment.evidenceStrength()).isEqualTo("LOW");
            assertThat(assessment.sources()).isEmpty();
            assertThat(assessment.explanation()).isEqualTo(
                    "ما رجع حتى مصدر موثوق كافي باش ندققو فهاد الادعاء.");
        });
    }

    @Test
    void keepsADecisiveVerdictWhenItHasASafeSource() {
        SpringAiFactCheckClient factCheckClient = mock(SpringAiFactCheckClient.class);
        when(factCheckClient.configured()).thenReturn(true);
        SourceReference source = new SourceReference(
                "Official statistics", "https://www.hcp.ma/report", "2026-09-01");
        when(factCheckClient.check(anyString(), anyString())).thenReturn(new SpringAiFactCheckClient.FactCheckResult(
                new FactCheckResponse(List.of(new FactCheckResponse.Item(
                        "claim-1", "SUPPORTED", "Confirmed by official statistics", "HIGH", List.of(source)))),
                AiUsage.empty()));

        GatewayFactCheckResult result = gateway(factCheckClient).factCheck(
                List.of(factualClaim("claim-1")), OutputLanguage.ENGLISH);

        assertThat(result.assessments()).singleElement().satisfies(assessment -> {
            assertThat(assessment.verdict()).isEqualTo(ClaimVerdict.SUPPORTED);
            assertThat(assessment.evidenceStrength()).isEqualTo("HIGH");
            assertThat(assessment.sources()).containsExactly(source);
            assertThat(assessment.explanation()).isEqualTo("Confirmed by official statistics");
        });
    }

    @Test
    void localizesTheMissingEvidenceExplanationInFrench() {
        SpringAiFactCheckClient factCheckClient = mock(SpringAiFactCheckClient.class);
        when(factCheckClient.configured()).thenReturn(true);
        when(factCheckClient.check(anyString(), anyString())).thenReturn(new SpringAiFactCheckClient.FactCheckResult(
                new FactCheckResponse(List.of(new FactCheckResponse.Item(
                        "claim-1", "SUPPORTED", "Confirmé", "HIGH", List.of()))),
                AiUsage.empty()));

        GatewayFactCheckResult result = gateway(factCheckClient).factCheck(
                List.of(factualClaim("claim-1")), OutputLanguage.FRENCH);

        assertThat(result.assessments()).singleElement().satisfies(assessment ->
                assertThat(assessment.explanation()).isEqualTo(
                        "Aucune source suffisamment fiable n’a été trouvée pour vérifier cette affirmation."));
    }

    @Test
    void exposesANewFactCheckPromptVersionForCacheInvalidation() {
        SpringAiFactCheckClient factCheckClient = mock(SpringAiFactCheckClient.class);

        assertThat(gateway(factCheckClient).factCheckPromptVersion())
                .isEqualTo("2026-09-08-fact-check-v2");
    }

    private VideoIntelligenceGateway gateway(SpringAiFactCheckClient factCheckClient) {
        GeminiInteractionsClient client = mock(GeminiInteractionsClient.class);
        when(client.configured()).thenReturn(true);
        return new VideoIntelligenceGateway(
                client, factCheckClient, new ObjectMapper(), "test", 512, 256, 128);
    }

    private Claim factualClaim(String id) {
        return new Claim(
                id, "A factual claim", "Speaker", 10, ClaimKind.FACT,
                ClaimVerdict.NOT_APPLICABLE, "", "", List.of());
    }
}
