package dev.maboullaite.fhemni.identity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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
        if (properties.github().configured()) {
            configured.put("github", CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(properties.github().clientId())
                    .clientSecret(properties.github().clientSecret())
                    .scope("read:user", "user:email")
                    .build());
        }
        registrations = Map.copyOf(configured);
        List<ProviderOption> providerOptions = new ArrayList<>();
        if (registrations.containsKey("google")) {
            providerOptions.add(new ProviderOption("google", "Google", "/oauth2/authorization/google"));
        }
        if (registrations.containsKey("github")) {
            providerOptions.add(new ProviderOption("github", "GitHub", "/oauth2/authorization/github"));
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
