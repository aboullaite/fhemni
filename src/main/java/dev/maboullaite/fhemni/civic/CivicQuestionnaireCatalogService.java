package dev.maboullaite.fhemni.civic;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Question;
import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Source;
import dev.maboullaite.fhemni.civic.CivicQuestionnaire.Theme;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.EditionData;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.QuestionData;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireRepository.ThemeData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CivicQuestionnaireCatalogService {

    private final CivicQuestionnaireRepository repository;

    CivicQuestionnaireCatalogService(CivicQuestionnaireRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CivicQuestionnaire current(String requestedLanguage) {
        return localize(repository.currentPublished(), language(requestedLanguage));
    }

    @Transactional(readOnly = true)
    public CivicQuestionnaire edition(UUID editionId, String requestedLanguage) {
        return localize(repository.published(editionId), language(requestedLanguage));
    }

    public static String language(String requestedLanguage) {
        if (requestedLanguage == null || requestedLanguage.isBlank()) {
            return "ar";
        }
        String normalized = requestedLanguage.strip().toLowerCase(Locale.ROOT);
        if (!List.of("ar", "fr", "en").contains(normalized)) {
            throw new IllegalArgumentException("Supported languages are ar, fr and en.");
        }
        return normalized;
    }

    private CivicQuestionnaire localize(EditionData edition, String language) {
        List<ThemeData> themeData = repository.themes(edition.id());
        Map<String, ThemeData> themesByCode = themeData.stream()
                .collect(Collectors.toUnmodifiableMap(ThemeData::code, Function.identity()));
        List<Theme> themes = themeData.stream()
                .map(theme -> new Theme(theme.code(), theme.label().get(language), theme.order()))
                .toList();
        List<Question> questions = repository.questions(edition.id()).stream()
                .map(question -> localize(question, themesByCode, language))
                .toList();
        if (questions.size() != 18) {
            throw new IllegalStateException("A published priority questionnaire must contain exactly 18 questions.");
        }
        return new CivicQuestionnaire(
                edition.id(),
                edition.version(),
                language,
                language.equals("ar") ? "rtl" : "ltr",
                edition.title().get(language),
                edition.intro().get(language),
                edition.methodology().get(language),
                edition.dataCutoff(),
                themes,
                questions);
    }

    private Question localize(QuestionData question, Map<String, ThemeData> themes, String language) {
        ThemeData theme = themes.get(question.themeCode());
        if (theme == null) {
            throw new IllegalStateException("Question references an unknown theme: " + question.themeCode());
        }
        List<Source> sources = question.sources().stream()
                .map(source -> new Source(source.label().get(language), source.url(), source.date()))
                .toList();
        if (sources.isEmpty()) {
            throw new IllegalStateException("Every published question must have a source: " + question.key());
        }
        return new Question(
                question.key(),
                question.themeCode(),
                theme.label().get(language),
                question.order(),
                question.prompt().get(language),
                question.context().get(language),
                sources);
    }
}
