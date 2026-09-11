package dev.maboullaite.fhemni;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FhemniApplication {

    public static void main(String[] args) {
        SpringApplication.run(FhemniApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    ExecutorService analysisExecutor(
            @Value("${fhemni.cost-control.analysis-concurrency:1}") int concurrency,
            @Value("${fhemni.cost-control.analysis-queue-capacity:2}") int queueCapacity) {
        if (concurrency < 1 || queueCapacity < 0) {
            throw new IllegalArgumentException("Analysis concurrency must be positive and queue capacity cannot be negative");
        }
        BlockingQueue<Runnable> workQueue = queueCapacity == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(queueCapacity);
        return new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0L,
                TimeUnit.MILLISECONDS,
                workQueue,
                Thread.ofVirtual().name("fhemni-analysis-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "catalogImportExecutor", destroyMethod = "close")
    ExecutorService catalogImportExecutor(
            @Value("${fhemni.catalog.import-concurrency:3}") int concurrency) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("Catalog import concurrency must be between 1 and 8");
        }
        return Executors.newFixedThreadPool(
                concurrency,
                Thread.ofVirtual().name("fhemni-catalog-import-", 0).factory());
    }

    @Bean(name = "catalogBatchAnalysisExecutor", destroyMethod = "shutdownNow")
    ExecutorService catalogBatchAnalysisExecutor() {
        return Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("fhemni-catalog-batch-", 0).factory());
    }

    @Bean(name = "programmeAssessmentExecutor", destroyMethod = "close")
    ExecutorService programmeAssessmentExecutor(
            @Value("${fhemni.programme-jobs.concurrency:1}") int concurrency) {
        if (concurrency < 1 || concurrency > 4) {
            throw new IllegalArgumentException("Programme job concurrency must be between 1 and 4");
        }
        return Executors.newFixedThreadPool(
                concurrency,
                Thread.ofVirtual().name("fhemni-programme-job-", 0).factory());
    }

    @Bean(name = "programmeMediaExecutor", destroyMethod = "close")
    @ConditionalOnProperty(name = "fhemni.programme-media.worker-enabled", havingValue = "true")
    ExecutorService programmeMediaExecutor() {
        return Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("fhemni-programme-media-", 0).factory());
    }
}
