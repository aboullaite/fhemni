package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.catalog.PersonDirectory.CuratedPerson;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Guards the Flyway-seeded reference data: every party and verified affiliation
 * the catalogue relies on must survive migrations intact.
 */
@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:directory-seed-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class DirectorySeedIntegrationTest {

    @Autowired
    private PoliticalPartyRepository partyRepository;

    @Autowired
    private DirectoryPersonRepository personRepository;

    @Test
    void seedsAllExpectedPartiesInDisplayOrder() {
        List<PoliticalParty> parties = partyRepository.findAll();
        assertThat(parties).extracting(PoliticalParty::code)
                .containsExactly("RNI", "PAM", "PI", "PJD", "USFP", "PPS", "MP", "FGD",
                        "UC", "FFD", "MDS", "PSU", "IND", "UNKNOWN");
        assertThat(parties).allSatisfy(party ->
                assertThat(PartyDirectory.validColor(party.color())).isTrue());
    }

    @Test
    void seedsVerifiedAffiliationsWithBothSpellings() {        Map<String, CuratedPerson> bySlug = personRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(CuratedPerson::slug, Function.identity()));

        assertThat(bySlug).containsKeys(
                "driss-el-azami", "nizar-baraka", "mohamed-chouki", "mohamed-joudar",
                "nabil-benabdallah", "mustapha-benali", "abdeslam-el-aziz", "jamal-el-asri",
                "sanaa-rahimi", "abir-elmallouki", "jamaa-goulahcen",
                "lahcen-essaadi", "rachid-talbi-alami", "hicham-el-mhajri", "mehdi-bensaid",
                "abdallah-bouanou", "amina-maa-el-ainin", "el-habib-dekkak");

        CuratedPerson azami = bySlug.get("driss-el-azami");
        assertThat(azami.partyCode()).isEqualTo("PJD");
        assertThat(azami.displayNameFr()).isEqualTo("Driss El Azami");
        assertThat(azami.displayNameAr()).isEqualTo("إدريس الأزمي");
        assertThat(azami.aliases()).contains("إدريس الأزمي", "إدريس الأزمي الإدريسي");

        assertThat(bySlug.get("nabil-benabdallah").aliases())
                .contains("نبيل بن عبد الله", "محمد نبيل بن عبد الله");
        assertThat(bySlug.get("mustapha-benali").aliases()).contains("المصطفى بنعلي");
        assertThat(bySlug.get("sanaa-rahimi").partyCode()).isEqualTo(PartyDirectory.UNKNOWN);
    }

    @Test
    void hidesUnaffiliatedPartiesFromPublicSheets() {
        Map<String, PoliticalParty> byCode = partyRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(PoliticalParty::code, Function.identity()));
        assertThat(byCode.get("PJD").visible()).isTrue();
        assertThat(byCode.get("UNKNOWN").visible()).isFalse();
        assertThat(byCode.get("IND").visible()).isFalse();
    }

    @Test
    void seedsHonorificPrefixes() {
        assertThat(personRepository.findHonorifics())
                .contains("الدكتور", "الأستاذ", "السيد", "السيدة", "الحاج");
    }
}
