package dev.maboullaite.fhemni.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeminiSchemasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void locksProgrammeMediaReferencesToTheCurrentDossier() {
        var schema = GeminiSchemas.programmeMediaScript(
                mapper, List.of("PROMISE:one", "ASSESSMENT:one"));

        var segments = schema.path("properties").path("segments");
        var sourceRefs = segments.path("items").path("properties").path("sourceRefs");
        assertThat(segments.path("maxItems").asInt()).isEqualTo(16);
        assertThat(sourceRefs.path("maxItems").asInt()).isEqualTo(8);
        assertThat(sourceRefs.path("items").path("enum").toString())
                .isEqualTo("[\"PROMISE:one\",\"ASSESSMENT:one\"]");
    }

    @Test
    void rejectsAnEmptyProgrammeMediaDossier() {
        assertThatThrownBy(() -> GeminiSchemas.programmeMediaScript(mapper, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
