package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.time.Instant;

import dev.maboullaite.fhemni.metrics.AdminMetricsRepository;
import dev.maboullaite.fhemni.metrics.AdminMetricsRepository.UsageMetrics;
import dev.maboullaite.fhemni.metrics.AdminMetricsRepository.UsageOverview;
import dev.maboullaite.fhemni.metrics.AdminMetricsRepository.UserMetrics;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
public class AdminMetricsController {

    private static final Duration RESERVATION_STALE_AFTER = Duration.ofMinutes(30);

    private final AdminMetricsRepository metrics;

    public AdminMetricsController(AdminMetricsRepository metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/overview")
    public ResponseEntity<Overview> overview() {
        Instant generatedAt = Instant.now();
        UsageOverview usage = metrics.usageOverview(generatedAt.minus(RESERVATION_STALE_AFTER));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new Overview(
                        generatedAt,
                        metrics.usersSince(generatedAt.minus(Duration.ofHours(24))),
                        usage.chat(),
                        usage.allAi()));
    }

    public record Overview(
            Instant generatedAt,
            UserMetrics users,
            UsageMetrics chat,
            UsageMetrics allAi) {
    }
}
