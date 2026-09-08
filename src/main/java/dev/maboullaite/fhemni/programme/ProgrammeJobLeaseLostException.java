package dev.maboullaite.fhemni.programme;

final class ProgrammeJobLeaseLostException extends RuntimeException {

    ProgrammeJobLeaseLostException() {
        super("The programme assessment worker no longer owns this job lease.");
    }
}
