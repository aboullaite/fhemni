package dev.maboullaite.fhemni.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ToolChoiceOptions;
import com.openai.models.responses.WebSearchTool;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiProgrammeFactCheckGatewayTest {

    private final OpenAiProgrammeFactCheckGateway gateway = new OpenAiProgrammeFactCheckGateway(
            "", "gpt-5.6-terra", Duration.ofSeconds(30), 4_096, new ObjectMapper());

    @Test
    void buildsStrictResponseSchemaWithoutTheSdkVictoolsGenerator() {
        var parameters = gateway.requestParameters("Assess this promise");

        assertThat(parameters).isNotNull();
        var webSearch = parameters.tools().orElseThrow().getFirst().asWebSearch();
        var location = webSearch.userLocation().orElseThrow();
        assertThat(location.type())
                .contains(WebSearchTool.UserLocation.Type.APPROXIMATE);
        assertThat(parameters.toolChoice().orElseThrow().options())
                .contains(ToolChoiceOptions.REQUIRED);
        assertThat(parameters.include().orElseThrow())
                .contains(ResponseIncludable.WEB_SEARCH_CALL_ACTION_SOURCES);
    }

    @Test
    void citationMatchingIgnoresTrackingParametersOnly() {
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma/report?year=2024&utm_source=search&fbclid=tracking",
                "https://hcp.ma/report?year=2024"))
                .isTrue();
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma/report?section=jobs&year=2024",
                "https://hcp.ma/report?year=2024&section=jobs"))
                .isTrue();
    }

    @Test
    void citationMatchingRejectsDifferentSemanticQueries() {
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma/report?year=2024",
                "https://hcp.ma/report?year=2019"))
                .isFalse();
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma/report?year=%ZZ",
                "https://hcp.ma/report?year=%ZZ"))
                .isFalse();
    }

    @Test
    void citationMatchingRejectsDifferentSchemesPortsAndEncodedPaths() {
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma/report", "http://hcp.ma/report")).isFalse();
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma/report", "https://hcp.ma:8443/report")).isFalse();
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma/a%2Fb", "https://hcp.ma/a/b")).isFalse();
        assertThat(OpenAiProgrammeFactCheckGateway.sameDocument(
                "https://hcp.ma:443/report", "https://hcp.ma/report")).isTrue();
    }
}
