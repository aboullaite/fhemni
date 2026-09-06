package dev.maboullaite.fhemni.analysis;

import dev.maboullaite.fhemni.model.AnalysisStatus;

public record AnalysisEvent(AnalysisStatus status, int progress, String message) {
}
