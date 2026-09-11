package dev.maboullaite.fhemni.programme.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import dev.maboullaite.fhemni.catalog.PoliticalParty;
import dev.maboullaite.fhemni.catalog.PoliticalPartyRepository;
import dev.maboullaite.fhemni.gemini.ProgrammeMediaScriptGateway;
import dev.maboullaite.fhemni.programme.EditorialStatus;
import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import dev.maboullaite.fhemni.programme.media.GeminiProgrammeIllustrationGateway.GeneratedImage;
import dev.maboullaite.fhemni.programme.media.GeminiProgrammeTtsGateway.PcmAudio;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaRenderer.Illustration;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaRepository.Lease;
import dev.maboullaite.fhemni.programme.media.WavePcm.CombinedAudio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fhemni.programme-media.worker-enabled", havingValue = "true")
public class ProgrammeMediaWorker {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeMediaWorker.class);
    private static final Duration LEASE = Duration.ofMinutes(4);
    private static final int PROVIDER_ATTEMPTS = 3;

    private final ProgrammeMediaRepository repository;
    private final PartyProgrammeService programmes;
    private final PoliticalPartyRepository parties;
    private final ProgrammeMediaScriptGateway scripts;
    private final ProgrammeMediaScriptPolicy policy;
    private final GeminiProgrammeTtsGateway tts;
    private final GeminiProgrammeIllustrationGateway illustrations;
    private final ProgrammeMediaRenderer renderer;
    private final ProgrammeMediaStorage storage;
    private final ExecutorService executor;
    private final ExecutorService providerExecutor;
    private final Semaphore providerPermits;
    private final Duration retryDelay;
    private final Path tempRoot;
    private final Clock clock;
    private final String owner = UUID.randomUUID().toString();
    private final Set<UUID> submitted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Lease> activeLeases = new ConcurrentHashMap<>();

    @Autowired
    public ProgrammeMediaWorker(
            ProgrammeMediaRepository repository,
            PartyProgrammeService programmes,
            PoliticalPartyRepository parties,
            ProgrammeMediaScriptGateway scripts,
            ProgrammeMediaScriptPolicy policy,
            GeminiProgrammeTtsGateway tts,
            GeminiProgrammeIllustrationGateway illustrations,
            ProgrammeMediaRenderer renderer,
            ProgrammeMediaStorage storage,
            @Qualifier("programmeMediaExecutor") ExecutorService executor,
            @Qualifier("programmeMediaProviderExecutor") ExecutorService providerExecutor,
            @Value("${fhemni.programme-media.provider-concurrency:4}") int providerConcurrency,
            @Value("${fhemni.programme-media.retry-delay:PT1M}") Duration retryDelay,
            @Value("${fhemni.programme-media.temp-directory:/tmp}") String tempDirectory) {
        this(repository, programmes, parties, scripts, policy, tts, illustrations, renderer, storage,
                executor, providerExecutor, providerConcurrency, retryDelay, Path.of(tempDirectory), Clock.systemUTC());
    }

    ProgrammeMediaWorker(
            ProgrammeMediaRepository repository,
            PartyProgrammeService programmes,
            PoliticalPartyRepository parties,
            ProgrammeMediaScriptGateway scripts,
            ProgrammeMediaScriptPolicy policy,
            GeminiProgrammeTtsGateway tts,
            GeminiProgrammeIllustrationGateway illustrations,
            ProgrammeMediaRenderer renderer,
            ProgrammeMediaStorage storage,
            ExecutorService executor,
            ExecutorService providerExecutor,
            int providerConcurrency,
            Duration retryDelay,
            Path tempRoot,
            Clock clock) {
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("Programme media retry delay must be positive.");
        }
        if (providerConcurrency < 1 || providerConcurrency > 8) {
            throw new IllegalArgumentException("Programme media provider concurrency must be between 1 and 8.");
        }
        this.repository = repository;
        this.programmes = programmes;
        this.parties = parties;
        this.scripts = scripts;
        this.policy = policy;
        this.tts = tts;
        this.illustrations = illustrations;
        this.renderer = renderer;
        this.storage = storage;
        this.executor = executor;
        this.providerExecutor = providerExecutor;
        this.providerPermits = new Semaphore(providerConcurrency);
        this.retryDelay = retryDelay;
        this.tempRoot = tempRoot.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fhemni.programme-media.dispatch-interval-ms:5000}")
    public void dispatch() {
        Instant now = clock.instant();
        repository.recoverExpired(now);
        repository.dispatchable(now, 2).forEach(id -> {
            if (submitted.add(id)) {
                executor.submit(() -> run(id));
            }
        });
    }

    @Scheduled(fixedDelayString = "${fhemni.programme-media.lease-renew-interval-ms:30000}")
    public void renewLeases() {
        Instant now = clock.instant();
        activeLeases.entrySet().removeIf(entry ->
                !repository.renew(entry.getValue(), now, now.plus(LEASE)));
    }

    private void run(UUID id) {
        Lease lease = null;
        try {
            Instant now = clock.instant();
            lease = repository.claim(id, owner, now, now.plus(LEASE)).orElse(null);
            if (lease == null) {
                return;
            }
            activeLeases.put(id, lease);
            ProgrammeMedia media = repository.find(id).orElseThrow();
            AdminProgrammeView programme = programmes.adminProgramme(media.programmeId());
            if (programme.status() != EditorialStatus.PUBLISHED
                    || !programme.sourceSha256().equals(media.sourceSha256())) {
                repository.staleOwned(lease, clock.instant());
                return;
            }
            switch (media.status()) {
                case GENERATING_SCRIPT -> generateScript(lease, programme);
                case RENDERING_MEDIA -> generateMedia(lease, media, programme);
                default -> throw new IllegalStateException("Claimed programme media has no runnable stage.");
            }
        } catch (ProgrammeMediaLeaseLostException lost) {
            log.info("Programme media {} stopped after its lease moved to another worker", id);
        } catch (RuntimeException | IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Programme media {} failed", id, failure);
            if (lease != null) {
                try {
                    repository.failOrRetry(
                            lease,
                            "MEDIA_GENERATION_FAILED",
                            "The programme briefing could not be generated. It will retry automatically.",
                            clock.instant().plus(retryDelay),
                            clock.instant());
                } catch (ProgrammeMediaLeaseLostException lost) {
                    log.info("Programme media {} was reclaimed after its worker failed", id);
                }
            }
        } finally {
            if (lease != null) {
                activeLeases.remove(id, lease);
            }
            submitted.remove(id);
        }
    }

    private void generateScript(Lease lease, AdminProgrammeView programme) {
        ProgrammeMediaScript generated = policy.validate(scripts.generate(programme), programme);
        repository.completeScript(
                lease, generated, policy.spokenText(generated), scripts.model(), clock.instant());
    }

    private void generateMedia(Lease lease, ProgrammeMedia media, AdminProgrammeView programme)
            throws IOException, InterruptedException {
        ProgrammeMediaScript script = policy.validate(media.script(), programme);
        Files.createDirectories(tempRoot);
        Path work = Files.createTempDirectory(tempRoot, "fhemni-programme-media-");
        try {
            String prefix = objectPrefix(media);
            List<PcmAudio> audioSegments = parallelGenerate(script.segments().size(), index ->
                    narrationSegment(work, prefix, media, index, script.segments().get(index)));
            CombinedAudio combined = WavePcm.combine(audioSegments, 260);
            List<Illustration> generatedIllustrations = generateIllustrations(work, prefix, script, combined);
            PoliticalParty party = parties.findByCode(media.partyCode())
                    .orElseThrow(() -> new IllegalStateException("The programme party is missing from the directory."));
            var rendered = renderer.render(
                    work, party.symbolAsset(), script, combined, generatedIllustrations);
            String outputPrefix = attemptOutputPrefix(prefix, lease);
            String audioKey = outputPrefix + "/summary-darija.mp3";
            String videoKey = outputPrefix + "/summary-darija-4x5.mp4";
            String captionsKey = outputPrefix + "/summary-darija.vtt";
            storage.put(audioKey, rendered.audio(), "audio/mpeg");
            storage.put(videoKey, rendered.video(), "video/mp4");
            storage.put(captionsKey, rendered.captions(), "text/vtt; charset=utf-8");
            repository.completeMedia(
                    lease, tts.model(), tts.voice(),
                    generatedIllustrations.isEmpty() ? null : illustrations.model(), generatedIllustrations.size(),
                    audioKey, videoKey, captionsKey,
                    rendered.durationMs(), clock.instant());
        } finally {
            deleteTemporaryTree(work);
        }
    }

    private PcmAudio narrationSegment(
            Path work,
            String prefix,
            ProgrammeMedia media,
            int index,
            ProgrammeMediaScript.Segment segment) throws IOException, InterruptedException {
        String key = prefix + "/work/tts-" + cacheIdentity(tts.model())
                + "-" + media.pronunciationVersion()
                + "-" + tts.voiceCacheKeyForSection(index)
                + "/segment-" + String.format(java.util.Locale.ROOT, "%02d", index) + ".pcm";
        Path cached = work.resolve("narration-segment-" + index + ".pcm");
        if (restore(key, cached)) {
            try {
                PcmAudio audio = tts.restore(Files.readAllBytes(cached));
                log.info("Programme media {} restored narration section {}/{}", media.id(),
                        index + 1, media.script().segments().size());
                return audio;
            } catch (IllegalArgumentException corrupt) {
                log.warn("Ignoring corrupt cached narration section {} for programme media {}", index + 1, media.id());
            }
        }
        String sectionVoice = tts.voiceForSection(index);
        PcmAudio audio = retryProvider("narration section " + (index + 1),
                () -> tts.synthesize(policy.ttsText(segment.narration()), sectionVoice));
        Files.write(cached, audio.data());
        storage.put(key, cached, "application/octet-stream");
        log.info("Programme media {} generated narration section {}/{} with voice {}", media.id(),
                index + 1, media.script().segments().size(), sectionVoice);
        return audio;
    }

    private List<Illustration> generateIllustrations(
            Path work,
            String prefix,
            ProgrammeMediaScript script,
            CombinedAudio audio) throws IOException, InterruptedException {
        if (!illustrations.enabled()) {
            return List.of();
        }
        return parallelGenerate(script.segments().size(), index -> {
            String key = prefix + "/work/illustrations-" + cacheIdentity(illustrations.model())
                    + "/section-" + String.format(java.util.Locale.ROOT, "%02d", index) + ".image";
            Path file = work.resolve("illustration-" + index + ".image");
            if (!restore(key, file)) {
                GeneratedImage image = retryProvider("illustration section " + (index + 1),
                        () -> illustrations.generate(script.segments().get(index).message()));
                Files.write(file, image.data());
                storage.put(key, file, image.contentType());
                log.info("Generated illustration section {}/{}", index + 1, script.segments().size());
            } else {
                log.info("Restored illustration section {}/{}", index + 1, script.segments().size());
            }
            long startMs = audio.timings().get(index).startMs();
            long endMs = audio.timings().get(index).endMs();
            return new Illustration(file, startMs, endMs);
        });
    }

    private <T> List<T> parallelGenerate(int count, IndexedProviderCall<T> call)
            throws IOException, InterruptedException {
        List<Future<T>> futures = new ArrayList<>(count);
        try {
            for (int index = 0; index < count; index++) {
                int sectionIndex = index;
                futures.add(providerExecutor.submit(() -> {
                    providerPermits.acquire();
                    try {
                        return call.call(sectionIndex);
                    } finally {
                        providerPermits.release();
                    }
                }));
            }
            List<T> results = new ArrayList<>(count);
            for (Future<T> future : futures) {
                results.add(await(future));
            }
            return List.copyOf(results);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            futures.forEach(future -> future.cancel(true));
            throw failure;
        } catch (Error failure) {
            futures.forEach(future -> future.cancel(true));
            throw failure;
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
            throw new IllegalStateException("Programme media provider failed.", cause);
        }
    }

    private boolean restore(String key, Path target) {
        try (ProgrammeMediaStorage.StoredObject object = storage.open(key)) {
            if (object.size() == 0) {
                return false;
            }
            Files.copy(object.content(), target, StandardCopyOption.REPLACE_EXISTING);
            return Files.size(target) > 0;
        } catch (IOException missingOrUnavailable) {
            return false;
        }
    }

    private <T> T retryProvider(String stage, ProviderCall<T> call) throws InterruptedException {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= PROVIDER_ATTEMPTS; attempt++) {
            try {
                return call.call();
            } catch (RuntimeException failure) {
                last = failure;
                if (attempt == PROVIDER_ATTEMPTS) {
                    break;
                }
                log.warn("Programme media {} failed on attempt {}/{}; retrying", stage, attempt, PROVIDER_ATTEMPTS);
                Thread.sleep(Duration.ofSeconds(attempt * 2L));
            }
        }
        throw last;
    }

    private String objectPrefix(ProgrammeMedia media) {
        return "programmes/" + media.partyCode().toLowerCase(java.util.Locale.ROOT)
                + "/" + media.sourceSha256()
                + "/" + media.id()
                + "/script-" + media.scriptRevision();
    }

    static String cacheIdentity(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Programme media model name must not be blank.");
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.strip().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String attemptOutputPrefix(String objectPrefix, Lease lease) {
        if (objectPrefix == null || objectPrefix.isBlank()) {
            throw new IllegalArgumentException("Programme media object prefix must not be blank.");
        }
        return objectPrefix + "/attempt-" + lease.token();
    }

    private void deleteTemporaryTree(Path work) {
        if (work == null || !work.normalize().startsWith(tempRoot) || work.equals(tempRoot)) {
            return;
        }
        try (var paths = Files.walk(work)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("Could not remove temporary programme media file {}", path, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("Could not clean programme media workspace {}", work, exception);
        }
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T call();
    }

    @FunctionalInterface
    private interface IndexedProviderCall<T> {
        T call(int index) throws IOException, InterruptedException;
    }
}
