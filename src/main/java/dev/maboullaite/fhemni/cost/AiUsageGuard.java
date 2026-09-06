package dev.maboullaite.fhemni.cost;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiUsageGuard {

    private static final Logger log = LoggerFactory.getLogger(AiUsageGuard.class);

    private final AiUsageRepository repository;
    private final Clock clock;
    private final boolean analysisEnabled;
    private final boolean chatEnabled;
    private final int maxDailyAnalyses;
    private final int maxDailyQuestions;
    private final int maxDailyQuestionsPerUser;
    private final Object reservationMonitor = new Object();

    @Autowired
    public AiUsageGuard(
            AiUsageRepository repository,
            @Value("${fhemni.cost-control.analysis-enabled:true}") boolean analysisEnabled,
            @Value("${fhemni.cost-control.chat-enabled:false}") boolean chatEnabled,
            @Value("${fhemni.cost-control.max-daily-analyses:5}") int maxDailyAnalyses,
            @Value("${fhemni.cost-control.max-daily-questions:50}") int maxDailyQuestions,
            @Value("${fhemni.cost-control.max-daily-questions-per-user:5}") int maxDailyQuestionsPerUser) {
        this(repository, Clock.systemUTC(), analysisEnabled, chatEnabled,
                maxDailyAnalyses, maxDailyQuestions, maxDailyQuestionsPerUser);
    }

    AiUsageGuard(
            AiUsageRepository repository,
            Clock clock,
            boolean analysisEnabled,
            boolean chatEnabled,
            int maxDailyAnalyses,
            int maxDailyQuestions,
            int maxDailyQuestionsPerUser) {
        if (maxDailyAnalyses < 0 || maxDailyQuestions < 1 || maxDailyQuestionsPerUser < 1) {
            throw new IllegalArgumentException("The analysis limit cannot be negative and chat limits must be positive");
        }
        this.repository = repository;
        this.clock = clock;
        this.analysisEnabled = analysisEnabled;
        this.chatEnabled = chatEnabled;
        this.maxDailyAnalyses = maxDailyAnalyses;
        this.maxDailyQuestions = maxDailyQuestions;
        this.maxDailyQuestionsPerUser = maxDailyQuestionsPerUser;
    }

    public Reservation reserveAnalysis(UUID analysisId, String model) {
        if (!analysisEnabled) {
            throw new AiBudgetExceededException("Live analysis is disabled until the owner enables its production budget.");
        }
        synchronized (reservationMonitor) {
            Window window = today();
            if (maxDailyAnalyses > 0
                    && repository.countGlobal(window.from(), window.to(), true) >= maxDailyAnalyses) {
                throw new AiBudgetExceededException("The daily analysis budget has been reached.");
            }
            return new Reservation(repository.reserve(
                    AiOperation.ANALYSIS, null, analysisId, model, clock.instant()));
        }
    }

    public Reservation reserveFactCheck(UUID analysisId, String model) {
        return new Reservation(repository.reserve(
                AiOperation.FACT_CHECK, null, analysisId, model, clock.instant()));
    }

    public Reservation reserveQuestion(UUID analysisId, UUID userId, AiOperation operation, String model) {
        if (!chatEnabled) {
            throw new AiBudgetExceededException("Video chat is disabled until the owner enables its production budget.");
        }
        if (operation != AiOperation.CHAT_VIDEO && operation != AiOperation.CHAT_CHECK) {
            throw new IllegalArgumentException("A chat operation is required");
        }
        synchronized (reservationMonitor) {
            Window window = today();
            if (repository.countGlobal(window.from(), window.to(), false) >= maxDailyQuestions) {
                throw new AiBudgetExceededException("The daily chat budget has been reached.");
            }
            if (repository.countQuestionsForUser(userId, window.from(), window.to()) >= maxDailyQuestionsPerUser) {
                throw new AiBudgetExceededException("Your daily question allowance has been reached.");
            }
            return new Reservation(repository.reserve(
                    operation, userId, analysisId, model, clock.instant()));
        }
    }

    public void succeeded(Reservation reservation, AiUsage usage) {
        complete(reservation, "SUCCEEDED", usage);
    }

    public void failed(Reservation reservation) {
        complete(reservation, "FAILED", AiUsage.empty());
    }

    public void failed(Reservation reservation, AiUsage usage) {
        complete(reservation, "FAILED", usage);
    }

    public boolean analysisEnabled() {
        return analysisEnabled;
    }

    public boolean chatEnabled() {
        return chatEnabled;
    }

    private void complete(Reservation reservation, String status, AiUsage usage) {
        if (reservation != null) {
            try {
                repository.complete(reservation.id(), status, usage, clock.instant());
            } catch (RuntimeException exception) {
                log.error("Could not finalize AI usage reservation {} as {}", reservation.id(), status, exception);
            }
        }
    }

    private Window today() {
        LocalDate day = LocalDate.now(clock);
        return new Window(
                day.atStartOfDay().toInstant(ZoneOffset.UTC),
                day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    public record Reservation(UUID id) {
    }

    private record Window(Instant from, Instant to) {
    }
}
