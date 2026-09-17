package dev.maboullaite.fhemni.civic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fhemni.civic.shares.storage", havingValue = "local", matchIfMissing = true)
class LocalCivicPriorityShareImageStorage implements CivicPriorityShareImageStorage {

    private final Path root;

    LocalCivicPriorityShareImageStorage(
            @Value("${fhemni.civic.shares.local-directory:./data/civic-priority-shares}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, Path source) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public StoredObject open(String objectKey) throws IOException {
        Path object = resolve(objectKey);
        return new StoredObject(Files.newInputStream(object), Files.size(object));
    }

    @Override
    public boolean delete(String objectKey) throws IOException {
        return Files.deleteIfExists(resolve(objectKey));
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")) {
            throw new IllegalArgumentException("Civic-priority share object key is invalid.");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Civic-priority share object key escapes its storage root.");
        }
        return target;
    }
}
