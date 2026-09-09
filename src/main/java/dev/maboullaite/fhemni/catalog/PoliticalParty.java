package dev.maboullaite.fhemni.catalog;

/**
 * A Moroccan political party reference entry.
 *
 * <p>Names follow the party's commonly used French and Arabic designations.
 * The color is an indicative UI tint close to the party's visual identity.
 * Symbol assets are hosted locally: verified catalogue artwork is used when
 * its provenance is recorded, otherwise the UI falls back to a neutral mark.
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
