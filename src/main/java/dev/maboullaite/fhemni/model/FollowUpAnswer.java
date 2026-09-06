package dev.maboullaite.fhemni.model;

import java.time.Instant;
import java.util.List;

public record FollowUpAnswer(
        String question,
        QuestionMode mode,
        String answer,
        List<SourceReference> sources,
        Instant answeredAt) {

    public FollowUpAnswer {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
