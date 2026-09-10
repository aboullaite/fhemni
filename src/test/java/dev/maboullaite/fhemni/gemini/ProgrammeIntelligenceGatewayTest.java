package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.SourceReference;
import dev.maboullaite.fhemni.programme.FeasibilityVerdict;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.EvidenceDraft;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ProgrammeIntelligenceGatewayTest {

    @Test
    void allowsTheJointFgdPsuCampaignAsCanonicalFgd() {
        var partyCodes = new ArrayList<String>();
        GeminiSchemas.programmeExtraction(new ObjectMapper())
                .get("properties").get("partyCode").get("enum")
                .forEach(node -> partyCodes.add(node.stringValue()));

        assertThat(partyCodes).contains("FGD");
    }

    @Test
    void inventoriesTheWholePdfBeforeStructuredExtractionAndCombinesUsage() {
        GeminiInteractionsClient client = mock(GeminiInteractionsClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ProgrammeIntelligenceGateway gateway = new ProgrammeIntelligenceGateway(
                client, mapper, 32_768, 32_768);
        String extraction = """
                {
                  "partyCode":"PJD","electionYear":2026,"official2026Programme":true,
                  "sourceLabel":"Official programme","sourceLanguage":"ar","sourceSnapshot":"snapshot",
                  "title":{"ar":"برنامج","fr":"Programme","en":"Programme"},
                  "summary":{"ar":"ملخص","fr":"Résumé","en":"Summary"},
                  "warnings":[],
                  "promises":[{
                    "slug":"pjd-digital-court","topic":"justice",
                    "title":{"ar":"محكمة رقمية","fr":"Tribunal numérique","en":"Digital court"},
                    "promiseText":"Digital court by 2030","sourceLocator":"page 45",
                    "mechanism":"Digitisation","financing":""
                  }]
                }
                """;
        AiUsage inventoryUsage = new AiUsage(100, 20, null, null, null, null);
        AiUsage extractionUsage = new AiUsage(200, 40, 50, null, null, null);

        when(client.model()).thenReturn("gemini-3.8-flash");
        when(client.uploadPdf(any(), anyLong(), any()))
                .thenReturn(new GeminiInteractionsClient.UploadedFile("files/one", "https://files/one"));
        when(client.create(any()))
                .thenReturn(new InteractionResponse("inventory", "page 45: digital court", List.of(), inventoryUsage))
                .thenReturn(new InteractionResponse("extraction", extraction, List.of(), extractionUsage));

        var result = gateway.extractPdf(
                "https://party.ma/programme", "programme.pdf",
                new ByteArrayInputStream("%PDF-test".getBytes()), 9);

        assertThat(result.programme().promises()).hasSize(1);
        assertThat(result.usage()).isEqualTo(inventoryUsage.plus(extractionUsage));
        ArgumentCaptor<CreateModelInteraction> requests =
                ArgumentCaptor.forClass(CreateModelInteraction.class);
        verify(client, times(2)).create(requests.capture());
        assertThat(requests.getAllValues().getFirst().generationConfig())
                .hasValueSatisfying(config -> assertThat(config.maxOutputTokens()).contains(24_576));
        assertThat(requests.getAllValues().get(1).generationConfig())
                .hasValueSatisfying(config -> assertThat(config.maxOutputTokens()).contains(32_768));
        verify(client).deleteFile("files/one");
    }

    @Test
    void acceptsEvidenceOnlyWhenGeminiActuallyCitedTheSameDocument() {
        var assessment = assessment("https://hcp.ma/report?year=2026&utm_source=gemini");

        assertThat(ProgrammeIntelligenceGateway.groundedAssessments(
                List.of(assessment),
                List.of(new SourceReference("HCP", "https://hcp.ma/report?year=2026", ""))))
                .containsExactly(assessment);
    }

    @Test
    void rejectsHallucinatedOrSemanticallyDifferentEvidenceUrls() {
        var assessment = assessment("https://hcp.ma/report?year=2026");

        assertThatThrownBy(() -> ProgrammeIntelligenceGateway.groundedAssessments(
                List.of(assessment),
                List.of(new SourceReference("HCP", "https://hcp.ma/report?year=2019", ""))))
                .isInstanceOf(GeminiApiException.class)
                .hasMessageContaining("not backed");
    }

    @Test
    void stripsUnverifiedEvidenceFromAnUngroundedConsensusCandidate() {
        var candidate = ProgrammeIntelligenceGateway.candidateGrounding(
                List.of(assessment("https://invented.example/report")),
                List.of());

        assertThat(candidate.grounded()).isFalse();
        assertThat(candidate.assessments()).singleElement()
                .satisfies(item -> assertThat(item.evidence()).isEmpty());
    }

    @Test
    void preservesBilledUsageWhenPostResponseValidationRejectsGeminiOutput() {
        GeminiInteractionsClient client = mock(GeminiInteractionsClient.class);
        AiUsage billed = new AiUsage(900, 300, 20, 10, null, 2);
        when(client.model()).thenReturn("gemini-3.8-flash");
        when(client.create(any())).thenReturn(new InteractionResponse(
                "invalid", "not structured json", List.of(), billed));
        ProgrammeIntelligenceGateway gateway = new ProgrammeIntelligenceGateway(
                client, new ObjectMapper(), 32_768, 32_768);
        var promise = new ProgrammeIntelligenceGateway.ExtractedPromise(
                "party-promise", "employment", new LocalizedText("وعد", "Promesse", "Promise"),
                "Create jobs", "page 2", "", "");

        assertThatThrownBy(() -> gateway.assess("https://party.ma/programme", List.of(promise)))
                .isInstanceOf(GeminiApiException.class)
                .satisfies(exception -> assertThat(((GeminiApiException) exception).usage()).isEqualTo(billed));
    }

    private static ProgrammeIntelligenceGateway.GeneratedAssessment assessment(String url) {
        LocalizedText text = new LocalizedText("نص", "Texte", "Text");
        return new ProgrammeIntelligenceGateway.GeneratedAssessment(
                "party-promise", FeasibilityVerdict.HARD, text, text, text, text,
                List.of(new EvidenceDraft("HCP", "Report", url, null, "Baseline")));
    }
}
