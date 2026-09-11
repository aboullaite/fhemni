package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import dev.maboullaite.fhemni.gemini.ProgrammeMediaScriptGateway;
import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.PublicProgrammeView;
import org.junit.jupiter.api.Test;

class ProgrammeMediaServiceTest {

    @Test
    void returnsTheValidatedPublishedRecordWithoutFetchingItTwice() {
        ProgrammeMediaRepository repository = mock(ProgrammeMediaRepository.class);
        PartyProgrammeService programmes = mock(PartyProgrammeService.class);
        ProgrammeMedia record = mock(ProgrammeMedia.class);
        PublicProgrammeView programme = mock(PublicProgrammeView.class);
        when(repository.publishedByParty("RNI")).thenReturn(Optional.of(record));
        when(programmes.publishedProgramme("rni")).thenReturn(programme);
        when(record.sourceSha256()).thenReturn("current-source");
        when(programme.sourceSha256()).thenReturn("current-source");
        ProgrammeMediaService service = new ProgrammeMediaService(
                repository,
                programmes,
                mock(ProgrammeMediaScriptGateway.class),
                mock(GeminiProgrammeTtsGateway.class),
                new ProgrammeMediaScriptPolicy(),
                3);

        ProgrammeMedia result = service.publishedRecord("rni");

        assertThat(result).isSameAs(record);
        verify(repository, times(1)).publishedByParty("RNI");
        verify(programmes).publishedProgramme("rni");
    }
}
