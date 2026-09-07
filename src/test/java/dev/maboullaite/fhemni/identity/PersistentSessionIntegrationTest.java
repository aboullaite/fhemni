package dev.maboullaite.fhemni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.auth.google.client-id=",
        "fhemni.auth.google.client-secret=",
        "fhemni.auth.github.client-id=",
        "fhemni.auth.github.client-secret=",
        "spring.datasource.url=jdbc:h2:mem:persistent-session-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class PersistentSessionIntegrationTest {

    @Autowired
    private JdbcIndexedSessionRepository jdbcSessions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void browserRequestsUseTheStableDatabaseBackedCookie() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("FHEMNI_SESSION=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM spring_session")
                .query(Integer.class)
                .single()).isOne();
    }

    @Test
    void storesSessionsInTheSharedDatabase() {
        SessionRepository<Session> sessions = sessionRepository();

        Session session = sessions.createSession();
        session.setAttribute("restart-marker", "still-signed-in");
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                googleSecurityContext());
        sessions.save(session);

        assertThat(jdbc.sql("SELECT COUNT(*) FROM spring_session WHERE session_id = :sessionId")
                .param("sessionId", session.getId())
                .query(Integer.class)
                .single()).isOne();

        Session restored = sessions.findById(session.getId());
        assertThat(restored).isNotNull();
        assertThat(restored.<String>getAttribute("restart-marker")).isEqualTo("still-signed-in");
        SecurityContext securityContext = restored.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext.getAuthentication()).isInstanceOf(OAuth2AuthenticationToken.class);
        assertThat(securityContext.getAuthentication().getName()).isEqualTo("restart-user");

        sessions.deleteById(session.getId());
    }

    private SecurityContext googleSecurityContext() {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "test-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of("sub", "restart-user", "email", "owner@example.com"));
        DefaultOidcUser principal = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                idToken);
        var authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private SessionRepository<Session> sessionRepository() {
        return (SessionRepository) jdbcSessions;
    }
}
