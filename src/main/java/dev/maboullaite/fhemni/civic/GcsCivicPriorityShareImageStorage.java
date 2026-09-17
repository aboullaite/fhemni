package dev.maboullaite.fhemni.civic;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import dev.maboullaite.fhemni.programme.media.GcsProgrammeMediaStorage;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fhemni.civic.shares.storage", havingValue = "gcs")
class GcsCivicPriorityShareImageStorage implements CivicPriorityShareImageStorage {

    private static final String STORAGE_SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";

    private final GcsProgrammeMediaStorage delegate;

    GcsCivicPriorityShareImageStorage(
            @Value("${fhemni.civic.shares.gcs.project-id}") String projectId,
            @Value("${fhemni.civic.shares.gcs.bucket}") String bucket,
            @Value("${fhemni.civic.shares.gcs.credentials}") String credentialsPath,
            @Value("${fhemni.civic.shares.gcs.request-timeout:PT2M}") Duration timeout) throws IOException {
        try (InputStream input = Files.newInputStream(Path.of(credentialsPath))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(input).createScoped(List.of(STORAGE_SCOPE));
            delegate = new GcsProgrammeMediaStorage(
                    projectId, bucket, timeout, credentials,
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
        }
    }

    @Override
    public void put(String objectKey, Path source) throws IOException {
        delegate.put(objectKey, source, "image/png");
    }

    @Override
    public StoredObject open(String objectKey) throws IOException {
        ProgrammeMediaStorage.StoredObject object = delegate.open(objectKey);
        return new StoredObject(object.content(), object.size());
    }

    @Override
    public boolean delete(String objectKey) throws IOException {
        return delegate.delete(objectKey);
    }
}
