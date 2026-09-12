package dev.maboullaite.fhemni.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import dev.maboullaite.fhemni.identity.MagicLinkEmailSender;
import dev.maboullaite.fhemni.identity.MagicLinkRepository;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
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

    @Autowired
    private MagicLinkRepository links;

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

        var prepared = mvc.perform(get("/auth/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/login?confirm=magic-link"))
                .andReturn();
        var tokenCookie = prepared.getResponse().getCookie("FHEMNI_MAGIC_LINK");
        org.assertj.core.api.Assertions.assertThat(tokenCookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(tokenCookie.isHttpOnly()).isTrue();
        org.assertj.core.api.Assertions.assertThat(tokenCookie.getSecure()).isTrue();

        mvc.perform(get("/auth/magic-link/preview").cookie(tokenCookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.maskedEmail").value("r***r@example.com"));

        mvc.perform(get("/auth/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?confirm=magic-link"));

        var login = mvc.perform(post("/auth/magic-link/confirm")
                        .with(csrf())
                        .cookie(tokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnTo").value("/community"))
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
        mvc.perform(get("/auth/magic-link")
                        .param("token", token)
                        .param("lang", "fr"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=magic-link&lang=fr"));

        mvc.perform(post("/auth/magic-link/confirm")
                        .with(csrf())
                        .cookie(tokenCookie))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/auth/magic-link/preview").cookie(tokenCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmationPreviewRequiresThePreparedHttpOnlyCookie() throws Exception {
        mvc.perform(get("/auth/magic-link/preview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void localizesTheWholeEmailFromTheSelectedLoginLanguage() throws Exception {
        mvc.perform(post("/api/auth/magic-link")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"english@example.com","returnTo":"/","locale":"en-US"}
                                """))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/magic-link")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"french@example.com","returnTo":"/","locale":"fr"}
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(2)).send(
                eq("Fhemni <login@fhemni.example>"),
                recipient.capture(),
                subject.capture(),
                text.capture(),
                html.capture());

        org.assertj.core.api.Assertions.assertThat(recipient.getAllValues())
                .containsExactly("english@example.com", "french@example.com");
        org.assertj.core.api.Assertions.assertThat(subject.getAllValues())
                .containsExactly("Your Fhemni.ma sign-in link", "Votre lien de connexion Fhemni.ma");
        org.assertj.core.api.Assertions.assertThat(text.getAllValues().get(0))
                .contains("Use this one-time link", "expires in 15 minutes", "&lang=en");
        org.assertj.core.api.Assertions.assertThat(html.getAllValues().get(0))
                .contains(
                        "<html lang=\"en\" dir=\"ltr\">",
                        "Continue to Fhemni",
                        ">Sign in</a>",
                        "&amp;lang=en");
        org.assertj.core.api.Assertions.assertThat(text.getAllValues().get(1))
                .contains("Utilisez ce lien à usage unique", "expire dans 15 minutes", "&lang=fr");
        org.assertj.core.api.Assertions.assertThat(html.getAllValues().get(1))
                .contains(
                        "<html lang=\"fr\" dir=\"ltr\">",
                        "Continuez vers Fhemni",
                        ">Se connecter</a>",
                        "&amp;lang=fr");

        var englishToken = TOKEN.matcher(text.getAllValues().get(0));
        assert englishToken.find();
        mvc.perform(get("/auth/magic-link")
                        .param("token", englishToken.group(1))
                        .param("lang", "en"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?confirm=magic-link&lang=en"));
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

        var prepared = mvc.perform(get("/auth/magic-link").param("token", matcher.group(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?confirm=magic-link"))
                .andReturn();
        mvc.perform(post("/auth/magic-link/confirm")
                        .with(csrf())
                        .cookie(prepared.getResponse().getCookie("FHEMNI_MAGIC_LINK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnTo").value("/"));
    }

    @Test
    void keepsTheTokenValidWhenMailgunTimesOutAfterReceivingTheRequest() throws Exception {
        AtomicReference<String> message = new AtomicReference<>();
        doAnswer(invocation -> {
            message.set(invocation.getArgument(3));
            throw new ResourceAccessException("timed out waiting for Mailgun");
        }).when(emailSender).send(
                eq("Fhemni <login@fhemni.example>"),
                eq("timeout@example.com"),
                eq("رابط الدخول لفهّمني · Fhemni.ma"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> mvc.perform(post("/api/auth/magic-link")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"timeout@example.com\"}")))
                .hasRootCauseInstanceOf(ResourceAccessException.class);

        var matcher = TOKEN.matcher(message.get());
        assert matcher.find();
        mvc.perform(get("/auth/magic-link").param("token", matcher.group(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?confirm=magic-link"));
    }

    @Test
    void enforcesThePerEmailLimitAcrossConcurrentRequests() throws Exception {
        int requestCount = 12;
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<Optional<String>>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return links.create(
                            "concurrent@example.com",
                            "/",
                            Instant.now().plusSeconds(900));
                }));
            }
            ready.await();
            start.countDown();
            long created = 0;
            for (var future : futures) {
                if (future.get().isPresent()) {
                    created++;
                }
            }
            org.assertj.core.api.Assertions.assertThat(created).isEqualTo(5);
        }
    }
}
