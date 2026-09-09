package dev.maboullaite.fhemni.programme;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.SourceReference;
import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.ProgrammeChatDossier;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.ProgrammeChatPromise;
import dev.maboullaite.fhemni.programme.PromiseAssessment.Evidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProgrammeChatContextBuilder {

    private static final int CHUNK_SIZE = 3_500;
    private static final int CHUNK_OVERLAP = 350;
    private static final int MAX_PROMISES = 8;
    private static final int MAX_DOCUMENT_CHUNKS = 6;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "this", "that", "what", "how", "does", "will",
            "les", "des", "une", "pour", "avec", "dans", "est", "sur", "quel", "quelle",
            "واش", "شنو", "كيفاش", "هاد", "ديال", "على", "إلى", "من", "في", "ف", "و");

    private final int maxCharacters;

    public ProgrammeChatContextBuilder(
            @Value("${fhemni.programme-chat.max-context-characters:90000}") int maxCharacters) {
        if (maxCharacters < 20_000 || maxCharacters > 250_000) {
            throw new IllegalArgumentException("Programme chat context must be between 20,000 and 250,000 characters");
        }
        this.maxCharacters = maxCharacters;
    }

    public ProgrammeChatContext build(
            ProgrammeChatDossier dossier,
            String question,
            OutputLanguage language,
            List<ConversationTurn> history) {
        PartyProgramme programme = dossier.programme();
        Set<String> queryTokens = retrievalTokens(question, history);
        List<RankedPromise> ranked = rankPromises(dossier.promises(), queryTokens);
        List<RankedPromise> selected = ranked.stream().limit(MAX_PROMISES).toList();
        Map<String, SourceReference> sources = new LinkedHashMap<>();
        sources.put("PROGRAMME", new SourceReference(
                programme.sourceLabel(), programme.sourceUrl(), ""));

        StringBuilder material = new StringBuilder();
        append(material, "PARTY-LOCKED DOSSIER\n");
        append(material, "Party code: " + programme.partyCode() + "\n");
        append(material, "Election term: " + programme.termStartYear() + "-" + programme.termEndYear() + "\n");
        append(material, "Official programme title: " + localized(programme.title(), language) + "\n");
        append(material, "Official programme summary: " + localized(programme.summary(), language) + "\n");
        append(material, "Official source ID: [PROGRAMME]\n");
        append(material, "Official source URL: " + programme.sourceUrl() + "\n");
        append(material, "Frozen source SHA-256: " + programme.sourceSha256() + "\n\n");

        append(material, "PUBLISHED PROMISE INVENTORY\n");
        for (int index = 0; index < dossier.promises().size(); index++) {
            ProgrammeChatPromise item = dossier.promises().get(index);
            PartyPromise promise = item.promise();
            String id = promiseId(index);
            sources.put(id, new SourceReference(
                    programme.sourceLabel() + " · " + promise.sourceLocator(), programme.sourceUrl(), ""));
            append(material, "[" + id + "] " + localized(promise.title(), language)
                    + " | topic=" + promise.topic() + " | locator=" + promise.sourceLocator());
            appendCompactAssessment(material, sources, promise, item.assessment(), id, language);
            append(material, "\n");
        }

        append(material, "\nRELEVANT OFFICIAL PROGRAMME EXTRACTS\n");
        for (RankedPromise rankedPromise : selected) {
            ProgrammeChatPromise item = rankedPromise.item();
            PartyPromise promise = item.promise();
            String id = promiseId(rankedPromise.originalIndex());
            append(material, "\n[" + id + "]\n");
            append(material, "Title: " + localized(promise.title(), language) + "\n");
            append(material, "Exact programme wording: " + promise.promiseText() + "\n");
            append(material, "Location: " + promise.sourceLocator() + "\n");
            append(material, "Stated implementation: " + promise.mechanism() + "\n");
            append(material, "Stated financing: " + promise.financing() + "\n");
            if (item.assessment() != null) {
                appendAssessment(material, sources, promise, item.assessment(), id, language);
            }
        }

        List<RankedText> chunks = rankChunks(
                programme.sourceSnapshot(), expandedDocumentTokens(queryTokens, selected));
        if (!chunks.isEmpty()) {
            append(material, "\nRELEVANT EXTRACTS FROM THE FROZEN OFFICIAL DOCUMENT\n");
            for (RankedText chunk : chunks.stream().limit(MAX_DOCUMENT_CHUNKS).toList()) {
                append(material, "\n--- Official document extract " + (chunk.originalIndex() + 1)
                        + "; cite [PROGRAMME] ---\n");
                append(material, chunk.text() + "\n");
            }
        }

        if (history != null && !history.isEmpty()) {
            append(material, "\nRECENT PRIVATE CONVERSATION (context only; not evidence)\n");
            history.stream().skip(Math.max(0, history.size() - 4L)).forEach(turn -> {
                append(material, "User: " + clipped(turn.question(), 1_000) + "\n");
                append(material, "Assistant: " + clipped(turn.answer(), 3_000) + "\n");
            });
        }

        return new ProgrammeChatContext(material.toString(), Map.copyOf(sources));
    }

    private void appendCompactAssessment(
            StringBuilder material,
            Map<String, SourceReference> sources,
            PartyPromise promise,
            PromiseAssessment assessment,
            String promiseId,
            OutputLanguage language) {
        if (assessment == null) return;
        String assessmentId = assessmentId(promiseId);
        sources.put(assessmentId, assessmentSourceReference(promise, assessment, language));
        append(material, " | assessment=[" + assessmentId + "] published feasibility=" + assessment.verdict());
        append(material, " | review=" + clipped(localized(assessment.summary(), language), 450));
        for (int index = 0; index < Math.min(2, assessment.evidence().size()); index++) {
            Evidence evidence = assessment.evidence().get(index);
            int evidenceIndex = index + 1;
            String id = promiseId + "_E" + evidenceIndex;
            sources.put(id, sourceReference(evidence));
            append(material, " | evidence=[" + id + "] " + evidence.publisher() + " — "
                    + clipped(evidence.title(), 180));
        }
    }

    private void appendAssessment(
            StringBuilder material,
            Map<String, SourceReference> sources,
            PartyPromise promise,
            PromiseAssessment assessment,
            String promiseId,
            OutputLanguage language) {
        String assessmentId = assessmentId(promiseId);
        sources.put(assessmentId, assessmentSourceReference(promise, assessment, language));
        append(material, "Published Fhemni feasibility assessment [" + assessmentId
                + "] for programme promise [" + promiseId + "]:\n");
        append(material, "Five-year verdict: " + assessment.verdict() + "\n");
        append(material, "Assessment summary: " + localized(assessment.summary(), language) + "\n");
        append(material, "Requirements: " + localized(assessment.requirements(), language) + "\n");
        append(material, "Assumptions: " + localized(assessment.assumptions(), language) + "\n");
        append(material, "Calculation notes: " + localized(assessment.calculationNotes(), language) + "\n");
        append(material, "Data cutoff: " + assessment.dataCutoff() + "\n");
        for (int index = 0; index < assessment.evidence().size(); index++) {
            Evidence evidence = assessment.evidence().get(index);
            String id = promiseId + "_E" + (index + 1);
            sources.put(id, sourceReference(evidence));
            append(material, "Evidence [" + id + "]: " + evidence.publisher() + " — " + evidence.title()
                    + " | date=" + nullableDate(evidence.publishedOn()) + " | note=" + evidence.note() + "\n");
        }
    }

    private List<RankedPromise> rankPromises(List<ProgrammeChatPromise> items, Set<String> queryTokens) {
        List<RankedPromise> ranked = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ProgrammeChatPromise item = items.get(index);
            PartyPromise promise = item.promise();
            String text = String.join(" ", promise.topic(), localizedAll(promise.title()),
                    promise.promiseText(), promise.mechanism(), promise.financing());
            if (item.assessment() != null) {
                PromiseAssessment assessment = item.assessment();
                text += " " + localizedAll(assessment.summary()) + " " + localizedAll(assessment.requirements())
                        + " " + localizedAll(assessment.assumptions()) + " "
                        + localizedAll(assessment.calculationNotes());
            }
            ranked.add(new RankedPromise(item, index, score(text, queryTokens)));
        }
        ranked.sort(Comparator.comparingInt(RankedPromise::score).reversed()
                .thenComparingInt(RankedPromise::originalIndex));
        return ranked;
    }

    private List<RankedText> rankChunks(String snapshot, Set<String> queryTokens) {
        String clean = WHITESPACE.matcher(snapshot == null ? "" : snapshot).replaceAll(" ").strip();
        if (clean.isEmpty()) {
            return List.of();
        }
        List<RankedText> chunks = new ArrayList<>();
        int index = 0;
        for (int start = 0; start < clean.length(); start += CHUNK_SIZE - CHUNK_OVERLAP) {
            int end = Math.min(clean.length(), start + CHUNK_SIZE);
            String chunk = clean.substring(start, end);
            chunks.add(new RankedText(chunk, index++, score(chunk, queryTokens)));
            if (end == clean.length()) break;
        }
        chunks.sort(Comparator.comparingInt(RankedText::score).reversed()
                .thenComparingInt(RankedText::originalIndex));
        return chunks;
    }

    private static Set<String> retrievalTokens(
            String question,
            List<ConversationTurn> history) {
        StringBuilder retrievalQuery = new StringBuilder(question == null ? "" : question);
        if (history != null) {
            history.stream()
                    .skip(Math.max(0, history.size() - 2L))
                    .map(ConversationTurn::question)
                    .forEach(previous -> retrievalQuery.append(' ').append(previous));
        }
        return tokens(retrievalQuery.toString());
    }

    private static Set<String> expandedDocumentTokens(
            Set<String> queryTokens,
            List<RankedPromise> selected) {
        Set<String> expanded = new HashSet<>(queryTokens);
        selected.stream().limit(3).map(RankedPromise::item).map(ProgrammeChatPromise::promise)
                .map(promise -> String.join(" ", localizedAll(promise.title()),
                        promise.promiseText(), promise.mechanism(), promise.financing()))
                .map(ProgrammeChatContextBuilder::tokens)
                .forEach(expanded::addAll);
        return expanded;
    }

    private static int score(String text, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) return 0;
        Set<String> documentTokens = tokens(text);
        return (int) queryTokens.stream().filter(documentTokens::contains).count();
    }

    private static Set<String> tokens(String text) {
        String normalized = normalize(text);
        Set<String> tokens = new HashSet<>();
        for (String token : NON_WORD.split(normalized)) {
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String normalize(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD);
        return MARKS.matcher(normalized).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه');
    }

    private static String localized(LocalizedText value, OutputLanguage language) {
        if (value == null) return "";
        return switch (language) {
            case DARIJA -> first(value.ar(), value.fr(), value.en());
            case FRENCH -> first(value.fr(), value.en(), value.ar());
            case ENGLISH -> first(value.en(), value.fr(), value.ar());
        };
    }

    private static String localizedAll(LocalizedText value) {
        return value == null ? "" : String.join(" ", nullToEmpty(value.ar()), nullToEmpty(value.fr()), nullToEmpty(value.en()));
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullableDate(LocalDate value) {
        return value == null ? "unknown" : value.toString();
    }

    private static SourceReference sourceReference(Evidence evidence) {
        return new SourceReference(
                evidence.publisher() + " · " + evidence.title(),
                evidence.url(),
                evidence.publishedOn() == null ? "" : evidence.publishedOn().toString());
    }

    private static SourceReference assessmentSourceReference(
            PartyPromise promise,
            PromiseAssessment assessment,
            OutputLanguage language) {
        return new SourceReference(
                "Fhemni five-year feasibility review · " + localized(promise.title(), language),
                "/promises/" + promise.slug(),
                assessment.dataCutoff() == null ? "" : assessment.dataCutoff().toString());
    }

    private static String assessmentId(String promiseId) {
        return promiseId.replace("PROMISE_", "ASSESSMENT_");
    }

    private static String promiseId(int index) {
        return "PROMISE_" + (index + 1);
    }

    private void append(StringBuilder target, String value) {
        if (target.length() >= maxCharacters) return;
        int available = maxCharacters - target.length();
        target.append(value, 0, Math.min(value.length(), available));
    }

    private static String clipped(String value, int maxLength) {
        String clean = value == null ? "" : value.strip();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength) + "…";
    }

    public record ConversationTurn(String question, String answer) {
    }

    private record RankedPromise(ProgrammeChatPromise item, int originalIndex, int score) {
    }

    private record RankedText(String text, int originalIndex, int score) {
    }
}
