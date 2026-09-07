package dev.maboullaite.fhemni.programme;

import java.util.Locale;

public enum ProgrammeFactCheckMode {
    GEMINI,
    OPENAI,
    CONSENSUS;

    public static ProgrammeFactCheckMode from(String value) {
        try {
            return valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Programme fact-check mode must be gemini, openai, or consensus.", exception);
        }
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
