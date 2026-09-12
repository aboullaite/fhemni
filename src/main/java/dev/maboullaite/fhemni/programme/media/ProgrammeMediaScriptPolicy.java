package dev.maboullaite.fhemni.programme.media;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminPromiseView;
import dev.maboullaite.fhemni.programme.EditorialStatus;
import org.springframework.stereotype.Component;

@Component
public class ProgrammeMediaScriptPolicy {

    public static final String PRONUNCIATION_VERSION = "darija-v2-strong-h";
    private static final int HEADLINE_LINE_LENGTH = 34;
    private static final int MAX_HEADLINE_LINES = 2;
    private static final Pattern FHEMNI_BRAND = Pattern.compile(
            "ف[\\u064B-\\u065F\\u0670]*ه[\\u064B-\\u065F\\u0670]*م[\\u064B-\\u065F\\u0670]*ن[\\u064B-\\u065F\\u0670]*ي[\\u064B-\\u065F\\u0670]*");
    private static final Pattern INTERNAL_CODE = Pattern.compile(
            "(?i)\\b(POSSIBLE|HARD|NOT_ACHIEVABLE|INSUFFICIENT_DATA|PROMISE|ASSESSMENT)\\b");
    private static final Pattern MARKUP_OR_URL = Pattern.compile("https?://|www\\.|[<>]|```|\\[[^]]+]");

    public ProgrammeMediaScript validate(ProgrammeMediaScript script, AdminProgrammeView programme) {
        if (script == null) {
            throw new IllegalArgumentException("The programme media script is missing.");
        }
        String headline = text(script.headline(), "headline", 12, 64);
        if (wrappedLineCount(headline, HEADLINE_LINE_LENGTH) > MAX_HEADLINE_LINES) {
            throw new IllegalArgumentException("The headline must fit on no more than two video lines.");
        }
        List<ProgrammeMediaScript.Segment> supplied = script.segments() == null ? List.of() : script.segments();
        if (supplied.size() < 14 || supplied.size() > 16) {
            throw new IllegalArgumentException("A five-minute briefing needs between 14 and 16 segments.");
        }
        Set<String> allowedRefs = new HashSet<>();
        Map<String, String> assessmentPromiseRefs = new HashMap<>();
        List<AdminPromiseView> publishedPromises =
                programme.promises().stream()
                        .filter(item -> item.promise().status() == EditorialStatus.PUBLISHED)
                        .toList();
        publishedPromises.forEach(item -> {
            String promiseRef = "PROMISE:" + item.promise().id();
            allowedRefs.add(promiseRef);
            item.assessments().stream()
                    .filter(assessment -> assessment.status().name().equals("PUBLISHED"))
                    .forEach(assessment -> {
                        String assessmentRef = "ASSESSMENT:" + assessment.id();
                        allowedRefs.add(assessmentRef);
                        assessmentPromiseRefs.put(assessmentRef, promiseRef);
                    });
        });
        Set<String> citedPromises = new HashSet<>();
        int words = 0;
        List<ProgrammeMediaScript.Segment> segments = new ArrayList<>(supplied.size());
        for (ProgrammeMediaScript.Segment segment : supplied) {
            String message = visibleText(segment.message(), "segment message", 3, 100);
            String narration = visibleText(segment.narration(), "segment narration", 80, 700);
            int segmentWords = wordCount(narration);
            if (segmentWords < 26 || segmentWords > 40) {
                throw new IllegalArgumentException("Each narration segment needs between 26 and 40 spoken words.");
            }
            List<String> refs = segment.sourceRefs() == null ? List.of() : segment.sourceRefs().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::strip)
                    .distinct()
                    .toList();
            if (refs.isEmpty() || refs.size() > 8 || !allowedRefs.containsAll(refs)) {
                throw new IllegalArgumentException("Every segment needs one to eight valid programme source references.");
            }
            Set<String> segmentPromiseRefs = refs.stream()
                    .filter(value -> value.startsWith("PROMISE:"))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (segmentPromiseRefs.isEmpty()) {
                throw new IllegalArgumentException("Every segment must cite at least one programme promise.");
            }
            boolean assessmentWithoutPromise = refs.stream()
                    .filter(value -> value.startsWith("ASSESSMENT:"))
                    .map(assessmentPromiseRefs::get)
                    .anyMatch(promiseRef -> !segmentPromiseRefs.contains(promiseRef));
            if (assessmentWithoutPromise) {
                throw new IllegalArgumentException("An assessment source must be paired with its programme promise.");
            }
            citedPromises.addAll(segmentPromiseRefs);
            words += segmentWords;
            segments.add(new ProgrammeMediaScript.Segment(message, narration, refs));
        }
        if (words < 460 || words > 500) {
            throw new IllegalArgumentException("A five-minute briefing must contain between 460 and 500 spoken words.");
        }
        int requiredPromiseCoverage = Math.min(5, publishedPromises.size());
        if (citedPromises.size() < requiredPromiseCoverage) {
            throw new IllegalArgumentException("The briefing does not cover enough distinct programme promises.");
        }
        return new ProgrammeMediaScript(headline, segments);
    }

    public String spokenText(ProgrammeMediaScript script) {
        return script.segments().stream()
                .map(ProgrammeMediaScript.Segment::narration)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    public String ttsText(String displayText) {
        return FHEMNI_BRAND.matcher(displayText).replaceAll("فَهَّمْنِي");
    }

    private String visibleText(String value, String field, int min, int max) {
        String clean = text(value, field, min, max);
        if (INTERNAL_CODE.matcher(clean).find() || MARKUP_OR_URL.matcher(clean).find()) {
            throw new IllegalArgumentException("The " + field + " contains internal labels, markup, or a URL.");
        }
        return clean;
    }

    private String text(String value, String field, int min, int max) {
        String clean = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (clean.length() < min || clean.length() > max) {
            throw new IllegalArgumentException("The " + field + " must contain between " + min + " and " + max + " characters.");
        }
        return clean;
    }

    private int wordCount(String value) {
        return value.isBlank() ? 0 : value.split("\\s+").length;
    }

    private int wrappedLineCount(String value, int lineLength) {
        int lines = 1;
        int currentLength = 0;
        for (String word : value.split("\\s+")) {
            int nextLength = currentLength == 0 ? word.length() : currentLength + 1 + word.length();
            if (currentLength > 0 && nextLength > lineLength) {
                lines++;
                currentLength = word.length();
            } else {
                currentLength = nextLength;
            }
        }
        return lines;
    }
}
