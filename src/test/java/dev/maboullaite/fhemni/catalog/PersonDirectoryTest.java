package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.maboullaite.fhemni.catalog.PersonDirectory.CuratedPerson;
import org.junit.jupiter.api.Test;

class PersonDirectoryTest {

    private static final List<CuratedPerson> FIXTURE = List.of(
            new CuratedPerson("driss-el-azami", "Driss El Azami", "إدريس الأزمي", "PJD",
                    List.of("Driss Azami", "إدريس الأزمي", "إدريس الأزمي الإدريسي")),
            new CuratedPerson("nizar-baraka", "Nizar Baraka", "نزار بركة", "PI",
                    List.of("نزار بركة")),
            new CuratedPerson("driss-lachgar", "Driss Lachgar", "إدريس لشكر", "USFP",
                    List.of("Driss Lachguar", "إدريس لشكر")),
            new CuratedPerson("mohamed-chouki", "Mohamed Chouki", "محمد شوقي", "RNI",
                    List.of("Mohamed Chaouki")),
            new CuratedPerson("mohamed-joudar", "Mohamed Joudar", "محمد جودار", "UC",
                    List.of("Mohammed Joudar")),
            new CuratedPerson("nabil-benabdallah", "Nabil Benabdallah", "نبيل بنعبد الله", "PPS",
                    List.of("نبيل بنعبد الله", "محمد نبيل بنعبد الله", "نبيل بن عبد الله",
                            "محمد نبيل بن عبد الله")),
            new CuratedPerson("mustapha-benali", "Mustapha Benali", "مصطفى بنعلي", "FFD",
                    List.of("Moustapha Benali", "المصطفى بنعلي")),
            new CuratedPerson("sanaa-rahimi", "Sanaa Rahimi", "سناء رحيمي", "UNKNOWN",
                    List.of("Sanae Rahimi", "سناء رحيمي", "سناء")),
            new CuratedPerson("abir-elmallouki", "Abir Elmallouki", "عبير الملوكي", "UNKNOWN",
                    List.of("Abir El Mellouki", "عبير الملوكي", "عبير ملوكي")),
            new CuratedPerson("jamaa-goulahcen", "Jamaa Goulahcen", "جامع كولحسن", "UNKNOWN",
                    List.of("جامع كولحسن", "جامع غولحسن")));

    private static final List<String> HONORIFICS = List.of(
            "الدكتور", "دكتور", "الأستاذ", "أستاذ", "السيد", "السيدة", "الحاج");

    private final PersonDirectory persons = new PersonDirectory(FIXTURE, HONORIFICS);

    @Test
    void resolvesFrenchAndArabicSpellingsToTheSameVerifiedParty() {
        assertThat(persons.resolve("Driss El Azami").partyCode()).isEqualTo("PJD");
        assertThat(persons.resolve("إدريس الأزمي").partyCode()).isEqualTo("PJD");
        assertThat(persons.resolve("driss azami").partyCode()).isEqualTo("PJD");
        assertThat(persons.resolve("Nizar Baraka").partyCode()).isEqualTo("PI");
        assertThat(persons.resolve("نزار بركة").partyCode()).isEqualTo("PI");
    }

    @Test
    void exposesVerifiedNamesInBothScripts() {
        var azami = persons.resolve("إدريس الأزمي");
        assertThat(azami.displayNameFr()).isEqualTo("Driss El Azami");
        assertThat(azami.displayNameAr()).isEqualTo("إدريس الأزمي");
    }

    @Test
    void keepsUnknownArabicNamesExactlyAsWritten() {
        var unknown = persons.resolve("أميمة راضي");
        assertThat(unknown.partyCode()).isEqualTo(PartyDirectory.UNKNOWN);
        assertThat(unknown.curated()).isFalse();
        assertThat(unknown.displayNameAr()).isEqualTo("أميمة راضي");
        assertThat(unknown.displayNameFr()).isEqualTo("أميمة راضي");
    }

