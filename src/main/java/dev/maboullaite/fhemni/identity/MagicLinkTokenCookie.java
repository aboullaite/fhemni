package dev.maboullaite.fhemni.identity;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class MagicLinkTokenCookie {

    static final String COOKIE_NAME = "FHEMNI_MAGIC_LINK";

    private final AuthProperties properties;
    private final boolean secure;

    public MagicLinkTokenCookie(AuthProperties properties) {
        this.properties = properties;
        this.secure = properties.magicLink().baseUrl() != null
                && properties.magicLink().baseUrl().startsWith("https://");
    }

    public void save(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(token, properties.magicLink().lifetime()).toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())
                    && cookie.getValue() != null
                    && cookie.getValue().length() <= 256) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/auth/magic-link")
                .maxAge(maxAge)
                .build();
    }
}
