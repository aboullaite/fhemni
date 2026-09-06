package dev.maboullaite.fhemni.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.auth.bootstrap-admin-email=owner@example.com",
        "spring.datasource.url=jdbc:h2:mem:identity-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserAccountRepositoryTest {

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private AuthProperties properties;

    @Test
    void createsAndRefreshesTheSameExternalIdentity() {
        ExternalIdentityProfile firstLogin = new ExternalIdentityProfile(
                "github", "42", "med", "Mohammed", null, false, "https://example.com/one.png");
        AppUser created = users.recordLogin(firstLogin, properties.shouldBeAdmin(firstLogin));

        ExternalIdentityProfile nextLogin = new ExternalIdentityProfile(
                "github", "42", "med", "Mohammed A.", "med@example.com", false,
                "https://example.com/two.png");
        AppUser refreshed = users.recordLogin(nextLogin, properties.shouldBeAdmin(nextLogin));

        assertThat(refreshed.id()).isEqualTo(created.id());
        assertThat(refreshed.displayName()).isEqualTo("Mohammed");
        assertThat(refreshed.email()).isEqualTo("med@example.com");
        assertThat(users.findByIdentity("github", "42")).contains(refreshed);
    }

    @Test
    void keepsOnlyTheFirstNameProvidedByTheIdentityProvider() {
        ExternalIdentityProfile latin = new ExternalIdentityProfile(
                "google", "latin-name", null, "Mohammed Aboullaite", null, false, null);
        ExternalIdentityProfile arabic = new ExternalIdentityProfile(
                "google", "arabic-name", null, "محمد أبو الليث", null, false, null);

        assertThat(latin.displayName()).isEqualTo("Mohammed");
        assertThat(arabic.displayName()).isEqualTo("محمد");
    }

    @Test
    void bootstrapsAdminOnlyFromAVerifiedEmail() {
        ExternalIdentityProfile verified = new ExternalIdentityProfile(
                "google", "google-owner", null, "Owner", "owner@example.com", true, null);
        ExternalIdentityProfile unverified = new ExternalIdentityProfile(
                "github", "github-owner", "owner", "Owner", "owner@example.com", false, null);

        AppUser admin = users.recordLogin(verified, properties.shouldBeAdmin(verified));
        AppUser user = users.recordLogin(unverified, properties.shouldBeAdmin(unverified));

        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(user.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void promotesAnExistingUserWhenTheVerifiedIdentityBecomesTheBootstrapAdmin() {
        ExternalIdentityProfile profile = new ExternalIdentityProfile(
                "google", "existing-google-owner", null, "Owner", "owner@example.com", true, null);

        AppUser initialUser = users.recordLogin(profile, false);
        AppUser promotedUser = users.recordLogin(profile, properties.shouldBeAdmin(profile));

        assertThat(initialUser.role()).isEqualTo(UserRole.USER);
        assertThat(promotedUser.role()).isEqualTo(UserRole.ADMIN);
        assertThat(users.findByIdentity("google", "existing-google-owner"))
                .get()
                .extracting(AppUser::role)
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void revokesAdministratorRoleOnTheNextLoginWhenConfigurationNoLongerMatches() {
        ExternalIdentityProfile profile = new ExternalIdentityProfile(
                "google", "former-owner", null, "Former Owner", "former@example.com", true, null);

        users.recordLogin(profile, true);
        AppUser revoked = users.recordLogin(profile, false);

        assertThat(revoked.role()).isEqualTo(UserRole.USER);
        assertThat(users.findByIdentity("google", "former-owner").orElseThrow().role())
                .isEqualTo(UserRole.USER);
    }
}