    @Test
    void mergesSpellingVariantsOfTheSameGuest() {
        assertThat(persons.resolve("محمد نبيل بن عبد الله").slug()).isEqualTo("nabil-benabdallah");
        assertThat(persons.resolve("نبيل بن عبد الله").partyCode()).isEqualTo("PPS");
        assertThat(persons.resolve("المصطفى بنعلي").slug()).isEqualTo("mustapha-benali");
        assertThat(persons.resolve("عبير ملوكي").slug()).isEqualTo("abir-elmallouki");
        assertThat(persons.resolve("جامع غولحسن").slug()).isEqualTo("jamaa-goulahcen");
        assertThat(persons.resolve("سناء").slug()).isEqualTo("sanaa-rahimi");
    }

    @Test
    void keepsVerifiedJournalistsExplicitlyUnaffiliated() {
        var sanaa = persons.resolve("سناء رحيمي");
        assertThat(sanaa.displayNameFr()).isEqualTo("Sanaa Rahimi");
        assertThat(sanaa.displayNameAr()).isEqualTo("سناء رحيمي");
        assertThat(sanaa.partyCode()).isEqualTo(PartyDirectory.UNKNOWN);
        assertThat(sanaa.curated()).isTrue();
        assertThat(persons.resolve("Abir Elmallouki").displayNameAr()).isEqualTo("عبير الملوكي");
    }

    @Test
    void stripsHonorificsBeforeMatching() {
        assertThat(persons.stripHonorifics("الدكتور سمير شواطي")).isEqualTo("سمير شواطي");
        assertThat(persons.stripHonorifics("السيد إدريس الأزمي").strip()).isEqualTo("إدريس الأزمي");
        var resolved = persons.resolve("الدكتور سمير شواطي");
        assertThat(resolved.slug()).isEqualTo("سمير-شواطي");
        assertThat(resolved.displayNameAr()).isEqualTo("سمير شواطي");
    }

    @Test
    void reflectsTheVerified2026LeadershipChanges() {
        // Mohamed Chouki succeeded Aziz Akhannouch at the RNI congress of 2026-02-07 (MAP).
        assertThat(persons.resolve("Mohamed Chouki").partyCode()).isEqualTo("RNI");
        // Mohamed Joudar succeeded Mohamed Sajid at the UC congress of 2022-10-01 (Le360).
        assertThat(persons.resolve("Mohamed Joudar").partyCode()).isEqualTo("UC");
        assertThat(persons.resolve("Mohamed Sajid").partyCode()).isEqualTo(PartyDirectory.UNKNOWN);
    }

    @Test
    void neverInventsAnAffiliationForUnknownNames() {
        var unknown = persons.resolve("Someone Never Mentioned");
        assertThat(unknown.partyCode()).isEqualTo(PartyDirectory.UNKNOWN);
        assertThat(unknown.curated()).isFalse();
        assertThat(unknown.displayNameFr()).isEqualTo("Someone Never Mentioned");
    }

    @Test
    void handlesBlankNamesWithoutThrowing() {
        assertThat(persons.resolve(null).slug()).isEqualTo("unknown-speaker");
        assertThat(persons.resolve("   ").slug()).isEqualTo("unknown-speaker");
    }

    @Test
    void normalizesAccentsAndSpacing() {
        assertThat(PersonDirectory.normalize("  Driss   LACHGAR ")).isEqualTo("driss lachgar");
        assertThat(persons.resolve("Driss Lachgar").partyCode()).isEqualTo("USFP");
    }

    @Test
    void resolvesThePartyThatWasValidWhenTheEpisodeWasPublished() {
        CuratedPerson guest = new CuratedPerson(
                "guest-who-moved",
                "Guest Who Moved",
                "ضيف بدّل الحزب",
                "PAM",
                List.of(),
                List.of(
                        new PersonAffiliation(1, "guest-who-moved", "PJD",
                                LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31),
                                "https://example.com/old", "Old affiliation", Instant.EPOCH),
                        new PersonAffiliation(2, "guest-who-moved", "PAM",
                                LocalDate.of(2025, 1, 1), null,
                                "https://example.com/current", "Current affiliation", Instant.EPOCH)));
        PersonDirectory dated = new PersonDirectory(List.of(guest), HONORIFICS);

        assertThat(dated.resolve("Guest Who Moved", LocalDate.of(2024, 6, 1)).partyCode())
                .isEqualTo("PJD");
        assertThat(dated.resolve("Guest Who Moved", LocalDate.of(2026, 6, 1)).partyCode())
                .isEqualTo("PAM");
    }
}
