package dev.maboullaite.fhemni.catalog;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.catalog.VideoMetadataGateway.VideoMetadata;
import org.springframework.stereotype.Component;

@Component
public class SuggestionSafetyPolicy {

    private static final Pattern BIDI_OVERRIDE = Pattern.compile("[\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern HIGH_RISK_METADATA = Pattern.compile(
            "(?iu)(porn(?:o|ography|ographique)?|xxx|snuff|bestiality|child\\s+sexual\\s+abuse|"
                    + "rape\\s+(?:video|footage)|beheading(?:\\s+(?:video|footage))?|"
                    + "p[eé]doporn|d[eé]capitation|viol\\s+film[eé]|"
                    + "إباحي|اباحي|إباحية|اباحية|بورنو|اغتصاب|ذبح|قطع\\s+الرأس)");

    public Assessment assess(VideoMetadata metadata) {
        String title = required(metadata == null ? null : metadata.title(), 500, "video title");
        String author = required(metadata.authorName(), 300, "channel name");
        String searchable = Normalizer.normalize(title + " " + author, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (BIDI_OVERRIDE.matcher(searchable).find()) {
            return new Assessment(
                    SuggestionModerationStatus.REVIEW_REQUIRED,
                    "The YouTube metadata contains hidden bidirectional control characters.");
        }
        if (HIGH_RISK_METADATA.matcher(searchable).find()) {
            return new Assessment(
                    SuggestionModerationStatus.REVIEW_REQUIRED,
                    "The title or channel needs a manual safety review before community voting.");
        }
        return new Assessment(SuggestionModerationStatus.APPROVED, null);
    }

    private String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("YouTube returned an empty " + label + ".");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("The YouTube " + label + " is too long.");
        }
        return normalized;
    }

    public record Assessment(SuggestionModerationStatus status, String reason) {
    }
}
