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
    void reportsQuotaAndUsesTheSameMondayToMondayWindowForAdmission() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        when(repository.countQuestionsForUser(userId, WEEK_START, NEXT_WEEK)).thenReturn(4L, 5L);
        when(repository.reserve(
                eq(AiOperation.CHAT_VIDEO), eq(userId), eq(analysisId), eq("test-model"), any()))
                .thenReturn(UUID.randomUUID());
        AiUsageGuard guard = guard(repository);

        guard.reserveQuestion(analysisId, userId, AiOperation.CHAT_VIDEO, "test-model");
        AiUsageGuard.ChatQuota quota = guard.chatQuota(userId);

        assertThat(quota.weeklyLimit()).isEqualTo(5);
        assertThat(quota.used()).isEqualTo(5);
        assertThat(quota.remaining()).isZero();
        assertThat(quota.resetsAt()).isEqualTo(NEXT_WEEK);
        verify(repository, times(2)).countQuestionsForUser(userId, WEEK_START, NEXT_WEEK);
    }

    @Test
    void rejectsTheSixthAttemptEvenWhenEarlierProviderCallsFailed() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        UUID userId = UUID.randomUUID();
        when(repository.countQuestionsForUser(userId, WEEK_START, NEXT_WEEK)).thenReturn(5L);
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

        verify(repository, never()).countQuestionsForUser(any(), any(), any());
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

        verify(repository, never()).countQuestionsForUser(any(), any(), any());
        verify(repository, never()).reserve(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsTheNextQuestionAfterAnAnswerCrossesTheDailyTokenLimit() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        UUID userId = UUID.randomUUID();
        when(repository.countGlobal(HOUR_START, NEXT_HOUR, false)).thenReturn(12L);
        when(repository.countGlobal(DAY_START, NEXT_DAY, false)).thenReturn(80L);
        when(repository.sumQuestionOutputTokensForUser(userId, DAY_START, NEXT_DAY)).thenReturn(10_250L);
        AiUsageGuard guard = guard(repository);

        assertThatThrownBy(() -> guard.reserveQuestion(
                UUID.randomUUID(), userId, AiOperation.CHAT_VIDEO, "test-model"))
                .isInstanceOfSatisfying(AiBudgetExceededException.class, exception ->
                        assertThat(exception.code()).isEqualTo("CHAT_DAILY_TOKEN_LIMIT"));

        verify(repository, never()).countQuestionsForUser(any(), any(), any());
        verify(repository, never()).reserve(any(), any(), any(), any(), any());
    }

    private static AiUsageGuard guard(AiUsageRepository repository) {
        return new AiUsageGuard(repository, SUNDAY, true, true, 5, 50, 500, 5);
    }
}
