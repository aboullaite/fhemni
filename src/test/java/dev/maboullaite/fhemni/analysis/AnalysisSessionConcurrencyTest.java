package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.maboullaite.fhemni.model.OutputLanguage;
import org.junit.jupiter.api.Test;

class AnalysisSessionConcurrencyTest {

    @Test
    void snapshotReadsRemainAvailableWhileAQuestionIsRunning() throws Exception {
        AnalysisSession session = new AnalysisSession(
                UUID.randomUUID(),
                "https://www.youtube.com/watch?v=n5B3boj2MFM",
                "n5B3boj2MFM",
                OutputLanguage.ENGLISH,
                true);

        session.beginQuestion();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var snapshot = executor.submit(session::snapshot).get(500, TimeUnit.MILLISECONDS);
            assertThat(snapshot.videoId()).isEqualTo("n5B3boj2MFM");
        } finally {
            session.endQuestion();
        }
    }
}
