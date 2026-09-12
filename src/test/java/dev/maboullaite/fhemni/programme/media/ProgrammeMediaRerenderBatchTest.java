package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProgrammeMediaRerenderBatchTest {

    @Test
    void cacheIdentityChangesWithEveryNarrationInput() {
        String baseline = ProgrammeMediaRerenderBatch.narrationCacheFileName(
                "gemini-3.1-flash-tts-preview", "Charon", "v3", 2, "النص الأول");

        assertThat(ProgrammeMediaRerenderBatch.narrationCacheFileName(
                "gemini-3.1-flash-tts-preview", "Charon", "v3", 2, "النص الأول"))
                .isEqualTo(baseline);
        assertThat(ProgrammeMediaRerenderBatch.narrationCacheFileName(
                "gemini-3.1-flash-tts-preview", "Charon", "v3", 2, "النص الثاني"))
                .isNotEqualTo(baseline);
        assertThat(ProgrammeMediaRerenderBatch.narrationCacheFileName(
                "gemini-3.1-flash-tts-preview", "Charon", "v4", 2, "النص الأول"))
                .isNotEqualTo(baseline);
        assertThat(ProgrammeMediaRerenderBatch.narrationCacheFileName(
                "gemini-3.1-pro-tts-preview", "Charon", "v3", 2, "النص الأول"))
                .isNotEqualTo(baseline);
        assertThat(ProgrammeMediaRerenderBatch.narrationCacheFileName(
                "gemini-3.1-flash-tts-preview", "Kore", "v3", 2, "النص الأول"))
                .isNotEqualTo(baseline);
    }

    @Test
    void refusesToRunAlongsideAssessmentWorkers() {
        assertThatThrownBy(() -> new ProgrammeMediaRerenderBatch(
                null, null, null, null, null, null,
                "input.jsonl", "output", "gemini-3.1-flash-image", 4, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Disable programme assessment workers");
    }
}
