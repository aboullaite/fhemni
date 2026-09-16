package dev.maboullaite.fhemni.civic;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.civic.CivicProfileService.Answer;
import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Question;

final class CivicAnswerValidator {

    private CivicAnswerValidator() {
    }

    static ValidatedAnswers validate(CivicQuestionnaire questionnaire, List<Answer> submittedAnswers) {
        if (submittedAnswers == null || submittedAnswers.isEmpty()) {
            throw new IllegalArgumentException("Answer at least one question.");
        }

        Map<String, Question> questions = questionnaire.questions().stream()
                .collect(Collectors.toUnmodifiableMap(Question::key, Function.identity()));
        Set<String> seen = new HashSet<>();
        Map<String, Answer> byKey = new LinkedHashMap<>();
        for (Answer answer : submittedAnswers) {
            if (answer == null || answer.questionKey() == null || answer.questionKey().isBlank()) {
                throw new IllegalArgumentException("Every answer needs a question key.");
            }
            if (answer.value() == null) {
                throw new IllegalArgumentException("Every answer needs a value.");
            }
            if (!seen.add(answer.questionKey())) {
                throw new IllegalArgumentException("Answer each question only once.");
            }
            if (!questions.containsKey(answer.questionKey())) {
                throw new IllegalArgumentException("Answer references an unknown question.");
            }
            if (answer.value() < -2 || answer.value() > 2) {
                throw new IllegalArgumentException("Answer values must be between -2 and 2.");
            }
            byKey.put(answer.questionKey(), answer);
        }

        return new ValidatedAnswers(
                List.copyOf(byKey.values()),
                Collections.unmodifiableMap(byKey),
                questions);
    }

    record ValidatedAnswers(
            List<Answer> answers,
            Map<String, Answer> byKey,
            Map<String, Question> questions) {
    }
}
