package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import dev.maboullaite.fhemni.model.AnalysisStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AnalysisEventHubTest {

    @Test
    void replaysEventsAndRemovesAllStateWithTheAnalysis() throws Exception {
        AnalysisEventHub hub = new AnalysisEventHub(2);
        UUID id = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);

        hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 12, "Analyzing"));
        hub.subscribe(id, emitter);
        hub.publish(id, new AnalysisEvent(AnalysisStatus.FACT_CHECKING, 68, "Checking"));

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        hub.removeAnalysis(id);
        verify(emitter, times(1)).complete();
        assertThat(hub.channelCount()).isZero();
    }

    @Test
    void terminalReplaySendsAndCompletesExactlyOnce() throws Exception {
        AnalysisEventHub hub = new AnalysisEventHub(2);
        UUID id = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        AnalysisEvent completed = new AnalysisEvent(AnalysisStatus.COMPLETED, 100, "Complete");

        hub.publish(id, completed);
        hub.subscribe(id, emitter);
        hub.publish(id, completed);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
    }

    @Test
    void limitsConcurrentSubscribersPerAnalysis() {
        AnalysisEventHub hub = new AnalysisEventHub(2);
        UUID id = UUID.randomUUID();

        hub.subscribe(id, mock(SseEmitter.class));
        hub.subscribe(id, mock(SseEmitter.class));

        assertThatThrownBy(() -> hub.subscribe(id, mock(SseEmitter.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many live listeners");
    }
}
