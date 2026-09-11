package dev.maboullaite.fhemni.programme.media;

final class ProgrammeMediaLeaseLostException extends RuntimeException {

    ProgrammeMediaLeaseLostException() {
        super("The programme media lease is no longer owned by this worker.");
    }
}
