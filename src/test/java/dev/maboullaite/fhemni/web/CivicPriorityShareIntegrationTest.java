package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

import javax.imageio.ImageIO;

import dev.maboullaite.fhemni.civic.CivicPriorityShareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
        MockMultipartFile image = new MockMultipartFile(
                "image", "compass.png", MediaType.IMAGE_PNG_VALUE, png(1080, 566));

        String response = mvc.perform(multipart("/api/catalog/questionnaires/current/shares")
                        .file(image)
                        .param("kind", "compass")
                        .param("language", "ar")
                        .header(HttpHeaders.CONTENT_LENGTH, image.getSize() + 4_096)
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

        String duplicateResponse = mvc.perform(multipart("/api/catalog/questionnaires/current/shares")
                        .file(image)
                        .param("kind", "compass")
                        .param("language", "ar")
                        .header(HttpHeaders.CONTENT_LENGTH, image.getSize() + 4_096)
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
        MockMultipartFile image = new MockMultipartFile(
                "image", "result.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/catalog/questionnaires/current/shares")
                        .file(image)
                        .param("kind", "parties")
                        .param("language", "fr")
                        .header(HttpHeaders.CONTENT_LENGTH, image.getSize() + 4_096)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversizedRequestsBeforeMultipartResolution() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "too-large.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1});

        mvc.perform(multipart("/api/catalog/questionnaires/current/shares")
                        .file(image)
                        .param("kind", "compass")
                        .param("language", "en")
                        .header(HttpHeaders.CONTENT_LENGTH, 2_100_000)
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void removesExpiredMetadataAndItsStoredImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "expired.png", MediaType.IMAGE_PNG_VALUE, png(800, 400));
        String response = mvc.perform(multipart("/api/catalog/questionnaires/current/shares")
                        .file(image)
                        .param("kind", "parties")
                        .param("language", "en")
                        .header(HttpHeaders.CONTENT_LENGTH, image.getSize() + 4_096)
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
