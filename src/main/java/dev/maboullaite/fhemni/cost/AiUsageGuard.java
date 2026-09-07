package dev.maboullaite.fhemni.cost;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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
    private final int maxHourlyChatRounds;
    private final int maxDailyChatRounds;
    private final int maxWeeklyChatRoundsPerUser;
    private final int maxDailyChatOutputTokensPerUser;
    private final Object reservationMonitor = new Object();

    @Autowired
    public AiUsageGuard(
            AiUsageRepository repository,
            @Value("${fhemni.cost-control.analysis-enabled:true}") boolean analysisEnabled,
            @Value("${fhemni.cost-control.chat-enabled:false}") boolean chatEnabled,
            @Value("${fhemni.cost-control.max-daily-analyses:5}") int maxDailyAnalyses,
            @Value("${fhemni.cost-control.max-hourly-chat-rounds:50}") int maxHourlyChatRounds,
            @Value("${fhemni.cost-control.max-daily-chat-rounds:500}") int maxDailyChatRounds,
            @Value("${fhemni.cost-control.max-weekly-chat-rounds-per-user:5}") int maxWeeklyChatRoundsPerUser,
            @Value("${fhemni.cost-control.max-daily-chat-output-tokens-per-user:10000}")
            int maxDailyChatOutputTokensPerUser) {
        this(repository, Clock.systemUTC(), analysisEnabled, chatEnabled,
                maxDailyAnalyses, maxHourlyChatRounds, maxDailyChatRounds, maxWeeklyChatRoundsPerUser,
                maxDailyChatOutputTokensPerUser);
    }

    AiUsageGuard(
            AiUsageRepository repository,
            Clock clock,
            boolean analysisEnabled,
            boolean chatEnabled,
            int maxDailyAnalyses,
            int maxHourlyChatRounds,
            int maxDailyChatRounds,
            int maxWeeklyChatRoundsPerUser) {
        this(repository, clock, analysisEnabled, chatEnabled, maxDailyAnalyses, maxHourlyChatRounds,
                maxDailyChatRounds, maxWeeklyChatRoundsPerUser, 10_000);
    }

    AiUsageGuard(
            AiUsageRepository repository,
            Clock clock,
            boolean analysisEnabled,
            boolean chatEnabled,
            int maxDailyAnalyses,
            int maxHourlyChatRounds,
            int maxDailyChatRounds,
            int maxWeeklyChatRoundsPerUser,
            int maxDailyChatOutputTokensPerUser) {
        if (maxDailyAnalyses < 0
                || maxHourlyChatRounds < 1
                || maxDailyChatRounds < 1
                || maxWeeklyChatRoundsPerUser < 1
                || maxDailyChatOutputTokensPerUser < 1) {
            throw new IllegalArgumentException(
                    "The analysis limit cannot be negative and every chat limit must be positive");
        }
        this.repository = repository;
        this.clock = clock;
        this.analysisEnabled = analysisEnabled;
        this.chatEnabled = chatEnabled;
        this.maxDailyAnalyses = maxDailyAnalyses;
        this.maxHourlyChatRounds = maxHourlyChatRounds;
        this.maxDailyChatRounds = maxDailyChatRounds;
        this.maxWeeklyChatRoundsPerUser = maxWeeklyChatRoundsPerUser;
        this.maxDailyChatOutputTokensPerUser = maxDailyChatOutputTokensPerUser;
    }

    public Reservation reserveAnalysis(UUID analysisId, String model) {
        if (!analysisEnabled) {
            throw new AiBudgetExceededException(
                    "ANALYSIS_DISABLED",
                    "Live analysis is disabled until the owner enables its production budget.");
        }
        synchronized (reservationMonitor) {
            Window window = today();
            if (maxDailyAnalyses > 0
                    && repository.countGlobal(window.from(), window.to(), true) >= maxDailyAnalyses) {
                throw new AiBudgetExceededException(
                        "ANALYSIS_DAILY_LIMIT",
                        "The daily analysis budget has been reached.");
            }
            return new Reservation(repository.reserve(
                    AiOperation.ANALYSIS, null, analysisId, model, clock.instant()));
        }
    }

    public Reservation reserveFactCheck(UUID analysisId, String model) {
        return new Reservation(repository.reserve(
                AiOperation.FACT_CHECK, null, analysisId, model, clock.instant()));
    }

    public Reservation reserveEditorial(AiOperation operation, String model) {
        if (operation != AiOperation.PROGRAMME_EXTRACTION
                && operation != AiOperation.PROMISE_FEASIBILITY) {
            throw new IllegalArgumentException("A programme editorial operation is required");
        }
        return new Reservation(repository.reserve(operation, null, null, model, clock.instant()));
    }

    public Reservation reserveQuestion(UUID analysisId, UUID userId, AiOperation operation, String model) {
        if (!chatEnabled) {
            throw new AiBudgetExceededException(
                    "CHAT_DISABLED",
                    "Video chat is disabled until the owner enables its production budget.");
        }
        if (operation != AiOperation.CHAT_VIDEO && operation != AiOperation.CHAT_CHECK) {
            throw new IllegalArgumentException("A chat operation is required");
        }
        synchronized (reservationMonitor) {
            Window hourlyWindow = currentHour();
            if (repository.countGlobal(hourlyWindow.from(), hourlyWindow.to(), false)
                    >= maxHourlyChatRounds) {
                throw new AiBudgetExceededException(
                        "CHAT_HOURLY_LIMIT",
                        "The hourly chat budget has been reached.");
            }
            Window dailyWindow = today();
            if (repository.countGlobal(dailyWindow.from(), dailyWindow.to(), false) >= maxDailyChatRounds) {
                throw new AiBudgetExceededException(
                        "CHAT_DAILY_LIMIT",
                        "The daily chat budget has been reached.");
            }
            if (repository.sumQuestionOutputTokensForUser(userId, dailyWindow.from(), dailyWindow.to())
                    >= maxDailyChatOutputTokensPerUser) {
                throw new AiBudgetExceededException(
                        "CHAT_DAILY_TOKEN_LIMIT",
                        "Your daily answer-token allowance has been reached.");
            }
            Window weeklyWindow = currentWeek();
            if (repository.countQuestionsForUser(userId, weeklyWindow.from(), weeklyWindow.to())
                    >= maxWeeklyChatRoundsPerUser) {
                throw new AiBudgetExceededException(
                        "CHAT_WEEKLY_LIMIT",
                        "Your weekly chat allowance has been reached.");
            }
            return new Reservation(repository.reserve(
                    operation, userId, analysisId, model, clock.instant()));
        }
    }

    public ChatQuota chatQuota(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A user is required to read the chat quota.");
        }
        synchronized (reservationMonitor) {
            Window window = currentWeek();
            long used = repository.countQuestionsForUser(userId, window.from(), window.to());
            Window dailyWindow = today();
            long dailyOutputTokensUsed = repository.sumQuestionOutputTokensForUser(
                    userId, dailyWindow.from(), dailyWindow.to());
            return new ChatQuota(
                    maxWeeklyChatRoundsPerUser,
                    used,
                    Math.max(0L, maxWeeklyChatRoundsPerUser - used),
                    window.to(),
                    maxDailyChatOutputTokensPerUser,
                    dailyOutputTokensUsed,
                    Math.max(0L, maxDailyChatOutputTokensPerUser - dailyOutputTokensUsed),
                    dailyWindow.to());
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

    private Window currentHour() {
        Instant hourStart = clock.instant().atOffset(ZoneOffset.UTC)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
        return new Window(hourStart, hourStart.plusSeconds(3_600));
    }

    private Window currentWeek() {
        LocalDate weekStart = LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Window(
                weekStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                weekStart.plusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    public record Reservation(UUID id) {
    }

    public record ChatQuota(
            int weeklyLimit,
            long used,
            long remaining,
            Instant resetsAt,
            int dailyOutputTokenLimit,
            long dailyOutputTokensUsed,
            long dailyOutputTokensRemaining,
            Instant dailyTokensResetAt) {
    }

    private record Window(Instant from, Instant to) {
    }
}
