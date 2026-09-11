package dev.maboullaite.fhemni.programme.media;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fhemni.programme-media.storage", havingValue = "local", matchIfMissing = true)
public class LocalProgrammeMediaStorage implements ProgrammeMediaStorage {

    private final Path root;

    public LocalProgrammeMediaStorage(
            @Value("${fhemni.programme-media.local-directory:./data/programme-media}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, Path source, String contentType) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Optional<URI> deliveryUri(String objectKey, Duration validity) {
        return Optional.empty();
    }

    @Override
    public StoredObject open(String objectKey) throws IOException {
        Path object = resolve(objectKey);
        return new StoredObject(Files.newInputStream(object), Files.size(object), Files.probeContentType(object));
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")) {
            throw new IllegalArgumentException("Programme media object key is invalid.");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Programme media object key escapes its storage root.");
        }
        return target;
    }
}
