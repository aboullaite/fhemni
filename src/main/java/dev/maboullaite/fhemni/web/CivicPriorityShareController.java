package dev.maboullaite.fhemni.web;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import dev.maboullaite.fhemni.civic.CivicPriorityShare;
import dev.maboullaite.fhemni.civic.CivicPriorityShareService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

@RestController
public class CivicPriorityShareController {

    private static final CacheControl PAGE_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
    private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic().mustRevalidate();

    private final CivicPriorityShareService shares;

    CivicPriorityShareController(CivicPriorityShareService shares) {
        this.shares = shares;
    }

    @PostMapping(
            value = "/api/catalog/questionnaires/current/shares",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatedShare> create(
            @RequestParam String kind,
            @RequestParam String language,
            @RequestParam MultipartFile image) throws IOException {
        if (!MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(image.getContentType())) {
            throw new IllegalArgumentException("The share card must be a PNG image.");
        }
        if (image.getSize() > CivicPriorityShareService.MAX_UPLOAD_BYTES) {
            throw new PriorityShareUploadTooLargeException();
        }
        CivicPriorityShare share = shares.create(kind, language, image.getBytes());
        String path = path(share.token());
        return ResponseEntity.created(URI.create(path)).body(new CreatedShare(path, path + "/image"));
    }

    @GetMapping(value = "/s/priorities/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String token, HttpServletRequest request) {
        CivicPriorityShare.Metadata share = shares.findMetadata(token);
        ShareCopy copy = ShareCopy.forShare(share.kind(), share.language());
        String path = path(share.token());
        String canonical = absolute(request, path);
        String image = absolute(request, path + "/image");
        String destination = "/priorities?lang=" + share.language();
        String html = pageHtml(share, copy, canonical, image, destination);
        return ResponseEntity.ok()
                .cacheControl(PAGE_CACHE)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping(value = "/s/priorities/{token}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String token) throws IOException {
        CivicPriorityShareService.StoredImage image = shares.findImage(token);
        String filename = image.kind() == CivicPriorityShare.Kind.COMPASS
                ? "fhemni-priority-compass.png"
                : "fhemni-party-matches.png";
        return ResponseEntity.ok()
                .cacheControl(IMAGE_CACHE)
                .eTag(image.sha256())
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(image.bytes());
    }

    @DeleteMapping("/api/admin/civic-priority-shares/{token}")
    public ResponseEntity<Void> delete(@PathVariable String token) throws IOException {
        shares.delete(token);
        return ResponseEntity.noContent().build();
    }

    private static String pageHtml(
            CivicPriorityShare.Metadata share,
            ShareCopy copy,
            String canonical,
            String image,
            String destination) {
        String language = escape(share.language());
        String direction = share.language().equals("ar") ? "rtl" : "ltr";
        String title = escape(copy.title());
        String description = escape(copy.description());
        String imageAlt = escape(copy.imageAlt());
        String cta = escape(copy.cta());
        String safeCanonical = escape(canonical);
        String safeImage = escape(image);
        String safeDestination = escape(destination);
        return """
                <!doctype html>
                <html lang="%s" dir="%s" data-theme="fhemni">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="%s">
                    <link rel="icon" href="/favicon.ico?v=20260911" sizes="32x32">
                    <link rel="stylesheet" href="/css/dist.css?v=20260917-9">
                    <link rel="stylesheet" href="/css/priority-share.css?v=20260917-2">
                    <meta property="og:type" content="website">
                    <meta property="og:site_name" content="Fhemni.ma">
                    <meta property="og:locale" content="%s">
                    <meta property="og:url" content="%s">
                    <meta property="og:title" content="%s">
                    <meta property="og:description" content="%s">
                    <meta property="og:image" content="%s">
                    <meta property="og:image:secure_url" content="%s">
                    <meta property="og:image:type" content="image/png">
                    <meta property="og:image:width" content="2400">
                    <meta property="og:image:height" content="1260">
                    <meta property="og:image:alt" content="%s">
                    <meta name="twitter:card" content="summary_large_image">
                    <meta name="twitter:title" content="%s">
                    <meta name="twitter:description" content="%s">
                    <meta name="twitter:image" content="%s">
                    <meta name="twitter:image:alt" content="%s">
                </head>
                <body class="priority-share-public-page">
                    <main class="priority-share-public-shell">
                        <a class="priority-share-public-brand" href="/" aria-label="Fhemni home">
                            <img src="/assets/brand/fhemni-logo.png" alt="Fhemni">
                        </a>
                        <article class="priority-share-public-card">
                            <img src="%s" alt="%s" width="2400" height="1260">
                            <div>
                                <h1>%s</h1>
                                <p>%s</p>
                                <a class="priority-primary-button" href="%s">%s</a>
                            </div>
                        </article>
                    </main>
                </body>
                </html>
                """.formatted(
                language, direction, title, description, safeCanonical,
                copy.openGraphLocale(), safeCanonical, title, description,
                safeImage, safeImage, imageAlt, title, description, safeImage, imageAlt,
                safeImage, imageAlt, title, description, safeDestination, cta);
    }

    private static String path(String token) {
        return "/s/priorities/" + token;
    }

    private static String absolute(HttpServletRequest request, String path) {
        String requestUrl = request.getRequestURL().toString();
        String requestUri = request.getRequestURI();
        String origin = requestUrl.substring(0, requestUrl.length() - requestUri.length());
        return origin + request.getContextPath() + path;
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }

    public record CreatedShare(String url, String imageUrl) {
    }

    private record ShareCopy(
            String title,
            String description,
            String imageAlt,
            String cta,
            String openGraphLocale) {

        static ShareCopy forShare(CivicPriorityShare.Kind kind, String language) {
            boolean compass = kind == CivicPriorityShare.Kind.COMPASS;
            return switch (language) {
                case "fr" -> new ShareCopy(
                        compass ? "Ma boussole des priorités — Fhemni" : "Les partis les plus proches de mes priorités — Fhemni",
                        "Un résultat personnel fondé sur les positions documentées dans les programmes officiels publiés.",
                        compass ? "Boussole personnelle des priorités" : "Trois partis proches des priorités exprimées",
                        "Essayez la boussole", "fr_MA");
                case "en" -> new ShareCopy(
                        compass ? "My priority compass — Fhemni" : "Parties closest to my priorities — Fhemni",
                        "A personal result based on documented positions in published official programmes.",
                        compass ? "Personal priority compass" : "Three parties close to the expressed priorities",
                        "Try the compass", "en_US");
                default -> new ShareCopy(
                        compass ? "بوصلة الأولويات ديالي — فهّمني" : "الأحزاب الأقرب لأولوياتي — فهّمني",
                        "نتيجة شخصية مبنية على المواقف الموثقة فالبرامج الرسمية المنشورة.",
                        compass ? "بوصلة الأولويات الشخصية" : "ثلاثة أحزاب قريبين من الأولويات المعبر عليها",
                        "دخل حتى نتا وجرّبها", "ar_MA");
            };
        }
    }
}
