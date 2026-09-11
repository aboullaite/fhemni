package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.maboullaite.fhemni.programme.media.ProgrammeMedia;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaService;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicProgrammeMediaControllerTest {

    @Test
    void redirectsVideoToAShortLivedStorageUrl() throws Exception {
        ProgrammeMediaService media = mock(ProgrammeMediaService.class);
        ProgrammeMediaStorage storage = mock(ProgrammeMediaStorage.class);
        ProgrammeMedia record = mock(ProgrammeMedia.class);
        when(media.publishedRecord("FGD")).thenReturn(record);
        when(record.videoObjectKey()).thenReturn("programme/video.mp4");
        when(storage.deliveryUri(any(), any(Duration.class)))
                .thenReturn(Optional.of(URI.create("https://storage.googleapis.com/private/video.mp4?signature=test")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new PublicProgrammeMediaController(media, storage)).build();

        mvc.perform(get("/api/catalog/parties/FGD/programme/media/video"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://storage.googleapis.com/private/video.mp4?signature=test"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void keepsCaptionsSameOriginForBrowserTrackCompatibility() throws Exception {
        byte[] captions = "WEBVTT\n\n00:00.000 --> 00:01.000\nفهّمني".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ProgrammeMediaService media = mock(ProgrammeMediaService.class);
        ProgrammeMediaStorage storage = mock(ProgrammeMediaStorage.class);
        ProgrammeMedia record = mock(ProgrammeMedia.class);
        when(media.publishedRecord("FGD")).thenReturn(record);
        when(record.captionsObjectKey()).thenReturn("programme/captions.vtt");
        when(storage.open("programme/captions.vtt")).thenReturn(new ProgrammeMediaStorage.StoredObject(
                new ByteArrayInputStream(captions), captions.length, "text/vtt; charset=utf-8"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new PublicProgrammeMediaController(media, storage)).build();

        MvcResult pending = mvc.perform(get("/api/catalog/parties/FGD/programme/media/captions"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/vtt;charset=utf-8"))
                .andExpect(content().bytes(captions));
        verify(storage, never()).deliveryUri(any(), any(Duration.class));
    }

    @Test
    void servesByteRangesWhenLocalStorageHasNoSignedDeliveryUrl() throws Exception {
        byte[] video = "0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ProgrammeMediaService media = mock(ProgrammeMediaService.class);
        ProgrammeMediaStorage storage = mock(ProgrammeMediaStorage.class);
        ProgrammeMedia record = mock(ProgrammeMedia.class);
        when(media.publishedRecord("FFD")).thenReturn(record);
        when(record.videoObjectKey()).thenReturn("programme/video.mp4");
        when(storage.deliveryUri(any(), any(Duration.class))).thenReturn(Optional.empty());
        when(storage.open("programme/video.mp4")).thenReturn(new ProgrammeMediaStorage.StoredObject(
                new ByteArrayInputStream(video), video.length, "video/mp4"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new PublicProgrammeMediaController(media, storage)).build();

        MvcResult pending = mvc.perform(get("/api/catalog/parties/FFD/programme/media/video")
                        .header("Range", "bytes=3-6"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", "bytes 3-6/10"))
                .andExpect(content().bytes("3456".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void closesAnOpenedAssetWhenResponseConstructionFails() throws Exception {
        ProgrammeMediaService media = mock(ProgrammeMediaService.class);
        ProgrammeMediaStorage storage = mock(ProgrammeMediaStorage.class);
        ProgrammeMedia record = mock(ProgrammeMedia.class);
        AtomicBoolean closed = new AtomicBoolean();
        var content = new ByteArrayInputStream(new byte[] { 1 }) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        when(media.publishedRecord("PJD")).thenReturn(record);
        when(record.captionsObjectKey()).thenReturn("programme/captions.vtt");
        when(storage.open("programme/captions.vtt")).thenReturn(
                new ProgrammeMediaStorage.StoredObject(content, 1, "not a valid media type"));
        var controller = new PublicProgrammeMediaController(media, storage);

        assertThatThrownBy(() -> controller.asset("PJD", "captions", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(closed).isTrue();
    }
}
