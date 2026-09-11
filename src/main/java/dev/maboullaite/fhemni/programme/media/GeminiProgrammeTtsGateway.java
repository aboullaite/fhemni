package dev.maboullaite.fhemni.programme.media;

import java.time.Duration;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.VoiceConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiProgrammeTtsGateway {

    private static final int SAMPLE_RATE = 24_000;
    private static final String DIRECTION = """
            Read the text after NARRATION exactly as written. Speak natural contemporary Moroccan Darija with an
            authentic Moroccan accent. Sound like a warm, clear and trustworthy public-service radio narrator,
            never like an advertisement or political campaign. Use a moderate pace and natural pauses.
            Every occurrence of the brand is written فَهَّمْنِي. Pronounce it consistently as “fah-HAM-ni”:
            clearly hold and double the هّ sound, including when it appears more than once. Do not translate,
            paraphrase, add, or omit anything.

            NARRATION
            """;

    private final Client client;
    private final String model;
    private final String primaryVoice;
    private final String secondaryVoice;

    public GeminiProgrammeTtsGateway(
            @Value("${fhemni.gemini.api-key:}") String apiKey,
            @Value("${fhemni.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${fhemni.gemini.api-version:v1beta}") String apiVersion,
            @Value("${fhemni.programme-media.tts-timeout:PT3M}") Duration timeout,
            @Value("${fhemni.programme-media.tts-model:gemini-2.5-pro-preview-tts}") String model,
            @Value("${fhemni.programme-media.tts-voice:Charon}") String primaryVoice,
            @Value("${fhemni.programme-media.tts-secondary-voice:Kore}") String secondaryVoice) {
        this.model = required(model, "TTS model");
        this.primaryVoice = required(primaryVoice, "Primary TTS voice");
        this.secondaryVoice = required(secondaryVoice, "Secondary TTS voice");
        String key = apiKey == null ? "" : apiKey.strip();
        if (key.isBlank()) {
            this.client = null;
            return;
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Programme TTS timeout must be positive and fit in milliseconds.");
        }
        HttpOptions.Builder http = HttpOptions.builder()
                .apiVersion(apiVersion)
                .timeout(Math.toIntExact(timeout.toMillis()));
        if (baseUrl != null && !baseUrl.isBlank()) {
            http.baseUrl(baseUrl.strip());
        }
        this.client = Client.builder().apiKey(key).httpOptions(http.build()).build();
    }

    public boolean live() {
        return client != null;
    }

    public String model() {
        return model;
    }

    public String voice() {
        return primaryVoice + " + " + secondaryVoice;
    }

    public String voiceForSection(int sectionIndex) {
        if (sectionIndex < 0) {
            throw new IllegalArgumentException("Section index must not be negative.");
        }
        return sectionIndex % 2 == 0 ? primaryVoice : secondaryVoice;
    }

    public String voiceCacheKeyForSection(int sectionIndex) {
        return voiceForSection(sectionIndex).toLowerCase(java.util.Locale.ROOT);
    }

    public PcmAudio synthesize(String narration, String selectedVoice) {
        if (!live()) {
            throw new IllegalStateException("Gemini is not configured for programme narration.");
        }
        String requestedVoice = required(selectedVoice, "Selected TTS voice");
        try {
            var response = client.models.generateContent(
                    model,
                    DIRECTION + narration,
                    GenerateContentConfig.builder()
                            .responseModalities("AUDIO")
                            .speechConfig(SpeechConfig.builder()
                                    .languageCode("ar")
                                    .voiceConfig(VoiceConfig.builder()
                                            .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder()
                                                    .voiceName(requestedVoice)
                                                    .build())
                                            .build())
                                    .build())
                            .build());
            response.checkFinishReason();
            byte[] pcm = response.parts().stream()
                    .flatMap(part -> part.inlineData().stream())
                    .filter(blob -> blob.mimeType().orElse("").startsWith("audio/"))
                    .flatMap(blob -> blob.data().stream())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Gemini returned no narration audio."));
            if (pcm.length < SAMPLE_RATE) {
                throw new IllegalStateException("Gemini returned an unexpectedly short narration segment.");
            }
            return restore(pcm);
        } catch (ApiException exception) {
            throw new IllegalStateException("Gemini narration failed (HTTP " + exception.code() + ").", exception);
        }
    }

    PcmAudio restore(byte[] pcm) {
        if (pcm == null || pcm.length < SAMPLE_RATE) {
            throw new IllegalArgumentException("Cached Gemini narration is unexpectedly short.");
        }
        return new PcmAudio(pcm, SAMPLE_RATE, 1, 16);
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return value.strip();
    }

    public record PcmAudio(byte[] data, int sampleRate, int channels, int bitsPerSample) {
        public long durationMs() {
            long bytesPerSecond = (long) sampleRate * channels * bitsPerSample / 8;
            return data.length * 1_000L / bytesPerSecond;
        }
    }
}
