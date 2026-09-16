package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import dev.maboullaite.fhemni.civic.CivicCompassService;
import dev.maboullaite.fhemni.civic.CivicCompassService.CompassResult;
import dev.maboullaite.fhemni.civic.CivicPartyPositionService;

import dev.maboullaite.fhemni.civic.CivicProfileService;
import dev.maboullaite.fhemni.civic.CivicProfileService.Profile;
import dev.maboullaite.fhemni.civic.CivicProfileService.ProfileRequest;
import dev.maboullaite.fhemni.civic.CivicQuestionnaire;
import dev.maboullaite.fhemni.civic.CivicQuestionnaireCatalogService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CivicQuestionnaireController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final CivicQuestionnaireCatalogService catalog;
    private final CivicProfileService profiles;
    private final CivicPartyPositionService partyPositions;
    private final CivicCompassService compass;

    public CivicQuestionnaireController(CivicQuestionnaireCatalogService catalog,
                                        CivicProfileService profiles,
                                        CivicPartyPositionService partyPositions,
                                        CivicCompassService compass) {
        this.catalog = catalog;
        this.profiles = profiles;
        this.partyPositions = partyPositions;
        this.compass = compass;
    }

    @GetMapping("/api/catalog/questionnaires/current")
    public ResponseEntity<CivicQuestionnaire> current(
            @RequestParam(name = "lang", defaultValue = "ar") String language) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(catalog.current(language));
    }

    @GetMapping("/api/catalog/questionnaires/{editionId}")
    public ResponseEntity<CivicQuestionnaire> edition(
            @PathVariable UUID editionId,
            @RequestParam(name = "lang", defaultValue = "ar") String language) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(catalog.edition(editionId, language));
    }

    @GetMapping("/api/catalog/questionnaires/{editionId}/methodology")
    public ResponseEntity<Map<String, Object>> methodology(
            @PathVariable UUID editionId,
            @RequestParam(name = "lang", defaultValue = "ar") String language) {
        CivicQuestionnaire questionnaire = catalog.edition(editionId, language);
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(Map.of(
                "editionId", questionnaire.id(),
                "version", questionnaire.version(),
                "language", questionnaire.language(),
                "dataCutoff", questionnaire.dataCutoff(),
                "methodology", questionnaire.methodology()));
    }

    @PostMapping("/api/catalog/questionnaires/current/profile")
    public ResponseEntity<Profile> profile(@RequestBody ProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.profile(request));
    }

    @PostMapping("/api/catalog/questionnaires/current/compass")
    public ResponseEntity<CompassResult> compass(@RequestBody ProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(compass.compass(request));
    }
}
