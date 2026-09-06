package dev.maboullaite.fhemni.identity;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountRepository users;
    private final AuthProperties properties;

    public CurrentUserService(UserAccountRepository users, AuthProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    public Optional<AppUser> find(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth) || !oauth.isAuthenticated()) {
            return Optional.empty();
        }
        String provider = oauth.getAuthorizedClientRegistrationId();
        String subject = oauth.getName();
        Optional<AppUser> current = users.findByIdentity(provider, subject);
        if (current.isEmpty()) {
            return current;
        }

        AppUser user = current.get();
        boolean configuredAdministrator = properties.isAdminIdentity(provider, subject)
                || properties.isBootstrapAdminEmail(user.email())
                        && users.identityHasVerifiedEmail(provider, subject, user.email());
        if (configuredAdministrator && user.role() != UserRole.ADMIN) {
            return users.setRole(provider, subject, UserRole.ADMIN);
        }
        if (!configuredAdministrator && user.role() == UserRole.ADMIN) {
            return users.setRole(provider, subject, UserRole.USER);
        }
        return current;
    }

    public boolean isAdministrator(Authentication authentication) {
        Optional<AppUser> persisted = find(authentication);
        if (persisted.isPresent()) {
            return persisted.get().role() == UserRole.ADMIN;
        }
        return !(authentication instanceof OAuth2AuthenticationToken)
                && authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
