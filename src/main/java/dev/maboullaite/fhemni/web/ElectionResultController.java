package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.util.NoSuchElementException;

import dev.maboullaite.fhemni.election.CoalitionEvaluationService;
import dev.maboullaite.fhemni.election.CoalitionEvaluationService.CoalitionEvaluation;
import dev.maboullaite.fhemni.election.CoalitionEvaluationService.CoalitionRequest;
import dev.maboullaite.fhemni.election.ElectionResultService;
import dev.maboullaite.fhemni.election.ElectionResultService.ElectionResultSnapshot;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ElectionResultController {

    private static final CacheControl SHORT_PUBLIC_CACHE = CacheControl.maxAge(Duration.ofSeconds(5)).cachePublic();

    private final ElectionResultService results;
    private final CoalitionEvaluationService coalitions;

    public ElectionResultController(ElectionResultService results,
                                    CoalitionEvaluationService coalitions) {
        this.results = results;
        this.coalitions = coalitions;
    }

    @GetMapping("/api/catalog/elections/{year}/results")
    public ResponseEntity<ElectionResultSnapshot> result(
            @PathVariable String year,
            @RequestParam(name = "lang", defaultValue = "ar") String language) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(SHORT_PUBLIC_CACHE)
                    .body(results.result(slug(year), language));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/api/catalog/elections/{year}/coalitions/evaluate")
    public ResponseEntity<CoalitionEvaluation> evaluate(
            @PathVariable String year,
            @RequestBody CoalitionRequest request) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(coalitions.evaluate(slug(year), request));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    private static String slug(String year) {
        if (!"2026".equals(year)) {
            throw new NoSuchElementException("Election not found: " + year);
        }
        return ElectionResultService.ELECTION_2026;
    }
}
