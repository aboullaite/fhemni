package dev.maboullaite.fhemni.identity;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fhemni.auth")
public record AuthProperties(
        Set<String> adminIdentities,
        String bootstrapAdminEmail,
        Provider google,
        Provider github) {

    public AuthProperties {
        adminIdentities = adminIdentities == null
                ? Set.of()
                : adminIdentities.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.strip().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        bootstrapAdminEmail = normalize(bootstrapAdminEmail);
        google = google == null ? Provider.empty() : google;
        github = github == null ? Provider.empty() : github;
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
}
