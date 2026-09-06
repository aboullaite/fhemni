package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.util.List;

import dev.maboullaite.fhemni.catalog.PersonCatalogService;
import dev.maboullaite.fhemni.catalog.PersonCatalogService.PartyProfile;
import dev.maboullaite.fhemni.catalog.PersonCatalogService.PartySummary;
import dev.maboullaite.fhemni.catalog.PersonCatalogService.PersonProfile;
import dev.maboullaite.fhemni.catalog.PersonCatalogService.PersonSummary;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only guest and party sheets, aggregated from published analyses only.
 */
@RestController
@RequestMapping("/api/catalog")
public class PublicPeopleController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final PersonCatalogService people;

    public PublicPeopleController(PersonCatalogService people) {
        this.people = people;
    }

    @GetMapping("/people")
    public ResponseEntity<List<PersonSummary>> people(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String party) {
        if (q != null && q.length() > 200) {
            throw new IllegalArgumentException("Search query must be under 200 characters.");
        }
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(people.searchPeople(q, party));
    }

    @GetMapping("/people/{slug}")
    public ResponseEntity<PersonProfile> person(@PathVariable String slug) {
        PersonProfile profile = people.person(slug);
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(profile);
    }

    @GetMapping("/parties")
    public ResponseEntity<List<PartySummary>> parties() {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(people.parties());
    }

    @GetMapping("/parties/{code}")
    public ResponseEntity<PartyProfile> party(@PathVariable String code) {
        PartyProfile profile = people.party(code);
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(profile);
    }
}
