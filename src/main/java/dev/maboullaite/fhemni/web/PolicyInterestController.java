package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.util.List;

import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.programme.PolicyInterestService;
import dev.maboullaite.fhemni.programme.PolicyTopic;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PolicyInterestController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final PolicyInterestService interests;
    private final CurrentUserService currentUser;

    public PolicyInterestController(PolicyInterestService interests, CurrentUserService currentUser) {
        this.interests = interests;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/catalog/policy-topics")
    public ResponseEntity<TopicCatalogResponse> topics() {
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .body(new TopicCatalogResponse(PolicyInterestService.MAX_SELECTED_TOPICS, interests.topics()));
    }

    @GetMapping("/api/account/policy-topics")
    public ResponseEntity<PreferenceResponse> preferences(Authentication authentication) {
        AppUser user = requireUser(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PreferenceResponse(
                        PolicyInterestService.MAX_SELECTED_TOPICS,
                        interests.preferences(user.id())));
    }

    @PutMapping("/api/account/policy-topics")
    public ResponseEntity<PreferenceResponse> replacePreferences(
            Authentication authentication,
            @RequestBody PreferenceRequest request) {
        AppUser user = requireUser(authentication);
        List<String> selected = interests.replacePreferences(
                user.id(), request == null ? List.of() : request.topicCodes());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PreferenceResponse(PolicyInterestService.MAX_SELECTED_TOPICS, selected));
    }

    private AppUser requireUser(Authentication authentication) {
        return currentUser.find(authentication).orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException("Sign in to synchronize policy interests."));
    }

    public record TopicCatalogResponse(int maxSelections, List<PolicyTopic> topics) {
    }

    public record PreferenceRequest(List<String> topicCodes) {
    }

    public record PreferenceResponse(int maxSelections, List<String> topicCodes) {
    }
}
