package dev.maboullaite.fhemni.analysis;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.maboullaite.fhemni.model.AnalysisStatus;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AnalysisEventHub {

    private static final long EMITTER_TIMEOUT_MILLIS = 15 * 60 * 1000L;

    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private final int maxSubscribersPerAnalysis;
    private final long sendTimeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledThreadPoolExecutor timeouts = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon().name("analysis-sse-timeout").factory());

    public AnalysisEventHub(int maxSubscribersPerAnalysis) {
        this(maxSubscribersPerAnalysis, 30_000);
    }

    @Autowired
    public AnalysisEventHub(
            @Value("${fhemni.sessions.max-subscribers-per-analysis:16}") int maxSubscribersPerAnalysis,
            @Value("${fhemni.sessions.sse-send-timeout-ms:30000}") long sendTimeoutMillis) {
        if (maxSubscribersPerAnalysis < 1 || sendTimeoutMillis < 1) {
            throw new IllegalArgumentException("Subscriber limit and send timeout must be positive");
        }
        this.maxSubscribersPerAnalysis = maxSubscribersPerAnalysis;
        this.sendTimeoutMillis = sendTimeoutMillis;
        timeouts.setRemoveOnCancelPolicy(true);
    }

    public SseEmitter subscribe(UUID analysisId) {
        return subscribe(analysisId, new SseEmitter(EMITTER_TIMEOUT_MILLIS));
    }

    SseEmitter subscribe(UUID analysisId, SseEmitter emitter) {
        if (closed.get()) throw new IllegalStateException("Live updates are shutting down");
        Channel channel = channels.computeIfAbsent(analysisId, ignored -> new Channel());
        Subscriber subscriber = new Subscriber(channel, emitter);
        emitter.onCompletion(subscriber::transportFinished);
        emitter.onTimeout(subscriber::transportFinished);
        emitter.onError(ignored -> subscriber.transportFinished());
        synchronized (channel) {
            if (closed.get()) throw new IllegalStateException("Live updates are shutting down");
            if (channel.subscribers.size() >= maxSubscribersPerAnalysis) {
                throw new IllegalStateException("Too many live listeners for this analysis. Please retry shortly.");
            }
            channel.subscribers.add(subscriber);
            if (channel.latest != null) subscriber.offer(channel.latest);
            subscriber.writer.start();
        }
        return emitter;
    }

    public void publish(UUID analysisId, AnalysisEvent event) {
        if (closed.get()) return;
        Channel channel = channels.computeIfAbsent(analysisId, ignored -> new Channel());

        synchronized (channel) {
            if (channel.latest != null && terminal(channel.latest.status())) {
                return;
            }
            channel.latest = event;
            // Only enqueue here. Browser I/O never runs on the analysis worker.
            for (Subscriber subscriber : channel.subscribers) subscriber.offer(event);
        }
    }

    public void removeAnalysis(UUID analysisId) {
        Channel channel = channels.remove(analysisId);
        if (channel == null) {
            return;
        }

        synchronized (channel) {
            for (Subscriber subscriber : List.copyOf(channel.subscribers)) subscriber.stop();
            channel.latest = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        closed.set(true);
        for (UUID id : List.copyOf(channels.keySet())) removeAnalysis(id);
        timeouts.shutdownNow();
    }

    int channelCount() {
        return channels.size();
    }

    private boolean terminal(AnalysisStatus status) {
        return status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED;
    }

    private static final class Channel {
        private AnalysisEvent latest;
        private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    }

    private final class Subscriber {
        private final Channel channel;
        private final SseEmitter emitter;
        private final ArrayBlockingQueue<AnalysisEvent> pending = new ArrayBlockingQueue<>(1);
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean transportFinished = new AtomicBoolean();
        private final Thread writer;

        private Subscriber(Channel channel, SseEmitter emitter) {
            this.channel = channel;
            this.emitter = emitter;
            writer = Thread.ofVirtual().name("analysis-sse-writer").unstarted(this::drain);
        }

        // Serialized by the channel: progress is a snapshot, so retain only the
        // latest pending update. A terminal update cannot be superseded.
        private void offer(AnalysisEvent event) {
            if (stopped.get()) return;
            pending.poll();
            pending.offer(event);
        }

        private void drain() {
            try {
                while (!stopped.get()) {
                    AnalysisEvent event = pending.take();
                    if (stopped.get()) break;
                    var deadline = timeouts.schedule(this::stop, sendTimeoutMillis, TimeUnit.MILLISECONDS);
                    try {
                        emitter.send(SseEmitter.event().name("progress").data(event));
                    } finally {
                        deadline.cancel(false);
                    }
                    if (terminal(event.status())) break;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException ignored) {
                // The browser disconnected or transport failed; analysis continues.
            } finally {
                stop();
                // Completion may also acquire the transport write lock. Keep it on
                // this writer, never on a publisher or timeout thread.
                if (!transportFinished.get()) {
                    try { emitter.complete(); } catch (RuntimeException ignored) { /* Already closed. */ }
                }
                // Socket writes and completion may ignore interruption. Reserve
                // capacity until this writer really exits, including its final flush.
                channel.subscribers.remove(this);
            }
        }

        private void transportFinished() {
            transportFinished.set(true);
            stop();
        }

        private void stop() {
            if (stopped.compareAndSet(false, true)) {
                pending.clear();
                if (Thread.currentThread() != writer) writer.interrupt();
            }
        }
    }
}
