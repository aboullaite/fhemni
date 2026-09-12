package dev.maboullaite.fhemni.identity;

import java.util.Map;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class ExternalIdentityMapper {

    public ExternalIdentityProfile fromOidc(String provider, OidcUser user) {
        return new ExternalIdentityProfile(
                provider,
                user.getSubject(),
                user.getPreferredUsername(),
                user.getGivenName(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getPicture());
    }

    public ExternalIdentityProfile fromOAuth2(String provider, OAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        if ("discord".equals(provider)) {
            String subject = string(attributes, "id");
            String username = string(attributes, "username");
            return new ExternalIdentityProfile(
                    provider, subject, username,
                    first(string(attributes, "global_name"), username),
                    string(attributes, "email"),
                    booleanValue(attributes, "verified"),
                    discordAvatar(subject, string(attributes, "avatar")));
        }
        return new ExternalIdentityProfile(
                provider,
                subject(provider, user),
                string(attributes, "login"),
                first(string(attributes, "name"), string(attributes, "login")),
                string(attributes, "email"),
                booleanValue(attributes, "email_verified"),
                first(string(attributes, "avatar_url"), string(attributes, "picture")));
    }

    private String subject(String provider, OAuth2User user) {
        return user.getName();
    }

    private static String discordAvatar(String subject, String avatar) {
        return subject == null || avatar == null
                ? null
                : "https://cdn.discordapp.com/avatars/" + subject + "/" + avatar + ".png?size=128";
    }

    private static String string(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        return value instanceof Boolean result && result;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
