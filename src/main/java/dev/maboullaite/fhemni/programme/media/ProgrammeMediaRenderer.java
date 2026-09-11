package dev.maboullaite.fhemni.programme.media;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.programme.media.WavePcm.CombinedAudio;
import dev.maboullaite.fhemni.programme.media.WavePcm.Timing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ProgrammeMediaRenderer {

    public static final int WIDTH = 1_080;
    public static final int HEIGHT = 1_350;
    private static final Pattern WESTERN_PERCENTAGE = Pattern.compile("(?<![0-9])([0-9]+(?:[.,][0-9]+)?)%");
    private final String ffmpeg;
    private final Duration timeout;

    public ProgrammeMediaRenderer(
            @Value("${fhemni.programme-media.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${fhemni.programme-media.render-timeout:PT20M}") Duration timeout) {
        if (ffmpeg == null || ffmpeg.isBlank()) {
            throw new IllegalArgumentException("FFmpeg executable must not be blank.");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Programme media render timeout must be positive.");
        }
        this.ffmpeg = ffmpeg.strip();
        this.timeout = timeout;
    }

    public RenderedMedia render(
            Path work,
            String partyAsset,
            ProgrammeMediaScript script,
            CombinedAudio audio,
            List<Illustration> illustrations) throws IOException, InterruptedException {
        Files.createDirectories(work);
        Path narrationWav = work.resolve("narration.wav");
        Files.write(narrationWav, audio.wav());
        Path captions = work.resolve("captions.vtt");
        Path subtitles = work.resolve("captions.ass");
        Files.writeString(captions, vtt(script, audio.timings()), StandardCharsets.UTF_8);
        Files.writeString(subtitles, ass(script, audio.timings()), StandardCharsets.UTF_8);

        Path partyLogo = copyResource(work, partyAsset, "party-logo.png");
        Path fhemniLogo = copyResource(work, "/assets/brand/fhemni-logo.png", "fhemni-logo.png");
        Path fontDirectory = work.resolve("fonts");
        Files.createDirectories(fontDirectory);
        copyResource(fontDirectory, "/assets/fonts/noto-sans-arabic-medium.ttf", "noto-sans-arabic-medium.ttf");
        copyResource(fontDirectory, "/assets/fonts/noto-sans-arabic-bold.ttf", "noto-sans-arabic-bold.ttf");

        Path video = work.resolve("programme-summary-4x5.mp4");
        Path audioMp3 = work.resolve("programme-summary.mp3");
        run(work, List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-i", narrationWav.toString(),
                "-codec:a", "libmp3lame", "-b:a", "160k", audioMp3.toString()));

        List<String> command = new ArrayList<>();
        command.addAll(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "color=c=0xf8f4ea:s=" + WIDTH + "x" + HEIGHT + ":r=30:d=" + seconds(audio.durationMs()),
                "-i", narrationWav.toString(),
                "-loop", "1", "-i", partyLogo.toString(),
                "-loop", "1", "-i", fhemniLogo.toString()));
        for (Illustration illustration : illustrations) {
            command.addAll(List.of("-loop", "1", "-i", illustration.path().toString()));
        }
        command.addAll(List.of(
                "-filter_complex", filter(subtitles, fontDirectory, illustrations),
                "-map", "[vout]", "-map", "1:a:0",
                "-c:v", "libx264", "-preset", "medium", "-crf", "20",
                "-pix_fmt", "yuv420p", "-r", "30",
                "-c:a", "aac", "-b:a", "192k",
                "-t", seconds(audio.durationMs()), "-movflags", "+faststart",
                video.toString()));
        run(work, command);
        return new RenderedMedia(audioMp3, video, captions, audio.durationMs());
    }

    String filter(Path subtitles, Path fonts, List<Illustration> illustrations) {
        StringBuilder filter = new StringBuilder();
        filter.append("[2:v]scale=140:140:force_original_aspect_ratio=decrease,")
                .append("pad=160:160:(ow-iw)/2:(oh-ih)/2:color=white@0,format=rgba[party];")
                .append("[3:v]scale=280:140:force_original_aspect_ratio=decrease,")
                .append("pad=300:160:(ow-iw)/2:(oh-ih)/2:color=white@0,format=rgba[fhemni];")
                .append("[0:v][party]overlay=W-w-64:56[brand1];")
                .append("[brand1][fhemni]overlay=64:56[brand2];")
                .append("[1:a]aformat=channel_layouts=mono,showwaves=s=820x64:mode=line:colors=0x176b63@0.72,")
                .append("format=rgba[wave];[brand2][wave]overlay=(W-w)/2:950[base]");
        String previous = "base";
        for (int index = 0; index < illustrations.size(); index++) {
            Illustration illustration = illustrations.get(index);
            int input = index + 4;
            String icon = "icon" + index;
            String layer = "layer" + index;
            double start = illustration.startMs() / 1_000d;
            double end = illustration.endMs() / 1_000d;
            double fadeOut = Math.max(start, end - 0.35d);
            filter.append(';').append('[').append(input).append(":v]")
                    .append("scale=300:300:force_original_aspect_ratio=decrease,")
                    .append("pad=320:320:(ow-iw)/2:(oh-ih)/2:color=white@0,format=rgba,")
                    .append("fade=t=in:st=").append(decimal(start)).append(":d=0.35:alpha=1,")
                    .append("fade=t=out:st=").append(decimal(fadeOut)).append(":d=0.35:alpha=1[")
                    .append(icon).append("];[").append(previous).append("][").append(icon).append(']')
                    .append("overlay=x=(W-w)/2:y=555:enable='between(t,")
                    .append(decimal(start)).append(',').append(decimal(end)).append(")'[")
                    .append(layer).append(']');
            previous = layer;
        }
        filter.append(';');
        filter.append('[').append(previous).append("]subtitles='")
                .append(escapeFilterPath(subtitles)).append("':fontsdir='")
                .append(escapeFilterPath(fonts)).append("'[vout]");
        return filter.toString();
    }

    private String ass(ProgrammeMediaScript script, List<Timing> timings) {
        StringBuilder out = new StringBuilder("""
                [Script Info]
                ScriptType: v4.00+
                PlayResX: 1080
                PlayResY: 1350
                WrapStyle: 0
                ScaledBorderAndShadow: yes

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                Style: Headline,Noto Sans Arabic,82,&H00346516,&H000000FF,&H00F8F4EA,&H00000000,1,0,0,0,100,100,0,0,1,0,0,8,80,80,225,-1
                Style: Message,Noto Sans Arabic,70,&H00132C2B,&H000000FF,&H00F8F4EA,&H00000000,1,0,0,0,100,100,0,0,1,0,0,8,100,100,400,-1
                Style: Caption,Noto Sans Arabic Med,58,&H00132C2B,&H000000FF,&H00F8F4EA,&H00000000,0,0,0,0,100,100,0,0,1,0,0,2,90,90,76,-1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """);
        long fullEnd = timings.getLast().endMs();
        out.append("Dialogue: 0,0:00:00.00,").append(assTime(fullEnd))
                .append(",Headline,,0,0,0,,").append(wrappedText(script.headline(), 34)).append('\n');
        for (int index = 0; index < script.segments().size(); index++) {
            Timing timing = timings.get(index);
            ProgrammeMediaScript.Segment segment = script.segments().get(index);
            out.append("Dialogue: 0,").append(assTime(timing.startMs())).append(',').append(assTime(timing.endMs()))
                    .append(",Message,,0,0,0,,").append(wrappedText(segment.message(), 40)).append('\n');
            for (CaptionCue cue : captionCues(segment.narration(), timing)) {
                out.append("Dialogue: 1,").append(assTime(cue.startMs())).append(',').append(assTime(cue.endMs()))
                        .append(",Caption,,0,0,0,,").append(wrappedText(cue.text(), 34)).append('\n');
            }
        }
        return out.toString();
    }

    private String vtt(ProgrammeMediaScript script, List<Timing> timings) {
        StringBuilder out = new StringBuilder("WEBVTT\n\n");
        int cueNumber = 1;
        for (int index = 0; index < script.segments().size(); index++) {
            Timing timing = timings.get(index);
            for (CaptionCue cue : captionCues(script.segments().get(index).narration(), timing)) {
                out.append(cueNumber++).append('\n')
                        .append(vttTime(cue.startMs())).append(" --> ").append(vttTime(cue.endMs())).append('\n')
                        .append(cleanDisplayText(cue.text())).append("\n\n");
            }
        }
        return out.toString();
    }

    private void run(Path work, List<String> command) throws IOException, InterruptedException {
        Path log = work.resolve("ffmpeg.log");
        Process process = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectErrorStream(true)
                .start();
        Thread drainer = Thread.ofVirtual().start(() -> {
            try (var input = process.getInputStream(); OutputStream output = Files.newOutputStream(log)) {
                input.transferTo(output);
            } catch (IOException ignored) {
                // The exit code below remains authoritative; the log is diagnostic only.
            }
        });
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("FFmpeg timed out while rendering programme media.");
        }
        drainer.join(Duration.ofSeconds(5));
        if (process.exitValue() != 0) {
            String details = Files.exists(log) ? Files.readString(log).strip() : "";
            throw new IllegalStateException("FFmpeg could not render programme media: "
                    + details.substring(0, Math.min(details.length(), 500)));
        }
    }

    private Path copyResource(Path directory, String asset, String filename) throws IOException {
        String clean = asset.startsWith("/") ? asset.substring(1) : asset;
        if (clean.startsWith("assets/")) {
            clean = "static/" + clean;
        }
        ClassPathResource resource = new ClassPathResource(clean);
        if (!resource.exists()) {
            throw new IOException("Required programme media asset is missing: " + clean);
        }
        Path target = directory.resolve(filename);
        try (var input = resource.getInputStream()) {
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private String seconds(long milliseconds) {
        return String.format(Locale.ROOT, "%.3f", milliseconds / 1_000d);
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String assTime(long milliseconds) {
        long hundredths = milliseconds / 10;
        long hours = hundredths / 360_000;
        long minutes = (hundredths / 6_000) % 60;
        long seconds = (hundredths / 100) % 60;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths % 100);
    }

    private String vttTime(long milliseconds) {
        long hours = milliseconds / 3_600_000;
        long minutes = (milliseconds / 60_000) % 60;
        long seconds = (milliseconds / 1_000) % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds % 1_000);
    }

    private String assText(String value) {
        String escaped = cleanDisplayText(value).replace("\\", "\\\\")
                .replace("{", "\\{").replace("}", "\\}")
                .replace("\r", "").replace("\n", "\\N");
        String smallerPercent = Matcher.quoteReplacement("{\\fscx75\\fscy75}%{\\r}");
        return WESTERN_PERCENTAGE.matcher(escaped).replaceAll(smallerPercent + "$1");
    }

    private String cleanDisplayText(String value) {
        return value.replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "");
    }

    private String wrappedText(String value, int lineLength) {
        String[] words = cleanDisplayText(value).strip().split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > lineLength) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.stream().map(this::assText).collect(java.util.stream.Collectors.joining("\\N"));
    }

    List<CaptionCue> captionCues(String narration, Timing timing) {
        List<String> chunks = captionChunks(narration);
        int totalWeight = chunks.stream().mapToInt(this::captionWeight).sum();
        long duration = timing.endMs() - timing.startMs();
        long cursor = timing.startMs();
        int consumedWeight = 0;
        List<CaptionCue> cues = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            consumedWeight += captionWeight(chunk);
            long end = index + 1 == chunks.size()
                    ? timing.endMs()
                    : timing.startMs() + Math.round(duration * (consumedWeight / (double) totalWeight));
            cues.add(new CaptionCue(cursor, Math.max(cursor + 1, end), chunk));
            cursor = end;
        }
        return List.copyOf(cues);
    }

    private List<String> captionChunks(String narration) {
        String clean = narration == null ? "" : narration.strip().replaceAll("\\s+", " ");
        if (clean.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int words = 0;
        for (String word : clean.split(" ")) {
            boolean wouldOverflow = words >= 9 || (!chunk.isEmpty() && chunk.length() + 1 + word.length() > 62);
            if (wouldOverflow) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
                words = 0;
            }
            if (!chunk.isEmpty()) {
                chunk.append(' ');
            }
            chunk.append(word);
            words++;
            if (words >= 5 && word.matches(".*[،؛.!؟?:]$")) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
                words = 0;
            }
        }
        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString());
        }
        return List.copyOf(chunks);
    }

    private int captionWeight(String value) {
        return Math.max(1, value.codePointCount(0, value.length()));
    }

    private String escapeFilterPath(Path path) {
        return path.toAbsolutePath().toString()
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "'\\\\''");
    }

    public record Illustration(Path path, long startMs, long endMs) {
    }

    record CaptionCue(long startMs, long endMs, String text) {
    }

    public record RenderedMedia(Path audio, Path video, Path captions, long durationMs) {
    }
}
