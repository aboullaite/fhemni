package dev.maboullaite.fhemni.web;

import java.util.List;

import dev.maboullaite.fhemni.cost.AiUsageGuard;
import dev.maboullaite.fhemni.cost.AiUsageGuard.ChatQuota;
import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.identity.MagicLinkService;
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
    private final AiUsageGuard usageGuard;
    private final MagicLinkService magicLinks;

    public AuthController(
            CurrentUserService currentUser,
            OAuthProviderCatalog providers,
            AiUsageGuard usageGuard,
            MagicLinkService magicLinks) {
        this.currentUser = currentUser;
        this.providers = providers;
        this.usageGuard = usageGuard;
        this.magicLinks = magicLinks;
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
                magicLinks.configured(),
                csrfToken.getHeaderName(),
                csrfToken.getToken(),
                user == null ? null : usageGuard.chatQuota(user.id()));
    }

    public record SessionResponse(
            boolean authenticated,
            UserResponse user,
            List<ProviderOption> providers,
            boolean magicLinkEnabled,
            String csrfHeader,
            String csrfToken,
            ChatQuota chatQuota) {
    }

    public record UserResponse(
            String id,
            String displayName,
            String avatarUrl,
            String role) {
    }
}
