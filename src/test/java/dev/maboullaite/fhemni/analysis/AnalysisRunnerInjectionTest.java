package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.VideoReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:analysis-runner-injection;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class AnalysisRunnerInjectionTest {

    @Autowired private AnalysisService service;
    @Autowired private AnalysisRevisionRepository revisions;
    @MockitoBean private VideoAnalysisRunner runner;

    @Test
    void persistsTheReportProducedByTheConfiguredRunner() throws InterruptedException {
        var report = new VideoReport(
                "Configured runner report", "Summary", "Details", List.of(), List.of(), List.of(), List.of());
        when(runner.run(any(), any(), any())).thenReturn(
                new VideoAnalysisRunner.RunnerResult(report, "configured-runner-interaction"));

        AnalysisSnapshot created = service.reprocess("https://youtu.be/n5B3boj2MFM", "en");
        AnalysisSnapshot result = created;
        Instant deadline = Instant.now().plusSeconds(3);
        while (result.status() != AnalysisStatus.COMPLETED
                && result.status() != AnalysisStatus.FAILED
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            result = service.get(created.id());
        }

        assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.report().title()).isEqualTo("Configured runner report");
        var stored = revisions.find(created.id()).orElseThrow();
        assertThat(stored.snapshot().report().title()).isEqualTo("Configured runner report");
        assertThat(stored.interactionId()).isEqualTo("configured-runner-interaction");
    }
}
