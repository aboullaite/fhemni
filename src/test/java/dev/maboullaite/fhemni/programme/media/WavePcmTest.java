package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.maboullaite.fhemni.programme.media.GeminiProgrammeTtsGateway.PcmAudio;
import org.junit.jupiter.api.Test;

class WavePcmTest {

    @Test
    void combinesPcmSegmentsWithExactCaptionTimings() {
        PcmAudio first = new PcmAudio(new byte[2_000], 1_000, 1, 16);
        PcmAudio second = new PcmAudio(new byte[1_000], 1_000, 1, 16);

        WavePcm.CombinedAudio combined = WavePcm.combine(List.of(first, second), 250);

        assertThat(combined.durationMs()).isEqualTo(1_750);
        assertThat(combined.timings()).containsExactly(
                new WavePcm.Timing(0, 1_000),
                new WavePcm.Timing(1_250, 1_750));
        assertThat(new String(combined.wav(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(combined.wav()).hasSize(44 + 2_000 + 500 + 1_000);
    }

    @Test
    void rejectsMixedPcmFormats() {
        PcmAudio mono = new PcmAudio(new byte[2_000], 1_000, 1, 16);
        PcmAudio stereo = new PcmAudio(new byte[4_000], 1_000, 2, 16);

        assertThatThrownBy(() -> WavePcm.combine(List.of(mono, stereo), 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same PCM format");
    }
}
