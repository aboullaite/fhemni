package dev.maboullaite.fhemni.programme;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;

public record PolicyTopic(
        String code,
        String parentCode,
        LocalizedText label,
        int sortOrder,
        boolean selectable) {
}
