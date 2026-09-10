package dev.maboullaite.fhemni.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class NavigationConsistencyTest {

    private static final List<String> ADMIN_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html");

    private static final List<String> SECONDARY_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html",
            "community.html",
            "compare-programmes.html",
            "methodology.html",
            "parties.html",
            "party.html",
            "person.html",
            "promise.html",
            "video.html",
            "videos.html");

    @Test
    void everyPrimaryNavigationLinksToHowItWorks() throws IOException {
        assertThat(html("index.html"))
                .contains("href=\"#how-it-works\" data-i18n=\"common.howItWorks\"");

        for (String page : SECONDARY_PAGES) {
            assertThat(html(page))
                    .as("primary navigation in %s", page)
                    .contains("href=\"/#how-it-works\" data-i18n=\"common.howItWorks\"");
        }
    }

    @Test
    void homePagePublishesACompleteLargeSocialCard() throws IOException {
        String home = html("index.html");
        assertThat(home)
                .contains("property=\"og:title\"")
                .contains("property=\"og:description\"")
                .contains("دقق فوعود الأحزاب المغربية وقارن البرامج والتقييمات والمصادر")
                .contains("property=\"og:site_name\"")
                .contains("name=\"twitter:card\" content=\"summary_large_image\"")
                .contains("https://fhemni.ma/assets/social/fhemni-og.png?v=20260910-2")
                .doesNotContain("fhemni.aboullaite.me");

        var imageResource = new ClassPathResource("static/assets/social/fhemni-og.png");
        var image = ImageIO.read(imageResource.getInputStream());
        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(630);
    }

    @Test
    void homePageUsesOneConciseFactCheckHeading() throws IOException {
        assertThat(html("index.html"))
                .contains("id=\"factCheckTitle\" data-i18n=\"landing.factCheckTitle\"")
                .doesNotContain("landing.factCheckKicker")
                .doesNotContain("landing.factCheckIntro");
    }

    @Test
    void adminPagesUseTheLatestMobileAssets() throws IOException {
        for (String page : ADMIN_PAGES) {
            assertThat(html(page))
                    .as("mobile assets in %s", page)
                    .contains("/css/dist.css?v=20260910-31")
                    .contains("/js/i18n.js?v=20260910-12");
        }
    }

    @Test
    void narrowAdminHeadersPutLanguageChoicesBesideTheNavigation() throws IOException {
        assertThat(html("css/dist.css"))
                .contains("@media (max-width:360px)")
                .contains(".admin-page .public-header .header-actions{display:contents}")
                .contains(".admin-page .public-header .header-actions>.site-language-options{grid-area:2/1");
    }

    private String html(String page) throws IOException {
        return new ClassPathResource("static/" + page).getContentAsString(UTF_8);
    }
}
