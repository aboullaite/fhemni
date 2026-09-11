package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import dev.maboullaite.fhemni.programme.media.ProgrammeMediaRepository.Lease;
import org.junit.jupiter.api.Test;

class ProgrammeMediaWorkerPathTest {

    @Test
    void isolatesPublishedOutputsByLeaseAttempt() {
        UUID id = UUID.randomUUID();

        assertThat(ProgrammeMediaWorker.attemptOutputPrefix(
                "programmes/pjd/source/media/script-2", new Lease(id, "worker-a", 7)))
                .isEqualTo("programmes/pjd/source/media/script-2/attempt-7");
        assertThat(ProgrammeMediaWorker.attemptOutputPrefix(
                "programmes/pjd/source/media/script-2", new Lease(id, "worker-b", 8)))
                .isEqualTo("programmes/pjd/source/media/script-2/attempt-8");
    }

    @Test
    void rejectsABlankObjectPrefix() {
        assertThatThrownBy(() -> ProgrammeMediaWorker.attemptOutputPrefix(
                " ", new Lease(UUID.randomUUID(), "worker", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void separatesProviderCachesByModel() {
        assertThat(ProgrammeMediaWorker.cacheIdentity("gemini-2.5-pro-tts"))
                .isNotEqualTo(ProgrammeMediaWorker.cacheIdentity("gemini-2.5-flash-tts"))
                .matches("[A-Za-z0-9_-]+");
    }
}
