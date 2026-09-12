package dev.maboullaite.fhemni.programme.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
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
            @Value("${fhemni.programme-media.provider-concurrency:4}") int concurrency,
            @Value("${fhemni.programme-jobs.worker-enabled:true}") boolean assessmentWorkerEnabled) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("Programme media batch concurrency must be between 1 and 8.");
        }
        if (assessmentWorkerEnabled) {
            throw new IllegalStateException(
                    "Disable programme assessment workers before starting a programme media rerender batch.");
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
                    source.imageModel(), illustrations.size(), audioKey, videoKey, captionsKey,
                    rendered.durationMs(), Instant.now());
            Files.writeString(manifest, mapper.writeValueAsString(result) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Completed {}: {} ms", source.partyCode(), rendered.durationMs());
        } finally {
            deleteTemporaryTree(work, partyOutput);
        }
    }

    private List<PcmAudio> narration(SourceMedia source, ProgrammeMediaScript script, Path partyOutput) throws Exception {
        Path cache = partyOutput.resolve("audio-cache").resolve(ProgrammeMediaWorker.cacheIdentity(tts.model()));
        Files.createDirectories(cache);
        return parallel(script.segments().size(), section -> {
            String voice = tts.voiceForSection(section);
            String narration = policy.ttsText(script.segments().get(section).narration());
            Path cached = cache.resolve(narrationCacheFileName(
                    tts.model(), voice, source.pronunciationVersion(), section, narration));
            if (Files.exists(cached)) {
                try {
                    return tts.restore(Files.readAllBytes(cached));
                } catch (IllegalArgumentException corrupt) {
                    Files.deleteIfExists(cached);
                }
            }
            PcmAudio generated = synthesize(narration, voice);
            writeAtomically(cached, generated.data());
            log.info("Generated {} narration section {}/{}", source.partyCode(), section + 1,
                    script.segments().size());
            return generated;
        });
    }

    private PcmAudio synthesize(String narration, String voice) throws InterruptedException {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= PROVIDER_ATTEMPTS; attempt++) {
            try {
                return tts.synthesize(narration, voice);
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
            SourceMedia source, int count, CombinedAudio audio, Path work) throws IOException, InterruptedException {
        String root = sourceRoot(source.audioObjectKey());
        String identity = ProgrammeMediaWorker.cacheIdentity(source.imageModel());
        return parallel(count, index -> {
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
            return new Illustration(
                    target, audio.timings().get(index).startMs(), audio.timings().get(index).endMs());
        });
    }

    private <T> List<T> parallel(int count, IndexedCall<T> call) throws IOException, InterruptedException {
        Semaphore permits = new Semaphore(concurrency);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletionService<IndexedResult<T>> completed = new ExecutorCompletionService<>(executor);
            List<Future<IndexedResult<T>>> futures = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int section = index;
                futures.add(completed.submit(() -> {
                    permits.acquire();
                    try {
                        return new IndexedResult<>(section, call.call(section));
                    } finally {
                        permits.release();
                    }
                }));
            }
            List<T> ordered = new ArrayList<>(Collections.nCopies(count, null));
            try {
                for (int index = 0; index < count; index++) {
                    IndexedResult<T> result = await(completed.take());
                    ordered.set(result.index(), result.value());
                }
                return List.copyOf(ordered);
            } catch (IOException | InterruptedException | RuntimeException failure) {
                futures.forEach(future -> future.cancel(true));
                throw failure;
            } catch (Error failure) {
                futures.forEach(future -> future.cancel(true));
                throw failure;
            }
        }
    }

    private <T> T await(Future<T> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException exception) throw exception;
            if (cause instanceof InterruptedException exception) throw exception;
            if (cause instanceof RuntimeException exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Programme media rerender operation failed.", cause);
        }
    }

    static String narrationCacheFileName(
            String model, String voice, String pronunciationVersion, int section, String narration) {
        if (section < 0) {
            throw new IllegalArgumentException("Narration section index must not be negative.");
        }
        String identity = String.join("\u0000",
                requiredCacheValue(model, "model"),
                requiredCacheValue(voice, "voice"),
                requiredCacheValue(pronunciationVersion, "pronunciation version"),
                requiredCacheValue(narration, "narration"));
        try {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
            return ProgrammeMediaWorker.cacheIdentity(voice)
                    + "-segment-" + String.format(Locale.ROOT, "%02d", section)
                    + "-" + digest.substring(0, 24) + ".pcm";
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private static String requiredCacheValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Narration cache " + label + " must not be blank.");
        }
        return value.strip();
    }

    private void writeAtomically(Path target, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Set<String> completedParties(Path manifest) throws IOException {
        if (!Files.exists(manifest)) return Set.of();
        HashSet<String> completed = new HashSet<>();
        for (String line : Files.readAllLines(manifest)) {
            if (!line.isBlank()) completed.add(mapper.readTree(line).get("partyCode").asText());
        }
        return Set.copyOf(completed);
    }

    private SourceMedia source(String line) throws IOException {
        JsonNode json = mapper.readTree(line);
        JsonNode recordedImageModel = json.get("image_model");
        String sourceImageModel = recordedImageModel == null || recordedImageModel.asText().isBlank()
                ? illustrationModel
                : recordedImageModel.asText().strip();
        return new SourceMedia(
                UUID.fromString(json.get("id").asText()), UUID.fromString(json.get("programme_id").asText()),
                json.get("party_code").asText(), json.get("source_sha256").asText(),
                json.get("script_revision").asInt(), json.get("script_json").asText(),
                json.get("pronunciation_version").asText(), sourceImageModel,
                json.get("audio_object_key").asText());
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
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
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
            String imageModel,
            String audioObjectKey) {
    }

    @FunctionalInterface
    private interface IndexedCall<T> {
        T call(int index) throws Exception;
    }

    private record IndexedResult<T>(int index, T value) {
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
