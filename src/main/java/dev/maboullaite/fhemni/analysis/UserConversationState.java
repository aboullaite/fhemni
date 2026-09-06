package dev.maboullaite.fhemni.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import dev.maboullaite.fhemni.model.FollowUpAnswer;

final class UserConversationState {

    private final List<FollowUpAnswer> answers = new ArrayList<>();
    private final String baseInteractionId;
    private final int maxTurns;
    private final int maxProviderTurns;
    private final ReentrantLock lock = new ReentrantLock();

    private String interactionId;
    private int providerTurns;
    private Instant lastAccessedAt = Instant.now();

    UserConversationState(String baseInteractionId, int maxTurns, int maxProviderTurns) {
        this.baseInteractionId = baseInteractionId;
        this.interactionId = baseInteractionId;
        this.maxTurns = maxTurns;
        this.maxProviderTurns = maxProviderTurns;
    }

    void lock() {
        lock.lock();
        touch();
    }

    void unlock() {
        touch();
        lock.unlock();
    }

    synchronized String interactionId() {
        return interactionId;
    }

    synchronized void add(FollowUpAnswer answer, String nextInteractionId) {
        if (answers.size() == maxTurns) {
            answers.removeFirst();
        }
        answers.add(answer);
        providerTurns++;
        if (providerTurns >= maxProviderTurns) {
            interactionId = baseInteractionId;
            providerTurns = 0;
        } else {
            interactionId = nextInteractionId;
        }
        touch();
    }

    synchronized List<FollowUpAnswer> snapshot() {
        touch();
        return List.copyOf(answers);
    }

    synchronized Instant lastAccessedAt() {
        return lastAccessedAt;
    }

    boolean evictable() {
        return !lock.isLocked();
    }

    private synchronized void touch() {
        lastAccessedAt = Instant.now();
    }
}
