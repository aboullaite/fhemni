package dev.maboullaite.fhemni.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
        "spring.datasource.url=jdbc:h2:mem:magic-link-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class MagicLinkIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JavaMailSender mailSender;

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

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        String body = message.getValue().getText();
        var matcher = TOKEN.matcher(body);
        assert matcher.find();
        String token = matcher.group(1);

        var login = mvc.perform(get("/auth/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community"))
                .andReturn();
        var sessionCookie = login.getResponse().getCookie("FHEMNI_SESSION");

        mvc.perform(get("/api/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.displayName").value("reader"));

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
    }
}
