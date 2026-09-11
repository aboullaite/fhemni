package dev.maboullaite.fhemni.programme.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fhemni.programme-media.storage", havingValue = "gcs")
public class GcsProgrammeMediaStorage implements ProgrammeMediaStorage {

    private static final String STORAGE_SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";

    private final HttpClient http;
    private final GoogleCredentials credentials;
    private final String bucket;
    private final Duration timeout;

    @Autowired
    public GcsProgrammeMediaStorage(
            @Value("${fhemni.programme-media.gcs.project-id}") String projectId,
            @Value("${fhemni.programme-media.gcs.bucket}") String bucket,
            @Value("${fhemni.programme-media.gcs.request-timeout:PT10M}") Duration timeout) throws IOException {
        this(projectId, bucket, timeout,
                GoogleCredentials.getApplicationDefault().createScoped(List.of(STORAGE_SCOPE)),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    GcsProgrammeMediaStorage(
            String projectId,
            String bucket,
            Duration timeout,
            GoogleCredentials credentials,
            HttpClient http) {
        if (projectId == null || projectId.isBlank() || bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("GCS project and bucket must be configured for programme media.");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("GCS programme media request timeout must be positive.");
        }
        this.bucket = bucket.strip();
        this.timeout = timeout;
        this.credentials = Objects.requireNonNull(credentials, "GCS credentials must not be null.");
        this.http = Objects.requireNonNull(http, "GCS HTTP client must not be null.");
    }

    @Override
    public void put(String objectKey, Path source, String contentType) throws IOException {
        URI uri = URI.create("https://storage.googleapis.com/upload/storage/v1/b/"
                + encode(bucket) + "/o?uploadType=media&name=" + encode(requiredObjectKey(objectKey)));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", bearer())
                .header("Content-Type", contentType == null ? "application/octet-stream" : contentType)
                .POST(HttpRequest.BodyPublishers.ofFile(source))
                .build();
        HttpResponse<InputStream> response = send(request);
        try (InputStream body = response.body()) {
            requireSuccess(response.statusCode(), body, "upload");
        }
    }

    @Override
    public Optional<URI> deliveryUri(String objectKey, Duration validity) {
        if (validity == null || validity.isNegative() || validity.isZero()
                || validity.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("A programme media delivery URL must be valid for at most seven days.");
        }
        if (!(credentials instanceof ServiceAccountCredentials serviceAccount)) {
            return Optional.empty();
        }
        try {
            Instant now = Instant.now();
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC).format(now);
            String date = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(now);
            String scope = date + "/auto/storage/goog4_request";
            String canonicalPath = "/" + encodePath(bucket) + "/" + encodePath(requiredObjectKey(objectKey));
            String query = "X-Goog-Algorithm=GOOG4-RSA-SHA256"
                    + "&X-Goog-Credential=" + encode(serviceAccount.getClientEmail() + "/" + scope)
                    + "&X-Goog-Date=" + timestamp
                    + "&X-Goog-Expires=" + validity.toSeconds()
                    + "&X-Goog-SignedHeaders=host";
            String canonicalRequest = "GET\n" + canonicalPath + "\n" + query
                    + "\nhost:storage.googleapis.com\n\nhost\nUNSIGNED-PAYLOAD";
            String stringToSign = "GOOG4-RSA-SHA256\n" + timestamp + "\n" + scope + "\n"
                    + sha256(canonicalRequest);
            String signature = HexFormat.of().formatHex(
                    serviceAccount.sign(stringToSign.getBytes(StandardCharsets.UTF_8)));
            return Optional.of(URI.create("https://storage.googleapis.com" + canonicalPath + "?" + query
                    + "&X-Goog-Signature=" + signature));
        } catch (RuntimeException signingFailure) {
            // User ADC cannot sign locally; the authenticated streaming fallback remains available.
            return Optional.empty();
        }
    }

    @Override
    public StoredObject open(String objectKey) throws IOException {
        URI uri = URI.create("https://storage.googleapis.com/download/storage/v1/b/"
                + encode(bucket) + "/o/" + encode(requiredObjectKey(objectKey)) + "?alt=media");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", bearer())
                .GET()
                .build();
        HttpResponse<InputStream> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream body = response.body()) {
                requireSuccess(response.statusCode(), body, "download");
            }
        }
        long size = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
        return new StoredObject(response.body(), size, contentType);
    }

    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("GCS programme media request was interrupted.", exception);
        }
    }

    private synchronized String bearer() throws IOException {
        credentials.refreshIfExpired();
        if (credentials.getAccessToken() == null) {
            credentials.refresh();
        }
        return "Bearer " + credentials.getAccessToken().getTokenValue();
    }

    private void requireSuccess(int status, InputStream body, String operation) throws IOException {
        if (status >= 200 && status < 300) {
            return;
        }
        String detail = new String(body.readNBytes(1_024), StandardCharsets.UTF_8).strip();
        throw new IOException("GCS programme media " + operation + " failed with HTTP " + status
                + (detail.isEmpty() ? "." : ": " + detail));
    }

    private String requiredObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("Programme media object key is invalid.");
        }
        return objectKey;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    private String encodePath(String value) {
        return java.util.Arrays.stream(value.split("/", -1))
                .map(this::encode)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}
