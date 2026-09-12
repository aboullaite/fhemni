package dev.maboullaite.fhemni.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;

class LoginSuccessHandlerTest {

    @Test
    void redirectsToTheValidatedCookieTargetAndClearsIt() throws Exception {
        LoginReturnTargetCookie returnTarget = new LoginReturnTargetCookie(false);
        MockHttpServletResponse cookieResponse = new MockHttpServletResponse();
        returnTarget.save(cookieResponse, "/admin");

        Cookie saved = cookieResponse.getCookie(LoginReturnTargetCookie.COOKIE_NAME);
        assertThat(saved).isNotNull();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(saved);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new LoginSuccessHandler(returnTarget).onAuthenticationSuccess(
                request,
                response,
                new TestingAuthenticationToken("user", "credential"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin");
        assertThat(response.getHeader("Set-Cookie"))
                .contains(LoginReturnTargetCookie.COOKIE_NAME + "=")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void acceptsOnlySafeLocalReturnPaths() {
        assertThat(LoginSuccessHandler.safeLocalPath("/analyses/123")).isTrue();
        assertThat(LoginSuccessHandler.safeLocalPath("/analyses/123?tab=claims")).isTrue();

        assertThat(LoginSuccessHandler.safeLocalPath("https://example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("//example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/\\example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/\tevil.example")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/safe" + (char) 0 + "evil")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/safe" + (char) 0x7f + "evil")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/safe\r\nLocation: https://example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/" + "a".repeat(1024))).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath(null)).isFalse();
    }
}
