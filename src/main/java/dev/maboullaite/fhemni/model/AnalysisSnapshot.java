package dev.maboullaite.fhemni.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisSnapshot(
        UUID id,
        String videoUrl,
        String videoId,
        OutputLanguage language,
        AnalysisStatus status,
        int progress,
        String progressMessage,
        boolean demo,
        Instant createdAt,
        VideoReport report,
        String error,
        List<FollowUpAnswer> conversation,
        boolean published,
        String catalogSlug) {

    public AnalysisSnapshot {
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
    }

    public AnalysisSnapshot withPublication(boolean isPublished, String slug) {
        return new AnalysisSnapshot(
                id, videoUrl, videoId, language, status, progress, progressMessage, demo,
                createdAt, report, error, conversation, isPublished, slug);
    }
}
