package dev.maboullaite.fhemni.civic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CivicPriorityShareService {

    public static final int OPEN_GRAPH_WIDTH = 2400;
    public static final int OPEN_GRAPH_HEIGHT = 1260;
    static final int MAX_UPLOAD_BYTES = 2_000_000;
    private static final long MAX_SOURCE_PIXELS = 8_000_000;

    private final CivicPriorityShareRepository repository;

    CivicPriorityShareService(CivicPriorityShareRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CivicPriorityShare create(String kindValue, String languageValue, byte[] uploadedImage) {
        CivicPriorityShare.Kind kind = CivicPriorityShare.Kind.parse(kindValue);
        String language = language(languageValue);
        byte[] image = normalizePng(uploadedImage);
        String digest = sha256(image);

        return repository.findByImage(digest, kind, language)
                .orElseGet(() -> insert(kind, language, image, digest));
    }

    @Transactional(readOnly = true)
    public CivicPriorityShare find(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}")) {
            throw new NoSuchElementException("Shared priority result not found.");
        }
        return repository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Shared priority result not found."));
    }

    private CivicPriorityShare insert(
            CivicPriorityShare.Kind kind,
            String language,
            byte[] image,
            String digest) {
        for (int attempt = 0; attempt < 3; attempt++) {
            CivicPriorityShare share = new CivicPriorityShare(
                    UUID.randomUUID(), token(), kind, language, image, digest, Instant.now());
            try {
                repository.insert(share);
                return share;
            } catch (DuplicateKeyException duplicate) {
                var existing = repository.findByImage(digest, kind, language);
                if (existing.isPresent()) return existing.get();
            }
        }
        throw new IllegalStateException("Could not create the share link. Please retry.");
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
}
