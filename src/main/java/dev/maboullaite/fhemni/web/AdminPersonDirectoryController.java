package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.catalog.PersonDirectoryAdminService;
import dev.maboullaite.fhemni.catalog.PersonDirectoryAdminService.AffiliationCommand;
import dev.maboullaite.fhemni.catalog.PersonDirectoryAdminService.CuratePerson;
import dev.maboullaite.fhemni.catalog.PersonDirectoryAdminService.DirectoryWorkbench;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only guest identity and affiliation editor. */
@RestController
@RequestMapping("/api/admin/people")
public class AdminPersonDirectoryController {

    private final PersonDirectoryAdminService directory;

    public AdminPersonDirectoryController(PersonDirectoryAdminService directory) {
        this.directory = directory;
    }

    @GetMapping
    public ResponseEntity<DirectoryWorkbench> workbench() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(directory.workbench());
    }

    @PostMapping
    public ResponseEntity<Void> curate(@RequestBody CuratePerson request) {
        directory.curate(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/{slug}/affiliations")
    public ResponseEntity<Void> addAffiliation(
            @PathVariable String slug,
            @RequestBody AffiliationCommand request) {
        directory.addAffiliation(slug, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PutMapping("/{slug}/affiliations/{id}")
    public ResponseEntity<Void> updateAffiliation(
            @PathVariable String slug,
            @PathVariable long id,
            @RequestBody AffiliationCommand request) {
        directory.updateAffiliation(slug, id, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
