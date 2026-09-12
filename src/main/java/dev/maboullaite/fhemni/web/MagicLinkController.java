package dev.maboullaite.fhemni.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.AuthProperties;
import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.LoginReturnTargetCookie;
import dev.maboullaite.fhemni.identity.MagicLinkService;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class MagicLinkController {

    private static final String PROVIDER = "magic-link";

    private final MagicLinkService magicLinks;
    private final UserAccountRepository users;
    private final AuthProperties properties;
    private final LoginReturnTargetCookie returnTargetCookie;
    private final HttpSessionSecurityContextRepository securityContexts =
            new HttpSessionSecurityContextRepository();

    public MagicLinkController(
            MagicLinkService magicLinks,
            UserAccountRepository users,
            AuthProperties properties,
            LoginReturnTargetCookie returnTargetCookie) {
        this.magicLinks = magicLinks;
        this.users = users;
        this.properties = properties;
        this.returnTargetCookie = returnTargetCookie;
    }

    @PostMapping("/api/auth/magic-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@RequestBody MagicLinkRequest request) {
        if (!magicLinks.configured()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            magicLinks.send(request.email(), request.returnTo());
        } catch (IllegalArgumentException invalidEmail) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }
    }

    @GetMapping("/auth/magic-link")
    public void verify(
            @RequestParam String token,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        var verified = magicLinks.consume(token);
        if (verified.isEmpty()) {
            response.sendRedirect("/login?error=magic-link");
            return;
        }

        String email = verified.get().email();
        ExternalIdentityProfile profile = new ExternalIdentityProfile(
                PROVIDER, email, email, displayName(email), email, true, null);
        AppUser user = users.recordLogin(profile, properties.shouldBeAdmin(profile));
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        var principal = new DefaultOAuth2User(
                authorities,
                Map.of("sub", email, "email", email, "email_verified", true),
                "sub");
        var authentication = new OAuth2AuthenticationToken(principal, authorities, PROVIDER);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        securityContexts.saveContext(context, request, response);
        returnTargetCookie.clear(response);
        response.sendRedirect(verified.get().returnTarget());
    }

    private static String displayName(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        return localPart.isBlank() ? "Fhemni user" : localPart;
    }

    public record MagicLinkRequest(String email, String returnTo) {
    }
}
