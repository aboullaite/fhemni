package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.imageio.ImageIO;

import dev.maboullaite.fhemni.civic.CivicPriorityShareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "server.forward-headers-strategy=framework",
        "spring.datasource.url=jdbc:h2:mem:civic-priority-share-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CivicPriorityShareIntegrationTest {

    private static final String SHARE_PATH = "/api/catalog/questionnaires/current/shares";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CivicPriorityShareService shares;

    @Test
    void createsAConsentDrivenPublicPreviewWithoutUploadingAnswers() throws Exception {
        byte[] image = png(1080, 566);

        String response = mvc.perform(post(SHARE_PATH)
                        .queryParam("kind", "compass")
                        .queryParam("language", "ar")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(image)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/s/priorities/")))
                .andExpect(jsonPath("$.url", org.hamcrest.Matchers.startsWith("/s/priorities/")))
                .andExpect(jsonPath("$.imageUrl", org.hamcrest.Matchers.endsWith("/image")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(response);
        String pageUrl = created.get("url").asText();
        String imageUrl = created.get("imageUrl").asText();

        String duplicateResponse = mvc.perform(post(SHARE_PATH)
                        .queryParam("kind", "compass")
                        .queryParam("language", "ar")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(image)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(duplicateResponse).get("url").asText()).isEqualTo(pageUrl);
        assertThat(jdbc.sql("SELECT image_object_key FROM civic_priority_shares WHERE share_token = :token")
                .param("token", pageUrl.substring(pageUrl.lastIndexOf('/') + 1))
                .query(String.class)
                .single()).startsWith("civic-priority-shares/");

        mvc.perform(get(pageUrl).header("Forwarded", "proto=https;host=fhemni.ma"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<meta property=\"og:image:width\" content=\"2400\">")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<meta property=\"og:image:height\" content=\"1260\">")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<meta name=\"twitter:card\" content=\"summary_large_image\">")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://fhemni.ma")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("targeted-subsidies"))));

        byte[] normalized = mvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("must-revalidate")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        BufferedImage openGraphImage = ImageIO.read(new ByteArrayInputStream(normalized));
        assertThat(openGraphImage.getWidth()).isEqualTo(2400);
        assertThat(openGraphImage.getHeight()).isEqualTo(1260);

        String token = pageUrl.substring(pageUrl.lastIndexOf('/') + 1);
        mvc.perform(delete("/api/admin/civic-priority-shares/{token}", token)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(pageUrl)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonPngShareCards() throws Exception {
        mvc.perform(post(SHARE_PATH)
                        .queryParam("kind", "parties")
                        .queryParam("language", "fr")
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(new byte[] {1, 2, 3})
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsOversizedRequestsBeforeReadingTheBody() throws Exception {
        mvc.perform(post(SHARE_PATH)
                        .queryParam("kind", "compass")
                        .queryParam("language", "en")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(new byte[CivicPriorityShareService.MAX_UPLOAD_BYTES + 1])
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void rejectsOversizedMatrixParameterizedUploadsInTheFilter() throws Exception {
        mvc.perform(post(SHARE_PATH + ";pad=x")
                        .queryParam("kind", "compass")
                        .queryParam("language", "en")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(new byte[CivicPriorityShareService.MAX_UPLOAD_BYTES + 1])
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void rejectsActualBytesBeyondTheLimitWhenContentLengthLies() throws Exception {
        mvc.perform(post(SHARE_PATH)
                        .queryParam("kind", "compass")
                        .queryParam("language", "en")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(new byte[CivicPriorityShareService.MAX_UPLOAD_BYTES + 1])
                        .header(HttpHeaders.CONTENT_LENGTH, 1)
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void removesExpiredMetadataAndItsStoredImage() throws Exception {
        String response = mvc.perform(post(SHARE_PATH)
                        .queryParam("kind", "parties")
                        .queryParam("language", "en")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(png(800, 400))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String pageUrl = objectMapper.readTree(response).get("url").asText();
        String token = pageUrl.substring(pageUrl.lastIndexOf('/') + 1);
        jdbc.sql("UPDATE civic_priority_shares SET created_at = :createdAt WHERE share_token = :token")
                .param("createdAt", Instant.now().minus(31, ChronoUnit.DAYS))
                .param("token", token)
                .update();

        shares.deleteExpiredShares();

        mvc.perform(get(pageUrl)).andExpect(status().isNotFound());
    }

    @Test
    void cleanupDrainsMoreThanOneBatchOfExpiredShares() {
        Instant expiredAt = Instant.now().minus(31, ChronoUnit.DAYS);
        for (int index = 1; index <= 205; index++) {
            jdbc.sql("""
                            INSERT INTO civic_priority_shares (
                                id, share_token, share_kind, language, image_object_key, image_sha256, created_at
                            ) VALUES (
                                :id, :token, 'COMPASS', 'en', :objectKey, :digest, :createdAt
                            )
                            """)
                    .param("id", UUID.randomUUID())
                    .param("token", "%032x".formatted(index))
                    .param("objectKey", "civic-priority-shares/en/compass/cleanup-%d.png".formatted(index))
                    .param("digest", "%064x".formatted(index))
                    .param("createdAt", expiredAt)
                    .update();
        }

        shares.deleteExpiredShares();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM civic_priority_shares WHERE created_at < :createdAt")
                .param("createdAt", Instant.now().minus(30, ChronoUnit.DAYS))
                .query(Long.class)
                .single()).isZero();
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(0xff, 0xfd, 0xf7));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
