package dev.maboullaite.fhemni.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.maboullaite.fhemni.catalog.VideoMetadataGateway.VideoMetadata;
import org.junit.jupiter.api.Test;

class SuggestionSafetyPolicyTest {

    private final SuggestionSafetyPolicy policy = new SuggestionSafetyPolicy();

    @Test
    void approvesOrdinaryPublicAffairsMetadata() {
        var result = policy.assess(new VideoMetadata(
                "ساعة الصراحة: الاستعدادات الانتخابية",
                "2M",
                "https://i.ytimg.com/example.jpg"));

        assertEquals(SuggestionModerationStatus.APPROVED, result.status());
    }

    @Test
    void holdsHighRiskOrSpoofedMetadataForManualReview() {
        var highRisk = policy.assess(new VideoMetadata(
                "XXX video",
                "Example channel",
                "https://i.ytimg.com/example.jpg"));
        var spoofed = policy.assess(new VideoMetadata(
                "Normal title\u202Etxt.exe",
                "Example channel",
                "https://i.ytimg.com/example.jpg"));

        assertEquals(SuggestionModerationStatus.REVIEW_REQUIRED, highRisk.status());
        assertEquals(SuggestionModerationStatus.REVIEW_REQUIRED, spoofed.status());
    }
}
