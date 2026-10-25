package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.mockito.ArgumentCaptor;

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
    void emitterErrorCallbackCanFinishWhilePublicationIsInsideSend() throws Exception {
        AnalysisEventHub hub = new AnalysisEventHub(1);
        UUID id = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(id, emitter);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> errors = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(errors.capture());
        var sending = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        doAnswer(call -> {
            sending.countDown();
            if (!releaseSend.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Send did not release");
            }
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var publication = threads.submit(() ->
                    hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 12, "Analyzing")));
            try {
                assertThat(sending.await(2, TimeUnit.SECONDS)).isTrue();
                // Spring may invoke this callback while holding the emitter's write lock.
                // It must not wait for the channel monitor held by publication.
                threads.submit(() -> errors.getValue().accept(new IllegalStateException("Async startup failed")))
                        .get(2, TimeUnit.SECONDS);
            } finally {
                releaseSend.countDown();
            }
            publication.get(2, TimeUnit.SECONDS);
        }
        hub.subscribe(id, mock(SseEmitter.class));
        hub.publish(id, new AnalysisEvent(AnalysisStatus.FACT_CHECKING, 68, "Checking"));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void lateProgressCannotReplaceTerminalReplay() throws Exception {
        AnalysisEventHub hub = new AnalysisEventHub(2);
        UUID id = UUID.randomUUID();
        hub.publish(id, new AnalysisEvent(AnalysisStatus.FAILED, 12, "Lease lost"));
        hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 12, "Late progress"));
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(id, emitter);
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
