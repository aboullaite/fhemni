package dev.maboullaite.fhemni.programme.media;

import java.util.List;

public record ProgrammeMediaScript(
        String headline,
        List<Segment> segments) {

    public record Segment(
            String message,
            String narration,
            List<String> sourceRefs) {
    }
}
