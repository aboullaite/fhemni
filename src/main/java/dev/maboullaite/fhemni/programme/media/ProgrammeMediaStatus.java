package dev.maboullaite.fhemni.programme.media;

public enum ProgrammeMediaStatus {
    QUEUED_SCRIPT,
    GENERATING_SCRIPT,
    SCRIPT_REVIEW,
    QUEUED_MEDIA,
    RENDERING_MEDIA,
    MEDIA_REVIEW,
    PUBLISHED,
    FAILED,
    STALE
}
