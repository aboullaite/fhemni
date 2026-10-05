package dev.maboullaite.fhemni.memberanalysis;

import java.time.*;

public record MemberAllowanceWindow(Instant start, Instant end, YearMonth month) {
    public static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
    public static MemberAllowanceWindow current(Clock clock) {
        return of(YearMonth.from(clock.instant().atZone(ZONE)));
    }
    public static MemberAllowanceWindow of(YearMonth month) {
        return new MemberAllowanceWindow(month.atDay(1).atStartOfDay(ZONE).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant(), month);
    }
}
