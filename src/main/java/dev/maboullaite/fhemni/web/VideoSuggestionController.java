package dev.maboullaite.fhemni.web;

import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.catalog.RankedVideoSuggestion;
import dev.maboullaite.fhemni.catalog.VideoSuggestionService;
import dev.maboullaite.fhemni.catalog.VideoSuggestionService.SuggestionOutcome;
import dev.maboullaite.fhemni.catalog.VideoSuggestionService.SuggestionResult;
import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggestions")
public class VideoSuggestionController {

    private static final int MAX_BOARD_SIZE = 100;

    private final VideoSuggestionService suggestions;
    private final CurrentUserService currentUser;
    private final SuggestionRateLimiter rateLimiter;

    public VideoSuggestionController(
            VideoSuggestionService suggestions,
            CurrentUserService currentUser,
            SuggestionRateLimiter rateLimiter) {
        this.suggestions = suggestions;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ResponseEntity<List<RankedVideoSuggestion>> list(Authentication authentication) {
        UUID viewerId = currentUser.find(authentication).map(AppUser::id).orElse(null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(suggestions.rankedFor(viewerId, MAX_BOARD_SIZE));
    }

    @PostMapping
    public ResponseEntity<SuggestionResult> suggest(
            @RequestBody SuggestionRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        AppUser user = requireUser(authentication);
        rateLimiter.check(httpRequest.getRemoteAddr());
        SuggestionResult result = suggestions.suggest(request.youtubeUrl(), user.id());
        HttpStatus status = result.outcome() == SuggestionOutcome.CREATED
                || result.outcome() == SuggestionOutcome.HELD_FOR_REVIEW
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping("/{id}/votes")
    public ResponseEntity<Void> vote(
            @PathVariable UUID id,
            @RequestBody VoteRequest request,
            Authentication authentication) {
        AppUser user = requireUser(authentication);
        suggestions.vote(id, user.id(), request.value());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private AppUser requireUser(Authentication authentication) {
        return currentUser.find(authentication)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Sign in to continue."));
    }

    public record SuggestionRequest(String youtubeUrl) {
    }

    public record VoteRequest(int value) {
    }
}
