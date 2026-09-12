package dev.maboullaite.fhemni.identity;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MagicLinkService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AuthProperties properties;
    private final MagicLinkRepository links;
    private final ObjectProvider<JavaMailSender> mailSender;

    public MagicLinkService(
            AuthProperties properties,
            MagicLinkRepository links,
            ObjectProvider<JavaMailSender> mailSender) {
        this.properties = properties;
        this.links = links;
        this.mailSender = mailSender;
    }

    public boolean configured() {
        return properties.magicLink().configured() && mailSender.getIfAvailable() != null;
    }

    public void send(String candidate, String requestedReturnTarget) {
        if (!configured()) {
            throw new IllegalStateException("Magic-link sign-in is not configured");
        }
        String email = normalizeEmail(candidate);
        String returnTarget = LoginSuccessHandler.safeLocalPath(requestedReturnTarget)
                ? requestedReturnTarget
                : "/";
        links.create(email, returnTarget, Instant.now().plus(properties.magicLink().lifetime()))
                .ifPresent(token -> sendEmail(email, token));
    }

    public Optional<MagicLinkRepository.VerifiedLink> consume(String token) {
        return links.consume(token);
    }

    private void sendEmail(String email, String token) {
        URI link = UriComponentsBuilder.fromUriString(properties.magicLink().baseUrl())
                .path("/auth/magic-link")
                .queryParam("token", token)
                .build()
                .toUri();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.magicLink().from());
        message.setTo(email);
        message.setSubject("Your Fhemni sign-in link");
        message.setText("Use this one-time link to sign in to Fhemni:\n\n" + link
                + "\n\nThis link expires in " + properties.magicLink().lifetime().toMinutes()
                + " minutes. If you did not request it, you can ignore this email.");
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Mail delivery is not configured");
        }
        sender.send(message);
    }

    private static String normalizeEmail(String candidate) {
        String email = candidate == null ? "" : candidate.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        return email;
    }
}
