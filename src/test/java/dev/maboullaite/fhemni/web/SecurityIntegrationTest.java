package dev.maboullaite.fhemni.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;

import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.auth.google.client-id=",
        "fhemni.auth.google.client-secret=",
        "fhemni.auth.github.client-id=",
        "fhemni.auth.github.client-secret=",
        "fhemni.auth.bootstrap-admin-email=owner@example.com",
        "spring.datasource.url=jdbc:h2:mem:security-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void publishesAnonymousSessionAndCsrfToken() throws Exception {
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.providers").isEmpty())
                .andExpect(jsonPath("$.csrfHeader").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());

        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisEnabled").value(false))
                .andExpect(jsonPath("$.chatEnabled").value(false))
                .andExpect(jsonPath("$.analyticsMeasurementId").value(""));
    }

    @Test
    void exposesOnlyADataFreeHealthCheckAndSecurityHeaders() throws Exception {
        mvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    @Test
    void servesThePublicLocalizationShell() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-site-language")))
                .andExpect(content().string(containsString("/css/dist.css")))
                .andExpect(content().string(containsString("/js/analytics.js")))
                .andExpect(content().string(containsString("/js/i18n.js")));

        mvc.perform(get("/js/i18n.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("landing.headline")))
                .andExpect(content().string(containsString("'landing.promiseAsk': 'Jump to the moment'")))
                .andExpect(content().string(containsString("ar: {")));

        mvc.perform(get("/css/dist.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TIDO Arabic")))
                .andExpect(content().string(containsString(".card")))
                .andExpect(content().string(containsString(".chat-start")))
                .andExpect(content().string(containsString(".public-header")));

    }

    @Test
    void rejectsAnonymousChatAtTheServerBoundary() throws Exception {
        mvc.perform(post("/api/analyses/00000000-0000-0000-0000-000000000000/questions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Hello\",\"mode\":\"VIDEO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsUnpublishedAnalysesPrivateFromOrdinaryUsers() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "ordinary-user", null, "Ordinary Member", null, false, null), false);
        mvc.perform(post("/api/analyses/00000000-0000-0000-0000-000000000000/questions")
                        .with(oauth2Login().attributes(attributes -> attributes.put("sub", "ordinary-user")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Hello\",\"mode\":\"VIDEO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsUnpublishedAnalysesPrivateFromPremiumUsers() throws Exception {
        var user = users.recordLogin(new ExternalIdentityProfile(
                "test", "premium-user", null, "Premium Member", null, false, null), false);
        jdbc.sql("UPDATE app_users SET role = 'PREMIUM' WHERE id = :id")
                .param("id", user.id())
                .update();

        mvc.perform(post("/api/analyses/00000000-0000-0000-0000-000000000000/questions")
                        .with(oauth2Login().attributes(attributes -> attributes.put("sub", "premium-user")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Hello\",\"mode\":\"VIDEO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesThePublicAnalysisShellButHidesUnpublishedData() throws Exception {
        String id = "00000000-0000-0000-0000-000000000000";

        mvc.perform(get("/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/analysis.html"));
        mvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/{id}", id).with(user("member").roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/{id}", id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnonymousPaidAnalysisCreation() throws Exception {
        mvc.perform(post("/api/analyses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"youtubeUrl\":\"https://youtu.be/n5B3boj2MFM\",\"language\":\"ar\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redirectsAnonymousAdminRequestsToLogin() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?continue=/admin"));

        mvc.perform(get("/admin.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?continue=/admin"));
    }

    @Test
    void rejectsOrdinaryUsersFromAdministration() throws Exception {
        mvc.perform(get("/admin").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/admin.html").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void servesAdministrationToAdministrators() throws Exception {
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin.html"));

        mvc.perform(get("/admin.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"catalogImportForm\"")));
    }

    @Test
    void usesThePersistedAdminRoleWhenTheBrowserAuthorityIsStale() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "persisted-admin", null, "Mohammed Admin", "owner@example.com", true, null), true);

        mvc.perform(get("/admin")
                        .with(oauth2Login()
                                .attributes(attributes -> attributes.put("sub", "persisted-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin.html"));
    }

    @Test
    void reconcilesAnExistingVerifiedBootstrapOwnerWithoutAnotherLogin() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "stale-owner", null, "Mohammed", "owner@example.com", true, null), false);

        mvc.perform(get("/admin")
                        .with(oauth2Login()
                                .attributes(attributes -> attributes.put("sub", "stale-owner"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin.html"));

        mvc.perform(get("/api/auth/session")
                        .with(oauth2Login()
                                .attributes(attributes -> attributes.put("sub", "stale-owner"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void doesNotPromoteAnUnverifiedBootstrapEmail() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "unverified-owner", null, "Not the owner", "owner@example.com", false, null), false);

        mvc.perform(get("/admin")
                        .with(oauth2Login()
                                .attributes(attributes -> attributes.put("sub", "unverified-owner"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void persistedUserRoleOverridesAStaleAdminAuthority() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "persisted-user", null, "Ordinary Member", null, false, null), false);

        mvc.perform(get("/admin")
                        .with(oauth2Login()
                                .attributes(attributes -> attributes.put("sub", "persisted-user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokesAStoredAdminThatIsNoLongerConfigured() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "former-admin", null, "Former Admin", "former@example.com", true, null), true);

        mvc.perform(get("/admin")
                        .with(oauth2Login()
                                .attributes(attributes -> attributes.put("sub", "former-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        assertThat(users.findByIdentity("test", "former-admin").orElseThrow().role())
                .isEqualTo(dev.maboullaite.fhemni.identity.UserRole.USER);
    }
}
