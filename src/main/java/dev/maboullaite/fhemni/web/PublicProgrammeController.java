package dev.maboullaite.fhemni.web;

import java.time.Duration;
import java.util.List;

import dev.maboullaite.fhemni.programme.PartyProgrammeService;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.PublicProgrammeView;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.PublicPromiseHighlight;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.PublicPromiseView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class PublicProgrammeController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final PartyProgrammeService programmes;

    public PublicProgrammeController(PartyProgrammeService programmes) {
        this.programmes = programmes;
    }

    @GetMapping("/parties/{code}/programme")
    public ResponseEntity<PublicProgrammeView> programme(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(programmes.publishedProgramme(code));
    }

    @GetMapping("/promises/{slug}")
    public ResponseEntity<PublicPromiseView> promise(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(programmes.publishedPromise(slug));
    }

    @GetMapping("/promises")
    public ResponseEntity<List<PublicPromiseHighlight>> promises(
            @RequestParam(defaultValue = "3") int size) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(programmes.featuredPublishedPromises(size));
    }
}
