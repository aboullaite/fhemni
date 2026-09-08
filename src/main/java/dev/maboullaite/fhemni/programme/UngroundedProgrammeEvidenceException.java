package dev.maboullaite.fhemni.programme;

/** Identifies the one promise that a provider could not support with a verified citation. */
public final class UngroundedProgrammeEvidenceException extends IllegalArgumentException {

    private final String promiseSlug;

    public UngroundedProgrammeEvidenceException(String promiseSlug) {
        super("No grounded evidence was returned for promise " + promiseSlug + ".");
        this.promiseSlug = promiseSlug;
    }

    public String promiseSlug() {
        return promiseSlug;
    }
}
