package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SuggestionRateLimiter {

    private final Map<String, SubmissionWindow> clients = new HashMap<>();
    private final int maxSubmissions;
    private final Duration window;
    private final int maxTrackedClients;

    public SuggestionRateLimiter(
            @Value("${fhemni.suggestions.max-submissions-per-window:10}") int maxSubmissions,
            @Value("${fhemni.suggestions.window:PT1H}") Duration window,
            @Value("${fhemni.suggestions.max-tracked-clients:10000}") int maxTrackedClients) {
        if (maxSubmissions < 1 || maxTrackedClients < 1 || window == null
                || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Suggestion rate limits must be positive");
        }
        this.maxSubmissions = maxSubmissions;
        this.window = window;
        this.maxTrackedClients = maxTrackedClients;
    }

    public synchronized void check(String remoteAddress) {
        Instant now = Instant.now();
        String client = normalize(remoteAddress);
        SubmissionWindow current = clients.get(client);
        if (current == null || !now.isBefore(current.startedAt().plus(window))) {
            makeRoom(now);
            if (!clients.containsKey(client) && clients.size() >= maxTrackedClients) {
                throw new SuggestionRateLimitException(window.toSeconds());
            }
            clients.put(client, new SubmissionWindow(now, 1));
            return;
        }
        if (current.count() >= maxSubmissions) {
            long retryAfter = Math.max(1, Duration.between(now, current.startedAt().plus(window)).toSeconds());
            throw new SuggestionRateLimitException(retryAfter);
        }
        clients.put(client, new SubmissionWindow(current.startedAt(), current.count() + 1));
    }

    private void makeRoom(Instant now) {
        if (clients.size() < maxTrackedClients) {
            return;
        }
        clients.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(window)));
    }

    private String normalize(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        String value = remoteAddress.strip();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private record SubmissionWindow(Instant startedAt, int count) {
    }
}
