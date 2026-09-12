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

    public void send(String candidate, String requestedReturnTarget, String requestedLocale) {
        if (!configured()) {
            throw new IllegalStateException("Magic-link sign-in is not configured");
        }
        String email = normalizeEmail(candidate);
        String returnTarget = LoginSuccessHandler.safeLocalPath(requestedReturnTarget)
                ? requestedReturnTarget
                : "/";
        links.create(email, returnTarget, Instant.now().plus(properties.magicLink().lifetime()))
                .ifPresent(token -> sendEmail(email, token, requestedLocale));
    }

    public boolean valid(String token) {
        return links.valid(token);
    }

    public Optional<MagicLinkPreview> preview(String token) {
        return links.findValid(token)
                .map(link -> new MagicLinkPreview(maskEmail(link.email())));
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

    private void sendEmail(String email, String token, String requestedLocale) {
        long lifetimeMinutes = properties.magicLink().lifetime().toMinutes();
        EmailCopy copy = emailCopy(requestedLocale, lifetimeMinutes);
        URI link = UriComponentsBuilder.fromUriString(properties.magicLink().baseUrl())
                .path("/auth/magic-link")
                .queryParam("token", token)
                .queryParam("lang", copy.language())
                .build()
                .toUri();
        String text = copy.textIntroduction() + "\n\n" + link + "\n\n" + copy.textExpiry();
        String html = htmlEmail(link, copy);
        emailSender.send(
                properties.magicLink().from(),
                email,
                copy.subject(),
                text,
                html);
    }

    private static String htmlEmail(URI link, EmailCopy copy) {
        String escapedLink = HtmlUtils.htmlEscape(link.toASCIIString());
        return """
                <!doctype html>
                <html lang="%s" dir="%s">
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
                              <h1 style="margin:0 0 14px;color:#173936;font-size:28px;line-height:1.35;">%s</h1>
                              <p style="margin:0 auto 24px;max-width:410px;color:#627572;font-size:16px;line-height:1.8;">%s</p>
                              <a href="%s" style="display:inline-block;border-radius:10px;background:#176b63;color:#ffffff;padding:13px 28px;font-size:16px;font-weight:700;text-decoration:none;">%s</a>
                              <p style="margin:24px auto 0;max-width:430px;color:#70807d;font-size:13px;line-height:1.75;">%s</p>
                              <div style="height:1px;background:#e1e6e1;margin:28px 0 20px;"></div>
                              <p style="margin:0 0 7px;color:#84918f;font-size:11px;line-height:1.6;">%s</p>
                              <p dir="ltr" style="margin:0;word-break:break-all;color:#176b63;font-size:11px;line-height:1.55;"><a href="%s" style="color:#176b63;">%s</a></p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                        copy.language(),
                        copy.direction(),
                        LOGO_URL,
                        copy.heading(),
                        copy.introduction(),
                        escapedLink,
                        copy.action(),
                        copy.expiry(),
                        copy.fallback(),
                        escapedLink,
                        escapedLink);
    }

    private static EmailCopy emailCopy(String requestedLocale, long lifetimeMinutes) {
        String locale = normalizeLocale(requestedLocale);
        return switch (locale) {
            case "en" -> new EmailCopy(
                    "en",
                    "ltr",
                    "Your Fhemni.ma sign-in link",
                    "Continue to Fhemni",
                    "Use the button to sign in, ask Fhemni in chat, suggest, and vote.",
                    "Sign in",
                    "This link can only be used once and expires in " + lifetimeMinutes
                            + " minutes. If you did not request it, you can ignore this email.",
                    "If the button does not work, copy this link:",
                    "Use this one-time link to sign in to Fhemni:",
                    "This link can only be used once and expires in " + lifetimeMinutes
                            + " minutes. If you did not request it, you can ignore this email.");
            case "fr" -> new EmailCopy(
                    "fr",
                    "ltr",
                    "Votre lien de connexion Fhemni.ma",
                    "Continuez vers Fhemni",
                    "Utilisez le bouton pour vous connecter, interroger Fhemni dans le chat, proposer et voter.",
                    "Se connecter",
                    "Ce lien est à usage unique et expire dans " + lifetimeMinutes
                            + " minutes. Si vous ne l’avez pas demandé, vous pouvez ignorer cet e-mail.",
                    "Si le bouton ne fonctionne pas, copiez ce lien :",
                    "Utilisez ce lien à usage unique pour vous connecter à Fhemni :",
                    "Ce lien est à usage unique et expire dans " + lifetimeMinutes
                            + " minutes. Si vous ne l’avez pas demandé, vous pouvez ignorer cet e-mail.");
            default -> new EmailCopy(
                    "ar",
                    "rtl",
                    "رابط الدخول لفهّمني · Fhemni.ma",
                    "كمّل الدخول لفهّمني",
                    "كليكي على الزر باش تدخل لحسابك وتسول فهّمني فالشات، تقترح وتصوّت.",
                    "دخل لحسابك",
                    "هاد الرابط كيتستعمل مرة وحدة وكيصالي من بعد " + lifetimeMinutes
                            + " دقيقة. إلا ما طلبتيهش، تقدر تتجاهل هاد الإيميل.",
                    "إلا ما خدمش الزر، نسخ هاد الرابط:",
                    "استعمل هاد الرابط اللي كيتستعمل مرة وحدة باش تدخل لفهّمني:",
                    "هاد الرابط كيتستعمل مرة وحدة وكيصالي من بعد " + lifetimeMinutes
                            + " دقيقة. إلا ما طلبتيهش، تقدر تتجاهل هاد الإيميل.");
        };
    }

    private static String normalizeLocale(String candidate) {
        String locale = candidate == null ? "" : candidate.strip().toLowerCase(Locale.ROOT);
        if (locale.equals("en") || locale.startsWith("en-")) {
            return "en";
        }
        if (locale.equals("fr") || locale.startsWith("fr-")) {
            return "fr";
        }
        return "ar";
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

    private static String maskEmail(String email) {
        int separator = email.lastIndexOf('@');
        String localPart = email.substring(0, separator);
        String domain = email.substring(separator);
        if (localPart.length() == 1) {
            return localPart + "***" + domain;
        }
        return localPart.charAt(0) + "***" + localPart.substring(localPart.length() - 1) + domain;
    }

    public record MagicLinkPreview(String maskedEmail) {
    }

    public record AuthenticatedLink(String subject, String email, String returnTarget, AppUser user) {
    }

    private record EmailCopy(
            String language,
            String direction,
            String subject,
            String heading,
            String introduction,
            String action,
            String expiry,
            String fallback,
            String textIntroduction,
            String textExpiry) {
    }
}
