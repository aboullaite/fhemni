package dev.maboullaite.fhemni.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.analysis.AnalysisService;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class AnalysisControllerAuthorizationTest {

    @Test
    void rejectsAnUnpublishedEventStreamBeforeSubscribing() {
        AnalysisService analyses = mock(AnalysisService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        Authentication authentication = mock(Authentication.class);
        UUID analysisId = UUID.randomUUID();
        when(currentUser.find(authentication)).thenReturn(Optional.empty());
        when(currentUser.isAdministrator(authentication)).thenReturn(false);
        when(analyses.getPublished(analysisId, null))
                .thenThrow(new NoSuchElementException("Published analysis not found."));
        AnalysisController controller = new AnalysisController(analyses, currentUser, "");

        assertThrows(NoSuchElementException.class, () -> controller.events(analysisId, authentication));

        verify(analyses).getPublished(analysisId, null);
        verify(analyses, never()).subscribe(any());
        verify(analyses, never()).get(eq(analysisId), any());
    }
}
