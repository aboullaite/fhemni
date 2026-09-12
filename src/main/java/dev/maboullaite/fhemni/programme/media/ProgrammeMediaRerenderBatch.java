package dev.maboullaite.fhemni.programme.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import dev.maboullaite.fhemni.programme.media.GeminiProgrammeTtsGateway.PcmAudio;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaRenderer.Illustration;
import dev.maboullaite.fhemni.programme.media.WavePcm.CombinedAudio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in operations runner for rebuilding published videos from their already-reviewed scripts and illustrations.
 * It never writes application rows; it emits a manifest that can be inspected before an atomic database swap.
 */
@Component
@ConditionalOnProperty(name = "fhemni.programme-media.rerender-batch.enabled", havingValue = "true")
public class ProgrammeMediaRerenderBatch implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeMediaRerenderBatch.class);
    private static final int PROVIDER_ATTEMPTS = 3;

    private final GeminiProgrammeTtsGateway tts;
    private final ProgrammeMediaScriptPolicy policy;
    private final ProgrammeMediaRenderer renderer;
    private final ProgrammeMediaStorage storage;
    private final ObjectMapper mapper;
    private final ConfigurableApplicationContext applicationContext;
    private final Path input;
    private final Path output;
    private final String illustrationModel;
    private final int concurrency;

    public ProgrammeMediaRerenderBatch(
            GeminiProgrammeTtsGateway tts,
            ProgrammeMediaScriptPolicy policy,
            ProgrammeMediaRenderer renderer,
            ProgrammeMediaStorage storage,
            ObjectMapper mapper,
            ConfigurableApplicationContext applicationContext,
            @Value("${fhemni.programme-media.rerender-batch.input}") String input,
            @Value("${fhemni.programme-media.rerender-batch.output}") String output,
            @Value("${fhemni.programme-media.image-model:gemini-3.1-flash-image}") String illustrationModel,
            @Value("${fhemni.programme-media.provider-concurrency:4}") int concurrency) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("Programme media batch concurrency must be between 1 and 8.");
        }
        this.tts = tts;
        this.policy = policy;
        this.renderer = renderer;
        this.storage = storage;
        this.mapper = mapper;
        this.applicationContext = applicationContext;
        this.input = Path.of(input).toAbsolutePath().normalize();
        this.output = Path.of(output).toAbsolutePath().normalize();
        this.illustrationModel = illustrationModel;
        this.concurrency = concurrency;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        if (!tts.live()) {
            throw new IllegalStateException("Gemini TTS must be configured for a rerender batch.");
        }
        Files.createDirectories(output);
        Path manifest = output.resolve("manifest.jsonl");
        Set<String> completed = completedParties(manifest);
        for (String line : Files.readAllLines(input)) {
            if (line.isBlank()) continue;
            SourceMedia source = source(line);
            if (completed.contains(source.partyCode())) {
                log.info("Skipping completed programme media rerender for {}", source.partyCode());
                continue;
            }
            rerender(source, manifest);
        }
        log.info("Programme media rerender batch completed: {}", manifest);
        // ApplicationRunner executes while Spring Boot is still completing startup. Closing the
        // context inline can make that startup path touch already-destroyed infrastructure beans.
        // Let the runner return first, then stop the short-lived batch application cleanly.
        Thread.ofPlatform().name("programme-media-rerender-shutdown").start(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            applicationContext.close();
        });
    }

    private void rerender(SourceMedia source, Path manifest) throws Exception {
        ProgrammeMediaScript script = mapper.readValue(source.scriptJson(), ProgrammeMediaScript.class);
        if (script.segments() == null || script.segments().isEmpty()) {
            throw new IllegalArgumentException("Published script has no segments for " + source.partyCode());
        }
        UUID replacementId = UUID.randomUUID();
        Path partyOutput = output.resolve(source.partyCode().toLowerCase(Locale.ROOT));
        Files.createDirectories(partyOutput);
        Path work = Files.createTempDirectory(partyOutput, "work-");
        log.info("Rerendering {} with {} narration sections using {}", source.partyCode(),
                script.segments().size(), tts.model());
        try {
            List<PcmAudio> audioSegments = narration(source, script, partyOutput);
            CombinedAudio combined = WavePcm.combine(audioSegments, 260);
            List<Illustration> illustrations = illustrations(source, script.segments().size(), combined, work);
            var rendered = renderer.render(work, partyAsset(source.partyCode()), script, combined, illustrations);
            String prefix = "programmes/" + source.partyCode().toLowerCase(Locale.ROOT)
                    + "/" + source.sourceSha256() + "/" + replacementId
                    + "/script-" + source.scriptRevision() + "/attempt-1";
            String audioKey = prefix + "/summary-darija.mp3";
            String videoKey = prefix + "/summary-darija-4x5.mp4";
            String captionsKey = prefix + "/summary-darija.vtt";
            storage.put(audioKey, rendered.audio(), "audio/mpeg");
            storage.put(videoKey, rendered.video(), "video/mp4");
            storage.put(captionsKey, rendered.captions(), "text/vtt; charset=utf-8");

            Files.copy(rendered.audio(), partyOutput.resolve("summary-darija.mp3"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(rendered.video(), partyOutput.resolve("summary-darija-4x5.mp4"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(rendered.captions(), partyOutput.resolve("summary-darija.vtt"), StandardCopyOption.REPLACE_EXISTING);
            Result result = new Result(
                    source.id(), replacementId, source.programmeId(), source.partyCode(), source.sourceSha256(),
                    source.scriptRevision(), source.scriptJson(), source.pronunciationVersion(), tts.model(), tts.voice(),
                    illustrationModel, illustrations.size(), audioKey, videoKey, captionsKey,
                    rendered.durationMs(), Instant.now());
            Files.writeString(manifest, mapper.writeValueAsString(result) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Completed {}: {} ms", source.partyCode(), rendered.durationMs());
        } finally {
            deleteTemporaryTree(work, partyOutput);
        }
    }

    private List<PcmAudio> narration(SourceMedia source, ProgrammeMediaScript script, Path partyOutput) throws Exception {
        Semaphore permits = new Semaphore(concurrency);
        Path cache = partyOutput.resolve("audio-cache").resolve(ProgrammeMediaWorker.cacheIdentity(tts.model()));
        Files.createDirectories(cache);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<PcmAudio>> futures = new ArrayList<>(script.segments().size());
            for (int index = 0; index < script.segments().size(); index++) {
                int section = index;
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        String voice = tts.voiceForSection(section);
                        Path cached = cache.resolve(ProgrammeMediaWorker.cacheIdentity(voice)
                                + "-segment-" + String.format(Locale.ROOT, "%02d", section) + ".pcm");
                        if (Files.exists(cached)) {
                            try {
                                return tts.restore(Files.readAllBytes(cached));
                            } catch (IllegalArgumentException corrupt) {
                                Files.deleteIfExists(cached);
                            }
                        }
                        PcmAudio generated = synthesize(script.segments().get(section).narration(), voice);
                        Files.write(cached, generated.data());
                        log.info("Generated {} narration section {}/{}", source.partyCode(), section + 1,
                                script.segments().size());
                        return generated;
                    } finally {
                        permits.release();
                    }
                }));
            }
            List<PcmAudio> generated = new ArrayList<>(futures.size());
            try {
                for (Future<PcmAudio> future : futures) {
                    generated.add(future.get());
                }
                return List.copyOf(generated);
            } catch (ExecutionException failure) {
                futures.forEach(future -> future.cancel(true));
                Throwable cause = failure.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Narration generation failed.", cause);
            }
        }
    }

    private PcmAudio synthesize(String narration, String voice) throws InterruptedException {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= PROVIDER_ATTEMPTS; attempt++) {
            try {
                return tts.synthesize(policy.ttsText(narration), voice);
            } catch (RuntimeException failure) {
                last = failure;
                if (attempt < PROVIDER_ATTEMPTS) {
                    log.warn("Narration generation failed with voice {} on attempt {}/{}", voice, attempt,
                            PROVIDER_ATTEMPTS);
                    Thread.sleep(attempt * 2_000L);
                }
            }
        }
        throw last;
    }

    private List<Illustration> illustrations(
            SourceMedia source, int count, CombinedAudio audio, Path work) throws IOException {
        String root = sourceRoot(source.audioObjectKey());
        String identity = ProgrammeMediaWorker.cacheIdentity(illustrationModel);
        List<Illustration> restored = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String filename = "section-" + String.format(Locale.ROOT, "%02d", index) + ".image";
            List<String> candidates = List.of(
                    root + "/work/illustrations-" + identity + "/" + filename,
                    root + "/work/illustrations/" + filename);
            Path target = work.resolve("illustration-" + index + ".image");
            IOException failure = null;
            boolean found = false;
            for (String candidate : candidates) {
                try (ProgrammeMediaStorage.StoredObject object = storage.open(candidate)) {
                    Files.copy(object.content(), target, StandardCopyOption.REPLACE_EXISTING);
                    found = Files.size(target) > 0;
                    if (found) break;
                } catch (IOException missing) {
                    failure = missing;
                }
            }
            if (!found) {
                throw new IOException("Missing retained illustration " + (index + 1) + " for " + source.partyCode(),
                        failure);
            }
            restored.add(new Illustration(target, audio.timings().get(index).startMs(), audio.timings().get(index).endMs()));
        }
        return List.copyOf(restored);
    }

    private Set<String> completedParties(Path manifest) throws IOException {
        if (!Files.exists(manifest)) return Set.of();
        java.util.HashSet<String> completed = new java.util.HashSet<>();
        for (String line : Files.readAllLines(manifest)) {
            if (!line.isBlank()) completed.add(mapper.readTree(line).get("partyCode").asText());
        }
        return Set.copyOf(completed);
    }

    private SourceMedia source(String line) throws IOException {
        JsonNode json = mapper.readTree(line);
        return new SourceMedia(
                UUID.fromString(json.get("id").asText()), UUID.fromString(json.get("programme_id").asText()),
                json.get("party_code").asText(), json.get("source_sha256").asText(),
                json.get("script_revision").asInt(), json.get("script_json").asText(),
                json.get("pronunciation_version").asText(), json.get("audio_object_key").asText());
    }

    private String sourceRoot(String audioObjectKey) {
        int filename = audioObjectKey.lastIndexOf('/');
        if (filename < 1) throw new IllegalArgumentException("Invalid source media object key.");
        String parent = audioObjectKey.substring(0, filename);
        if (parent.matches(".*/attempt-[0-9]+$")) {
            return parent.substring(0, parent.lastIndexOf('/'));
        }
        return parent;
    }

    private String partyAsset(String partyCode) {
        return "/assets/parties/" + partyCode.toLowerCase(Locale.ROOT) + "-display.png";
    }

    private void deleteTemporaryTree(Path work, Path allowedRoot) {
        if (work == null || !work.normalize().startsWith(allowedRoot) || work.equals(allowedRoot)) return;
        try (var paths = Files.walk(work)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("Could not remove batch workspace {}", path, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("Could not clean batch workspace {}", work, exception);
        }
    }

    private record SourceMedia(
            UUID id,
            UUID programmeId,
            String partyCode,
            String sourceSha256,
            int scriptRevision,
            String scriptJson,
            String pronunciationVersion,
            String audioObjectKey) {
    }

    private record Result(
            UUID previousMediaId,
            UUID mediaId,
            UUID programmeId,
            String partyCode,
            String sourceSha256,
            int scriptRevision,
            String scriptJson,
            String pronunciationVersion,
            String ttsModel,
            String ttsVoice,
            String imageModel,
            int illustrationCount,
            String audioObjectKey,
            String videoObjectKey,
            String captionsObjectKey,
            long durationMs,
            Instant renderedAt) {
    }
}
