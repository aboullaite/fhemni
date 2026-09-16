package dev.maboullaite.fhemni.civic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Question;
import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Theme;
import org.springframework.stereotype.Service;

@Service
public class CivicProfileService {

    private final CivicQuestionnaireCatalogService catalog;

    CivicProfileService(CivicQuestionnaireCatalogService catalog) {
        this.catalog = catalog;
    }

    public Profile profile(ProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Answers are required.");
        }
        String language = CivicQuestionnaireCatalogService.language(request.language());
        CivicQuestionnaire questionnaire = catalog.current(language);
        List<Answer> answers = request.answers() == null ? List.of() : List.copyOf(request.answers());
        if (answers.isEmpty()) {
            throw new IllegalArgumentException("Answer at least one question to build your profile.");
        }

        Map<String, Question> questions = questionnaire.questions().stream()
                .collect(Collectors.toUnmodifiableMap(Question::key, Function.identity()));
        Set<String> seen = new HashSet<>();
        Map<String, Answer> answersByKey = new LinkedHashMap<>();
        for (Answer answer : answers) {
            if (answer == null || answer.questionKey() == null || answer.questionKey().isBlank()) {
                throw new IllegalArgumentException("Every answer needs a question key.");
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
            answersByKey.put(answer.questionKey(), answer);
        }

        Map<String, ThemeAccumulator> byTheme = new HashMap<>();
        for (Answer answer : answers) {
            Question question = questions.get(answer.questionKey());
            byTheme.computeIfAbsent(question.themeCode(), ignored -> new ThemeAccumulator()).add(answer);
        }

        Map<String, Theme> themes = questionnaire.themes().stream()
                .collect(Collectors.toUnmodifiableMap(Theme::code, Function.identity()));
        List<Priority> priorities = byTheme.entrySet().stream()
                .map(entry -> priority(themes.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt(Priority::score).reversed()
                        .thenComparingInt(priority -> themes.get(priority.themeCode()).order()))
                .toList();

        List<Position> positions = answers.stream()
                .filter(answer -> answer.value() != 0)
                .sorted(Comparator.comparingInt(CivicProfileService::strength).reversed()
                        .thenComparingInt(answer -> questions.get(answer.questionKey()).order()))
                .limit(3)
                .map(answer -> position(questions.get(answer.questionKey()), answer, language))
                .toList();

        int answeredCount = answers.size();
        return new Profile(
                questionnaire.id().toString(),
                questionnaire.version(),
                language,
                questionnaire.questions().size(),
                answeredCount,
                questionnaire.questions().size() - answeredCount,
                Math.round(answeredCount * 100f / questionnaire.questions().size()),
                priorities,
                positions,
                mixedThemes(questionnaire, answersByKey, language),
                questionnaire.methodology());
    }

    private Priority priority(Theme theme, ThemeAccumulator values) {
        int maximum = values.answered * 4;
        int score = maximum == 0 ? 0 : Math.round(values.strength * 100f / maximum);
        return new Priority(theme.code(), theme.label(), score, values.answered, values.important);
    }

    private Position position(Question question, Answer answer, String language) {
        return new Position(
                question.key(), question.themeCode(), question.themeLabel(), question.prompt(),
                stance(answer.value(), language), answer.important());
    }

    private List<Tension> mixedThemes(
            CivicQuestionnaire questionnaire,
            Map<String, Answer> answers,
            String language) {
        List<Tension> tensions = new ArrayList<>();
        for (Theme theme : questionnaire.themes()) {
            boolean positive = false;
            boolean negative = false;
            for (Question question : questionnaire.questions()) {
                if (!question.themeCode().equals(theme.code())) continue;
                Answer answer = answers.get(question.key());
                positive |= answer != null && answer.value() >= 1;
                negative |= answer != null && answer.value() <= -1;
            }
            if (positive && negative) {
                tensions.add(new Tension(theme.code(), theme.label(), tensionCopy(theme.label(), language)));
            }
            if (tensions.size() == 3) break;
        }
        return List.copyOf(tensions);
    }

    private String stance(int value, String language) {
        return switch (language) {
            case "ar" -> switch (value) {
                case -2 -> "ما متافقش نهائيا";
                case -1 -> "ما متافقش";
                case 0 -> "بين وبين";
                case 1 -> "متافق";
                case 2 -> "متافق بزاف";
                default -> throw new IllegalArgumentException("Answer values must be between -2 and 2.");
            };
            case "fr" -> switch (value) {
                case -2 -> "Pas du tout d’accord";
                case -1 -> "Pas d’accord";
                case 0 -> "Neutre";
                case 1 -> "D’accord";
                case 2 -> "Tout à fait d’accord";
                default -> throw new IllegalArgumentException("Answer values must be between -2 and 2.");
            };
            default -> switch (value) {
                case -2 -> "Strongly disagree";
                case -1 -> "Disagree";
                case 0 -> "Neither";
                case 1 -> "Agree";
                case 2 -> "Strongly agree";
                default -> throw new IllegalArgumentException("Answer values must be between -2 and 2.");
            };
        };
    }

    private String tensionCopy(String theme, String language) {
        return switch (language) {
            case "ar" -> "الأجوبة ديالك فـ" + theme + " كيبينو باللي كتشوف أكثر من جهة فالموضوع. رجع للسياق باش تدقق فالمفاضلة.";
            case "fr" -> "Vos réponses sur « " + theme + " » combinent plusieurs préférences. Relisez le contexte pour préciser cet arbitrage.";
            default -> "Your answers on “" + theme + "” combine different preferences. Revisit the context to sharpen this trade-off.";
        };
    }

    private static int strength(Answer answer) {
        return Math.abs(answer.value()) * (answer.important() ? 2 : 1);
    }

    public record ProfileRequest(String language, List<Answer> answers) {
    }

    public record Answer(String questionKey, int value, boolean important) {
    }

    public record Profile(
            String editionId,
            String version,
            String language,
            int questionCount,
            int answeredCount,
            int skippedCount,
            int completionPercent,
            List<Priority> priorities,
            List<Position> strongestPositions,
            List<Tension> tensions,
            String methodology) {
    }

    public record Priority(String themeCode, String label, int score, int answeredCount, int importantCount) {
    }

    public record Position(
            String questionKey,
            String themeCode,
            String themeLabel,
            String prompt,
            String stance,
            boolean important) {
    }

    public record Tension(String themeCode, String label, String explanation) {
    }

    private static final class ThemeAccumulator {
        private int answered;
        private int important;
        private int strength;

        void add(Answer answer) {
            answered++;
            if (answer.important()) important++;
            strength += CivicProfileService.strength(answer);
        }
    }
}
