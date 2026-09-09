package dev.maboullaite.fhemni.programme;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import dev.maboullaite.fhemni.cost.AiBudgetExceededException;
import dev.maboullaite.fhemni.cost.AiOperation;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.ProgrammeChatGateway;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.SourceReference;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.ProgrammeChatDossier;
import dev.maboullaite.fhemni.programme.ProgrammeChatContextBuilder.ConversationTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProgrammeChatService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeChatService.class);

    private final PartyProgrammeService programmes;
    private final ProgrammeChatContextBuilder contexts;
    private final ProgrammeChatGateway gateway;
    private final AiUsageGuard usageGuard;
    private final int maxTurns;
    private final int maxConversations;
    private final Duration retention;
    private final Map<ConversationKey, Conversation> conversations = new ConcurrentHashMap<>();
    private final Object capacityMonitor = new Object();

    public ProgrammeChatService(
            PartyProgrammeService programmes,
            ProgrammeChatContextBuilder contexts,
            ProgrammeChatGateway gateway,
            AiUsageGuard usageGuard,
            @Value("${fhemni.programme-chat.max-conversation-turns:8}") int maxTurns,
            @Value("${fhemni.programme-chat.max-user-conversations:1000}") int maxConversations,
            @Value("${fhemni.programme-chat.retention:PT6H}") Duration retention) {
        if (maxTurns < 1 || maxConversations < 1 || retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Programme chat conversation limits must be positive");
        }
        this.programmes = programmes;
        this.contexts = contexts;
        this.gateway = gateway;
        this.usageGuard = usageGuard;
        this.maxTurns = maxTurns;
        this.maxConversations = maxConversations;
        this.retention = retention;
    }

    public ProgrammeChatAnswer ask(String partyCode, UUID userId, String question, String languageCode) {
        if (userId == null) {
            throw new IllegalArgumentException("A user is required to ask about a programme.");
        }
        String cleanQuestion = question == null ? "" : question.strip();
        if (cleanQuestion.isEmpty()) {
            throw new IllegalArgumentException("Write a question to continue.");
        }
        if (cleanQuestion.length() > 600) {
            throw new IllegalArgumentException("Keep questions under 600 characters.");
        }
        if (!enabled()) {
            throw new AiBudgetExceededException(
                    "CHAT_DISABLED", "Programme chat is not available right now.");
        }

        OutputLanguage language = OutputLanguage.fromCode(
                languageCode == null || languageCode.isBlank() ? "ar" : languageCode);
        ProgrammeChatDossier dossier = programmes.publishedChatDossier(partyCode);
        Conversation conversation = conversation(dossier.programme().id(), userId);
        conversation.lock();
        Reservation reservation = null;
        try {
            ProgrammeChatContext context = contexts.build(
                    dossier, cleanQuestion, language, conversation.snapshot());
            reservation = usageGuard.reserveQuestion(
                    null, userId, AiOperation.CHAT_PROGRAMME, gateway.model());
            ProgrammeChatGateway.Result generated = gateway.answer(
                    cleanQuestion, language, context.material());
            List<SourceReference> sources = validatedSources(generated, context);
            usageGuard.succeeded(reservation, generated.usage());
            reservation = null;
            ProgrammeChatAnswer answer = new ProgrammeChatAnswer(
                    cleanQuestion, generated.basis(), generated.answer(), sources, Instant.now());
            conversation.add(new ConversationTurn(cleanQuestion, generated.answer()));
            return answer;
        } catch (GeminiApiException exception) {
            log.warn(
                    "Programme chat response rejected for party {}: {} (upstreamStatus={}, inputTokens={}, outputTokens={})",
                    partyCode,
                    exception.getMessage(),
                    exception.upstreamStatus(),
                    exception.usage().inputTokens(),
                    exception.usage().outputTokens());
            usageGuard.failed(reservation, exception.usage());
            throw exception;
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation);
            throw exception;
        } finally {
            conversation.unlock();
        }
    }

    public boolean enabled() {
        return gateway.live() && usageGuard.chatEnabled();
    }

    @Scheduled(
            initialDelayString = "${fhemni.programme-chat.cleanup-interval-ms:300000}",
            fixedDelayString = "${fhemni.programme-chat.cleanup-interval-ms:300000}")
    public void evictExpiredConversations() {
        Instant cutoff = Instant.now().minus(retention);
        synchronized (capacityMonitor) {
            conversations.entrySet().removeIf(entry -> entry.getValue().evictableBefore(cutoff));
        }
    }

    private List<SourceReference> validatedSources(
            ProgrammeChatGateway.Result generated,
            ProgrammeChatContext context) {
        LinkedHashSet<String> citedIds = new LinkedHashSet<>(generated.citationIds());
        if (generated.basis() == ProgrammeChatBasis.NOT_FOUND && citedIds.isEmpty()) {
            citedIds.add("PROGRAMME");
        }
        LinkedHashSet<String> recognizedIds = citedIds.stream()
                .filter(context.sources()::containsKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SourceReference> sources = recognizedIds.stream()
                .map(context.sources()::get)
                .distinct()
                .toList();
        if (sources.isEmpty()) {
            throw new GeminiApiException(
                    "Programme chat returned no recognized source", generated.usage());
        }
        boolean programmeCitation = recognizedIds.stream()
                .anyMatch(id -> id.equals("PROGRAMME")
                        || id.startsWith("PROMISE_") && !id.contains("_E"));
        boolean assessmentCitation = recognizedIds.stream().anyMatch(id -> id.startsWith("ASSESSMENT_"));
        boolean evidenceCitation = recognizedIds.stream().anyMatch(id -> id.contains("_E"));
        boolean feasibilityCitation = assessmentCitation || evidenceCitation;
        boolean validBasis = switch (generated.basis()) {
            case PROGRAMME, NOT_FOUND -> programmeCitation;
            case FEASIBILITY -> feasibilityCitation;
            case BOTH -> programmeCitation && feasibilityCitation;
        };
        if (!validBasis) {
            log.warn(
                    "Programme chat citation mismatch: basis={}, recognizedCitationIds={}",
                    generated.basis(),
                    recognizedIds);
            throw new GeminiApiException(
                    "Programme chat evidence did not match its answer basis", generated.usage());
        }
        return sources;
    }

    private Conversation conversation(UUID programmeId, UUID userId) {
        ConversationKey key = new ConversationKey(programmeId, userId);
        synchronized (capacityMonitor) {
            Conversation existing = conversations.get(key);
            if (existing != null) return existing;
            makeRoomLocked();
            if (conversations.size() >= maxConversations) {
                throw new IllegalStateException("The conversation service is busy. Please retry shortly.");
            }
            Conversation created = new Conversation(maxTurns);
            conversations.put(key, created);
            return created;
        }
    }

    private void makeRoomLocked() {
        int removals = conversations.size() - maxConversations + 1;
        if (removals <= 0) return;
        conversations.entrySet().stream()
                .filter(entry -> entry.getValue().evictable())
                .sorted(Comparator.comparing(entry -> entry.getValue().lastAccessedAt()))
                .limit(removals)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(conversations::remove);
    }

    private record ConversationKey(UUID programmeId, UUID userId) {
    }

    private static final class Conversation {
        private final ReentrantLock lock = new ReentrantLock();
        private final List<ConversationTurn> turns = new ArrayList<>();
        private final int maxTurns;
        private Instant lastAccessedAt = Instant.now();

        private Conversation(int maxTurns) {
            this.maxTurns = maxTurns;
        }

        private void lock() {
            lock.lock();
            touch();
        }

        private void unlock() {
            touch();
            lock.unlock();
        }

        private synchronized List<ConversationTurn> snapshot() {
            touch();
            return List.copyOf(turns);
        }

        private synchronized void add(ConversationTurn turn) {
            if (turns.size() == maxTurns) turns.removeFirst();
            turns.add(turn);
            touch();
        }

        private synchronized Instant lastAccessedAt() {
            return lastAccessedAt;
        }

        private boolean evictable() {
            return !lock.isLocked();
        }

        private synchronized boolean evictableBefore(Instant cutoff) {
            return !lock.isLocked() && lastAccessedAt.isBefore(cutoff);
        }

        private synchronized void touch() {
            lastAccessedAt = Instant.now();
        }
    }
}
