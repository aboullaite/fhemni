package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeminiSchemasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsProgrammeMediaSchemaWithinProviderComplexityLimits() {
        var schema = GeminiSchemas.programmeMediaScript(
                mapper, List.of("PROMISE:one", "ASSESSMENT:one"));

        var segments = schema.path("properties").path("segments");
        var sourceRefs = segments.path("items").path("properties").path("sourceRefs");
        assertThat(segments.path("minItems").asInt()).isEqualTo(14);
        assertThat(segments.path("maxItems").asInt()).isEqualTo(16);
        assertThat(sourceRefs.path("maxItems").asInt()).isEqualTo(8);
        // Dynamic enums make the schema too complex for larger programmes. The policy performs
        // the authoritative allowed-reference check after generation.
        assertThat(sourceRefs.path("items").has("enum")).isFalse();
    }

    @Test
    void rejectsAnEmptyProgrammeMediaDossier() {
        assertThatThrownBy(() -> GeminiSchemas.programmeMediaScript(mapper, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
