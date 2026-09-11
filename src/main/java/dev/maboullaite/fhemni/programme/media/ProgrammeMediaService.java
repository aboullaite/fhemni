package dev.maboullaite.fhemni.programme.media;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import dev.maboullaite.fhemni.gemini.ProgrammeMediaScriptGateway;
import dev.maboullaite.fhemni.programme.EditorialStatus;
import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProgrammeMediaService {

    private final ProgrammeMediaRepository repository;
    private final PartyProgrammeService programmes;
    private final ProgrammeMediaScriptGateway scripts;
    private final GeminiProgrammeTtsGateway tts;
    private final ProgrammeMediaScriptPolicy policy;
    private final int maxAttempts;

    public ProgrammeMediaService(
            ProgrammeMediaRepository repository,
            PartyProgrammeService programmes,
            ProgrammeMediaScriptGateway scripts,
            GeminiProgrammeTtsGateway tts,
            ProgrammeMediaScriptPolicy policy,
            @Value("${fhemni.programme-media.max-attempts:3}") int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("Programme media attempts must be between one and five.");
        }
        this.repository = repository;
        this.programmes = programmes;
        this.scripts = scripts;
        this.tts = tts;
        this.policy = policy;
        this.maxAttempts = maxAttempts;
    }

    public Map<UUID, ProgrammeMedia> latest() {
        return repository.latestByProgramme();
    }

    public List<ProgrammeMedia> history(UUID programmeId) {
        programmes.adminProgramme(programmeId);
        return repository.history(programmeId);
    }

    public ProgrammeMedia start(UUID programmeId, boolean regenerate) {
        AdminProgrammeView programme = requirePublished(programmeId);
        if (!scripts.live() || !tts.live()) {
            throw new IllegalStateException("Gemini script and narration services must be configured.");
        }
        ProgrammeMedia working = repository.working(programmeId).orElse(null);
        if (!regenerate && working != null && working.sourceSha256().equals(programme.sourceSha256())) {
            return working;
        }
        return repository.create(
                programmeId,
                programme.partyCode(),
                programme.sourceSha256(),
                ProgrammeMediaScriptPolicy.PRONUNCIATION_VERSION,
                maxAttempts,
                Instant.now());
    }

    public ProgrammeMedia saveScript(UUID mediaId, ProgrammeMediaScript candidate) {
        ProgrammeMedia media = media(mediaId);
        ProgrammeMediaScript validated = policy.validate(candidate, requireCurrentSource(media));
        return repository.saveReviewedScript(mediaId, validated, policy.spokenText(validated), Instant.now());
    }

    public ProgrammeMedia approveScript(UUID mediaId, ProgrammeMediaScript candidate) {
        ProgrammeMedia media = media(mediaId);
        ProgrammeMediaScript validated = policy.validate(candidate, requireCurrentSource(media));
        return repository.queueMedia(mediaId, validated, policy.spokenText(validated), Instant.now());
    }

    public ProgrammeMedia publish(UUID mediaId) {
        ProgrammeMedia media = media(mediaId);
        requireCurrentSource(media);
        if (media.audioObjectKey() == null || media.videoObjectKey() == null || media.captionsObjectKey() == null) {
            throw new IllegalStateException("Audio, video and captions must all exist before publication.");
        }
        return repository.publish(mediaId, Instant.now());
    }

    public ProgrammeMedia retry(UUID mediaId) {
        ProgrammeMedia media = media(mediaId);
        requireCurrentSource(media);
        return repository.retryFailed(mediaId, Instant.now());
    }

    public PublicProgrammeMedia published(String partyCode) {
        ProgrammeMedia media = currentPublishedMedia(partyCode);
        String assetVersion = "?v=" + media.id();
        return new PublicProgrammeMedia(
                media.id(), media.partyCode(), media.durationMs(), media.script().headline(), media.script().segments(),
                "/api/catalog/parties/" + media.partyCode() + "/programme/media/audio" + assetVersion,
                "/api/catalog/parties/" + media.partyCode() + "/programme/media/video" + assetVersion,
                "/api/catalog/parties/" + media.partyCode() + "/programme/media/captions" + assetVersion,
                media.publishedAt());
    }

    public ProgrammeMedia publishedRecord(String partyCode) {
        return currentPublishedMedia(partyCode);
    }

    public ProgrammeMedia adminRecord(UUID mediaId) {
        return media(mediaId);
    }

    private ProgrammeMedia media(UUID id) {
        return repository.find(id).orElseThrow(() -> new NoSuchElementException("Programme media was not found."));
    }

    private ProgrammeMedia currentPublishedMedia(String partyCode) {
        ProgrammeMedia media = repository.publishedByParty(partyCode.toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new NoSuchElementException("No published programme briefing was found."));
        if (!media.sourceSha256().equals(programmes.publishedSourceSha256(partyCode))) {
            throw new NoSuchElementException("The published briefing is stale for the current programme.");
        }
        return media;
    }

    private AdminProgrammeView requireCurrentSource(ProgrammeMedia media) {
        AdminProgrammeView programme = requirePublished(media.programmeId());
        if (!programme.sourceSha256().equals(media.sourceSha256())) {
            throw new IllegalStateException("The programme source changed; generate a fresh briefing.");
        }
        return programme;
    }

    private AdminProgrammeView requirePublished(UUID programmeId) {
        AdminProgrammeView programme = programmes.adminProgramme(programmeId);
        if (programme.status() != EditorialStatus.PUBLISHED) {
            throw new IllegalStateException("Programme media can only be generated from a published programme.");
        }
        return programme;
    }

    public record PublicProgrammeMedia(
            UUID id,
            String partyCode,
            Long durationMs,
            String headline,
            List<ProgrammeMediaScript.Segment> transcript,
            String audioUrl,
            String videoUrl,
            String captionsUrl,
            Instant publishedAt) {
    }
}
