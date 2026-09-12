package dev.maboullaite.fhemni.identity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

@Component
public class OAuthProviderCatalog implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private final Map<String, ClientRegistration> registrations;
    private final List<ProviderOption> options;

    public OAuthProviderCatalog(AuthProperties properties) {
        Map<String, ClientRegistration> configured = new LinkedHashMap<>();
        if (properties.google().configured()) {
            configured.put("google", CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(properties.google().clientId())
                    .clientSecret(properties.google().clientSecret())
                    .build());
        }
        if (properties.discord().configured()) {
            configured.put("discord", ClientRegistration.withRegistrationId("discord")
                    .clientId(properties.discord().clientId())
                    .clientSecret(properties.discord().clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("identify", "email")
                    .authorizationUri("https://discord.com/oauth2/authorize")
                    .tokenUri("https://discord.com/api/oauth2/token")
                    .userInfoUri("https://discord.com/api/users/@me")
                    .userNameAttributeName("id")
                    .clientName("Discord")
                    .build());
        }
        registrations = Map.copyOf(configured);
        List<ProviderOption> providerOptions = new ArrayList<>();
        if (registrations.containsKey("google")) {
            providerOptions.add(new ProviderOption("google", "Google", "/oauth2/authorization/google"));
        }
        if (registrations.containsKey("discord")) {
            providerOptions.add(new ProviderOption("discord", "Discord", "/oauth2/authorization/discord"));
        }
        options = List.copyOf(providerOptions);
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }

    public List<ProviderOption> options() {
        return options;
    }

    public boolean configured() {
        return !registrations.isEmpty();
    }

    public record ProviderOption(String id, String label, String loginUrl) {
    }
}
