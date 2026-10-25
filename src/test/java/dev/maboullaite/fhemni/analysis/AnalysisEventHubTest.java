package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;

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
    private final java.util.List<AnalysisEventHub> hubs = new java.util.ArrayList<>();

    private AnalysisEventHub hub(int limit) {
        var hub = new AnalysisEventHub(limit);
        hubs.add(hub);
        return hub;
    }

    @org.junit.jupiter.api.AfterEach
    void closeHubs() {
        hubs.forEach(AnalysisEventHub::shutdown);
    }

    @Test
    void timedOutUninterruptibleSendRetainsCapacityUntilTransportActuallyFinishes() throws Exception {
        var hub = new AnalysisEventHub(1, 50);
        hubs.add(hub);
        var id = UUID.randomUUID();
        var emitter = mock(SseEmitter.class);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var releaseCompletion = new CountDownLatch(1);
        var completing = new CountDownLatch(1);
        var writer = new java.util.concurrent.atomic.AtomicReference<Thread>();
        doAnswer(call -> {
            writer.set(Thread.currentThread());
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { interrupted.countDown(); }
            }
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doAnswer(call -> { completing.countDown();releaseCompletion.await(5,TimeUnit.SECONDS);return null; }).when(emitter).complete();
        hub.subscribe(id, emitter);
        hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 10, "Start"));
        try {
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            for(int attempt=0;attempt<3;attempt++)assertThatThrownBy(() -> hub.subscribe(id,mock(SseEmitter.class)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Too many live listeners");
            release.countDown();
            assertThat(completing.await(2,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> hub.subscribe(id,mock(SseEmitter.class))).isInstanceOf(IllegalStateException.class);
        } finally {
            release.countDown();releaseCompletion.countDown();
        }
        assertThat(writer.get().join(java.time.Duration.ofSeconds(2))).isTrue();
        hub.subscribe(id,mock(SseEmitter.class));
    }

    @Test
    void sendTimeoutInterruptsStalledWriterAndAllowsReconnect() throws Exception {
        var hub = new AnalysisEventHub(1, 50);
        hubs.add(hub);
        var id = UUID.randomUUID();
        var slow = mock(SseEmitter.class);
        var interrupted = new CountDownLatch(1);
        var writer = new java.util.concurrent.atomic.AtomicReference<Thread>();
        doAnswer(call -> {
            writer.set(Thread.currentThread());
            try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
            catch (InterruptedException expected) { interrupted.countDown(); throw new java.io.IOException(expected); }
            return null;
        }).when(slow).send(any(SseEmitter.SseEventBuilder.class));
        hub.subscribe(id, slow);
        hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 10, "Start"));
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(slow, timeout(1000)).complete();
        assertThat(writer.get().join(java.time.Duration.ofSeconds(2))).isTrue();
        var replacement = mock(SseEmitter.class);
        hub.subscribe(id, replacement);
        hub.publish(id, new AnalysisEvent(AnalysisStatus.COMPLETED, 100, "Done"));
        verify(replacement, timeout(1000)).complete();
    }

    @Test
    void cleanupDoesNotWaitForBlockedReplayOrCompletion() throws Exception {
        var hub = hub(1);
        var id = UUID.randomUUID();
        var emitter = mock(SseEmitter.class);
        var sending = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            sending.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        hub.publish(id, new AnalysisEvent(AnalysisStatus.COMPLETED, 100, "Done"));
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                threads.submit(() -> hub.subscribe(id, emitter)).get(1, TimeUnit.SECONDS);
                assertThat(sending.await(2, TimeUnit.SECONDS)).isTrue();
                threads.submit(() -> hub.removeAnalysis(id)).get(1, TimeUnit.SECONDS);
                assertThat(hub.channelCount()).isZero();
            } finally { release.countDown(); }
        }
    }

    @Test
    void blockedBrowserDoesNotBlockWorkerOrAnotherBrowserAndTerminalSurvivesQueuedProgress() throws Exception {
        var hub = hub(2);
        var id = UUID.randomUUID();
        var slow = mock(SseEmitter.class);
        var fast = mock(SseEmitter.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var received = new java.util.concurrent.CopyOnWriteArrayList<AnalysisStatus>();
        doAnswer(call -> {
            var builder = call.getArgument(0, SseEmitter.SseEventBuilder.class);
            builder.build().stream().map(item -> item.getData()).filter(AnalysisEvent.class::isInstance)
                    .map(AnalysisEvent.class::cast).forEach(event -> received.add(event.status()));
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(slow).send(any(SseEmitter.SseEventBuilder.class));
        hub.subscribe(id, slow);
        hub.subscribe(id, fast);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 10, "Start")));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                first.get(1, TimeUnit.SECONDS);
                threads.submit(() -> {
                    for (int i = 0; i < 100; i++) hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 20, "Progress"));
                    hub.publish(id, new AnalysisEvent(AnalysisStatus.COMPLETED, 100, "Done"));
                    hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 20, "Late"));
                }).get(1, TimeUnit.SECONDS);
                verify(fast, timeout(1000)).complete();
            } finally {
                release.countDown();
            }
            verify(slow, timeout(1000)).complete();
            assertThat(received).containsExactly(AnalysisStatus.ANALYZING, AnalysisStatus.COMPLETED);
            hub.removeAnalysis(id);
        }
    }

    @Test
    void replaysEventsAndRemovesAllStateWithTheAnalysis() throws Exception {
        AnalysisEventHub hub = hub(2);
        UUID id = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);

        hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 12, "Analyzing"));
        hub.subscribe(id, emitter);
        verify(emitter, timeout(1000).times(1)).send(any(SseEmitter.SseEventBuilder.class));
        hub.publish(id, new AnalysisEvent(AnalysisStatus.FACT_CHECKING, 68, "Checking"));

        verify(emitter, timeout(1000).times(2)).send(any(SseEmitter.SseEventBuilder.class));
        hub.removeAnalysis(id);
        verify(emitter, timeout(1000).times(1)).complete();
        assertThat(hub.channelCount()).isZero();
    }

    @Test
    void terminalReplaySendsAndCompletesExactlyOnce() throws Exception {
        AnalysisEventHub hub = hub(2);
        UUID id = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        AnalysisEvent completed = new AnalysisEvent(AnalysisStatus.COMPLETED, 100, "Complete");

        hub.publish(id, completed);
        hub.subscribe(id, emitter);
        hub.publish(id, completed);

        verify(emitter, timeout(1000).times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, timeout(1000).times(1)).complete();
    }

    @Test
    void emitterErrorCallbackCanFinishWhilePublicationIsInsideSend() throws Exception {
        AnalysisEventHub hub = hub(1);
        UUID id = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(id, emitter);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> errors = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(errors.capture());
        var sending = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        var writer = new java.util.concurrent.atomic.AtomicReference<Thread>();
        doAnswer(call -> {
            writer.set(Thread.currentThread());
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
        assertThat(writer.get().join(java.time.Duration.ofSeconds(2))).isTrue();
        hub.subscribe(id, mock(SseEmitter.class));
        hub.publish(id, new AnalysisEvent(AnalysisStatus.FACT_CHECKING, 68, "Checking"));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void lateProgressCannotReplaceTerminalReplay() throws Exception {
        AnalysisEventHub hub = hub(2);
        UUID id = UUID.randomUUID();
        hub.publish(id, new AnalysisEvent(AnalysisStatus.FAILED, 12, "Lease lost"));
        hub.publish(id, new AnalysisEvent(AnalysisStatus.ANALYZING, 12, "Late progress"));
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(id, emitter);
        verify(emitter, timeout(1000).times(1)).complete();
    }

    @Test
    void limitsConcurrentSubscribersPerAnalysis() {
        AnalysisEventHub hub = hub(2);
        UUID id = UUID.randomUUID();

        hub.subscribe(id, mock(SseEmitter.class));
        hub.subscribe(id, mock(SseEmitter.class));

        assertThatThrownBy(() -> hub.subscribe(id, mock(SseEmitter.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many live listeners");
    }
}
