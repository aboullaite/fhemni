package dev.maboullaite.fhemni.programme;

import java.util.Map;

import dev.maboullaite.fhemni.model.SourceReference;

public record ProgrammeChatContext(
        String material,
        Map<String, SourceReference> sources) {
}
