package dev.maboullaite.fhemni.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MailgunMagicLinkEmailSenderTest {

    @Test
    void sendsThroughTheRegionalMailgunApiWithoutExposingTheKeyInTheUrl() {
        var properties = new AuthProperties.Mailgun(
                "private-api-key",
                "mail.fhemni.ma",
                "https://api.eu.mailgun.net",
                Duration.ofSeconds(10));
        var builder = RestClient.builder().baseUrl(properties.baseUrl());
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new MailgunMagicLinkEmailSender(properties, builder.build());
        String authorization = "Basic "
                + HttpHeaders.encodeBasicAuth("api", "private-api-key", StandardCharsets.UTF_8);

        server.expect(once(), requestTo("https://api.eu.mailgun.net/v3/mail.fhemni.ma/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authorization))
                .andRespond(withSuccess("{\"id\":\"queued\"}", MediaType.APPLICATION_JSON));

        sender.send(
                "Fhemni <login@mail.fhemni.ma>",
                "reader@example.com",
                "Your Fhemni sign-in link",
                "Click the link",
                "<a href=\"https://fhemni.ma\">Sign in</a>");

        server.verify();
    }

    @Test
    void refusesToSendWhenTheApiKeyOrDomainIsMissing() {
        var properties = AuthProperties.Mailgun.empty();
        var sender = new MailgunMagicLinkEmailSender(properties, RestClient.create());

        assertThatThrownBy(() -> sender.send("from", "to", "subject", "text", "<p>html</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mailgun delivery is not configured");
    }
}
