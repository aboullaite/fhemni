package dev.maboullaite.fhemni.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.ProgrammeAssessmentJobService;
import dev.maboullaite.fhemni.programme.ProgrammeIngestionService;
import dev.maboullaite.fhemni.programme.PromiseAssessment;
import dev.maboullaite.fhemni.programme.PromiseAssessmentReportService;
import dev.maboullaite.fhemni.programme.media.ProgrammeMediaService;
import org.junit.jupiter.api.Test;

class AdminProgrammeControllerTest {

    @Test
    void publishingAReassessmentClosesItsCapturedReportsAndInvalidatesOldMedia() {
        PartyProgrammeService programmes = mock(PartyProgrammeService.class);
        PromiseAssessmentReportService reports = mock(PromiseAssessmentReportService.class);
        ProgrammeMediaService media = mock(ProgrammeMediaService.class);
        AdminProgrammeController controller = new AdminProgrammeController(
                programmes,
                mock(ProgrammeIngestionService.class),
                mock(ProgrammeAssessmentJobService.class),
                reports,
                media);
        UUID assessmentId = UUID.randomUUID();
        UUID promiseId = UUID.randomUUID();
        PromiseAssessment published = mock(PromiseAssessment.class);
        when(published.id()).thenReturn(assessmentId);
        when(published.promiseId()).thenReturn(promiseId);
        when(programmes.publishAssessment(assessmentId)).thenReturn(published);

        var response = controller.publishAssessment(assessmentId);

        assertThat(response.getBody()).isSameAs(published);
        verify(reports).resolveForAssessment(assessmentId);
        verify(media).invalidateForAssessment(promiseId);
    }
}
