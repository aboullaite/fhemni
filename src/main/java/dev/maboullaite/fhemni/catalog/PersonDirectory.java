package dev.maboullaite.fhemni.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mapping between speaker names found in episode reports and political
 * parties.
 *
 * <p>Reference data lives in the {@code directory_persons} and
 * {@code person_aliases} tables (Flyway migration V14, seeded from verified
 * Moroccan press sources); this class only indexes one loaded snapshot for
 * matching. Only the party code is stored — never a leadership title, which
 * changes more often. Any name that is not curated resolves to a dynamic
 * entry with party {@code UNKNOWN}, so the catalogue shows "affiliation non
 * renseignée" instead of guessing.
 */
public class PersonDirectory {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9\\u0600-\\u06FF]+");

    private final List<CuratedPerson> curated;
    private final Pattern honorificPrefix;

    private final Map<String, CuratedPerson> byNormalizedAlias = new LinkedHashMap<>();
    private final Map<String, CuratedPerson> bySlug = new LinkedHashMap<>();

    public PersonDirectory(List<CuratedPerson> curated, List<String> honorifics) {
        this.curated = List.copyOf(curated);
        String alternatives = honorifics.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(prefix -> Pattern.quote(prefix.strip()))
                .sorted()
                .collect(Collectors.joining("|"));
        this.honorificPrefix = alternatives.isEmpty()
                ? null
                : Pattern.compile("^(" + alternatives + ")\\s+");
        for (CuratedPerson person : curated) {
            bySlug.put(person.slug(), person);
            byNormalizedAlias.putIfAbsent(normalize(person.displayNameFr()), person);
            byNormalizedAlias.putIfAbsent(normalize(person.displayNameAr()), person);
            for (String alias : person.aliases()) {
                byNormalizedAlias.putIfAbsent(normalize(alias), person);
            }
        }
    }

    public List<CuratedPerson> curated() {
        return curated;
    }

    public Optional<CuratedPerson> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySlug.get(slug.strip().toLowerCase(Locale.ROOT)));
    }

    /**
     * Resolves a raw speaker name to a stable identity. Curated names carry
     * verified French and Arabic spellings. Unknown names produce a dynamic
     * identity with party {@code UNKNOWN} instead of guessing, shown exactly
     * as written in the episode.
     */
    public ResolvedPerson resolve(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return new ResolvedPerson(
                    "unknown-speaker", "Intervenant non identifié", "متدخل غير معروف",
                    PartyDirectory.UNKNOWN, false, List.of());
        }
        String display = rawName.strip();
        CuratedPerson curated = byNormalizedAlias.get(normalize(display));
        if (curated == null) {
            String withoutHonorifics = stripHonorifics(display);
            if (!withoutHonorifics.equals(display)) {
                curated = byNormalizedAlias.get(normalize(withoutHonorifics));
            }
        }
        if (curated != null) {
            List<String> spellings = new ArrayList<>();
            spellings.add(curated.displayNameFr());
            spellings.add(curated.displayNameAr());
            spellings.addAll(curated.aliases());
            return new ResolvedPerson(curated.slug(), curated.displayNameFr(), curated.displayNameAr(),
                    curated.partyCode(), true, List.copyOf(spellings));
        }
        String canonical = stripHonorifics(display);
        if (canonical.isEmpty()) {
            canonical = display;
        }
        return new ResolvedPerson(slugify(canonical), canonical, canonical,
                PartyDirectory.UNKNOWN, false, List.of(display, canonical));
    }

    String stripHonorifics(String value) {
        if (honorificPrefix == null) {
            return value.strip();
        }
        String current = value.strip();
        String stripped = honorificPrefix.matcher(current).replaceFirst("");
        while (!stripped.equals(current)) {
            current = stripped.strip();
            stripped = honorificPrefix.matcher(current).replaceFirst("");
        }
        return current;
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String ascii = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD)).replaceAll("");
        String lowered = ascii.toLowerCase(Locale.ROOT);
        // Unify common Arabic letter variants so search matches across spellings.
        String unified = lowered
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ة', 'ه')
                .replace('ى', 'ي');
        return NON_WORD.matcher(unified).replaceAll(" ").strip().replaceAll("\\s+", " ");
    }

    public static String slugify(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return "unknown-speaker";
        }
        String slug = normalized.replace(' ', '-').replaceAll("[^-a-z0-9\\u0600-\\u06FF]", "");
        slug = slug.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "unknown-speaker" : slug;
    }

    public record CuratedPerson(
            String slug,
            String displayNameFr,
            String displayNameAr,
            String partyCode,
            List<String> aliases) {
    }

    public record ResolvedPerson(
            String slug,
            String displayNameFr,
            String displayNameAr,
            String partyCode,
            boolean curated,
            List<String> spellings) {

        /** French/English interface name. */
        public String displayName() {
            return displayNameFr;
        }
    }
}
