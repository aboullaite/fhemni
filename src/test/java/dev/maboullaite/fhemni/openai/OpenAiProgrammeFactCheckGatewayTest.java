package dev.maboullaite.fhemni.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiProgrammeFactCheckGatewayTest {

    private final OpenAiProgrammeFactCheckGateway gateway = new OpenAiProgrammeFactCheckGateway(
            "", "gpt-5.6-terra", Duration.ofSeconds(30), 4_096, new ObjectMapper());

    @Test
    void buildsStrictResponseSchemaWithoutTheSdkVictoolsGenerator() {
        assertThat(gateway.requestParameters("Assess this promise")).isNotNull();
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
}
