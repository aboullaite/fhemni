package dev.maboullaite.fhemni.catalog;

import java.time.Instant;
import java.time.LocalDate;

/** A sourced, time-bound political affiliation for one directory person. */
public record PersonAffiliation(
        long id,
        String personSlug,
        String partyCode,
        LocalDate validFrom,
        LocalDate validUntil,
        String sourceUrl,
        String sourceLabel,
        Instant verifiedAt) {

    public boolean activeOn(LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        return (validFrom == null || !effectiveDate.isBefore(validFrom))
                && (validUntil == null || !effectiveDate.isAfter(validUntil));
    }
}
