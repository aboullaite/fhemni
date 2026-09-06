package dev.maboullaite.fhemni.analysis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import jakarta.annotation.PostConstruct;
import dev.maboullaite.fhemni.gemini.GatewayAnalysisResult;
import dev.maboullaite.fhemni.gemini.GatewayAnswerResult;
import dev.maboullaite.fhemni.gemini.GatewayFactCheckResult;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.cost.AiOperation;
import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimKind;
import dev.maboullaite.fhemni.model.ClaimVerdict;
import dev.maboullaite.fhemni.model.FactCheckAssessment;
import dev.maboullaite.fhemni.model.FollowUpAnswer;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.QuestionMode;
import dev.maboullaite.fhemni.model.VideoReport;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.PublicationState;
import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository.StoredRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final Map<UUID, AnalysisSession> sessions = new ConcurrentHashMap<>();
    private final Map<ConversationKey, UserConversationState> conversations = new ConcurrentHashMap<>();
    private final YouTubeUrlParser urlParser;
    private final VideoIntelligenceGateway gateway;
    private final AnalysisEventHub eventHub;
    private final ExecutorService executor;
    private final AiUsageGuard usageGuard;
    private final AnalysisRevisionRepository revisions;
    private final int maxSessions;
    private final Duration sessionRetention;
    private final int maxConversationTurns;
    private final int maxProviderConversationTurns;
    private final int maxUserConversations;
    private final Object capacityMonitor = new Object();

    public AnalysisService(
            YouTubeUrlParser urlParser,
            VideoIntelligenceGateway gateway,
            AnalysisEventHub eventHub,
            @Qualifier("analysisExecutor") ExecutorService analysisExecutor,
            AiUsageGuard usageGuard,
            AnalysisRevisionRepository revisions,
            @Value("${fhemni.sessions.max:250}") int maxSessions,
            @Value("${fhemni.sessions.retention:PT6H}") Duration sessionRetention,
            @Value("${fhemni.sessions.max-conversation-turns:10}") int maxConversationTurns,
            @Value("${fhemni.sessions.max-provider-conversation-turns:5}") int maxProviderConversationTurns,
            @Value("${fhemni.sessions.max-user-conversations:1000}") int maxUserConversations) {
        if (maxSessions < 1 || maxConversationTurns < 1 || maxProviderConversationTurns < 1
                || maxUserConversations < 1
                || sessionRetention.isNegative() || sessionRetention.isZero()) {
            throw new IllegalArgumentException("Session limits and retention must be positive");
        }
        this.urlParser = urlParser;
        this.gateway = gateway;
        this.eventHub = eventHub;
        this.executor = analysisExecutor;
        this.usageGuard = usageGuard;
        this.revisions = revisions;
        this.maxSessions = maxSessions;
        this.sessionRetention = sessionRetention;
        this.maxConversationTurns = maxConversationTurns;
        this.maxProviderConversationTurns = maxProviderConversationTurns;
        this.maxUserConversations = maxUserConversations;
    }

    public AnalysisSnapshot create(String youtubeUrl, String languageCode) {
        return create(youtubeUrl, languageCode, true);
    }

    public AnalysisSnapshot reprocess(String youtubeUrl, String languageCode) {
        return create(youtubeUrl, languageCode, false);
    }

    private AnalysisSnapshot create(String youtubeUrl, String languageCode, boolean reuseStoredCompleted) {
        var parsed = urlParser.parse(youtubeUrl);
        OutputLanguage language = OutputLanguage.fromCode(languageCode == null ? "en" : languageCode);
        if (reuseStoredCompleted) {
            Optional<StoredRevision> reusable = revisions.findReusable(
                    parsed.videoId(), language,
                    gateway.model(), gateway.promptVersion(),
                    gateway.factCheckModel(), gateway.factCheckPromptVersion(),
                    !gateway.live());
            if (reusable.isPresent()) {
                return reusable.get().snapshot();
            }
            if (revisions.hasCompleted(parsed.videoId(), language, !gateway.live())) {
                throw new IllegalStateException(
                        "A completed analysis exists with different model or prompt settings. Use Re-analyze to create a new draft.");
            }
        }
        UUID id = UUID.randomUUID();
        AnalysisSession session = new AnalysisSession(
                id,
                parsed.canonicalUrl(),
                parsed.videoId(),
                language,
                !gateway.live());
        AnalysisSession registered = registerOrReuse(session);
        if (registered != session) {
            return withPublication(registered.snapshot());
        }
        Reservation reservation = null;
        boolean revisionCreated = false;
        try {
            if (gateway.live()) {
                reservation = usageGuard.reserveAnalysis(id, gateway.model());
            }
            revisions.create(
                    session.snapshot(),
                    gateway.model(), gateway.promptVersion(),
                    gateway.factCheckModel(), gateway.factCheckPromptVersion());
            revisionCreated = true;
            publish(session, AnalysisStatus.QUEUED, 2, "Video accepted");
            Reservation admittedReservation = reservation;
            executor.execute(() -> analyze(session, admittedReservation));
        } catch (RejectedExecutionException exception) {
            usageGuard.failed(reservation);
            if (revisionCreated) {
                revisions.fail(id, "The analysis queue is full. Please retry later.");
            }
            removeIf(id, candidate -> candidate == session);
            throw new IllegalStateException("The analysis queue is full. Please retry later.");
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation);
            if (revisionCreated) {
                try {
                    revisions.fail(id, exception.getMessage());
                } catch (RuntimeException persistenceException) {
                    log.error("Could not persist admission failure for analysis {}", id, persistenceException);
                }
            }
            removeIf(id, candidate -> candidate == session);
            throw exception;
        }
        return withPublication(session.snapshot());
    }

    public AnalysisSnapshot get(UUID id) {
        return withPublication(session(id).snapshot());
    }

    public AnalysisSnapshot getPublished(UUID id) {
        return revisions.findPublished(id)
                .map(StoredRevision::snapshot)
                .orElseThrow(() -> new NoSuchElementException("Published analysis not found."));
    }

    public AnalysisSnapshot getPublished(UUID id, UUID userId) {
        AnalysisSnapshot snapshot = getPublished(id);
        if (userId == null) {
            return snapshot;
        }
        UserConversationState conversation = conversations.get(new ConversationKey(id, userId));
        return conversation == null ? snapshot : withConversation(snapshot, conversation.snapshot());
    }

    public AnalysisSnapshot get(UUID id, UUID userId) {
        AnalysisSnapshot snapshot = get(id);
        if (userId == null) {
            return snapshot;
        }
        UserConversationState conversation = conversations.get(new ConversationKey(id, userId));
        if (conversation == null) {
            return snapshot;
        }
        return withConversation(snapshot, conversation.snapshot());
    }

    public SseEmitter subscribe(UUID id) {
        session(id);
        return eventHub.subscribe(id);
    }

    public FollowUpAnswer ask(UUID id, UUID userId, String question, QuestionMode mode) {
        if (userId == null) {
            throw new IllegalArgumentException("A user is required to ask a question.");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Write a question to continue.");
        }
        if (question.length() > 600) {
            throw new IllegalArgumentException("Keep questions under 600 characters.");
        }

        AnalysisSession session = session(id);
        AnalysisSnapshot snapshot = session.snapshot();
        if (snapshot.status() != AnalysisStatus.COMPLETED || snapshot.report() == null) {
            throw new IllegalStateException("Wait for the video analysis to finish before asking questions.");
        }
        UserConversationState conversation = conversation(id, userId, session.analysisInteractionId());
        conversation.lock();
        session.beginQuestion();
        Reservation reservation = null;
        try {
            QuestionMode resolvedMode = mode == null ? QuestionMode.VIDEO : mode;
            if (gateway.live()) {
                AiOperation operation = resolvedMode == QuestionMode.CHECK
                        ? AiOperation.CHAT_CHECK
                        : AiOperation.CHAT_VIDEO;
                reservation = usageGuard.reserveQuestion(id, userId, operation, gateway.model());
            }
            GatewayAnswerResult result = gateway.ask(
                    conversation.interactionId(),
                    question.strip(),
                    resolvedMode,
                    snapshot.language(),
                    snapshot.report());
            usageGuard.succeeded(reservation, result.usage());
            reservation = null;
            FollowUpAnswer answer = new FollowUpAnswer(
                    question.strip(),
                    resolvedMode,
                    result.answer(),
                    result.sources(),
                    Instant.now());
            conversation.add(answer, result.interactionId());
            return answer;
        } catch (GeminiApiException exception) {
            usageGuard.failed(reservation, exception.usage());
            throw exception;
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation);
            throw exception;
        } finally {
            session.endQuestion();
            conversation.unlock();
        }
    }

    public boolean live() {
        return gateway.live();
    }

    public boolean chatEnabled() {
        return gateway.live() && usageGuard.chatEnabled();
    }

    public boolean analysisEnabled() {
        return gateway.live() && usageGuard.analysisEnabled();
    }

    public String model() {
        return gateway.model();
    }

    public PublicationState publication(UUID id) {
        return revisions.publication(id);
    }

    public PublicationState publish(UUID id, UUID administratorId) {
        return revisions.publish(id, administratorId);
    }

    @PostConstruct
    void markInterruptedAnalysesFailed() {
        int interrupted = revisions.failInterrupted();
        if (interrupted > 0) {
            log.warn("Marked {} interrupted analysis revisions as failed after application startup", interrupted);
        }
    }

    @Scheduled(
            initialDelayString = "${fhemni.sessions.cleanup-interval-ms:300000}",
            fixedDelayString = "${fhemni.sessions.cleanup-interval-ms:300000}")
    public void evictExpiredSessions() {
        synchronized (capacityMonitor) {
            evictExpiredLocked(Instant.now().minus(sessionRetention));
        }
    }

    private void analyze(AnalysisSession session, Reservation analysisReservation) {
        Reservation factCheckReservation = null;
        AiUsage analysisUsage = AiUsage.empty();
        AiUsage factCheckUsage = AiUsage.empty();
        try {
            AnalysisSnapshot input = session.snapshot();
            publish(session, AnalysisStatus.ANALYZING, 12, "Gemini is exploring the video timeline");
            GatewayAnalysisResult analysis = gateway.analyze(
                    input.videoUrl(), input.videoId(), input.language());
            analysisUsage = analysis.usage();
            usageGuard.succeeded(analysisReservation, analysisUsage);
            analysisReservation = null;

            publish(session, AnalysisStatus.FACT_CHECKING, 68, "Checking factual claims against external evidence");
            if (gateway.live() && analysis.report().claims().stream()
                    .anyMatch(claim -> claim.kind() == ClaimKind.FACT)) {
                factCheckReservation = usageGuard.reserveFactCheck(input.id(), gateway.factCheckModel());
            }
            GatewayFactCheckResult factChecks = gateway.factCheck(
                    analysis.report().claims(), input.language());
            factCheckUsage = factChecks.usage();
            usageGuard.succeeded(factCheckReservation, factCheckUsage);
            factCheckReservation = null;
            VideoReport report = analysis.report().withClaims(
                    mergeAssessments(analysis.report().claims(), factChecks.assessments()));

            revisions.complete(input.id(), report, analysis.interactionId());
            session.complete(report, analysis.interactionId());
            eventHub.publish(input.id(), new AnalysisEvent(AnalysisStatus.COMPLETED, 100, "Analysis complete"));
        } catch (RuntimeException exception) {
            usageGuard.failed(analysisReservation, analysisUsage);
            usageGuard.failed(factCheckReservation, factCheckUsage);
            log.warn("Analysis failed: {}", exception.getMessage());
            String message = friendlyError(exception);
            session.fail(message);
            try {
                revisions.fail(session.snapshot().id(), message);
            } catch (RuntimeException persistenceException) {
                log.error("Could not persist failure state for analysis {}", session.snapshot().id(), persistenceException);
            }
            AnalysisSnapshot failed = session.snapshot();
            eventHub.publish(failed.id(), new AnalysisEvent(AnalysisStatus.FAILED, failed.progress(), message));
        }
    }

    private List<Claim> mergeAssessments(List<Claim> claims, List<FactCheckAssessment> assessments) {
        Map<String, FactCheckAssessment> byClaim = new HashMap<>();
        assessments.forEach(assessment -> byClaim.put(assessment.claimId(), assessment));
        List<Claim> result = new ArrayList<>();

        for (Claim claim : claims) {
            if (claim.kind() != ClaimKind.FACT) {
                result.add(new Claim(
                        claim.id(), claim.statement(), claim.speaker(), claim.startSeconds(), claim.kind(),
                        ClaimVerdict.NOT_APPLICABLE,
                        "Opinions, proposals, and predictions are shown separately rather than rated true or false.",
                        "", List.of()));
                continue;
            }

            FactCheckAssessment assessment = byClaim.getOrDefault(
                    claim.id(),
                    new FactCheckAssessment(
                            claim.id(), ClaimVerdict.UNVERIFIABLE,
                            "No sufficiently reliable evidence was returned for this claim.",
                            "LOW", List.of()));
            result.add(claim.withAssessment(assessment));
        }
        return result;
    }

    private void publish(AnalysisSession session, AnalysisStatus status, int progress, String message) {
        session.progress(status, progress, message);
        AnalysisSnapshot snapshot = session.snapshot();
        revisions.updateProgress(snapshot.id(), status, progress, message);
        eventHub.publish(snapshot.id(), new AnalysisEvent(status, progress, message));
    }

    private AnalysisSession session(UUID id) {
        AtomicReference<AnalysisSession> result = new AtomicReference<>();
        sessions.computeIfPresent(id, (ignored, session) -> {
            session.touch();
            result.set(session);
            return session;
        });
        if (result.get() != null) {
            return result.get();
        }

        StoredRevision stored = revisions.find(id)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        AnalysisSession restored = AnalysisSession.restore(stored.snapshot(), stored.interactionId());
        synchronized (capacityMonitor) {
            AnalysisSession concurrent = sessions.get(id);
            if (concurrent != null) {
                concurrent.touch();
                return concurrent;
            }
            makeRoomForOneLocked();
            if (sessions.size() >= maxSessions) {
                throw new IllegalStateException("The analysis service is busy. Please retry shortly.");
            }
            sessions.put(id, restored);
            return restored;
        }
    }

    private AnalysisSession registerOrReuse(AnalysisSession session) {
        synchronized (capacityMonitor) {
            evictExpiredLocked(Instant.now().minus(sessionRetention));
            AnalysisSnapshot requested = session.snapshot();
            AnalysisSession existing = sessions.values().stream()
                    .filter(candidate -> {
                        AnalysisSnapshot snapshot = candidate.snapshot();
                        return snapshot.videoId().equals(requested.videoId())
                                && snapshot.language() == requested.language()
                                && snapshot.status() != AnalysisStatus.FAILED
                                && snapshot.status() != AnalysisStatus.COMPLETED;
                    })
                    .max(Comparator.comparing(candidate -> candidate.snapshot().createdAt()))
                    .orElse(null);
            if (existing != null) {
                existing.touch();
                return existing;
            }
            makeRoomForOneLocked();
            if (sessions.size() >= maxSessions) {
                throw new IllegalStateException("The analysis service is busy. Please retry shortly.");
            }
            sessions.put(session.snapshot().id(), session);
            return session;
        }
    }

    private UserConversationState conversation(UUID analysisId, UUID userId, String baseInteractionId) {
        ConversationKey key = new ConversationKey(analysisId, userId);
        synchronized (capacityMonitor) {
            UserConversationState existing = conversations.get(key);
            if (existing != null) {
                return existing;
            }
            makeRoomForConversationLocked();
            if (conversations.size() >= maxUserConversations) {
                throw new IllegalStateException("The conversation service is busy. Please retry shortly.");
            }
            UserConversationState created = new UserConversationState(
                    baseInteractionId,
                    maxConversationTurns,
                    maxProviderConversationTurns);
            conversations.put(key, created);
            return created;
        }
    }

    private void makeRoomForConversationLocked() {
        int removalsNeeded = conversations.size() - maxUserConversations + 1;
        if (removalsNeeded <= 0) {
            return;
        }
        conversations.entrySet().stream()
                .filter(entry -> entry.getValue().evictable())
                .sorted(Comparator.comparing(entry -> entry.getValue().lastAccessedAt()))
                .limit(removalsNeeded)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(conversations::remove);
    }

    private void evictExpiredLocked(Instant cutoff) {
        List<UUID> candidates = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().expiredTerminal(cutoff))
                .map(Map.Entry::getKey)
                .toList();
        candidates.forEach(id -> removeIf(id, session -> session.expiredTerminal(cutoff)));
    }

    private void makeRoomForOneLocked() {
        int removalsNeeded = sessions.size() - maxSessions + 1;
        if (removalsNeeded <= 0) {
            return;
        }

        sessions.entrySet().stream()
                .filter(entry -> entry.getValue().evictable())
                .sorted(Comparator.comparing(entry -> entry.getValue().lastAccessedAt()))
                .limit(removalsNeeded)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(id -> removeIf(id, AnalysisSession::evictable));
    }

    private void removeIf(UUID id, Predicate<AnalysisSession> predicate) {
        AtomicBoolean removed = new AtomicBoolean();
        sessions.computeIfPresent(id, (ignored, session) -> {
            if (predicate.test(session)) {
                removed.set(true);
                return null;
            }
            return session;
        });
        if (removed.get()) {
            conversations.keySet().removeIf(key -> key.analysisId().equals(id));
            eventHub.removeAnalysis(id);
        }
    }

    private AnalysisSnapshot withConversation(
            AnalysisSnapshot snapshot,
            List<FollowUpAnswer> conversation) {
        return new AnalysisSnapshot(
                snapshot.id(),
                snapshot.videoUrl(),
                snapshot.videoId(),
                snapshot.language(),
                snapshot.status(),
                snapshot.progress(),
                snapshot.progressMessage(),
                snapshot.demo(),
                snapshot.createdAt(),
                snapshot.report(),
                snapshot.error(),
                conversation,
                snapshot.published(),
                snapshot.catalogSlug());
    }

    private AnalysisSnapshot withPublication(AnalysisSnapshot snapshot) {
        PublicationState publication = revisions.publication(snapshot.id());
        return snapshot.withPublication(publication.published(), publication.catalogSlug());
    }

    private record ConversationKey(UUID analysisId, UUID userId) {
    }

    private String friendlyError(RuntimeException exception) {
        if (exception instanceof GeminiApiException) {
            return "The AI service could not complete this analysis. Please retry later.";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "The analysis could not be completed. Please try again.";
        }
        return message;
    }
}
