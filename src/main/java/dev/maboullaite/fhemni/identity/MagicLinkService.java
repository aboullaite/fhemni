package dev.maboullaite.fhemni.identity;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.identity.MagicLinkRepository.VerifiedLink;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MagicLinkService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String LOGO_URL = "https://fhemni.ma/assets/brand/fhemni-logo.png";

    private final AuthProperties properties;
    private final MagicLinkRepository links;
    private final UserAccountRepository users;
    private final MagicLinkEmailSender emailSender;

    public MagicLinkService(
            AuthProperties properties,
            MagicLinkRepository links,
            UserAccountRepository users,
            MagicLinkEmailSender emailSender) {
        this.properties = properties;
        this.links = links;
        this.users = users;
        this.emailSender = emailSender;
    }

    public boolean configured() {
        return properties.magicLink().configured();
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

    public boolean valid(String token) {
        return links.valid(token);
    }

    @Transactional
    public Optional<AuthenticatedLink> authenticate(String token) {
        Optional<VerifiedLink> verified = links.consume(token);
        if (verified.isEmpty()) {
            return Optional.empty();
        }
        String email = verified.get().email();
        String subject = hash(email);
        ExternalIdentityProfile profile = new ExternalIdentityProfile(
                "magic-link", subject, null, displayName(email), email, true, null);
        AppUser user = users.recordLogin(profile, properties.shouldBeAdmin(profile));
        return Optional.of(new AuthenticatedLink(subject, email, verified.get().returnTarget(), user));
    }

    private void sendEmail(String email, String token) {
        URI link = UriComponentsBuilder.fromUriString(properties.magicLink().baseUrl())
                .path("/auth/magic-link")
                .queryParam("token", token)
                .build()
                .toUri();
        String text = "Use this one-time link to sign in to Fhemni:\n\n" + link
                + "\n\nThis link expires in " + properties.magicLink().lifetime().toMinutes()
                + " minutes. If you did not request it, you can ignore this email.";
        String html = htmlEmail(link, properties.magicLink().lifetime().toMinutes());
        emailSender.send(
                properties.magicLink().from(),
                email,
                "رابط الدخول لفهّمني · Fhemni.ma",
                text,
                html);
    }

    private static String htmlEmail(URI link, long lifetimeMinutes) {
        String escapedLink = HtmlUtils.htmlEscape(link.toASCIIString());
        return """
                <!doctype html>
                <html lang="ar" dir="rtl">
                <body style="margin:0;padding:0;background:#f4f1e8;color:#173936;font-family:Arial,Tahoma,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f1e8;padding:28px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#fffdf7;border:1px solid #d8ded8;border-radius:18px;overflow:hidden;box-shadow:0 12px 34px rgba(24,57,54,.08);">
                          <tr>
                            <td style="padding:34px 34px 16px;text-align:center;">
                              <img src="%s" width="156" alt="Fhemni.ma · فهّمني" style="display:inline-block;width:156px;max-width:60%%;height:auto;border:0;">
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 34px 34px;text-align:center;">
                              <p style="margin:0 0 8px;color:#17766c;font-size:14px;font-weight:700;">Fhemni.ma</p>
                              <h1 style="margin:0 0 14px;color:#173936;font-size:28px;line-height:1.35;">كمّل الدخول لفهّمني</h1>
                              <p style="margin:0 auto 24px;max-width:410px;color:#627572;font-size:16px;line-height:1.8;">كليكي على الزر باش تدخل لحسابك وتسول فهّمني فالشات، تقترح وتصوّت.</p>
                              <a href="%s" style="display:inline-block;border-radius:10px;background:#176b63;color:#ffffff;padding:13px 28px;font-size:16px;font-weight:700;text-decoration:none;">دخل لحسابك</a>
                              <p style="margin:24px auto 0;max-width:430px;color:#70807d;font-size:13px;line-height:1.75;">هاد الرابط كيتستعمل مرة وحدة وكيصالي من بعد %d دقيقة. إلا ما طلبتيهش، تقدر تتجاهل هاد الإيميل.</p>
                              <div style="height:1px;background:#e1e6e1;margin:28px 0 20px;"></div>
                              <p style="margin:0 0 7px;color:#84918f;font-size:11px;line-height:1.6;">إلا ما خدمش الزر، نسخ هاد الرابط:</p>
                              <p dir="ltr" style="margin:0;word-break:break-all;color:#176b63;font-size:11px;line-height:1.55;"><a href="%s" style="color:#176b63;">%s</a></p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(LOGO_URL, escapedLink, lifetimeMinutes, escapedLink, escapedLink);
    }

    private static String normalizeEmail(String candidate) {
        String email = candidate == null ? "" : candidate.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        return email;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String displayName(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        if (localPart.isBlank()) {
            return "Fhemni user";
        }
        return localPart.length() <= 200 ? localPart : localPart.substring(0, 200);
    }

    public record AuthenticatedLink(String subject, String email, String returnTarget, AppUser user) {
    }
}
