package dev.maboullaite.fhemni.model;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FactCheckEvidencePolicy {

    private FactCheckEvidencePolicy() {
    }

    public static FactCheckAssessment sanitize(
            FactCheckAssessment assessment,
            OutputLanguage language) {
        List<SourceReference> sources = safeSources(assessment.sources());
        if (sources.isEmpty() && decisive(assessment.verdict())) {
            return new FactCheckAssessment(
                    assessment.claimId(),
                    ClaimVerdict.UNVERIFIABLE,
                    missingEvidenceText(language),
                    "LOW",
                    List.of());
        }
        return new FactCheckAssessment(
                assessment.claimId(),
                assessment.verdict(),
                assessment.explanation(),
                assessment.evidenceStrength(),
                sources);
    }

    public static VideoReport sanitize(VideoReport report, OutputLanguage language) {
        if (report == null) {
            return null;
        }
        return report.withClaims(report.claims().stream()
                .map(claim -> sanitize(claim, language))
                .toList());
    }

    public static boolean isSafeWebUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Claim sanitize(Claim claim, OutputLanguage language) {
        FactCheckAssessment assessment = sanitize(new FactCheckAssessment(
                claim.id(),
                claim.verdict(),
                claim.explanation(),
                claim.evidenceStrength(),
                claim.sources()), language);
        return claim.withAssessment(assessment);
    }

    private static List<SourceReference> safeSources(List<SourceReference> sources) {
        Map<String, SourceReference> unique = new LinkedHashMap<>();
        sources.stream()
                .filter(source -> source != null && isSafeWebUrl(source.url()))
                .forEach(source -> unique.putIfAbsent(source.url(), source));
        return List.copyOf(unique.values());
    }

    private static boolean decisive(ClaimVerdict verdict) {
        return verdict == ClaimVerdict.SUPPORTED || verdict == ClaimVerdict.CONTRADICTED;
    }

    private static String missingEvidenceText(OutputLanguage language) {
        return switch (language) {
            case DARIJA -> "ما رجع حتى مصدر موثوق كافي باش ندققو فهاد الادعاء.";
            case FRENCH -> "Aucune source suffisamment fiable n’a été trouvée pour vérifier cette affirmation.";
            case ENGLISH -> "No sufficiently reliable source was returned to verify this claim.";
        };
    }
}
