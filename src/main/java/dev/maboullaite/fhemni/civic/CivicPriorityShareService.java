package dev.maboullaite.fhemni.civic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CivicPriorityShareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CivicPriorityShareService.class);
    public static final int OPEN_GRAPH_WIDTH = 2400;
    public static final int OPEN_GRAPH_HEIGHT = 1260;
    public static final int MAX_UPLOAD_BYTES = 2_000_000;
    private static final long MAX_SOURCE_PIXELS = 8_000_000;
    private static final int MAX_STORED_IMAGE_BYTES = 12_000_000;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final int MAX_CONCURRENT_IMAGE_DECODES = 3;

    private final CivicPriorityShareRepository repository;
    private final CivicPriorityShareImageStorage storage;
    private final Duration retention;
    private final Duration cleanupClaimLease;
    private final Semaphore imageDecoders = new Semaphore(MAX_CONCURRENT_IMAGE_DECODES, true);

    CivicPriorityShareService(
            CivicPriorityShareRepository repository,
            CivicPriorityShareImageStorage storage,
            @Value("${fhemni.civic.shares.retention:P30D}") Duration retention,
            @Value("${fhemni.civic.shares.cleanup-claim-lease:PT5M}") Duration cleanupClaimLease) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("Priority-share retention must be positive");
        }
        if (cleanupClaimLease == null || cleanupClaimLease.isNegative()) {
            throw new IllegalArgumentException("Priority-share cleanup claim lease must not be negative");
        }
        this.repository = repository;
        this.storage = storage;
        this.retention = retention;
        this.cleanupClaimLease = cleanupClaimLease;
    }

    public CivicPriorityShare create(String kindValue, String languageValue, byte[] uploadedImage) {
        CivicPriorityShare.Kind kind = CivicPriorityShare.Kind.parse(kindValue);
        String language = language(languageValue);
        byte[] image = normalizePngWithPermit(uploadedImage);
        String digest = sha256(image);

        var existing = renewExisting(digest, kind, language, image);
        if (existing.isPresent()) return existing.get();

        return insert(kind, language, digest, image);
    }

    @Transactional(readOnly = true)
    public CivicPriorityShare find(String token) {
        requireValidToken(token);
        return repository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Shared priority result not found."));
    }

    @Transactional(readOnly = true)
    public CivicPriorityShare.Metadata findMetadata(String token) {
        requireValidToken(token);
        return repository.findMetadataByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Shared priority result not found."));
    }

    public StoredImage findImage(String token) throws IOException {
        CivicPriorityShare share = find(token);
        try (CivicPriorityShareImageStorage.StoredObject object = storage.open(share.imageObjectKey())) {
            if (object.size() > MAX_STORED_IMAGE_BYTES) {
                throw new IOException("Shared priority image exceeds its storage limit.");
            }
            byte[] image = object.content().readNBytes(MAX_STORED_IMAGE_BYTES + 1);
            if (image.length > MAX_STORED_IMAGE_BYTES) {
                throw new IOException("Shared priority image exceeds its storage limit.");
            }
            return new StoredImage(image, share.kind(), share.imageSha256());
        }
    }

    public void delete(String token) throws IOException {
        CivicPriorityShare.Metadata share = findMetadata(token);
        deleteStoredImage(share);
        repository.deleteByToken(token);
    }

    @Scheduled(fixedDelayString = "${fhemni.civic.shares.cleanup-interval-ms:3600000}")
    public void deleteExpiredShares() {
        Instant cutoff = Instant.now().minus(retention);
        moveExpiredSharesToDeletionQueue(cutoff);
        deletePendingShares();
    }

    private void moveExpiredSharesToDeletionQueue(Instant cutoff) {
        while (true) {
            var expired = repository.findCreatedBefore(cutoff, CLEANUP_BATCH_SIZE);
            if (expired.isEmpty()) return;

            int moved = 0;
            for (CivicPriorityShare.Metadata share : expired) {
                if (repository.moveToDeletionQueueIfExpired(share.token(), cutoff, Instant.now()).isPresent()) {
                    moved++;
                }
            }
            if (expired.size() < CLEANUP_BATCH_SIZE) return;
            if (moved == 0) return;
        }
    }

    private void deletePendingShares() {
        Instant readyAt = Instant.now();
        while (true) {
            var pending = repository.findPendingDeletions(readyAt, CLEANUP_BATCH_SIZE);
            if (pending.isEmpty()) return;

            for (CivicPriorityShareRepository.PendingDeletion deletion : pending) {
                Instant attemptedAt = Instant.now();
                UUID claimToken = UUID.randomUUID();
                if (repository.claimPendingDeletion(
                        deletion,
                        claimToken,
                        attemptedAt,
                        attemptedAt.plus(cleanupClaimLease)) == 0) {
                    continue;
                }
                try {
                    storage.delete(deletion.objectKey());
                    repository.deleteClaimedDeletion(deletion.token(), claimToken);
                } catch (IOException failure) {
                    LOGGER.warn("Could not delete expired civic-priority share {}", deletion.token(), failure);
                }
            }
            if (pending.size() < CLEANUP_BATCH_SIZE) return;
        }
    }

    private byte[] normalizePngWithPermit(byte[] uploadedImage) {
        boolean acquired = false;
        try {
            imageDecoders.acquire();
            acquired = true;
            return normalizePng(uploadedImage);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Share image processing was interrupted.", interrupted);
        } finally {
            if (acquired) imageDecoders.release();
        }
    }

    private CivicPriorityShare insert(
            CivicPriorityShare.Kind kind,
            String language,
            String digest,
            byte[] image) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String token = token();
            String objectKey = objectKey(kind, language, token, digest);
            CivicPriorityShare share = new CivicPriorityShare(
                    UUID.randomUUID(), token, kind, language, objectKey, digest, Instant.now());
            store(objectKey, image);
            try {
                if (repository.insertIfAbsent(share)) {
                    return share;
                }
            } catch (RuntimeException failure) {
                deleteUnusedStoredImage(objectKey);
                throw failure;
            }
            deleteUnusedStoredImage(objectKey);
            var existing = renewExisting(digest, kind, language, image);
            if (existing.isPresent()) return existing.get();
        }
        throw new IllegalStateException("Could not create the share link. Please retry.");
    }

    private Optional<CivicPriorityShare> renewExisting(
            String digest,
            CivicPriorityShare.Kind kind,
            String language,
            byte[] image) {
        var existing = repository.findByImage(digest, kind, language);
        if (existing.isEmpty()) return existing;

        CivicPriorityShare share = existing.get();
        // Rewriting the same object also renews its GCS creation time, keeping
        // bucket lifecycle expiry aligned with the database retention window.
        store(share.imageObjectKey(), image);
        Instant renewedAt = Instant.now();
        if (repository.renew(share.token(), renewedAt) == 0) {
            deleteUnusedStoredImage(share.imageObjectKey());
            return Optional.empty();
        }
        return Optional.of(new CivicPriorityShare(
                share.id(), share.token(), share.kind(), share.language(),
                share.imageObjectKey(), share.imageSha256(), renewedAt));
    }

    private void store(String objectKey, byte[] image) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("fhemni-priority-share-", ".png");
            Files.write(temporary, image);
            storage.put(objectKey, temporary);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not store the share image. Please retry.", failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    LOGGER.warn("Could not delete temporary civic-priority share image", cleanupFailure);
                }
            }
        }
    }

    private void deleteStoredImage(CivicPriorityShare.Metadata share) throws IOException {
        if (share.imageObjectKey() != null) storage.delete(share.imageObjectKey());
    }

    private void deleteUnusedStoredImage(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (IOException cleanupFailure) {
            LOGGER.warn("Could not delete an unused civic-priority share object {}", objectKey, cleanupFailure);
        }
    }

    private static String objectKey(
            CivicPriorityShare.Kind kind,
            String language,
            String token,
            String digest) {
        return "civic-priority-shares/" + language + "/" + kind.name().toLowerCase(Locale.ROOT)
                + "/" + token + "-" + digest + ".png";
    }

    private static void requireValidToken(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}")) {
            throw new NoSuchElementException("Shared priority result not found.");
        }
    }

    private static String language(String value) {
        String language = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!language.equals("ar") && !language.equals("fr") && !language.equals("en")) {
            throw new IllegalArgumentException("The language must be ar, fr, or en.");
        }
        return language;
    }

    private static byte[] normalizePng(byte[] uploadedImage) {
        if (uploadedImage == null || uploadedImage.length == 0) {
            throw new IllegalArgumentException("A PNG share card is required.");
        }
        if (uploadedImage.length > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("The share card must be 2 MB or smaller.");
        }

        try (var bytes = new ByteArrayInputStream(uploadedImage);
             ImageInputStream input = ImageIO.createImageInputStream(bytes)) {
            if (input == null) throw invalidImage();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) {
                    throw new IllegalArgumentException("The share card must be a PNG image.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 600 || height < 300 || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new IllegalArgumentException("The share card has unsupported dimensions.");
                }
                return renderOpenGraphImage(reader.read(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private static byte[] renderOpenGraphImage(BufferedImage source) throws IOException {
        BufferedImage output = new BufferedImage(OPEN_GRAPH_WIDTH, OPEN_GRAPH_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(new Color(0xff, 0xfd, 0xf7));
            graphics.fillRect(0, 0, OPEN_GRAPH_WIDTH, OPEN_GRAPH_HEIGHT);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, OPEN_GRAPH_WIDTH, OPEN_GRAPH_HEIGHT, null);
        } finally {
            graphics.dispose();
        }
        var outputBytes = new ByteArrayOutputStream();
        if (!ImageIO.write(output, "png", outputBytes)) throw new IOException("PNG encoder unavailable");
        return outputBytes.toByteArray();
    }

    private static IllegalArgumentException invalidImage() {
        return new IllegalArgumentException("The share card must be a valid PNG image.");
    }

    private static String sha256(byte[] image) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record StoredImage(byte[] bytes, CivicPriorityShare.Kind kind, String sha256) {
    }
}
