package dev.maboullaite.fhemni.memberanalysis;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class MemberAllowanceWindowTest {
    @Test void resetIsTheFirstOfTheMonthInMoroccoNotUtc(){
        var before=MemberAllowanceWindow.current(Clock.fixed(Instant.parse("2026-09-30T22:59:59Z"),ZoneOffset.UTC));
        var after=MemberAllowanceWindow.current(Clock.fixed(Instant.parse("2026-09-30T23:00:00Z"),ZoneOffset.UTC));
        assertThat(before.month()).isEqualTo(YearMonth.of(2026,9));
        assertThat(before.end()).isEqualTo(after.start()).isEqualTo(Instant.parse("2026-09-30T23:00:00Z"));
        assertThat(after.month()).isEqualTo(YearMonth.of(2026,10));
    }
}
