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
        if (date == null) {
            return false;
        }
        return (validFrom == null || !date.isBefore(validFrom))
                && (validUntil == null || !date.isAfter(validUntil));
    }
}
