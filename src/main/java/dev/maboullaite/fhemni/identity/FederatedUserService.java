package dev.maboullaite.fhemni.identity;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class FederatedUserService {

    private final UserAccountRepository users;
    private final ExternalIdentityMapper mapper;
    private final AuthProperties properties;

    public FederatedUserService(
            UserAccountRepository users,
            ExternalIdentityMapper mapper,
            AuthProperties properties) {
        this.users = users;
        this.mapper = mapper;
        this.properties = properties;
    }

    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User principal = delegate.loadUser(request);
            String provider = request.getClientRegistration().getRegistrationId();
            ExternalIdentityProfile profile = mapper.fromOAuth2(provider, principal);
            AppUser user = users.recordLogin(profile, properties.shouldBeAdmin(profile));
            return new DefaultOAuth2User(
                    authorities(principal.getAuthorities(), user.role()),
                    principal.getAttributes(),
                    request.getClientRegistration().getProviderDetails()
                            .getUserInfoEndpoint().getUserNameAttributeName());
        };
    }

    public OAuth2UserService<OidcUserRequest, OidcUser> oidc() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser principal = delegate.loadUser(request);
            String provider = request.getClientRegistration().getRegistrationId();
            ExternalIdentityProfile profile = mapper.fromOidc(provider, principal);
            AppUser user = users.recordLogin(profile, properties.shouldBeAdmin(profile));
            return new DefaultOidcUser(
                    authorities(principal.getAuthorities(), user.role()),
                    principal.getIdToken(),
                    principal.getUserInfo(),
                    "sub");
        };
    }

    private Set<GrantedAuthority> authorities(
            Iterable<? extends GrantedAuthority> providerAuthorities,
            UserRole role) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        providerAuthorities.forEach(authorities::add);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return Set.copyOf(authorities);
    }
}
