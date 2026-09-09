package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import dev.maboullaite.fhemni.catalog.PersonDirectoryAdminService.AffiliationCommand;
import org.junit.jupiter.api.Test;

class PersonDirectoryAdminServiceTest {

    @Test
    void savesANormalPartyMappingWithoutInventingDates() {
        DirectoryPersonRepository people = mock(DirectoryPersonRepository.class);
        PoliticalPartyRepository parties = mock(PoliticalPartyRepository.class);
        PersonCatalogService catalogue = mock(PersonCatalogService.class);
        PersonDirectoryAdminService service = new PersonDirectoryAdminService(people, parties, catalogue);

        when(people.exists("guest")).thenReturn(true);
        when(people.findAffiliations("guest")).thenReturn(List.of());
        when(parties.findAll()).thenReturn(List.of(
                new PoliticalParty("PJD", "PJD", "PJD", "#000", true)));

        service.addAffiliation("guest", new AffiliationCommand("PJD", null, null));

        verify(people).insertAffiliation(
                org.mockito.ArgumentMatchers.eq("guest"),
                org.mockito.ArgumentMatchers.eq("PJD"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsTransitioningAHistoricalAffiliationWhenAnotherPeriodIsCurrent() {
        DirectoryPersonRepository people = mock(DirectoryPersonRepository.class);
        PoliticalPartyRepository parties = mock(PoliticalPartyRepository.class);
        PersonCatalogService catalogue = mock(PersonCatalogService.class);
        PersonDirectoryAdminService service = new PersonDirectoryAdminService(people, parties, catalogue);
        String slug = "guest";
        PersonAffiliation historical = affiliation(
                1, slug, "RNI", LocalDate.of(2020, 1, 1), LocalDate.of(2023, 12, 31));
        PersonAffiliation current = affiliation(
                2, slug, "PAM", LocalDate.of(2024, 1, 1), null);
        LocalDate transitionDate = LocalDate.of(2026, 10, 1);

        when(people.findAffiliation(1, slug)).thenReturn(Optional.of(historical));
        when(people.findAffiliations(slug)).thenReturn(List.of(historical, current));
        when(parties.findAll()).thenReturn(List.of(
                new PoliticalParty("RNI", "RNI", "RNI", "#000", true),
                new PoliticalParty("PJD", "PJD", "PJD", "#000", true)));

        assertThatThrownBy(() -> service.transitionAffiliation(
                slug, 1, new AffiliationCommand("PJD", transitionDate, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active immediately before");

        verify(people, never()).updateAffiliation(anyLong(), any(), any(), any(), any(), any());
        verify(people, never()).insertAffiliation(any(), any(), any(), any(), any(), any(), any());
    }

    private static PersonAffiliation affiliation(
            long id,
            String slug,
            String party,
            LocalDate validFrom,
            LocalDate validUntil) {
        return new PersonAffiliation(
                id, slug, party, validFrom, validUntil, null, "Admin", Instant.parse("2026-01-01T00:00:00Z"));
    }
}
