package dev.maboullaite.fhemni.programme;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Compares evidence links with provider citations without trusting marketing parameters. */
public final class EvidenceCitationMatcher {

    private static final Set<String> TRACKING_QUERY_PARAMETERS = Set.of(
            "dclid", "fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "msclkid");

    private EvidenceCitationMatcher() {
    }

    public static boolean sameDocument(String left, String right) {
        try {
            URI leftUri = URI.create(left);
            URI rightUri = URI.create(right);
            return leftUri.getHost() != null
                    && rightUri.getHost() != null
                    && leftUri.getHost().equalsIgnoreCase(rightUri.getHost())
                    && normalizedPath(leftUri).equals(normalizedPath(rightUri))
                    && normalizedQuery(leftUri).equals(normalizedQuery(rightUri));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizedPath(URI uri) {
        String path = uri.normalize().getPath();
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static List<QueryParameter> normalizedQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        List<QueryParameter> parameters = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String name = decode(separator < 0 ? pair : pair.substring(0, separator));
            if (isTrackingParameter(name)) {
                continue;
            }
            String value = decode(separator < 0 ? "" : pair.substring(separator + 1));
            parameters.add(new QueryParameter(name, value));
        }
        parameters.sort(Comparator.comparing(QueryParameter::name).thenComparing(QueryParameter::value));
        return List.copyOf(parameters);
    }

    private static boolean isTrackingParameter(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.startsWith("utm_") || TRACKING_QUERY_PARAMETERS.contains(normalized);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record QueryParameter(String name, String value) {
    }
}
