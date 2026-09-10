package dev.maboullaite.fhemni.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AiUsageGuardWeeklyLimitTest {

    private static final Instant WEEK_START = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant NEXT_WEEK = Instant.parse("2026-09-07T00:00:00Z");
    private static final Instant HOUR_START = Instant.parse("2026-09-06T20:00:00Z");
    private static final Instant NEXT_HOUR = Instant.parse("2026-09-06T21:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-09-06T00:00:00Z");
    private static final Instant NEXT_DAY = Instant.parse("2026-09-07T00:00:00Z");
    private static final Clock SUNDAY = Clock.fixed(
            Instant.parse("2026-09-06T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void reportsDailyAndWeeklyQuotaUsingTheirMatchingAdmissionWindows() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        when(repository.chatUsageForUser(userId, DAY_START, NEXT_DAY, WEEK_START, NEXT_WEEK))
                .thenReturn(
                        new AiUsageRepository.UserChatUsage(7L, 99L, 0L),
                        new AiUsageRepository.UserChatUsage(8L, 100L, 0L));
        when(repository.reserve(
                eq(AiOperation.CHAT_VIDEO), eq(userId), eq(analysisId), eq("test-model"), any()))
                .thenReturn(UUID.randomUUID());
        AiUsageGuard guard = guard(repository);

        guard.reserveQuestion(analysisId, userId, AiOperation.CHAT_VIDEO, "test-model");
        AiUsageGuard.ChatQuota quota = guard.chatQuota(userId);

        assertThat(quota.dailyRequestLimit()).isEqualTo(20);
        assertThat(quota.dailyRequestsUsed()).isEqualTo(8);
        assertThat(quota.dailyRequestsRemaining()).isEqualTo(12);
        assertThat(quota.dailyRequestsResetAt()).isEqualTo(NEXT_DAY);
        assertThat(quota.weeklyLimit()).isEqualTo(100);
        assertThat(quota.used()).isEqualTo(100);
        assertThat(quota.remaining()).isZero();
        assertThat(quota.resetsAt()).isEqualTo(NEXT_WEEK);
        assertThat(quota.dailyOutputTokenLimit()).isEqualTo(16_000);
        verify(repository, times(2)).chatUsageForUser(
                userId, DAY_START, NEXT_DAY, WEEK_START, NEXT_WEEK);
    }

    @Test
    void rejectsTheTwentyFirstDailyAttemptEvenWhenEarlierProviderCallsFailed() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        UUID userId = UUID.randomUUID();
        when(repository.chatUsageForUser(userId, DAY_START, NEXT_DAY, WEEK_START, NEXT_WEEK))
                .thenReturn(new AiUsageRepository.UserChatUsage(20L, 80L, 0L));
        AiUsageGuard guard = guard(repository);

        assertThatThrownBy(() -> guard.reserveQuestion(
                UUID.randomUUID(), userId, AiOperation.CHAT_CHECK, "test-model"))
                .isInstanceOfSatisfying(AiBudgetExceededException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("CHAT_USER_DAILY_LIMIT");
                    assertThat(exception).hasMessageContaining("daily chat allowance");
                });
        verify(repository, never()).reserve(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsTheWeeklyLimitBeforeTheDailyLimitWhenBothAreExhausted() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        UUID userId = UUID.randomUUID();
        when(repository.chatUsageForUser(userId, DAY_START, NEXT_DAY, WEEK_START, NEXT_WEEK))
                .thenReturn(new AiUsageRepository.UserChatUsage(20L, 100L, 0L));
        AiUsageGuard guard = guard(repository);

        assertThatThrownBy(() -> guard.reserveQuestion(
                UUID.randomUUID(), userId, AiOperation.CHAT_CHECK, "test-model"))
                .isInstanceOfSatisfying(AiBudgetExceededException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("CHAT_WEEKLY_LIMIT");
                    assertThat(exception).hasMessageContaining("weekly chat allowance");
                });
        verify(repository, never()).reserve(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsTheFiftyFirstGlobalAttemptWithinTheUtcHour() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        when(repository.countGlobal(HOUR_START, NEXT_HOUR, false)).thenReturn(50L);
        AiUsageGuard guard = guard(repository);

        assertThatThrownBy(() -> guard.reserveQuestion(
                UUID.randomUUID(), UUID.randomUUID(), AiOperation.CHAT_VIDEO, "test-model"))
                .isInstanceOfSatisfying(AiBudgetExceededException.class, exception ->
                        assertThat(exception.code()).isEqualTo("CHAT_HOURLY_LIMIT"));

        verify(repository, never()).chatUsageForUser(any(), any(), any(), any(), any());
        verify(repository, never()).reserve(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsTheFiveHundredAndFirstGlobalAttemptWithinTheUtcDay() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        when(repository.countGlobal(HOUR_START, NEXT_HOUR, false)).thenReturn(49L);
        when(repository.countGlobal(DAY_START, NEXT_DAY, false)).thenReturn(500L);
        AiUsageGuard guard = guard(repository);

        assertThatThrownBy(() -> guard.reserveQuestion(
                UUID.randomUUID(), UUID.randomUUID(), AiOperation.CHAT_CHECK, "test-model"))
                .isInstanceOfSatisfying(AiBudgetExceededException.class, exception ->
                        assertThat(exception.code()).isEqualTo("CHAT_DAILY_LIMIT"));

        verify(repository, never()).chatUsageForUser(any(), any(), any(), any(), any());
        verify(repository, never()).reserve(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsTheNextQuestionAfterAnAnswerCrossesTheDailyTokenLimit() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        UUID userId = UUID.randomUUID();
        when(repository.countGlobal(HOUR_START, NEXT_HOUR, false)).thenReturn(12L);
        when(repository.countGlobal(DAY_START, NEXT_DAY, false)).thenReturn(80L);
        when(repository.chatUsageForUser(userId, DAY_START, NEXT_DAY, WEEK_START, NEXT_WEEK))
                .thenReturn(new AiUsageRepository.UserChatUsage(12L, 80L, 16_250L));
        AiUsageGuard guard = guard(repository);

        assertThatThrownBy(() -> guard.reserveQuestion(
                UUID.randomUUID(), userId, AiOperation.CHAT_VIDEO, "test-model"))
                .isInstanceOfSatisfying(AiBudgetExceededException.class, exception ->
                        assertThat(exception.code()).isEqualTo("CHAT_DAILY_TOKEN_LIMIT"));

        verify(repository, never()).reserve(any(), any(), any(), any(), any());
    }

    private static AiUsageGuard guard(AiUsageRepository repository) {
        return new AiUsageGuard(repository, SUNDAY, true, true, 5, 50, 500, 20, 100, 16_000);
    }
}
