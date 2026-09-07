package dev.maboullaite.fhemni.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * In-memory view over the curated political parties.
 *
 * <p>Reference data lives in the {@code political_parties} table (Flyway
 * migration V14, seeded from verified sources); this class only indexes one
 * loaded snapshot for matching. Unknown or unaffiliated speakers resolve to
 * {@code UNKNOWN} so the catalogue never invents an affiliation.
 */
public class PartyDirectory {

    /**
     * Reference code of the catch-all entry tagging speakers without a verified
     * affiliation. This contract code must exist in {@code political_parties}.
     */
    public static final String UNKNOWN = "UNKNOWN";

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    private final List<PoliticalParty> parties;
    private final Map<String, PoliticalParty> byCode;

    public PartyDirectory(List<PoliticalParty> parties) {
        this.parties = List.copyOf(parties);
        this.byCode = this.parties.stream()
                .collect(Collectors.toUnmodifiableMap(PoliticalParty::code, Function.identity()));
    }

    public List<PoliticalParty> findAll() {
        return parties;
    }

    public Optional<PoliticalParty> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(code.strip().toUpperCase(Locale.ROOT)));
    }

    public PoliticalParty required(String code) {
        return findByCode(code)
                .orElseThrow(() -> new java.util.NoSuchElementException("Unknown political party: " + code));
    }

    public PoliticalParty fallback() {
        PoliticalParty unknown = byCode.get(UNKNOWN);
        if (unknown == null) {
            throw new IllegalStateException("The political_parties reference table is missing its UNKNOWN entry.");
        }
        return unknown;
    }

    public static boolean validColor(String color) {
        return color != null && HEX_COLOR.matcher(color).matches();
    }
}
