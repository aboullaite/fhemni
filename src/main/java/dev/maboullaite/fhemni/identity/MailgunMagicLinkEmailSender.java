package dev.maboullaite.fhemni.identity;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MailgunMagicLinkEmailSender implements MagicLinkEmailSender {

    private final AuthProperties.Mailgun properties;
    private final RestClient client;

    @Autowired
    public MailgunMagicLinkEmailSender(AuthProperties authProperties) {
        this(
                authProperties.magicLink().mailgun(),
                client(authProperties.magicLink().mailgun(), RestClient.builder()));
    }

    MailgunMagicLinkEmailSender(AuthProperties.Mailgun properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    private static RestClient client(AuthProperties.Mailgun properties, RestClient.Builder builder) {
        var http = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(properties.timeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void send(String from, String to, String subject, String text, String html) {
        if (!properties.configured()) {
            throw new IllegalStateException("Mailgun delivery is not configured");
        }
        var body = new MultipartBodyBuilder();
        body.part("from", from);
        body.part("to", to);
        body.part("subject", subject);
        body.part("text", text);
        body.part("html", html);

        client.post()
                .uri("/v3/{domain}/messages", properties.domain())
                .header(HttpHeaders.AUTHORIZATION, basicAuthorization())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .toBodilessEntity();
    }

    private String basicAuthorization() {
        return "Basic " + HttpHeaders.encodeBasicAuth("api", properties.apiKey(), StandardCharsets.UTF_8);
    }
}
