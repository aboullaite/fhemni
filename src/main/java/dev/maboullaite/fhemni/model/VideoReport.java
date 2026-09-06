package dev.maboullaite.fhemni.model;

import java.util.List;

public record VideoReport(
        String title,
        String summary,
        String detailedSummary,
        List<Participant> participants,
        List<Chapter> chapters,
        List<Claim> claims,
        List<String> suggestedQuestions) {

    public VideoReport {
        participants = participants == null ? List.of() : List.copyOf(participants);
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
        claims = claims == null ? List.of() : List.copyOf(claims);
        suggestedQuestions = suggestedQuestions == null ? List.of() : List.copyOf(suggestedQuestions);
    }

    public VideoReport withClaims(List<Claim> assessedClaims) {
        return new VideoReport(title, summary, detailedSummary, participants, chapters,
                assessedClaims, suggestedQuestions);
    }
}
