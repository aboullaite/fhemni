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
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.sessions.max=2",
        "fhemni.sessions.retention=PT0.05S",
        "fhemni.sessions.max-conversation-turns=2",
        "fhemni.sessions.cleanup-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:session-limit-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionLimitIntegrationTest {

    @Autowired
    private AnalysisService service;

    @Autowired
    private AnalysisEventHub eventHub;

    @Test
    void evictsOldTerminalSessionsAndBoundsConversationHistory() throws InterruptedException {
        AnalysisSnapshot first = awaitCompletion(service.create("https://youtu.be/n5B3boj2MFM", "en"));
        AnalysisSnapshot second = awaitCompletion(service.create("https://youtu.be/abcdefghijk", "en"));
        AnalysisSnapshot third = awaitCompletion(service.create("https://youtu.be/ABCDEFGHIJK", "en"));

        assertThat(service.get(first.id()).status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(service.get(second.id()).status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(eventHub.channelCount()).isLessThanOrEqualTo(2);

        UUID userId = UUID.randomUUID();
        service.ask(third.id(), userId, "First question", QuestionMode.VIDEO);
        service.ask(third.id(), userId, "Second question", QuestionMode.VIDEO);
        service.ask(third.id(), userId, "Third question", QuestionMode.VIDEO);

        assertThat(service.get(third.id(), userId).conversation())
                .hasSize(2)
                .extracting(answer -> answer.question())
                .containsExactly("Second question", "Third question");
        assertThat(service.get(third.id()).conversation()).isEmpty();
    }

    @Test
    void expiresHotSessionsButRestoresAndReusesTheirDurableReports() throws InterruptedException {
        AnalysisSnapshot completed = awaitCompletion(service.create("https://youtu.be/n5B3boj2MFM", "en"));

        Thread.sleep(80);
        service.evictExpiredSessions();

        assertThat(service.get(completed.id()).status()).isEqualTo(AnalysisStatus.COMPLETED);
        AnalysisSnapshot reused = service.create("https://www.youtube.com/watch?v=n5B3boj2MFM", "en");
        assertThat(reused.id()).isEqualTo(completed.id());
        assertThat(reused.report()).isNotNull();
        assertThat(eventHub.channelCount()).isZero();
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
