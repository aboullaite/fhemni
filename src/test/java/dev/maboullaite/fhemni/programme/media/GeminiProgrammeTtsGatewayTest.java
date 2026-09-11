package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class GeminiProgrammeTtsGatewayTest {

    private final GeminiProgrammeTtsGateway gateway = new GeminiProgrammeTtsGateway(
            "", "https://generativelanguage.googleapis.com", "v1beta", Duration.ofMinutes(1),
            "gemini-2.5-pro-preview-tts", "Charon", "Kore");

    @Test
    void alternatesTheConfiguredVoicesBySection() {
        assertThat(gateway.voiceForSection(0)).isEqualTo("Charon");
        assertThat(gateway.voiceForSection(1)).isEqualTo("Kore");
        assertThat(gateway.voiceForSection(2)).isEqualTo("Charon");
        assertThat(gateway.voice()).isEqualTo("Charon + Kore");
    }

    @Test
    void rejectsANegativeSectionIndex() {
        assertThatIllegalArgumentException().isThrownBy(() -> gateway.voiceForSection(-1));
    }
}
