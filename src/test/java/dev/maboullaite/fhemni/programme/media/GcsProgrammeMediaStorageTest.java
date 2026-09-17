package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GcsProgrammeMediaStorageTest {

    @Test
    void identifiesTheRuntimeConstructorForSpringInjection() {
        long injectableConstructors = Arrays.stream(GcsProgrammeMediaStorage.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertThat(injectableConstructors).isEqualTo(1);
    }

    @Test
    void signsPrivateObjectLinksWithoutExposingAReusableCredential() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2_048);
        var credentials = ServiceAccountCredentials.newBuilder()
                .setClientEmail("media@example-project.iam.gserviceaccount.com")
                .setPrivateKey(generator.generateKeyPair().getPrivate())
                .setPrivateKeyId("test-key")
                .setProjectId("example-project")
                .setScopes(List.of("https://www.googleapis.com/auth/devstorage.read_write"))
                .build();
        var storage = new GcsProgrammeMediaStorage(
                "example-project", "private-media", Duration.ofMinutes(2), credentials, HttpClient.newHttpClient());

        String url = storage.deliveryUri("programmes/RNI briefing.mp4", Duration.ofHours(1))
                .orElseThrow()
                .toASCIIString();

        assertThat(url)
                .startsWith("https://storage.googleapis.com/private-media/programmes/RNI%20briefing.mp4?")
                .contains("X-Goog-Algorithm=GOOG4-RSA-SHA256")
                .contains("X-Goog-Credential=media%40example-project.iam.gserviceaccount.com%2F")
                .contains("X-Goog-Expires=3600")
                .contains("X-Goog-SignedHeaders=host")
                .doesNotContain("PRIVATE KEY", "test-key");
        assertThat(url).containsPattern("X-Goog-Credential=[^&]+%2F\\d{8}%2Fauto%2Fstorage%2Fgoog4_request");
        assertThat(url).containsPattern("X-Goog-Date=\\d{8}T\\d{6}Z");
        assertThat(url).doesNotContainPattern("X-Goog-Credential=[^&]+%2F\\d{8}Z%2F");
        String signature = url.substring(url.indexOf("X-Goog-Signature=") + "X-Goog-Signature=".length());
        assertThat(signature).matches("[0-9a-f]{512}");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deletesAnObjectThroughTheAuthenticatedJsonApi() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken("test-token", new Date(System.currentTimeMillis() + 3_600_000)));
        HttpClient http = mock(HttpClient.class);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var storage = new GcsProgrammeMediaStorage(
                "example-project", "private-media", Duration.ofMinutes(2), credentials, http);

        assertThat(storage.delete("civic-priority-shares/ar/compass/card.png")).isTrue();

        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("DELETE");
        assertThat(request.getValue().uri().toASCIIString())
                .isEqualTo("https://storage.googleapis.com/storage/v1/b/private-media/o/"
                        + "civic-priority-shares%2Far%2Fcompass%2Fcard.png");
    }

    @Test
    void timesOutAStalledResponseBody() throws Exception {
        InputStream slow = new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    Thread.sleep(100);
                    return 1;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
        };

        try (InputStream body = GcsProgrammeMediaStorage.withBodyTimeout(slow, Duration.ofMillis(10))) {
            assertThatThrownBy(body::read).isInstanceOf(SocketTimeoutException.class);
        }
    }
}
