package dev.maboullaite.fhemni.analysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.VideoReport;

final class AnalysisSession {

    private final UUID id;
    private final String videoUrl;
    private final String videoId;
    private final OutputLanguage language;
    private final boolean demo;
    private final Instant createdAt;

    private AnalysisStatus status = AnalysisStatus.QUEUED;
    private int progress;
    private String progressMessage = "Waiting to start";
    private VideoReport report;
    private String error;
    private String analysisInteractionId;
    private Instant lastAccessedAt;
    private int activeQuestions;

    AnalysisSession(
            UUID id,
            String videoUrl,
            String videoId,
            OutputLanguage language,
            boolean demo) {
        this(id, videoUrl, videoId, language, demo, Instant.now());
    }

    private AnalysisSession(
            UUID id,
            String videoUrl,
            String videoId,
            OutputLanguage language,
            boolean demo,
            Instant createdAt) {
        this.id = id;
        this.videoUrl = videoUrl;
        this.videoId = videoId;
        this.language = language;
        this.demo = demo;
        this.createdAt = createdAt;
        this.lastAccessedAt = createdAt;
    }

    static AnalysisSession restore(AnalysisSnapshot snapshot, String interactionId) {
        AnalysisSession session = new AnalysisSession(
                snapshot.id(), snapshot.videoUrl(), snapshot.videoId(), snapshot.language(),
                snapshot.demo(), snapshot.createdAt());
        session.status = snapshot.status();
        session.progress = snapshot.progress();
        session.progressMessage = snapshot.progressMessage();
        session.report = snapshot.report();
        session.error = snapshot.error();
        session.analysisInteractionId = interactionId;
        session.lastAccessedAt = Instant.now();
        return session;
    }

    synchronized void progress(AnalysisStatus status, int progress, String message) {
        this.status = status;
        this.progress = progress;
        this.progressMessage = message;
        touch();
    }

    synchronized void complete(VideoReport report, String interactionId) {
        this.report = report;
        this.analysisInteractionId = interactionId;
        this.status = AnalysisStatus.COMPLETED;
        this.progress = 100;
        this.progressMessage = "Analysis complete";
        touch();
    }

    synchronized void fail(String message) {
        this.status = AnalysisStatus.FAILED;
        this.progressMessage = "Analysis failed";
        this.error = message;
        touch();
    }

    synchronized String analysisInteractionId() {
        return analysisInteractionId;
    }

    synchronized void beginQuestion() {
        activeQuestions++;
        touch();
    }

    synchronized void endQuestion() {
        activeQuestions = Math.max(0, activeQuestions - 1);
        touch();
    }

    synchronized void touch() {
        lastAccessedAt = Instant.now();
    }

    synchronized Instant lastAccessedAt() {
        return lastAccessedAt;
    }

    synchronized boolean expiredTerminal(Instant cutoff) {
        return terminal() && activeQuestions == 0 && lastAccessedAt.isBefore(cutoff);
    }

    synchronized boolean evictable() {
        return terminal() && activeQuestions == 0;
    }

    private boolean terminal() {
        return status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED;
    }

    synchronized AnalysisSnapshot snapshot() {
        return new AnalysisSnapshot(
                id,
                videoUrl,
                videoId,
                language,
                status,
                progress,
                progressMessage,
                demo,
                createdAt,
                report,
                error,
                List.of(),
                false,
                null);
    }
}
