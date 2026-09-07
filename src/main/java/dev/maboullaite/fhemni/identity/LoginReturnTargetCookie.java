package dev.maboullaite.fhemni.identity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class LoginReturnTargetCookie {

    static final String COOKIE_NAME = "FHEMNI_LOGIN_RETURN";
    private static final Duration LIFETIME = Duration.ofMinutes(10);

    private final boolean secure;

    public LoginReturnTargetCookie(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public void save(HttpServletResponse response, String returnTo) {
        if (!LoginSuccessHandler.safeLocalPath(returnTo)) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(encode(returnTo), LIFETIME).toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }
            String candidate = decode(cookie.getValue());
            if (LoginSuccessHandler.safeLocalPath(candidate)) {
                return Optional.of(candidate);
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
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidCookie) {
            return null;
        }
    }
}
