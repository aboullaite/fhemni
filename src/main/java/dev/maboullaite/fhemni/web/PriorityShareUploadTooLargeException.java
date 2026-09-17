package dev.maboullaite.fhemni.web;

final class PriorityShareUploadTooLargeException extends RuntimeException {

    PriorityShareUploadTooLargeException() {
        super("The share card must be 2 MB or smaller.");
    }
}
