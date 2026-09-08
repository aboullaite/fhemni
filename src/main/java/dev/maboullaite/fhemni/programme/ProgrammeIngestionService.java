package dev.maboullaite.fhemni.programme;

import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.maboullaite.fhemni.cost.AiOperation;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.Reservation;
import dev.maboullaite.fhemni.gemini.GeminiApiException;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway;
import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractionResult;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProgrammeIngestionService {

    static final long MAX_PDF_BYTES = 25L * 1024 * 1024;
    private static final int INGESTION_LOCK_STRIPES = 64;
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid");

    private final ProgrammeIntelligenceGateway gateway;
    private final PartyProgrammeService programmes;
    private final AiUsageGuard usageGuard;
    private final Object[] ingestionLocks = java.util.stream.IntStream.range(0, INGESTION_LOCK_STRIPES)
            .mapToObj(ignored -> new Object())
            .toArray();

    public ProgrammeIngestionService(
            ProgrammeIntelligenceGateway gateway,
            PartyProgrammeService programmes,
            AiUsageGuard usageGuard) {
        this.gateway = gateway;
        this.programmes = programmes;
        this.usageGuard = usageGuard;
    }

    public IngestionResult ingest(String rawSourceUrl) {
        String sourceUrl = publicHttpsUrl(rawSourceUrl);
        return ingest(sourceUrl, () -> extract(sourceUrl), false);
    }

    public IngestionResult ingestPdf(String rawSourceUrl, MultipartFile document) {
        return ingestPdf(rawSourceUrl, document, false);
    }

    public IngestionResult ingestPdf(
            String rawSourceUrl,
            MultipartFile document,
            boolean replaceExistingDraft) {
        String sourceUrl = publicHttpsUrl(rawSourceUrl);
        PdfUpload pdf = validPdf(document);
        return ingest(sourceUrl, () -> extractPdf(sourceUrl, pdf), replaceExistingDraft);
    }

    private IngestionResult ingest(
            String sourceUrl,
            ExtractionOperation extractionOperation,
            boolean replaceExistingDraft) {
        if (!gateway.live()) {
            throw new IllegalStateException("Gemini must be configured before importing a party programme.");
        }
        // Same-source clicks share a bounded lock; unrelated parties can be imported independently.
        synchronized (ingestionLock(sourceUrl)) {
            AdminProgrammeView programme = programmes.programmeBySourceUrl(sourceUrl).orElse(null);
            boolean extracted = false;
            List<String> warnings = programme == null ? List.of() : programme.extractionWarnings();

            if (replaceExistingDraft && programme != null && programme.status() != EditorialStatus.DRAFT) {
                throw new IllegalStateException("A published programme cannot be replaced by an uploaded PDF.");
            }
            if (programme == null || replaceExistingDraft) {
                ExtractionResult extraction = extractionOperation.extract();
                warnings = extraction.programme().warnings() == null
                        ? List.of()
                        : extraction.programme().warnings().stream().filter(value -> value != null && !value.isBlank()).toList();
                programme = replaceExistingDraft && programme != null
                        ? programmes.replaceGeneratedExtraction(
                                programme.id(), sourceUrl, extraction.programme(), warnings)
                        : programmes.saveGeneratedExtraction(sourceUrl, extraction.programme(), warnings);
                extracted = true;
            }

            return new IngestionResult(programme, !extracted, extracted, warnings);
        }
    }

    private Object ingestionLock(String sourceUrl) {
        return ingestionLocks[Math.floorMod(sourceUrl.hashCode(), ingestionLocks.length)];
    }

    private ExtractionResult extract(String sourceUrl) {
        Reservation reservation = usageGuard.reserveEditorial(AiOperation.PROGRAMME_EXTRACTION, gateway.model());
        try {
            ExtractionResult result = gateway.extract(sourceUrl);
            usageGuard.succeeded(reservation, result.usage());
            return result;
        } catch (GeminiApiException exception) {
            usageGuard.failed(reservation, exception.usage());
            throw exception;
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation);
            throw exception;
        }
    }

    private ExtractionResult extractPdf(String sourceUrl, PdfUpload pdf) {
        Reservation reservation = usageGuard.reserveEditorial(AiOperation.PROGRAMME_EXTRACTION, gateway.model());
        try (InputStream input = pdf.document().getInputStream()) {
            ExtractionResult result = gateway.extractPdf(sourceUrl, pdf.displayName(), input, pdf.document().getSize());
            usageGuard.succeeded(reservation, result.usage());
            return result;
        } catch (GeminiApiException exception) {
            usageGuard.failed(reservation, exception.usage());
            throw exception;
        } catch (IOException exception) {
            usageGuard.failed(reservation);
            throw new IllegalArgumentException("The uploaded PDF could not be read.", exception);
        } catch (RuntimeException exception) {
            usageGuard.failed(reservation);
            throw exception;
        }
    }

    static String publicHttpsUrl(String rawValue) {
        String value = rawValue == null ? "" : rawValue.strip();
        if (value.length() > 2_000) {
            throw new IllegalArgumentException("Programme URL must be under 2000 characters.");
        }
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IllegalArgumentException("Use a public HTTPS programme URL.");
            }
            String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
            if (isPrivateHost(asciiHost)) {
                throw new IllegalArgumentException("Use a public HTTPS programme URL.");
            }
            StringBuilder normalized = new StringBuilder("https://").append(asciiHost);
            if (uri.getRawPath() != null) {
                normalized.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null) {
                String query = withoutTrackingParameters(uri.getRawQuery());
                if (!query.isBlank()) {
                    normalized.append('?').append(query);
                }
            }
            return normalized.toString();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException
                    && exception.getMessage() != null
                    && exception.getMessage().startsWith("Use a public")) {
                throw (IllegalArgumentException) exception;
            }
            throw new IllegalArgumentException("Programme URL is invalid.");
        }
    }

    private static String withoutTrackingParameters(String rawQuery) {
        return List.of(rawQuery.split("&")).stream()
                .filter(parameter -> !isTrackingParameter(parameter))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static boolean isTrackingParameter(String parameter) {
        String rawKey = parameter.split("=", 2)[0];
        try {
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return key.startsWith("utm_") || TRACKING_PARAMETERS.contains(key);
        } catch (IllegalArgumentException invalidEncoding) {
            return false;
        }
    }

    private static PdfUpload validPdf(MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException("Choose a PDF programme to upload.");
        }
        if (document.getSize() > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("The programme PDF must be 25 MB or smaller.");
        }
        String originalName = document.getOriginalFilename() == null ? "programme-2026.pdf" : document.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\r\\n\\p{Cntrl}]", "").strip();
        if (safeName.length() > 120) {
            safeName = safeName.substring(safeName.length() - 120);
        }
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("The uploaded programme must be a PDF file.");
        }
        try (InputStream input = document.getInputStream()) {
            if (!java.util.Arrays.equals(input.readNBytes(5), "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("The uploaded file is not a valid PDF.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The uploaded PDF could not be read.", exception);
        }
        return new PdfUpload(document, safeName);
    }

    private static boolean isPrivateHost(String host) {
        if (!host.contains(".")
                || host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")) {
            return true;
        }
        return host.matches("(?:127|10)\\..*")
                || host.matches("192\\.168\\..*")
                || host.matches("172\\.(?:1[6-9]|2\\d|3[01])\\..*")
                || host.matches("169\\.254\\..*")
                || host.matches("0\\..*");
    }

    public record IngestionRequest(String sourceUrl) {
    }

    public record IngestionResult(
            AdminProgrammeView programme,
            boolean cacheHit,
            boolean extracted,
            List<String> warnings) {
    }

    private record PdfUpload(MultipartFile document, String displayName) {
    }

    @FunctionalInterface
    private interface ExtractionOperation {
        ExtractionResult extract();
    }
}
