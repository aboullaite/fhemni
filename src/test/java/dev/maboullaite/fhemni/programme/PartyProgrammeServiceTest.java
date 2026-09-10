package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftPromise;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PartyProgrammeServiceTest {

    @Test
    void doesNotMisreportATopicAssignmentFailureAsASlugConflict() {
        PartyProgrammeRepository repository = mock(PartyProgrammeRepository.class);
        PolicyTopicRepository policyTopics = mock(PolicyTopicRepository.class);
        PolicyTopicClassifier classifier = new PolicyTopicClassifier();
        PartyProgrammeService service = new PartyProgrammeService(repository, policyTopics, classifier);
        UUID programmeId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        PartyProgramme programme = new PartyProgramme(
                programmeId, "RNI", 2026, 2026, 2031,
                localized("برنامج", "Programme", "Programme"),
                localized("ملخص", "Résumé", "Summary"),
                "https://example.org/programme", "Official programme", "fr", "snapshot",
                List.of(), "sha256", now, true, EditorialStatus.DRAFT, now, now, null);
        when(repository.findById(programmeId)).thenReturn(Optional.of(programme));
        doThrow(new DataIntegrityViolationException("Unknown policy topic"))
                .when(policyTopics).replaceRuleAssignments(any(), anyList(), any());

        assertThatThrownBy(() -> service.createPromise(programmeId, new DraftPromise(
                "new-promise", "employment", localized("وعد", "Promesse", "Promise"),
                "A measurable employment promise.", "Page 1", "Mechanism", "Financing")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Unknown policy topic");
    }

    private static LocalizedText localized(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
