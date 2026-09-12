package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import dev.maboullaite.fhemni.programme.media.GeminiProgrammeTtsGateway.PcmAudio;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaRenderer.Illustration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class ProgrammeMediaRendererTest {

    @TempDir
    private Path temp;

    @Test
    void rendersTheVerticalVideoWithShortCaptionsAndAStationaryIllustration() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable());
        ProgrammeMediaScript script = new ProgrammeMediaScript(
                "قراءة فهّمني فـ2026",
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(index -> new ProgrammeMediaScript.Segment(
                                "المحور " + index,
                                "هاد خلاصة قصيرة فيها هدف 8,6% فـ2031 وواضحة بالدارجة المغربية، ".repeat(4).strip(),
                                List.of("PROMISE:00000000-0000-0000-0000-000000000000")))
                        .toList());
        PcmAudio segment = new PcmAudio(new byte[9_600], 24_000, 1, 16);
        WavePcm.CombinedAudio audio = WavePcm.combine(java.util.Collections.nCopies(10, segment), 20);
        Path illustration = new ClassPathResource("static/assets/brand/fhemni-icon.png").getFile().toPath();
        ProgrammeMediaRenderer renderer = new ProgrammeMediaRenderer("ffmpeg", Duration.ofMinutes(2));

        ProgrammeMediaRenderer.RenderedMedia rendered = renderer.render(
                temp,
                "/assets/parties/rni-display.png",
                script,
                audio,
                List.of(new Illustration(illustration, 0, audio.durationMs())));

        assertThat(rendered.durationMs()).isEqualTo(audio.durationMs());
        assertThat(rendered.video()).exists();
        assertThat(Files.size(rendered.video())).isPositive();
        assertThat(rendered.audio()).exists();
        assertThat(Files.size(rendered.audio())).isPositive();
        String vtt = Files.readString(rendered.captions());
        assertThat(vtt)
                .startsWith("WEBVTT")
                .contains("هاد خلاصة قصيرة", "8,6%", "2031")
                .doesNotContain("\u2066", "\u2069");
        assertThat(vtt.lines().filter(line -> line.contains(" --> ")).count()).isGreaterThan(10);
        String ass = Files.readString(temp.resolve("captions.ass"));
        assertThat(ass)
                .contains(
                        "Style: Headline,Noto Sans Arabic,82",
                        "&H00346516",
                        "Style: Caption,Noto Sans Arabic Med,58",
                        "فهّمني",
                        "2026",
                        "{\\fscx75\\fscy75}%{\\r}8,6")
                .doesNotContain("\u200D", "\u2066", "\u2069");
        List<String> captionEvents = ass.lines().filter(line -> line.contains(",Caption,")).toList();
        assertThat(captionEvents).hasSizeGreaterThan(10);
        assertThat(captionEvents).allSatisfy(line ->
                assertThat(line.split("\\\\N", -1)).hasSizeLessThanOrEqualTo(2));

        String filter = renderer.filter(
                temp.resolve("captions.ass"), temp.resolve("fonts"),
                List.of(new Illustration(illustration, 0, audio.durationMs())));
        assertThat(filter)
                .contains("overlay=x=(W-w)/2:y=555")
                .doesNotContain("sin(", "cos(")
                .contains("showwaves=s=820x64", "overlay=(W-w)/2:950")
                .contains("pad=160:160", "pad=300:160", "overlay=W-w-64:56", "overlay=64:56");
    }

    @Test
    void balancesLongHeadlinesAcrossNoMoreThanTwoLines() {
        ProgrammeMediaRenderer renderer = new ProgrammeMediaRenderer("ffmpeg", Duration.ofMinutes(2));

        String headline = renderer.headlineText(
                "قراءة فهّمني فبرنامج الحركة الديمقراطية الاجتماعية لانتخابات 2026");

        assertThat(headline.split("\\\\N", -1)).hasSize(2);
    }

    private static boolean ffmpegAvailable() {
        try {
            return new ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0;
        } catch (Exception unavailable) {
            return false;
        }
    }
}
