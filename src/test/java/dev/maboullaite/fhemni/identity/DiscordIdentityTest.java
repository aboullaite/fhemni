package dev.maboullaite.fhemni.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class DiscordIdentityTest {

    @Test
    void exposesDiscordInsteadOfGithubWhenConfigured() {
        AuthProperties properties = new AuthProperties(
                Set.of(),
                null,
                AuthProperties.Provider.empty(),
                new AuthProperties.Provider("discord-client", "discord-secret"),
                new AuthProperties.MagicLink(
                        false,
                        null,
                        null,
                        Duration.ofMinutes(15),
                        AuthProperties.Mailgun.empty()));

        OAuthProviderCatalog catalog = new OAuthProviderCatalog(properties);

        assertThat(catalog.options()).extracting(OAuthProviderCatalog.ProviderOption::id)
                .containsExactly("discord");
        assertThat(catalog.findByRegistrationId("discord").getScopes())
                .containsExactlyInAnyOrder("identify", "email");
        assertThat(catalog.findByRegistrationId("discord").getProviderDetails().getTokenUri())
                .isEqualTo("https://discord.com/api/v10/oauth2/token");
        assertThat(catalog.findByRegistrationId("discord").getProviderDetails().getUserInfoEndpoint().getUri())
                .isEqualTo("https://discord.com/api/v10/users/@me");
        assertThat(catalog.findByRegistrationId("github")).isNull();
    }

    @Test
    void mapsTheDiscordProfileAndVerifiedEmail() {
        var principal = new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "id", "1234",
                        "username", "med",
                        "global_name", "Mohammed Aboullaite",
                        "email", "med@example.com",
                        "verified", true,
                        "avatar", "avatar-hash"),
                "id");

        ExternalIdentityProfile profile = new ExternalIdentityMapper().fromOAuth2("discord", principal);

        assertThat(profile.provider()).isEqualTo("discord");
        assertThat(profile.subject()).isEqualTo("1234");
        assertThat(profile.displayName()).isEqualTo("Mohammed");
        assertThat(profile.email()).isEqualTo("med@example.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.avatarUrl())
                .isEqualTo("https://cdn.discordapp.com/avatars/1234/avatar-hash.png?size=128");
    }
}
