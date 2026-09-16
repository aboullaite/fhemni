package dev.maboullaite.fhemni.civic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class CivicPositionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CivicPositionSeeder.class);
    private static final String SEED_FILE_PROPERTY = "fhemni.seed-positions";

    private final CivicPartyPositionService positionService;

    CivicPositionSeeder(CivicPartyPositionService positionService) {
        this.positionService = positionService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(ApplicationArguments args) throws Exception {
        String seedFile = System.getProperty(SEED_FILE_PROPERTY);
        if (seedFile == null || seedFile.isBlank()) return;

        Path path = Path.of(seedFile);
        if (!Files.exists(path)) {
            log.warn("Seed file not found: {}", path);
            return;
        }

        UUID editionId = UUID.fromString("c1000000-0000-4000-8000-000000000001");
        String json = Files.readString(path);
        JacksonJsonParser parser = new JacksonJsonParser();
        List<Object> entries = parser.parseList(json);
        log.info("Seeding {} positions from {}", entries.size(), path);

        List<CivicPartyPositionService.PositionDraft> drafts = new ArrayList<>();
        for (Object obj : entries) {
            Map<String, Object> e = (Map<String, Object>) obj;
            drafts.add(new CivicPartyPositionService.PositionDraft(
                    (String) e.get("questionKey"),
                    (String) e.get("partyCode"),
                    PartyPositionStance.valueOf((String) e.get("stance")),
                    (String) e.getOrDefault("evidenceSummaryAr", ""),
                    (String) e.getOrDefault("evidenceSummaryFr", ""),
                    (String) e.getOrDefault("evidenceSummaryEn", ""),
                    (String) e.getOrDefault("reviewerNote", "")));
        }

        int created = positionService.batchUpsert(editionId, drafts);
        log.info("Batch upsert complete: {} positions", created);

        int published = positionService.publishAllDrafts(editionId);
        log.info("Published {} positions", published);
    }
}
