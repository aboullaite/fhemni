package dev.maboullaite.fhemni.memberanalysis;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemberAnalysisWorker {

    private final MemberAnalysisRepository repository;
    private final MemberAnalysisService service;
    private final boolean enabled;
    private final ExecutorService executor;
    private final AtomicBoolean busy = new AtomicBoolean();

    public MemberAnalysisWorker(
            MemberAnalysisRepository repository,
            MemberAnalysisService service,
            @Value("${fhemni.member-analysis.worker-enabled:false}") boolean enabled,
            @Qualifier("analysisExecutor") ExecutorService executor) {
        this.repository = repository;
        this.service = service;
        this.enabled = enabled;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${fhemni.member-analysis.worker-delay-ms:10000}",
            initialDelayString = "${fhemni.member-analysis.worker-initial-delay-ms:30000}")
    public void run() {
        if (!enabled || !busy.compareAndSet(false, true)) {
            return;
        }
        try {
            repository.expire(Instant.now());
            var claim = repository.claim(Instant.now());
            if (claim.isEmpty()) {
                busy.set(false);
                return;
            }
            try {
                executor.execute(() -> {
                    try {
                        service.process(claim.get());
                    } finally {
                        busy.set(false);
                    }
                });
            } catch (RuntimeException rejected) {
                repository.finish(claim.get(), null, null, "QUEUE_UNAVAILABLE");
                busy.set(false);
            }
        } catch (RuntimeException failure) {
            busy.set(false);
            throw failure;
        }
    }
}
