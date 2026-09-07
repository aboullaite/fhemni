package dev.maboullaite.fhemni.catalog;

/**
 * A Moroccan political party reference entry.
 *
 * <p>Names follow the party's commonly used French and Arabic designations.
 * The color is an indicative UI tint close to the party's visual identity.
 * Symbol assets are locally hosted neutral SVG illustrations of the party's
 * recognisable electoral symbol, not copied third-party artwork.
 */
public record PoliticalParty(
        String code,
        String nameFr,
        String nameAr,
        String color,
        String symbolLabelFr,
        String symbolLabelAr,
        String symbolAsset,
        boolean symbolVerified,
        boolean visible) {

    PoliticalParty(String code, String nameFr, String nameAr, String color, boolean visible) {
        this(code, nameFr, nameAr, color, code, code, "/assets/parties/party.svg", false, visible);
    }
}
