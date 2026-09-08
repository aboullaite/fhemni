package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.maboullaite.fhemni.model.SourceReference;
import dev.maboullaite.fhemni.programme.FeasibilityVerdict;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.EvidenceDraft;
import org.junit.jupiter.api.Test;

class ProgrammeIntelligenceGatewayTest {

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

    private static ProgrammeIntelligenceGateway.GeneratedAssessment assessment(String url) {
        LocalizedText text = new LocalizedText("نص", "Texte", "Text");
        return new ProgrammeIntelligenceGateway.GeneratedAssessment(
                "party-promise", FeasibilityVerdict.HARD, text, text, text, text,
                List.of(new EvidenceDraft("HCP", "Report", url, null, "Baseline")));
    }
}
