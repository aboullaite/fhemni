package dev.maboullaite.fhemni.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.LoginReturnTargetCookie;
import dev.maboullaite.fhemni.identity.MagicLinkRateLimiter;
import dev.maboullaite.fhemni.identity.MagicLinkService;
import dev.maboullaite.fhemni.identity.MagicLinkTokenCookie;
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

    private final MagicLinkService magicLinks;
    private final MagicLinkRateLimiter rateLimiter;
    private final MagicLinkTokenCookie tokenCookie;
    private final LoginReturnTargetCookie returnTargetCookie;
    private final HttpSessionSecurityContextRepository securityContexts =
            new HttpSessionSecurityContextRepository();

    public MagicLinkController(
            MagicLinkService magicLinks,
            MagicLinkRateLimiter rateLimiter,
            MagicLinkTokenCookie tokenCookie,
            LoginReturnTargetCookie returnTargetCookie) {
        this.magicLinks = magicLinks;
        this.rateLimiter = rateLimiter;
        this.tokenCookie = tokenCookie;
        this.returnTargetCookie = returnTargetCookie;
    }

    @PostMapping("/api/auth/magic-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(
            @RequestBody(required = false) MagicLinkRequest request,
            HttpServletRequest httpRequest) {
        if (!magicLinks.configured()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
            return;
        }
        try {
            magicLinks.send(
                    request == null ? null : request.email(),
                    request == null ? null : request.returnTo());
        } catch (IllegalArgumentException invalidEmail) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }
    }

    @GetMapping("/auth/magic-link")
    public void prepare(
            @RequestParam String token,
            HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (!magicLinks.valid(token)) {
            tokenCookie.clear(response);
            response.sendRedirect("/login?error=magic-link");
            return;
        }
        tokenCookie.save(response, token);
        response.sendRedirect("/login?confirm=magic-link");
    }

    @PostMapping("/auth/magic-link/confirm")
    public MagicLinkConfirmation confirm(
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        var token = tokenCookie.read(request);
        tokenCookie.clear(response);
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var authenticated = magicLinks.authenticate(token.get());
        if (authenticated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var link = authenticated.get();
        AppUser user = link.user();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        var principal = new DefaultOAuth2User(
                authorities,
                Map.of("sub", link.subject(), "email", link.email(), "email_verified", true),
                "sub");
        var authentication = new OAuth2AuthenticationToken(principal, authorities, "magic-link");
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        securityContexts.saveContext(context, request, response);
        returnTargetCookie.clear(response);
        return new MagicLinkConfirmation(link.returnTarget());
    }

    public record MagicLinkRequest(String email, String returnTo) {
    }

    public record MagicLinkConfirmation(String returnTo) {
    }
}
