package dev.maboullaite.fhemni.identity;

public record ExternalIdentityProfile(
        String provider,
        String subject,
        String providerUsername,
        String displayName,
        String email,
        boolean emailVerified,
        String avatarUrl) {

    public ExternalIdentityProfile {
        provider = required(provider, "provider");
        subject = required(subject, "subject");
        displayName = firstName(fallback(displayName, providerUsername, "Fhemni user"));
        providerUsername = clean(providerUsername);
        email = clean(email);
        avatarUrl = clean(avatarUrl);
    }

    public String identityKey() {
        return provider + ":" + subject;
    }

    private static String required(String value, String field) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new IllegalArgumentException("External identity " + field + " is required");
        }
        return cleaned;
    }

    private static String fallback(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        throw new IllegalArgumentException("At least one display name is required");
    }

    private static String firstName(String value) {
        return value.split("\\s+", 2)[0];
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
