package dev.maboullaite.fhemni.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class NavigationConsistencyTest {

    private static final List<String> SECONDARY_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html",
            "community.html",
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

    private String html(String page) throws IOException {
        return new ClassPathResource("static/" + page).getContentAsString(UTF_8);
    }
}
