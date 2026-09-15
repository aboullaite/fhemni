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
import org.springframework.core.io.ClassPathResource;

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
                        "UC", "FFD", "MDS", "PSU", "PE", "PML", "PVM", "ND", "PDN", "PGV",
                        "PEDD", "PUD", "PRV", "ALAMAL", "PRD", "IND", "UNKNOWN");
        assertThat(parties).allSatisfy(party ->
                assertThat(PartyDirectory.validColor(party.color())).isTrue());
        assertThat(parties).allSatisfy(party -> {
            assertThat(party.symbolLabelFr()).isNotBlank();
            assertThat(party.symbolLabelAr()).isNotBlank();
            assertThat(party.symbolAsset()).startsWith("/assets/parties/");
            assertThat(new ClassPathResource("static" + party.symbolAsset()).exists()).isTrue();
        });

        Map<String, PoliticalParty> byCode = parties.stream()
                .collect(Collectors.toUnmodifiableMap(PoliticalParty::code, Function.identity()));
        assertThat(byCode.get("RNI").symbolAsset()).isEqualTo("/assets/parties/rni-display.png");
        assertThat(byCode.get("PE").symbolAsset()).isEqualTo("/assets/parties/pe-display.png");
        assertThat(byCode.get("PE").symbolVerified()).isTrue();
        assertThat(byCode.get("FGD").symbolAsset()).isEqualTo("/assets/parties/fgd-display.png");
        assertThat(byCode.get("FGD").symbolVerified()).isTrue();
        assertThat(byCode.get("FGD").catalogueCode()).isEqualTo("FGD");
        assertThat(byCode.get("PSU").catalogueCode()).isEqualTo("FGD");
        assertThat(partyRepository.visibleCatalogueCodes())
                .contains("FGD", "PUD")
                .doesNotContain("PSU");

        PoliticalParty neoDemocrats = byCode.get("ND");
        PoliticalParty nationalDemocrats = byCode.get("PDN");
        assertThat(neoDemocrats.nameFr()).isEqualTo("Parti des Néo-Démocrates");
        assertThat(neoDemocrats.nameAr()).isEqualTo("حزب الديمقراطيين الجدد");
        assertThat(neoDemocrats.symbolAsset()).isEqualTo("/assets/parties/nd-display.png");
        assertThat(nationalDemocrats.nameFr()).isEqualTo("Parti Démocrate National");
        assertThat(nationalDemocrats.nameAr()).isEqualTo("الحزب الديمقراطي الوطني");
        assertThat(nationalDemocrats.symbolLabelFr()).isEqualTo("Parapluie");
        assertThat(nationalDemocrats.symbolAsset()).isEqualTo("/assets/parties/pdn-display.png");
        assertThat(nationalDemocrats.symbolVerified()).isTrue();
        assertThat(nationalDemocrats).isNotEqualTo(neoDemocrats);
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
    void seedsAffiliationsExplicitlyIdentifiedInPublishedEpisodeRoles() {
        Map<String, ExpectedAffiliation> expected = Map.ofEntries(
                Map.entry("خالد-البقالي", affiliation("PDN", "uE4zzVBstx4")),
                Map.entry("بدر-العربي", affiliation("PDN", "B1COnlcQLs4")),
                Map.entry("نبيل-العادل", affiliation("MP", "fzTGSPbbvc0")),
                Map.entry("مصطفي-صغيري", affiliation("MP", "26AjT1bdgSw")),
                Map.entry("هشام-ايت-منا", affiliation("RNI", "Z-ACRzTq2ZI")),
                Map.entry("محمد-اوجار", affiliation("RNI", "w3hjGY-Fcjo")),
                Map.entry("ليلي-ذاكري", affiliation("PPS", "4fVg02R0HAU")),
                Map.entry("عبد-الجبار-الرشيدي", affiliation("PI", "mYMsouy08Y8")),
                Map.entry("كمال-الهشومي", affiliation("USFP", "Wk3NOgPXwlQ")),
                Map.entry("سمير-الباز", affiliation("PML", "w3hjGY-Fcjo")),
                Map.entry("سليمه-غريطه", affiliation("PRD", "scW1dED_R28")),
                Map.entry("باني-محمد-ولد-بركه", affiliation("ALAMAL", "SS8hBq5eGe0")));

        Map<String, CuratedPerson> bySlug = personRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(CuratedPerson::slug, Function.identity()));

        assertThat(bySlug).containsKeys(expected.keySet().toArray(String[]::new));
        expected.forEach((slug, expectedAffiliation) -> {
            CuratedPerson person = bySlug.get(slug);
            assertThat(person.partyCode()).as(slug).isEqualTo(expectedAffiliation.partyCode());
            assertThat(person.affiliations()).as(slug).singleElement().satisfies(affiliation -> {
                assertThat(affiliation.sourceUrl()).isEqualTo(expectedAffiliation.sourceUrl());
                assertThat(affiliation.sourceLabel()).isNotBlank();
            });
        });
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

    private static ExpectedAffiliation affiliation(String partyCode, String youtubeVideoId) {
        return new ExpectedAffiliation(partyCode, "https://www.youtube.com/watch?v=" + youtubeVideoId);
    }

    private record ExpectedAffiliation(String partyCode, String sourceUrl) {
    }
}
