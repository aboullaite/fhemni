package dev.maboullaite.fhemni.web;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ElectionCoalitionRateLimiter {

    private final Map<String, RequestWindow> clients = new HashMap<>();
    private final ArrayDeque<ClientExpiry> expirations = new ArrayDeque<>();
    private final int maxRequestsPerClient;
    private final int maxRequestsGlobally;
    private final Duration window;
    private final int maxTrackedClients;
    private final Clock clock;
    private RequestWindow global;

    @Autowired
    ElectionCoalitionRateLimiter(
            @Value("${fhemni.elections.coalitions.max-requests-per-window:60}") int maxRequestsPerClient,
            @Value("${fhemni.elections.coalitions.global-max-requests-per-window:600}") int maxRequestsGlobally,
            @Value("${fhemni.elections.coalitions.request-window:PT1M}") Duration window,
            @Value("${fhemni.elections.coalitions.max-tracked-clients:10000}") int maxTrackedClients) {
        this(maxRequestsPerClient, maxRequestsGlobally, window, maxTrackedClients, Clock.systemUTC());
    }

    ElectionCoalitionRateLimiter(int maxRequestsPerClient,
                                 int maxRequestsGlobally,
                                 Duration window,
                                 int maxTrackedClients,
                                 Clock clock) {
        if (maxRequestsPerClient < 1
                || maxRequestsGlobally < 1
                || maxTrackedClients < 1
                || window == null
                || window.isZero()
                || window.isNegative()
                || clock == null) {
            throw new IllegalArgumentException("Election coalition rate limits must be positive");
        }
        this.maxRequestsPerClient = maxRequestsPerClient;
        this.maxRequestsGlobally = maxRequestsGlobally;
        this.window = window;
        this.maxTrackedClients = maxTrackedClients;
        this.clock = clock;
    }

    synchronized void check(String remoteAddress) {
        Instant now = clock.instant();
        purgeExpiredClients(now);

        String client = normalize(remoteAddress);
        RequestWindow current = clients.get(client);
        if (current != null && current.count() >= maxRequestsPerClient) {
            throw limited(now, current);
        }

        global = increment(global, maxRequestsGlobally, now);

        if (current != null) {
            clients.put(client, new RequestWindow(current.startedAt(), current.count() + 1));
        } else if (clients.size() < maxTrackedClients) {
            RequestWindow first = new RequestWindow(now, 1);
            clients.put(client, first);
            expirations.addLast(new ClientExpiry(client, now.plus(window)));
        }
        // When the bounded tracker is full, new clients remain untracked instead of
        // sharing one overflow bucket. The global window still caps total work.
    }

    private RequestWindow increment(RequestWindow current, int limit, Instant now) {
        if (current == null || isExpired(current, now)) {
            return new RequestWindow(now, 1);
        }
        if (current.count() >= limit) {
            throw limited(now, current);
        }
        return new RequestWindow(current.startedAt(), current.count() + 1);
    }

    private void purgeExpiredClients(Instant now) {
        while (!expirations.isEmpty() && !expirations.getFirst().expiresAt().isAfter(now)) {
            ClientExpiry expiry = expirations.removeFirst();
            RequestWindow current = clients.get(expiry.client());
            if (current != null && isExpired(current, now)) {
                clients.remove(expiry.client());
            }
        }
    }

    private boolean isExpired(RequestWindow current, Instant now) {
        return !now.isBefore(current.startedAt().plus(window));
    }

    private ElectionCoalitionRateLimitException limited(Instant now, RequestWindow current) {
        long retryAfter = Math.max(1, Duration.between(now, current.startedAt().plus(window)).toSeconds());
        return new ElectionCoalitionRateLimitException(retryAfter);
    }

    private static String normalize(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return "unknown";
        String value = remoteAddress.strip();
        int zoneIndex = value.indexOf('%');
        if (zoneIndex >= 0) value = value.substring(0, zoneIndex);
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.indexOf(':') >= 0) {
            try {
                InetAddress address = InetAddress.getByName(value);
                if (address instanceof Inet6Address) {
                    return HexFormat.of().formatHex(address.getAddress(), 0, 8) + "/64";
                }
            } catch (UnknownHostException ignored) {
                // Fall back to a bounded opaque key for malformed proxy input.
            }
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private record RequestWindow(Instant startedAt, int count) {
    }

    private record ClientExpiry(String client, Instant expiresAt) {
    }
}
