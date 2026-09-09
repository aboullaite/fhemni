package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.List;

import dev.maboullaite.fhemni.model.SourceReference;

public record ProgrammeChatAnswer(
        String question,
        ProgrammeChatBasis basis,
        String answer,
        List<SourceReference> sources,
        Instant answeredAt) {
}
