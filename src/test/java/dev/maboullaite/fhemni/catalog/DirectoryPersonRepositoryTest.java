package dev.maboullaite.fhemni.catalog;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class DirectoryPersonRepositoryTest {

    @Test
    void bindsAffiliationInstantsAsPostgresCompatibleUtcOffsets() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), nullable(Object.class))).thenReturn(statement);
        when(statement.param(anyString(), nullable(Object.class), anyInt())).thenReturn(statement);
        when(statement.update()).thenReturn(1);
        DirectoryPersonRepository repository = new DirectoryPersonRepository(jdbc);
        Instant verifiedAt = Instant.parse("2026-09-09T14:14:03.281Z");

        repository.insertAffiliation(
                "guest", "PJD", LocalDate.of(2026, 1, 1), null,
                null, "Admin assignment", verifiedAt);
        repository.updateAffiliation(
                1, "guest", "RNI", LocalDate.of(2026, 6, 1), null, verifiedAt);

        verify(statement, times(2)).param("verifiedAt", verifiedAt.atOffset(ZoneOffset.UTC));
        verify(statement, never()).param("verifiedAt", verifiedAt);
    }
}
