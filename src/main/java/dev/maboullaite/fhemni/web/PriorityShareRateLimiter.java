package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PriorityShareRateLimiter {

    private final Map<String, RequestWindow> clients = new HashMap<>();
    private final int maxRequests;
    private final Duration window;
    private final int maxTrackedClients;

    PriorityShareRateLimiter(
            @Value("${fhemni.civic.shares.max-requests-per-window:20}") int maxRequests,
            @Value("${fhemni.civic.shares.request-window:PT1H}") Duration window,
            @Value("${fhemni.civic.shares.max-tracked-clients:10000}") int maxTrackedClients) {
        if (maxRequests < 1 || maxTrackedClients < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Priority-share rate limits must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.maxTrackedClients = maxTrackedClients;
    }

    synchronized void check(String remoteAddress) {
        Instant now = Instant.now();
        String client = normalize(remoteAddress);
        RequestWindow current = clients.get(client);
        if (current == null || !now.isBefore(current.startedAt().plus(window))) {
            makeRoom(now);
            if (!clients.containsKey(client) && clients.size() >= maxTrackedClients) {
                throw new PriorityShareRateLimitException(window.toSeconds());
            }
            clients.put(client, new RequestWindow(now, 1));
            return;
        }
        if (current.count() >= maxRequests) {
            long retryAfter = Math.max(1, Duration.between(now, current.startedAt().plus(window)).toSeconds());
            throw new PriorityShareRateLimitException(retryAfter);
        }
        clients.put(client, new RequestWindow(current.startedAt(), current.count() + 1));
    }

    private void makeRoom(Instant now) {
        if (clients.size() < maxTrackedClients) return;
        clients.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(window)));
    }

    private static String normalize(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return "unknown";
        String value = remoteAddress.strip();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private record RequestWindow(Instant startedAt, int count) {
    }
}

