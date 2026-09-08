package dev.maboullaite.fhemni.programme;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.GeneratedAssessment;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ProgrammeExtraction;
import dev.maboullaite.fhemni.programme.ProgrammeFactCheckService.FactCheckResult;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PromiseAssessment.Evidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class PartyProgrammeService {

    public static final int ELECTION_YEAR = 2026;
    public static final int HORIZON_YEARS = 5;
    private static final int TERM_END_YEAR = 2031;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final PartyProgrammeRepository repository;

    public PartyProgrammeService(PartyProgrammeRepository repository) {
        this.repository = repository;
    }

    public List<AdminProgrammeView> adminProgrammes() {
        return repository.findAllForAdminList().stream().map(this::adminView).toList();
    }

    public Optional<AdminProgrammeView> programmeBySourceUrl(String sourceUrl) {
        return repository.findBySourceUrl(sourceUrl).map(this::adminView);
    }

    @Transactional
    public AdminProgrammeView saveGeneratedExtraction(
            String sourceUrl,
            ProgrammeExtraction extraction,
            List<String> extractionWarnings) {
        if (extraction == null || !extraction.official2026Programme() || extraction.electionYear() != ELECTION_YEAR) {
            throw new IllegalArgumentException("The URL must contain an official final programme for the 2026 election.");
        }
        List<ExtractedPromise> promises = extraction.promises() == null ? List.of() : extraction.promises();
        if (promises.isEmpty() || promises.size() > 30) {
            throw new IllegalArgumentException("Gemini must extract between 1 and 30 measurable promises.");
        }
        long distinctSlugs = promises.stream().map(ExtractedPromise::slug).distinct().count();
        if (distinctSlugs != promises.size()) {
            throw new IllegalArgumentException("Gemini returned duplicate promise slugs.");
        }

        AdminProgrammeView programme = createProgramme(new DraftProgramme(
                extraction.partyCode(), extraction.title(), extraction.summary(), sourceUrl,
                extraction.sourceLabel(), extraction.sourceLanguage(), extraction.sourceSnapshot(), false,
                extractionWarnings));
        for (ExtractedPromise promise : promises) {
            createPromise(programme.id(), new DraftPromise(
                    promise.slug(), promise.topic(), promise.title(), promise.promiseText(),
                    promise.sourceLocator(), promise.mechanism(), promise.financing()));
        }
        return adminView(repository.findById(programme.id()).orElseThrow());
    }

    @Transactional
    public AdminProgrammeView replaceGeneratedExtraction(
            UUID existingProgrammeId,
            String sourceUrl,
            ProgrammeExtraction extraction,
            List<String> extractionWarnings) {
        AdminProgrammeView existing = adminView(programme(existingProgrammeId));
        requireDraft(existing.status(), "programme");
        boolean hasPublishedChildren = existing.promises().stream().anyMatch(item ->
                item.promise().status() != EditorialStatus.DRAFT
                        || item.assessments().stream().anyMatch(assessment ->
                                assessment.status() != EditorialStatus.DRAFT));
        if (hasPublishedChildren) {
            throw new IllegalStateException(
                    "A programme with published promises or assessments cannot be replaced.");
        }
        repository.deleteDraftProgramme(existingProgrammeId);
        return saveGeneratedExtraction(sourceUrl, extraction, extractionWarnings);
    }

    @Transactional
    public AdminProgrammeView saveGeneratedAssessments(
            UUID programmeId,
            List<ExtractedPromise> requestedPromises,
            FactCheckResult generatedResult) {
        AdminProgrammeView programme = adminView(programme(programmeId));
        Map<String, AdminPromiseView> pending = new LinkedHashMap<>();
        for (ExtractedPromise requested : requestedPromises) {
            AdminPromiseView promise = programme.promises().stream()
                    .filter(item -> item.promise().slug().equals(requested.slug()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The fact-check batch contains an unknown promise."));
            if (!promise.assessments().isEmpty() || pending.put(requested.slug(), promise) != null) {
                throw new IllegalArgumentException(
                        "The fact-check batch contains an assessed or duplicate promise.");
            }
        }
        List<GeneratedAssessment> assessments = generatedResult == null || generatedResult.assessments() == null
                ? List.of()
                : generatedResult.assessments();
        if (assessments.size() != pending.size()) {
            throw new IllegalArgumentException("The fact-check provider must assess every promise in the requested batch.");
        }
        for (GeneratedAssessment generated : assessments) {
            AdminPromiseView promise = pending.remove(generated.promiseSlug());
            if (promise == null) {
                throw new IllegalArgumentException("The fact-check provider returned an unknown or duplicate promise slug.");
            }
            createAssessment(promise.promise().id(), new DraftAssessment(
                    generated.verdict(), generated.summary(), generated.requirements(), generated.assumptions(),
                    generated.calculationNotes(), generatedResult.methodologyVersion(), LocalDate.now(),
                    generated.evidence(), generatedResult.providerMode(), generatedResult.modelNames()));
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("The fact-check provider did not assess every promise.");
        }
        return adminView(repository.findById(programmeId).orElseThrow());
    }

    public List<ExtractedPromise> promisesAwaitingAssessment(AdminProgrammeView programme) {
        return programme.promises().stream()
                .filter(item -> item.assessments().isEmpty())
                .map(item -> new ExtractedPromise(
                        item.promise().slug(), item.promise().topic(), item.promise().title(),
                        item.promise().promiseText(), item.promise().sourceLocator(),
                        item.promise().mechanism(), item.promise().financing()))
                .toList();
    }

    @Transactional
    public AdminProgrammeView createProgramme(DraftProgramme draft) {
        String partyCode = required(draft.partyCode(), "Party code", 10).toUpperCase(Locale.ROOT);
        if (!repository.visiblePartyExists(partyCode)) {
            throw new IllegalArgumentException("Choose a visible party from the curated directory.");
        }
        if (repository.findByParty(partyCode, ELECTION_YEAR).isPresent()) {
            throw new IllegalStateException("That party already has a 2026 programme draft.");
        }
        String snapshot = required(draft.sourceSnapshot(), "Source snapshot", 2_000_000);
        Instant now = Instant.now();
        PartyProgramme programme = new PartyProgramme(
                UUID.randomUUID(), partyCode, ELECTION_YEAR, ELECTION_YEAR, TERM_END_YEAR,
                localized(draft.title(), "Title", 300), localized(draft.summary(), "Summary", 20_000),
                httpsUrl(draft.sourceUrl(), "Programme source URL"),
                required(draft.sourceLabel(), "Source label", 300),
                required(draft.sourceLanguage(), "Source language", 12).toLowerCase(Locale.ROOT),
                snapshot, cleanWarnings(draft.extractionWarnings()), sha256(snapshot), now,
                draft.sourceVerified(), EditorialStatus.DRAFT,
                now, now, null);
        repository.insertProgramme(programme);
        return adminView(programme);
    }

    @Transactional
    public AdminPromiseView createPromise(UUID programmeId, DraftPromise draft) {
        PartyProgramme programme = programme(programmeId);
        requireDraft(programme.status(), "programme");
        String slug = required(draft.slug(), "Promise slug", 180).toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Promise slug must contain lowercase letters, numbers and hyphens only.");
        }
        if (repository.promiseSlugExists(slug)) {
            throw new IllegalStateException("That promise slug is already in use.");
        }
        Instant now = Instant.now();
        PartyPromise promise = new PartyPromise(
                UUID.randomUUID(), programmeId, slug, required(draft.topic(), "Topic", 80),
                localized(draft.title(), "Promise title", 300),
                required(draft.promiseText(), "Exact promise wording", 30_000),
                required(draft.sourceLocator(), "Source locator", 300),
                text(draft.mechanism(), 30_000), text(draft.financing(), 30_000),
                EditorialStatus.DRAFT, now, now, null);
        try {
            repository.insertPromise(promise);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("That promise slug is already in use.", exception);
        }
        return adminPromiseView(promise);
    }

    @Transactional
    public PromiseAssessment createAssessment(UUID promiseId, DraftAssessment draft) {
        repository.lockPromise(promiseId);
        PartyPromise promise = promise(promiseId);
        requireAssessable(promise.status());
        if (draft.verdict() == null) {
            throw new IllegalArgumentException("Choose a five-year feasibility verdict.");
        }
        if (draft.dataCutoff() == null || draft.dataCutoff().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Data cutoff must be today or earlier.");
        }
        List<EvidenceDraft> evidenceDrafts = draft.evidence() == null ? List.of() : draft.evidence();
        if (evidenceDrafts.isEmpty() || evidenceDrafts.size() > 20) {
            throw new IllegalArgumentException("An assessment needs between 1 and 20 evidence sources.");
        }
        List<Evidence> evidence = java.util.stream.IntStream.range(0, evidenceDrafts.size())
                .mapToObj(index -> evidence(evidenceDrafts.get(index), index))
                .toList();
        PromiseAssessment assessment = new PromiseAssessment(
                UUID.randomUUID(), promiseId, repository.nextAssessmentRevision(promiseId), HORIZON_YEARS,
                draft.verdict(), localized(draft.summary(), "Assessment summary", 30_000),
                localized(draft.requirements(), "Requirements", 30_000),
                localized(draft.assumptions(), "Assumptions", 50_000),
                localized(draft.calculationNotes(), "Calculation notes", 50_000),
                required(draft.methodologyVersion(), "Methodology version", 40),
                providerMode(draft.providerMode()), text(draft.modelNames(), 300),
                draft.dataCutoff(), EditorialStatus.DRAFT, Instant.now(), null, evidence);
        repository.insertAssessment(assessment);
        return assessment;
    }

    @Transactional
    public PromiseAssessment publishAssessment(UUID assessmentId) {
        PromiseAssessment assessment = repository.findAssessment(assessmentId)
                .orElseThrow(() -> new NoSuchElementException("Assessment not found."));
        requireDraft(assessment.status(), "assessment");
        requirePublishableAssessment(assessment);
        repository.publishAssessment(assessment.id(), assessment.promiseId(), Instant.now());
        return repository.findAssessment(assessmentId).orElseThrow();
    }

    @Transactional
    public AdminPromiseView publishPromise(UUID promiseId) {
        PartyPromise promise = promise(promiseId);
        requireDraft(promise.status(), "promise");
        repository.publishPromise(promiseId, Instant.now());
        return adminPromiseView(repository.findPromise(promiseId).orElseThrow());
    }

    @Transactional
    public AdminProgrammeView publishProgramme(UUID programmeId) {
        PartyProgramme programme = programme(programmeId);
        requireDraft(programme.status(), "programme");
        repository.publishProgramme(programmeId, Instant.now());
        return adminView(repository.findById(programmeId).orElseThrow());
    }

    @Transactional
    public AdminProgrammeView publishAll(UUID programmeId) {
        PartyProgramme programme = programme(programmeId);
        requireDraft(programme.status(), "programme");
        if (!programme.sourceVerified()) {
            throw new IllegalStateException("Confirm the official 2026 source before publishing the programme.");
        }

        AdminProgrammeView view = adminView(programme);
        if (view.promises().isEmpty()) {
            throw new IllegalStateException("A programme needs at least one reviewed promise before publication.");
        }

        Map<UUID, PromiseAssessment> draftAssessments = new LinkedHashMap<>();
        for (AdminPromiseView item : view.promises()) {
            PartyPromise promise = item.promise();
            if (promise.status() == EditorialStatus.PUBLISHED) {
                continue;
            }
            requireDraft(promise.status(), "promise");
            PromiseAssessment draft = item.assessments().stream()
                    .filter(assessment -> assessment.status() == EditorialStatus.DRAFT)
                    .findFirst()
                    .orElse(null);
            boolean alreadyAssessed = item.assessments().stream()
                    .anyMatch(assessment -> assessment.status() == EditorialStatus.PUBLISHED);
            if (draft == null && !alreadyAssessed) {
                throw new IllegalStateException(
                        "Every promise needs a reviewed assessment before publishing the programme.");
            }
            if (draft != null) {
                requirePublishableAssessment(draft);
                draftAssessments.put(promise.id(), draft);
            }
        }

        Instant publishedAt = Instant.now();
        for (AdminPromiseView item : view.promises()) {
            PartyPromise promise = item.promise();
            if (promise.status() == EditorialStatus.PUBLISHED) {
                continue;
            }
            PromiseAssessment assessment = draftAssessments.get(promise.id());
            if (assessment != null) {
                repository.publishAssessment(assessment.id(), promise.id(), publishedAt);
            }
            repository.publishPromise(promise.id(), publishedAt);
        }
        repository.publishProgramme(programmeId, publishedAt);
        return adminView(repository.findById(programmeId).orElseThrow());
    }

    @Transactional
    public AdminProgrammeView verifySource(UUID programmeId) {
        PartyProgramme programme = programme(programmeId);
        requireDraft(programme.status(), "programme");
        repository.verifySource(programmeId, Instant.now());
        return adminView(repository.findById(programmeId).orElseThrow());
    }

    @Transactional
    public void discardAssessment(UUID assessmentId) {
        PromiseAssessment assessment = repository.findAssessment(assessmentId)
                .orElseThrow(() -> new NoSuchElementException("Assessment not found."));
        requireDraft(assessment.status(), "assessment");
        repository.deleteDraftAssessment(assessmentId);
    }

    @Transactional
    public void discardPromise(UUID promiseId) {
        PartyPromise promise = promise(promiseId);
        requireDraft(promise.status(), "promise");
        repository.deleteDraftPromise(promiseId);
    }

    @Transactional
    public void discardProgramme(UUID programmeId) {
        PartyProgramme programme = programme(programmeId);
        requireDraft(programme.status(), "programme");
        repository.deleteDraftProgramme(programmeId);
    }

    public PublicProgrammeView publishedProgramme(String partyCode) {
        PartyProgramme programme = repository.findPublishedByParty(
                        required(partyCode, "Party code", 10).toUpperCase(Locale.ROOT), ELECTION_YEAR)
                .orElseThrow(() -> new NoSuchElementException("No published 2026 programme was found for this party."));
        List<PartyPromise> publishedPromises = repository.findPromises(programme.id(), true);
        Map<UUID, List<PromiseAssessment>> assessments = repository.findAssessments(
                publishedPromises.stream().map(PartyPromise::id).toList(), true);
        List<PublicPromiseSummary> promises = publishedPromises.stream()
                .map(promise -> publicPromiseSummary(promise, assessments.getOrDefault(promise.id(), List.of())))
                .toList();
        return new PublicProgrammeView(
                programme.partyCode(), programme.electionYear(), programme.termStartYear(), programme.termEndYear(),
                programme.title(), programme.summary(), programme.sourceUrl(), programme.sourceLabel(),
                programme.sourceLanguage(), programme.sourceSha256(), programme.sourceRetrievedAt(),
                programme.publishedAt(), promises);
    }

    public PublicPromiseView publishedPromise(String slug) {
        PartyPromise promise = repository.findPublishedPromiseBySlug(required(slug, "Promise slug", 180))
                .orElseThrow(() -> new NoSuchElementException("Published promise not found."));
        PartyProgramme programme = repository.findSummaryById(promise.programmeId()).orElseThrow();
        PromiseAssessment assessment = latestPublishedAssessment(promise.id());
        return new PublicPromiseView(
                promise.id(), promise.slug(), programme.partyCode(), programme.electionYear(),
                programme.termStartYear(), programme.termEndYear(), promise.topic(), promise.title(),
                promise.promiseText(), promise.sourceLocator(), promise.mechanism(), promise.financing(),
                programme.sourceUrl(), programme.sourceLabel(), assessment);
    }

    public List<PublicPromiseHighlight> featuredPublishedPromises(int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 6);
        List<PartyProgrammeRepository.PublishedPromise> candidates = repository.findFeaturedPublishedPromises(limit);
        Map<UUID, List<PromiseAssessment>> assessments = repository.findAssessments(
                candidates.stream().map(candidate -> candidate.promise().id()).toList(), true);
        return candidates.stream()
                .map(candidate -> {
                    PartyPromise promise = candidate.promise();
                    PromiseAssessment assessment = latestPublishedAssessment(
                            promise.id(), assessments.getOrDefault(promise.id(), List.of()));
                    return new PublicPromiseHighlight(
                            candidate.partyCode(), promise.slug(), promise.topic(), promise.title(),
                            assessment.verdict(), assessment.summary(), assessment.dataCutoff());
                })
                .toList();
    }

    private AdminProgrammeView adminView(PartyProgramme programme) {
        List<PartyPromise> programmePromises = repository.findPromises(programme.id(), false);
        Map<UUID, List<PromiseAssessment>> assessments = repository.findAssessments(
                programmePromises.stream().map(PartyPromise::id).toList(), false);
        List<AdminPromiseView> promises = programmePromises.stream()
                .map(promise -> new AdminPromiseView(
                        promise, assessments.getOrDefault(promise.id(), List.of())))
                .toList();
        return new AdminProgrammeView(
                programme.id(), programme.partyCode(), programme.electionYear(), programme.termStartYear(),
                programme.termEndYear(), programme.title(), programme.summary(), programme.sourceUrl(),
                programme.sourceLabel(), programme.sourceLanguage(), programme.extractionWarnings(),
                programme.sourceSha256(),
                programme.sourceRetrievedAt(), programme.sourceVerified(), programme.status(),
                programme.publishedAt(), promises);
    }

    private AdminPromiseView adminPromiseView(PartyPromise promise) {
        return new AdminPromiseView(promise, repository.findAssessments(promise.id(), false));
    }

    private PublicPromiseSummary publicPromiseSummary(
            PartyPromise promise,
            List<PromiseAssessment> assessments) {
        PromiseAssessment assessment = latestPublishedAssessment(promise.id(), assessments);
        return new PublicPromiseSummary(
                promise.slug(), promise.topic(), promise.title(), promise.promiseText(),
                assessment.verdict(), assessment.summary(), assessment.dataCutoff());
    }

    private PromiseAssessment latestPublishedAssessment(UUID promiseId) {
        return latestPublishedAssessment(promiseId, repository.findAssessments(promiseId, true));
    }

    private PromiseAssessment latestPublishedAssessment(
            UUID promiseId,
            List<PromiseAssessment> assessments) {
        return assessments.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("A published promise is missing its assessment."));
    }

    private PartyProgramme programme(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Programme not found."));
    }

    private PartyPromise promise(UUID id) {
        return repository.findPromise(id).orElseThrow(() -> new NoSuchElementException("Promise not found."));
    }

    private Evidence evidence(EvidenceDraft draft, int sortOrder) {
        if (draft == null) {
            throw new IllegalArgumentException("Evidence cannot be empty.");
        }
        LocalDate publishedOn = draft.publishedOn();
        if (publishedOn != null && publishedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Evidence publication date cannot be in the future.");
        }
        return new Evidence(
                UUID.randomUUID(), required(draft.publisher(), "Evidence publisher", 200),
                required(draft.title(), "Evidence title", 500), httpsUrl(draft.url(), "Evidence URL"),
                publishedOn, text(draft.note(), 10_000), sortOrder);
    }

    private static LocalizedText localized(LocalizedText value, String field, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required in Darija, French and English.");
        }
        return new LocalizedText(
                required(value.ar(), field + " (Darija)", maxLength),
                required(value.fr(), field + " (French)", maxLength),
                required(value.en(), field + " (English)", maxLength));
    }

    private static String required(String value, String field, int maxLength) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (clean.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be under " + maxLength + " characters.");
        }
        return clean;
    }

    private static String text(String value, int maxLength) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() > maxLength) {
            throw new IllegalArgumentException("Text must be under " + maxLength + " characters.");
        }
        return clean;
    }

    private static List<String> cleanWarnings(List<String> warnings) {
        if (warnings == null) {
            return List.of();
        }
        List<String> clean = warnings.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .map(value -> required(value, "Extraction warning", 2_000))
                .distinct()
                .toList();
        if (clean.size() > 30) {
            throw new IllegalArgumentException("A programme can keep at most 30 extraction warnings.");
        }
        return clean;
    }

    private static String providerMode(String value) {
        String mode = value == null || value.isBlank() ? "editorial" : value.strip().toLowerCase(Locale.ROOT);
        if (!List.of("editorial", "gemini", "openai", "consensus").contains(mode)) {
            throw new IllegalArgumentException("Assessment provider must be editorial, gemini, openai, or consensus.");
        }
        return mode;
    }

    private static String httpsUrl(String value, String field) {
        String clean = required(value, field, 2_000);
        try {
            URI uri = new URI(clean);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException(field + " must be an absolute HTTPS URL.");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available.", impossible);
        }
    }

    private static void requireDraft(EditorialStatus status, String resource) {
        if (status != EditorialStatus.DRAFT) {
            throw new IllegalStateException("Published " + resource + " records are immutable.");
        }
    }

    private static void requireAssessable(EditorialStatus status) {
        if (status != EditorialStatus.DRAFT && status != EditorialStatus.PUBLISHED) {
            throw new IllegalStateException("This promise cannot receive a new assessment revision.");
        }
    }

    private static void requirePublishableAssessment(PromiseAssessment assessment) {
        if (assessment.evidence().isEmpty()) {
            throw new IllegalStateException("An assessment needs evidence before publication.");
        }
    }

    public record DraftProgramme(
            String partyCode,
            LocalizedText title,
            LocalizedText summary,
            String sourceUrl,
            String sourceLabel,
            String sourceLanguage,
            String sourceSnapshot,
            boolean sourceVerified,
            List<String> extractionWarnings) {

        public DraftProgramme(
                String partyCode,
                LocalizedText title,
                LocalizedText summary,
                String sourceUrl,
                String sourceLabel,
                String sourceLanguage,
                String sourceSnapshot,
                boolean sourceVerified) {
            this(partyCode, title, summary, sourceUrl, sourceLabel, sourceLanguage,
                    sourceSnapshot, sourceVerified, List.of());
        }
    }

    public record DraftPromise(
            String slug,
            String topic,
            LocalizedText title,
            String promiseText,
            String sourceLocator,
            String mechanism,
            String financing) {
    }

    public record DraftAssessment(
            FeasibilityVerdict verdict,
            LocalizedText summary,
            LocalizedText requirements,
            LocalizedText assumptions,
            LocalizedText calculationNotes,
            String methodologyVersion,
            LocalDate dataCutoff,
            List<EvidenceDraft> evidence,
            String providerMode,
            String modelNames) {

        public DraftAssessment(
                FeasibilityVerdict verdict,
                LocalizedText summary,
                LocalizedText requirements,
                LocalizedText assumptions,
                LocalizedText calculationNotes,
                String methodologyVersion,
                LocalDate dataCutoff,
                List<EvidenceDraft> evidence) {
            this(verdict, summary, requirements, assumptions, calculationNotes, methodologyVersion,
                    dataCutoff, evidence, "editorial", "");
        }
    }

    public record EvidenceDraft(
            String publisher,
            String title,
            String url,
            LocalDate publishedOn,
            String note) {
    }

    public record AdminProgrammeView(
            UUID id,
            String partyCode,
            int electionYear,
            int termStartYear,
            int termEndYear,
            LocalizedText title,
            LocalizedText summary,
            String sourceUrl,
            String sourceLabel,
            String sourceLanguage,
            List<String> extractionWarnings,
            String sourceSha256,
            Instant sourceRetrievedAt,
            boolean sourceVerified,
            EditorialStatus status,
            Instant publishedAt,
            List<AdminPromiseView> promises) {
    }

    public record AdminPromiseView(PartyPromise promise, List<PromiseAssessment> assessments) {
    }

    public record PublicProgrammeView(
            String partyCode,
            int electionYear,
            int termStartYear,
            int termEndYear,
            LocalizedText title,
            LocalizedText summary,
            String sourceUrl,
            String sourceLabel,
            String sourceLanguage,
            String sourceSha256,
            Instant sourceRetrievedAt,
            Instant publishedAt,
            List<PublicPromiseSummary> promises) {
    }

    public record PublicPromiseSummary(
            String slug,
            String topic,
            LocalizedText title,
            String promiseText,
            FeasibilityVerdict verdict,
            LocalizedText assessmentSummary,
            LocalDate dataCutoff) {
    }

    public record PublicPromiseView(
            UUID id,
            String slug,
            String partyCode,
            int electionYear,
            int termStartYear,
            int termEndYear,
            String topic,
            LocalizedText title,
            String promiseText,
            String sourceLocator,
            String mechanism,
            String financing,
            String programmeSourceUrl,
            String programmeSourceLabel,
            PromiseAssessment assessment) {
    }

    public record PublicPromiseHighlight(
            String partyCode,
            String slug,
            String topic,
            LocalizedText title,
            FeasibilityVerdict verdict,
            LocalizedText assessmentSummary,
            LocalDate dataCutoff) {
    }
}
