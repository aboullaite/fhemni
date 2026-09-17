package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

    @Test
    void createsAConsentDrivenPublicPreviewWithoutUploadingAnswers() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "compass.png", MediaType.IMAGE_PNG_VALUE, png(1080, 566));

        String response = mvc.perform(multipart("/api/catalog/questionnaires/current/shares")
                        .file(image)
                        .param("kind", "compass")
                        .param("language", "ar")
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
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("immutable")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        BufferedImage openGraphImage = ImageIO.read(new ByteArrayInputStream(normalized));
        assertThat(openGraphImage.getWidth()).isEqualTo(2400);
        assertThat(openGraphImage.getHeight()).isEqualTo(1260);
    }

    @Test
    void rejectsNonPngShareCards() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "result.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/catalog/questionnaires/current/shares")
                        .file(image)
                        .param("kind", "parties")
                        .param("language", "fr")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
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
