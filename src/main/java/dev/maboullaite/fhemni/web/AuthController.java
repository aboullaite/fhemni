package dev.maboullaite.fhemni.web;

import java.util.List;

import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.identity.OAuthProviderCatalog;
import dev.maboullaite.fhemni.identity.OAuthProviderCatalog.ProviderOption;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUser;
    private final OAuthProviderCatalog providers;

    public AuthController(CurrentUserService currentUser, OAuthProviderCatalog providers) {
        this.currentUser = currentUser;
        this.providers = providers;
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        AppUser user = currentUser.find(authentication).orElse(null);
        UserResponse response = user == null
                ? null
                : new UserResponse(
                        user.id().toString(),
                        user.displayName(),
                        user.avatarUrl(),
                        user.role().name());
        return new SessionResponse(
                user != null,
                response,
                providers.options(),
                csrfToken.getHeaderName(),
                csrfToken.getToken());
    }

    public record SessionResponse(
            boolean authenticated,
            UserResponse user,
            List<ProviderOption> providers,
            String csrfHeader,
            String csrfToken) {
    }

    public record UserResponse(
            String id,
            String displayName,
            String avatarUrl,
            String role) {
    }
}
