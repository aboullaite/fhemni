package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PartyDirectoryTest {

    private static final List<PoliticalParty> FIXTURE = List.of(
            new PoliticalParty("RNI", "Rassemblement National des Indépendants", "التجمع الوطني للأحرار", "#1B7FC1", true),
            new PoliticalParty("PJD", "Parti de la Justice et du Développement", "حزب العدالة والتنمية", "#E8A90C", true),
            new PoliticalParty("FFD", "Front des Forces Démocratiques", "جبهة القوى الديمقراطية", "#7CB342", true),
            new PoliticalParty("UNKNOWN", "Affiliation non renseignée", "الانتماء غير معروف", "#9E9E9E", false));

    private final PartyDirectory parties = new PartyDirectory(FIXTURE);

    @Test
    void indexesLoadedPartiesWithValidColors() {
        assertThat(parties.findAll())
                .extracting(PoliticalParty::code)
                .containsExactly("RNI", "PJD", "FFD", "UNKNOWN");

        assertThat(parties.findAll())
                .allSatisfy(party -> assertThat(PartyDirectory.validColor(party.color()))
                        .as("color of %s", party.code())
                        .isTrue());
    }

    @Test
    void resolvesCodesCaseInsensitively() {
        assertThat(parties.findByCode("pjd")).isPresent();
        assertThat(parties.findByCode(" PJD ")).isPresent();
        assertThat(parties.findByCode(null)).isEmpty();
        assertThat(parties.findByCode("XX")).isEmpty();
    }

    @Test
    void rejectsUnknownPartyCodes() {
        assertThatThrownBy(() -> parties.required("XX"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void exposesAnUnknownFallbackInsteadOfGuessing() {
        assertThat(parties.fallback().code()).isEqualTo(PartyDirectory.UNKNOWN);
    }
}
