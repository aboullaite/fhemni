package dev.maboullaite.fhemni.identity;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    public static final String RETURN_TO_SESSION_ATTRIBUTE = "fhemni.auth.return-to";

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        Object candidate = session == null ? null : session.getAttribute(RETURN_TO_SESSION_ATTRIBUTE);
        if (session != null) {
            session.removeAttribute(RETURN_TO_SESSION_ATTRIBUTE);
        }
        String destination = candidate instanceof String value && safeLocalPath(value) ? value : "/";
        response.sendRedirect(request.getContextPath() + destination);
    }

    public static boolean safeLocalPath(String value) {
        return value != null
                && value.startsWith("/")
                && !value.startsWith("//")
                && !value.contains("\\")
                && !value.contains("\r")
                && !value.contains("\n");
    }
}
