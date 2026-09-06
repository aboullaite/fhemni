package dev.maboullaite.fhemni.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginSuccessHandlerTest {

    @Test
    void acceptsOnlySafeLocalReturnPaths() {
        assertThat(LoginSuccessHandler.safeLocalPath("/analyses/123")).isTrue();
        assertThat(LoginSuccessHandler.safeLocalPath("/analyses/123?tab=claims")).isTrue();

        assertThat(LoginSuccessHandler.safeLocalPath("https://example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("//example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/\\example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath("/safe\r\nLocation: https://example.com")).isFalse();
        assertThat(LoginSuccessHandler.safeLocalPath(null)).isFalse();
    }
}
