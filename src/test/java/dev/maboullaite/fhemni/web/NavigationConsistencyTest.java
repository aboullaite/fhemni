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

    private static final List<String> ALL_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html",
            "analysis.html",
            "community.html",
            "compare-programmes.html",
            "index.html",
            "login.html",
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
    void everyPageUsesTheVersionedFhemniFavicon() throws IOException {
        for (String page : ALL_PAGES) {
            assertThat(html(page))
                    .as("favicon in %s", page)
                    .contains("href=\"/favicon.ico?v=20260911\" sizes=\"32x32\"");
        }

        var favicon = new ClassPathResource("static/favicon.ico");
        assertThat(favicon.exists()).isTrue();

        var touchIcon = ImageIO.read(new ClassPathResource(
                "static/assets/brand/apple-touch-icon.png").getInputStream());
        assertThat(touchIcon.getWidth()).isEqualTo(180);
        assertThat(touchIcon.getHeight()).isEqualTo(180);
    }

    @Test
    void everyPageUsesCampaignAwarePrivacySafeAnalytics() throws IOException {
        for (String page : ALL_PAGES) {
            assertThat(html(page))
                    .as("analytics asset in %s", page)
                    .contains("/js/analytics.js?v=20260911-1");
        }

        assertThat(html("js/analytics.js"))
                .contains("page_location: campaignLocation()")
                .contains("CAMPAIGN_PARAMETER")
                .contains("key === 'page_location' ? MAX_PAGE_LOCATION_LENGTH : 100")
                .doesNotContain("page_location: `${window.location.origin}${window.location.pathname}`")
                .doesNotContain("page_location: window.location.href");
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
                    .contains("/css/dist.css?v=20260911-1")
                    .contains("/js/i18n.js?v=20260911-1");
        }
    }

    @Test
    void narrowAdminHeadersPutLanguageChoicesBesideTheNavigation() throws IOException {
        assertThat(html("css/dist.css"))
                .contains("@media (max-width:360px)")
                .contains(".admin-page .public-header .header-actions{display:contents}")
                .contains(".admin-page .public-header .header-actions>.site-language-options{grid-area:2/1");
    }

    @Test
    void partyPageCombinesTheBriefingWithTheFullProgramme() throws IOException {
        String partyPage = html("party.html");
        assertThat(partyPage)
                .contains("id=\"partyProgrammeTab\"")
                .contains("id=\"partyChatTab\"")
                .contains("id=\"partyEpisodesTab\"")
                .contains("class=\"programme-overview\"")
                .contains("id=\"programmeMedia\" class=\"programme-media\"")
                .contains("class=\"video-js vjs-big-play-centered\"")
                .contains("/webjars/video.js/8.23.8/dist/video-js.min.css")
                .contains("/css/dist.css?v=20260911-8")
                .contains("/webjars/video.js/8.23.8/dist/video.min.js")
                .contains("/js/party.js?v=20260911-6")
                .doesNotContain("id=\"partyBriefingTab\"")
                .doesNotContain("id=\"programmeMediaAudio\"")
                .doesNotContain("id=\"programmeMediaTranscript\"")
                .doesNotContain("programme.mediaTitle")
                .doesNotContain("programme.mediaTranscript")
                .doesNotContain("data-programme-skip")
                .doesNotContain("id=\"partyPriorities\"");
        assertThat(partyPage.indexOf("id=\"programmeMedia\"")).isBetween(
                partyPage.indexOf("id=\"partyProgramme\""),
                partyPage.indexOf("id=\"partyPromises\""));
        assertThat(html("js/party.js"))
                .doesNotContain("['briefing'")
                .doesNotContain("#programmeMediaAudio")
                .doesNotContain("#programmeMediaTranscript");
        assertThat(new ClassPathResource("static/assets/vendor/videojs/VideoJS.woff").exists()).isTrue();
    }

    private String html(String page) throws IOException {
        return new ClassPathResource("static/" + page).getContentAsString(UTF_8);
    }
}
