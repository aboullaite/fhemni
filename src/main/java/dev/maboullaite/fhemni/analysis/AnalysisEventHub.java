package dev.maboullaite.fhemni.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.maboullaite.fhemni.model.AnalysisStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AnalysisEventHub {

    private static final long EMITTER_TIMEOUT_MILLIS = 15 * 60 * 1000L;

    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private final int maxSubscribersPerAnalysis;

    public AnalysisEventHub(
            @Value("${fhemni.sessions.max-subscribers-per-analysis:16}") int maxSubscribersPerAnalysis) {
        if (maxSubscribersPerAnalysis < 1) {
            throw new IllegalArgumentException("Subscriber limit must be at least one");
        }
        this.maxSubscribersPerAnalysis = maxSubscribersPerAnalysis;
    }

    public SseEmitter subscribe(UUID analysisId) {
        return subscribe(analysisId, new SseEmitter(EMITTER_TIMEOUT_MILLIS));
    }

    SseEmitter subscribe(UUID analysisId, SseEmitter emitter) {
        Channel channel = channels.computeIfAbsent(analysisId, ignored -> new Channel());
        Subscriber subscriber = new Subscriber(emitter);
        emitter.onCompletion(() -> finish(analysisId, channel, subscriber));
        emitter.onTimeout(() -> finish(analysisId, channel, subscriber));
        emitter.onError(ignored -> finish(analysisId, channel, subscriber));

        AnalysisEvent replay;
        synchronized (channel) {
            replay = channel.latest;
            if (replay == null || !terminal(replay.status())) {
                if (channel.subscribers.size() >= maxSubscribersPerAnalysis) {
                    throw new IllegalStateException("Too many live listeners for this analysis. Please retry shortly.");
                }
                if (replay != null && !send(subscriber, replay)) {
                    subscriber.complete();
                    return emitter;
                }
                channel.subscribers.add(subscriber);
                return emitter;
            }

            send(subscriber, replay);
        }
        subscriber.complete();
        return emitter;
    }

    public void publish(UUID analysisId, AnalysisEvent event) {
        Channel channel = channels.computeIfAbsent(analysisId, ignored -> new Channel());
        List<Subscriber> recipients;
        boolean terminal = terminal(event.status());

        synchronized (channel) {
            channel.latest = event;
            recipients = List.copyOf(channel.subscribers);
            if (terminal) {
                channel.subscribers.clear();
            }
        }

        for (Subscriber subscriber : recipients) {
            if (!send(subscriber, event)) {
                unsubscribe(analysisId, channel, subscriber);
            }
            if (terminal) {
                subscriber.complete();
            }
        }
    }

    public void removeAnalysis(UUID analysisId) {
        Channel channel = channels.remove(analysisId);
        if (channel == null) {
            return;
        }

        List<Subscriber> emitters;
        synchronized (channel) {
            emitters = List.copyOf(channel.subscribers);
            channel.subscribers.clear();
            channel.latest = null;
        }
        emitters.forEach(Subscriber::complete);
    }

    int channelCount() {
        return channels.size();
    }

    private boolean send(Subscriber subscriber, AnalysisEvent event) {
        if (subscriber.completed.get()) {
            return false;
        }
        try {
            subscriber.emitter.send(SseEmitter.event().name("progress").data(event));
            return true;
        } catch (IOException | IllegalStateException exception) {
            subscriber.completed.set(true);
            return false;
        }
    }

    private void finish(UUID analysisId, Channel expectedChannel, Subscriber subscriber) {
        subscriber.completed.set(true);
        unsubscribe(analysisId, expectedChannel, subscriber);
    }

    private void unsubscribe(UUID analysisId, Channel expectedChannel, Subscriber subscriber) {
        Channel channel = channels.get(analysisId);
        if (channel != expectedChannel) {
            return;
        }
        synchronized (channel) {
            channel.subscribers.remove(subscriber);
        }
    }

    private boolean terminal(AnalysisStatus status) {
        return status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED;
    }

    private static final class Channel {
        private AnalysisEvent latest;
        private final List<Subscriber> subscribers = new ArrayList<>();
    }

    private static final class Subscriber {
        private final SseEmitter emitter;
        private final AtomicBoolean completed = new AtomicBoolean();

        private Subscriber(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private void complete() {
            if (completed.compareAndSet(false, true)) {
                emitter.complete();
            }
        }
    }
}
