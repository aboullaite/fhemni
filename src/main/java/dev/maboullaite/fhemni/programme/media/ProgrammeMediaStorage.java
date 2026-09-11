package dev.maboullaite.fhemni.programme.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface ProgrammeMediaStorage {

    void put(String objectKey, Path source, String contentType) throws IOException;

    Optional<URI> deliveryUri(String objectKey, Duration validity);

    StoredObject open(String objectKey) throws IOException;

    record StoredObject(InputStream content, long size, String contentType) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }
}
