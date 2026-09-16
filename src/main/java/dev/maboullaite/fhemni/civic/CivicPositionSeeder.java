package dev.maboullaite.fhemni.civic;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class CivicPositionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CivicPositionSeeder.class);
    private static final String SEED_FILE_PROPERTY = "fhemni.seed-positions";

    private final CivicPartyPositionService positionService;
    private final ResourceLoader resources;
    private final String configuredSeed;

    CivicPositionSeeder(CivicPartyPositionService positionService,
                        ResourceLoader resources,
                        @Value("${fhemni.civic.position-seed:}") String configuredSeed) {
        this.positionService = positionService;
        this.resources = resources;
        this.configuredSeed = configuredSeed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(ApplicationArguments args) throws Exception {
        String location = configuredSeed;
        String legacySeedFile = System.getProperty(SEED_FILE_PROPERTY);
        if ((location == null || location.isBlank()) && legacySeedFile != null && !legacySeedFile.isBlank()) {
            location = Path.of(legacySeedFile).toAbsolutePath().toUri().toString();
        }
        if (location == null || location.isBlank()) return;

        Resource resource = resources.getResource(location);
        if (!resource.exists()) {
            log.warn("Civic position seed not found: {}", location);
            return;
        }

        UUID editionId = UUID.fromString("c1000000-0000-4000-8000-000000000001");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        JacksonJsonParser parser = new JacksonJsonParser();
        List<Object> entries = parser.parseList(json);
        log.info("Preparing {} civic positions from {}", entries.size(), location);

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

        int published = positionService.seedPublishedPositions(editionId, drafts);
        if (published == 0) {
            log.info("Civic positions already exist for edition {}; seed skipped", editionId);
        } else {
            log.info("Seeded and published {} civic positions", published);
        }
    }
}
