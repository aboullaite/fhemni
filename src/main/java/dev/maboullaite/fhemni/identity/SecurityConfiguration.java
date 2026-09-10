package dev.maboullaite.fhemni.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfiguration {

    @Bean
    CookieSerializer sessionCookieSerializer(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("FHEMNI_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            FederatedUserService federatedUsers,
            LoginSuccessHandler loginSuccessHandler,
            CurrentUserService currentUser) throws Exception {
        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookiePath("/");
        RequestMatcher apiRequests = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
        RequestMatcher adminPages = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/admin/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/admin.html"),
                PathPatternRequestMatcher.withDefaults().matcher("/admin-episodes.html"),
                PathPatternRequestMatcher.withDefaults().matcher("/admin-suggestions.html"),
                PathPatternRequestMatcher.withDefaults().matcher("/admin-programmes.html"),
                PathPatternRequestMatcher.withDefaults().matcher("/admin-people.html"));
        AuthorizationManager<RequestAuthorizationContext> administrator = (authentication, context) -> {
            var principal = authentication.get();
            return new AuthorizationDecision(currentUser.isAdministrator(principal));
        };

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/admin/**", "/admin.html", "/admin-episodes.html",
                                "/admin-suggestions.html", "/admin-programmes.html", "/admin-people.html",
                                "/api/admin/**")
                        .access(administrator)
                        .requestMatchers(HttpMethod.POST, "/api/analyses").access(administrator)
                        .requestMatchers(HttpMethod.GET, "/api/analyses/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/analyses/*/questions").authenticated()
                        .requestMatchers("/api/analyses/**").access(administrator)
                        .requestMatchers(
                                "/", "/index.html", "/videos", "/videos/**",
                                "/analyses/**", "/analysis.html",
                                "/people/**", "/person.html",
                                "/parties", "/parties/**", "/parties.html", "/party.html",
                                "/promises/**", "/promise.html",
                                "/catalog", "/catalog/**",
                                "/community", "/community/", "/community.html",
                                "/methodology", "/methodology/", "/methodology.html",
                                "/suggestions", "/suggestions/",
                                "/video.html", "/videos.html", "/login", "/login.html",
                                "/error", "/favicon.ico", "/css/**", "/js/**", "/assets/**",
                                "/oauth2/**", "/login/oauth2/**", "/api/auth/session", "/healthz")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/videos/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/catalog/parties/*/programme/questions").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/catalog/people/**", "/api/catalog/parties/**", "/api/catalog/promises/**",
                                "/api/catalog/policy-topics")
                        .permitAll()
                        .requestMatchers("/api/account/policy-topics").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/suggestions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/suggestions/*/votes").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/suggestions").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/meta").permitAll()
                        .anyRequest().denyAll())
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(federatedUsers.oauth2())
                                .oidcUserService(federatedUsers.oidc()))
                        .successHandler(loginSuccessHandler)
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "FHEMNI_SESSION"))
                // Fhemni carries its small, validated return target separately. Avoid creating a
                // database-backed session merely because an anonymous client requested /admin.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("""
                                default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; \
                                form-action 'self'; font-src 'self'; style-src 'self'; \
                                script-src 'self' https://www.googletagmanager.com https://www.youtube.com https://s.ytimg.com; \
                                connect-src 'self' https://www.google-analytics.com https://*.google-analytics.com https://analytics.google.com https://www.googletagmanager.com; \
                                img-src 'self' data: https://i.ytimg.com https://*.ytimg.com https://*.googleusercontent.com https://avatars.githubusercontent.com https://www.google-analytics.com https://www.googletagmanager.com; \
                                frame-src https://www.youtube.com https://www.youtube-nocookie.com
                                """.replace("\n", " ")))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(policy -> policy.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                apiRequests)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login?continue=/admin"),
                                adminPages)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new NegatedRequestMatcher(apiRequests)));
        return http.build();
    }
}
