package dev.maboullaite.fhemni.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MagicLinkRateLimiter {

    private final Map<String, RequestWindow> clients = new HashMap<>();
    private final int maxRequests;
    private final Duration window;
    private final int maxTrackedClients;

    public MagicLinkRateLimiter(
            @Value("${fhemni.auth.magic-link.max-requests-per-window:10}") int maxRequests,
            @Value("${fhemni.auth.magic-link.request-window:PT1H}") Duration window,
            @Value("${fhemni.auth.magic-link.max-tracked-clients:10000}") int maxTrackedClients) {
        if (maxRequests < 1 || maxTrackedClients < 1 || window == null
                || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Magic-link rate limits must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.maxTrackedClients = maxTrackedClients;
    }

    public synchronized boolean tryAcquire(String remoteAddress) {
        Instant now = Instant.now();
        String client = normalize(remoteAddress);
        RequestWindow current = clients.get(client);
        if (current == null || !now.isBefore(current.startedAt().plus(window))) {
            discardExpired(now);
            if (!clients.containsKey(client) && clients.size() >= maxTrackedClients) {
                return false;
            }
            clients.put(client, new RequestWindow(now, 1));
            return true;
        }
        if (current.count() >= maxRequests) {
            return false;
        }
        clients.put(client, new RequestWindow(current.startedAt(), current.count() + 1));
        return true;
    }

    private void discardExpired(Instant now) {
        if (clients.size() < maxTrackedClients) {
            return;
        }
        clients.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(window)));
    }

    private static String normalize(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        String value = remoteAddress.strip();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private record RequestWindow(Instant startedAt, int count) {
    }
}
