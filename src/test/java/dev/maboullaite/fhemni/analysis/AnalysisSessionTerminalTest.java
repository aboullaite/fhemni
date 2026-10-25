package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.VideoReport;
import org.junit.jupiter.api.Test;

class AnalysisSessionTerminalTest {
    @Test
    void failedSessionRejectsAllLateWorkerTransitions() {
        var session = session();
        session.fail("Lease expired");
        var failed = session.snapshot();
        session.progress(AnalysisStatus.ANALYZING, 12, "Late progress");
        session.complete(report(), "late-interaction");
        session.fail("Late failure");
        assertThat(session.snapshot()).isEqualTo(failed);
        assertThat(session.analysisInteractionId()).isNull();
    }

    @Test
    void completedSessionRejectsAllLateWorkerTransitions() {
        var session = session();
        session.complete(report(), "original-interaction");
        var completed = session.snapshot();
        session.progress(AnalysisStatus.FACT_CHECKING, 68, "Late progress");
        session.fail("Late failure");
        session.complete(report(), "replacement-interaction");
        assertThat(session.snapshot()).isEqualTo(completed);
        assertThat(session.analysisInteractionId()).isEqualTo("original-interaction");
    }

    private AnalysisSession session() {
        return new AnalysisSession(UUID.randomUUID(), "https://youtu.be/abcdefghijk", "abcdefghijk", OutputLanguage.ENGLISH, true);
    }

    private VideoReport report() {
        return new VideoReport("Report", "Summary", "Details", List.of(), List.of(), List.of(), List.of());
    }
}
