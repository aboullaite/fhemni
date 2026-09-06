package dev.maboullaite.fhemni.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.maboullaite.fhemni.analysis.AnalysisService;
import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.FollowUpAnswer;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.QuestionMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final CurrentUserService currentUser;
    private final String analyticsMeasurementId;

    public AnalysisController(
            AnalysisService analysisService,
            CurrentUserService currentUser,
            @Value("${fhemni.analytics.measurement-id:}") String analyticsMeasurementId) {
        this.analysisService = analysisService;
        this.currentUser = currentUser;
        this.analyticsMeasurementId = analyticsMeasurementId == null ? "" : analyticsMeasurementId.strip();
    }

    @GetMapping("/meta")
    public MetaResponse meta() {
        List<LanguageOption> languages = Arrays.stream(OutputLanguage.values())
                .map(language -> new LanguageOption(
                        language.code(), language.displayName(), language.rightToLeft()))
                .toList();
        return new MetaResponse(
                analysisService.live(),
                analysisService.analysisEnabled(),
                analysisService.chatEnabled(),
                analysisService.model(),
                analyticsMeasurementId,
                languages);
    }

    @PostMapping("/analyses")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisSnapshot create(@RequestBody CreateAnalysisRequest request) {
        return analysisService.create(request.youtubeUrl(), request.language());
    }

    @GetMapping("/analyses/{id}")
    public AnalysisSnapshot get(@PathVariable UUID id, Authentication authentication) {
        UUID userId = currentUser.find(authentication).map(AppUser::id).orElse(null);
        return currentUser.isAdministrator(authentication)
                ? analysisService.get(id, userId)
                : analysisService.getPublished(id, userId);
    }

    @GetMapping(value = "/analyses/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id, Authentication authentication) {
        UUID userId = currentUser.find(authentication).map(AppUser::id).orElse(null);
        if (currentUser.isAdministrator(authentication)) {
            analysisService.get(id, userId);
        } else {
            analysisService.getPublished(id, userId);
        }
        return analysisService.subscribe(id);
    }

    @PostMapping("/analyses/{id}/questions")
    public FollowUpAnswer ask(
            @PathVariable UUID id,
            @RequestBody AskQuestionRequest request,
            Authentication authentication) {
        AppUser user = currentUser.find(authentication)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Sign in to ask this video."));
        if (!currentUser.isAdministrator(authentication)) {
            analysisService.getPublished(id);
        }
        return analysisService.ask(id, user.id(), request.question(), request.mode());
    }

    public record CreateAnalysisRequest(String youtubeUrl, String language) {
    }

    public record AskQuestionRequest(String question, QuestionMode mode) {
    }

    public record MetaResponse(
            boolean live,
            boolean analysisEnabled,
            boolean chatEnabled,
            String model,
            String analyticsMeasurementId,
            List<LanguageOption> languages) {
    }

    public record LanguageOption(String code, String label, boolean rightToLeft) {
    }
}
