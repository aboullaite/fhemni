package dev.maboullaite.fhemni.catalog;

/**
 * A Moroccan political party reference entry.
 *
 * <p>Names follow the party's commonly used French and Arabic designations.
 * The color is an indicative UI tint close to the party's visual identity
 * (no logos are stored, to avoid third-party trademark assets).
 */
public record PoliticalParty(
        String code,
        String nameFr,
        String nameAr,
        String color,
        boolean visible) {
}
