package dev.maboullaite.fhemni.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;

import dev.maboullaite.fhemni.identity.MagicLinkEmailSender;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.auth.google.client-id=",
        "fhemni.auth.google.client-secret=",
        "fhemni.auth.discord.client-id=",
        "fhemni.auth.discord.client-secret=",
        "fhemni.auth.magic-link.enabled=true",
        "fhemni.auth.magic-link.base-url=https://fhemni.example",
        "fhemni.auth.magic-link.from=Fhemni <login@fhemni.example>",
        "fhemni.auth.magic-link.mailgun.api-key=test-key",
        "fhemni.auth.magic-link.mailgun.domain=fhemni.example",
        "spring.datasource.url=jdbc:h2:mem:magic-link-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class MagicLinkIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserAccountRepository users;

    @MockitoBean
    private MagicLinkEmailSender emailSender;

    @Test
    void sendsAndConsumesAOneTimeLoginLink() throws Exception {
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.magicLinkEnabled").value(true));

        mvc.perform(post("/api/auth/magic-link")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Reader@Example.com","returnTo":"/community"}
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(
                eq("Fhemni <login@fhemni.example>"),
                eq("reader@example.com"),
                eq("رابط الدخول لفهّمني · Fhemni.ma"),
                message.capture(),
                html.capture());
        String body = message.getValue();
        var matcher = TOKEN.matcher(body);
        assert matcher.find();
        String token = matcher.group(1);
        org.assertj.core.api.Assertions.assertThat(html.getValue())
                .contains("https://fhemni.ma/assets/brand/fhemni-logo.png")
                .contains("دخل لحسابك")
                .contains(token)
                .contains("15 دقيقة");

        var login = mvc.perform(get("/auth/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/community"))
                .andReturn();
        var sessionCookie = login.getResponse().getCookie("FHEMNI_SESSION");

        mvc.perform(get("/api/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.displayName").value("reader"));

        assert users.findByIdentity("magic-link", "reader@example.com").isEmpty();

        mvc.perform(get("/auth/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=magic-link"));
    }

    @Test
    void requiresCsrfAndRejectsInvalidEmail() throws Exception {
        mvc.perform(post("/api/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reader@example.com\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/magic-link")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/magic-link")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportsAnEmailLongerThanTheIdentitySubjectColumn() throws Exception {
        String email = "a".repeat(250) + "@example.com";
        mvc.perform(post("/api/auth/magic-link")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","returnTo":"/"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(
                eq("Fhemni <login@fhemni.example>"),
                eq(email),
                eq("رابط الدخول لفهّمني · Fhemni.ma"),
                message.capture(),
                html.capture());
        var matcher = TOKEN.matcher(message.getValue());
        assert matcher.find();

        mvc.perform(get("/auth/magic-link").param("token", matcher.group(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
