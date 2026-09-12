package dev.maboullaite.fhemni.identity;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fhemni.auth")
public record AuthProperties(
        Set<String> adminIdentities,
        String bootstrapAdminEmail,
        Provider google,
        Provider discord,
        MagicLink magicLink) {

    public AuthProperties {
        adminIdentities = adminIdentities == null
                ? Set.of()
                : adminIdentities.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.strip().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        bootstrapAdminEmail = normalize(bootstrapAdminEmail);
        google = google == null ? Provider.empty() : google;
        discord = discord == null ? Provider.empty() : discord;
        magicLink = magicLink == null ? MagicLink.empty() : magicLink;
    }

    public boolean shouldBeAdmin(ExternalIdentityProfile profile) {
        if (isAdminIdentity(profile.provider(), profile.subject())) {
            return true;
        }
        return profile.emailVerified()
                && isBootstrapAdminEmail(profile.email());
    }

    public boolean isAdminIdentity(String provider, String subject) {
        if (provider == null || subject == null) {
            return false;
        }
        return adminIdentities.contains((provider + ":" + subject).toLowerCase(Locale.ROOT));
    }

    public boolean isBootstrapAdminEmail(String email) {
        return email != null
                && bootstrapAdminEmail != null
                && bootstrapAdminEmail.equals(email.toLowerCase(Locale.ROOT));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    public record Provider(String clientId, String clientSecret) {

        public Provider {
            clientId = clean(clientId);
            clientSecret = clean(clientSecret);
        }

        public static Provider empty() {
            return new Provider(null, null);
        }

        public boolean configured() {
            return clientId != null && clientSecret != null;
        }

        private static String clean(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }

    public record MagicLink(
            boolean enabled,
            String baseUrl,
            String from,
            Duration lifetime,
            Mailgun mailgun) {

        public MagicLink {
            baseUrl = clean(baseUrl);
            from = clean(from);
            lifetime = lifetime == null || lifetime.isNegative() || lifetime.isZero()
                    ? Duration.ofMinutes(15)
                    : lifetime;
            mailgun = mailgun == null ? Mailgun.empty() : mailgun;
        }

        public static MagicLink empty() {
            return new MagicLink(false, null, null, Duration.ofMinutes(15), Mailgun.empty());
        }

        public boolean configured() {
            return enabled && baseUrl != null && from != null && mailgun.configured();
        }

        private static String clean(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }

    public record Mailgun(String apiKey, String domain, String baseUrl, Duration timeout) {

        public Mailgun {
            apiKey = clean(apiKey);
            domain = clean(domain);
            baseUrl = clean(baseUrl);
            baseUrl = baseUrl == null ? "https://api.eu.mailgun.net" : baseUrl;
            timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                    ? Duration.ofSeconds(10)
                    : timeout;
        }

        public static Mailgun empty() {
            return new Mailgun(null, null, "https://api.eu.mailgun.net", Duration.ofSeconds(10));
        }

        public boolean configured() {
            return apiKey != null && domain != null && baseUrl != null;
        }

        private static String clean(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }
}
