package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.QuestionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:analysis-flow-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class AnalysisFlowTest {

    @Autowired
    private AnalysisService service;

    @Test
    void completesTheWorkflowInDemoMode() throws InterruptedException {
        AnalysisSnapshot created = service.create("https://youtu.be/n5B3boj2MFM", "en");
        AnalysisSnapshot duplicate = service.create("https://www.youtube.com/watch?v=n5B3boj2MFM", "en");
        AnalysisSnapshot completed = awaitCompletion(created);

        assertThat(duplicate.id()).isEqualTo(created.id());
        assertThat(completed.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.demo()).isTrue();
        assertThat(completed.report()).isNotNull();
        assertThat(completed.report().chapters()).isNotEmpty();
        assertThat(completed.report().claims()).isNotEmpty();

        UUID userId = UUID.randomUUID();
        var answer = service.ask(completed.id(), userId, "What was discussed?", QuestionMode.VIDEO);
        assertThat(answer.answer()).contains("Demo mode");
        assertThat(service.get(completed.id()).conversation()).isEmpty();
        assertThat(service.get(completed.id(), userId).conversation()).hasSize(1);
        assertThat(service.get(completed.id(), UUID.randomUUID()).conversation()).isEmpty();

        AnalysisSnapshot reused = service.create("https://youtu.be/n5B3boj2MFM", "en");
        AnalysisSnapshot reprocessed = service.reprocess("https://youtu.be/n5B3boj2MFM", "en");
        assertThat(reused.id()).isEqualTo(completed.id());
        assertThat(reprocessed.id()).isNotEqualTo(completed.id());
        assertThat(awaitCompletion(reprocessed).status()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    private AnalysisSnapshot awaitCompletion(AnalysisSnapshot analysis) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        AnalysisSnapshot current = analysis;
        while (Instant.now().isBefore(deadline)
                && current.status() != AnalysisStatus.COMPLETED
                && current.status() != AnalysisStatus.FAILED) {
            Thread.sleep(20);
            current = service.get(analysis.id());
        }
        return current;
    }
}
