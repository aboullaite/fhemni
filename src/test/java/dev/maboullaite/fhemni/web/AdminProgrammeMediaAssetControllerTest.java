package dev.maboullaite.fhemni.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.media.ProgrammeMedia;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaService;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminProgrammeMediaAssetControllerTest {

    @Test
    void streamsLocalMediaAsAStreamingResponseBody() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] video = "video-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ProgrammeMediaService media = mock(ProgrammeMediaService.class);
        ProgrammeMediaStorage storage = mock(ProgrammeMediaStorage.class);
        ProgrammeMedia record = mock(ProgrammeMedia.class);
        when(media.adminRecord(id)).thenReturn(record);
        when(record.videoObjectKey()).thenReturn("programme/video.mp4");
        when(storage.deliveryUri(any(), any(Duration.class))).thenReturn(Optional.empty());
        when(storage.open("programme/video.mp4")).thenReturn(new ProgrammeMediaStorage.StoredObject(
                new ByteArrayInputStream(video), video.length, "video/mp4"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AdminProgrammeMediaAssetController(media, storage)).build();

        MvcResult pending = mvc.perform(get("/api/admin/programmes/media/{id}/asset/video", id))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(content().bytes(video));
    }
}
