package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;

import com.google.auth.oauth2.ServiceAccountCredentials;
import org.junit.jupiter.api.Test;

class GcsProgrammeMediaStorageTest {

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
        String signature = url.substring(url.indexOf("X-Goog-Signature=") + "X-Goog-Signature=".length());
        assertThat(signature).matches("[0-9a-f]{512}");
    }
}
