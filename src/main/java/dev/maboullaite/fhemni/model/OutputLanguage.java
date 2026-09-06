package dev.maboullaite.fhemni.model;

import java.util.Arrays;

public enum OutputLanguage {
    DARIJA("ary", "Moroccan Darija (الدارجة)", true),
    FRENCH("fr", "French (Français)", false),
    ENGLISH("en", "English", false);

    private final String code;
    private final String displayName;
    private final boolean rightToLeft;

    OutputLanguage(String code, String displayName, boolean rightToLeft) {
        this.code = code;
        this.displayName = displayName;
        this.rightToLeft = rightToLeft;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public boolean rightToLeft() {
        return rightToLeft;
    }

    public static OutputLanguage fromCode(String code) {
        if ("ar".equalsIgnoreCase(code)) {
            return DARIJA;
        }
        return Arrays.stream(values())
                .filter(language -> language.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + code));
    }
}
