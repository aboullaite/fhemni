package dev.maboullaite.fhemni.memberanalysis;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
class YouTubeEligibilityClientTest {
    @Test void durationAndAvailabilityAreVerifiedAndUnknownFailsClosed(){
        var mapper=new ObjectMapper();
        assertThat(YouTubeEligibilityClient.parse(mapper.readTree("{\"items\":[]}"),"abcdefghijk").available()).isFalse();
        var row=mapper.readTree("{\"items\":[{\"id\":\"abcdefghijk\",\"contentDetails\":{\"duration\":\"PT30M\"},\"snippet\":{\"liveBroadcastContent\":\"none\"},\"status\":{\"uploadStatus\":\"processed\",\"privacyStatus\":\"public\",\"embeddable\":true}}]}");
        assertThat(YouTubeEligibilityClient.parse(row,"abcdefghijk").durationSeconds()).isEqualTo(1800L);
        var fractional=mapper.readTree(row.toString().replace("PT30M","PT30M0.5S"));assertThat(YouTubeEligibilityClient.parse(fractional,"abcdefghijk").durationSeconds()).isEqualTo(1801L);
        assertThat(YouTubeEligibilityClient.parse(row,"other-video").available()).isFalse();
        assertThat(YouTubeEligibilityClient.parse(mapper.readTree("{\"items\":[{\"id\":\"abcdefghijk\"}]}"),"abcdefghijk").available()).isFalse();
    }
}
