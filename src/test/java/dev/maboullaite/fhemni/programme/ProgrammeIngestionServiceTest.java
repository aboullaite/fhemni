package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProgrammeIngestionServiceTest {

    @Test
    void normalizesAProgrammeUrlWithoutChangingItsEncodedPath() {
        assertThat(ProgrammeIngestionService.publicHttpsUrl(
                " HTTPS://PAM.MA:443/programme%20electoral/?year=2026#download "))
                .isEqualTo("https://pam.ma/programme%20electoral/?year=2026");
    }

    @Test
    void removesMarketingTrackingWithoutDroppingFunctionalQueryParameters() {
        assertThat(ProgrammeIngestionService.publicHttpsUrl(
                "https://pam.ma/programme-electoral/?utm_source=chatgpt.com&year=2026&fbclid=abc"))
                .isEqualTo("https://pam.ma/programme-electoral/?year=2026");
    }

    @Test
    void rejectsAFileThatOnlyPretendsToBeAPdf() {
        var service = new ProgrammeIngestionService(null, null, null, null);
        var document = new MockMultipartFile(
                "document", "programme.pdf", "application/pdf", "not a pdf".getBytes());

        assertThatThrownBy(() -> service.ingestPdf("https://party.ma/programme", document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid PDF");
    }

    @Test
    void rejectsNonPublicOrCredentialBearingUrlsBeforeGeminiSeesThem() {
        assertRejected("http://party.ma/programme");
        assertRejected("https://localhost/programme");
        assertRejected("https://127.0.0.1/programme");
        assertRejected("https://192.168.1.10/programme");
        assertRejected("https://admin:secret@party.ma/programme");
        assertRejected("https://party.local/programme");
    }

    private void assertRejected(String value) {
        assertThatThrownBy(() -> ProgrammeIngestionService.publicHttpsUrl(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
