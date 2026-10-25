package dev.maboullaite.fhemni.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class AnalysisSchedulingConfiguration {

    // Explicit pools also isolate fixed-delay jobs when virtual threads are enabled.
    @Bean
    ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:3}") int poolSize) {
        if (poolSize < 2) {
            throw new IllegalArgumentException("Scheduled maintenance requires at least two threads");
        }
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("fhemni-maintenance-");
        return scheduler;
    }

    @Bean
    ThreadPoolTaskScheduler analysisLeaseScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("fhemni-analysis-lease-");
        return scheduler;
    }
}
