package dev.maboullaite.fhemni.civic;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CivicQuestionnaire(
        UUID id,
        String version,
        String language,
        String direction,
        String title,
        String intro,
        String methodology,
        LocalDate dataCutoff,
        List<Theme> themes,
        List<Question> questions) {

    public record Theme(String code, String label, int order) {
    }

    public record Question(
            String key,
            String themeCode,
            String themeLabel,
            int order,
            String prompt,
            String context,
            List<Source> sources) {
    }

    public record Source(String label, String url, LocalDate date) {
    }
}
