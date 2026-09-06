package dev.maboullaite.fhemni.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class YouTubeUrlParserTest {

    private final YouTubeUrlParser parser = new YouTubeUrlParser();

    @Test
    void parsesWatchUrlAndRemovesTrackingParameters() {
        var parsed = parser.parse("https://www.youtube.com/watch?v=n5B3boj2MFM&si=tracking");

        assertThat(parsed.videoId()).isEqualTo("n5B3boj2MFM");
        assertThat(parsed.canonicalUrl()).isEqualTo("https://www.youtube.com/watch?v=n5B3boj2MFM");
    }

    @Test
    void parsesShortAndEmbedUrls() {
        assertThat(parser.parse("youtu.be/n5B3boj2MFM?si=x").videoId()).isEqualTo("n5B3boj2MFM");
        assertThat(parser.parse("https://youtube.com/embed/n5B3boj2MFM").videoId()).isEqualTo("n5B3boj2MFM");
        assertThat(parser.parse("https://m.youtube.com/shorts/n5B3boj2MFM").videoId()).isEqualTo("n5B3boj2MFM");
    }

    @Test
    void rejectsNonYouTubeAndMalformedUrls() {
        assertThatThrownBy(() -> parser.parse("https://example.com/watch?v=n5B3boj2MFM"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public");
        assertThatThrownBy(() -> parser.parse("https://youtube.com/watch?v=too-short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
