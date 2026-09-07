package dev.maboullaite.fhemni.identity;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginReturnTargetCookie returnTarget;

    public LoginSuccessHandler(LoginReturnTargetCookie returnTarget) {
        this.returnTarget = returnTarget;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        String destination = returnTarget.read(request).orElse("/");
        returnTarget.clear(response);
        response.sendRedirect(request.getContextPath() + destination);
    }

    public static boolean safeLocalPath(String value) {
        return value != null
                && value.length() <= 1024
                && value.startsWith("/")
                && !value.startsWith("//")
                && !value.contains("\\")
                && !value.contains("\r")
                && !value.contains("\n");
    }
}
