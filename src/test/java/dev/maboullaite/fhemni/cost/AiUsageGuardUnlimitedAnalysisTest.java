package dev.maboullaite.fhemni.cost;

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

class AiUsageGuardUnlimitedAnalysisTest {

    @Test
    void zeroDisablesOnlyTheDailyAnalysisCeiling() {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        when(repository.reserve(eq(AiOperation.ANALYSIS), eq(null), any(), eq("test-model"), any()))
                .thenReturn(UUID.randomUUID(), UUID.randomUUID());
        AiUsageGuard guard = new AiUsageGuard(
                repository,
                Clock.fixed(Instant.parse("2026-09-06T09:00:00Z"), ZoneOffset.UTC),
                true,
                false,
                0,
                10,
                10,
                5);

        guard.reserveAnalysis(UUID.randomUUID(), "test-model");
        guard.reserveAnalysis(UUID.randomUUID(), "test-model");

        verify(repository, never()).countGlobal(any(), any(), eq(true));
        verify(repository, times(2)).reserve(eq(AiOperation.ANALYSIS), eq(null), any(), eq("test-model"), any());
    }
}
