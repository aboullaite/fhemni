package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.government.GovernmentService;
import dev.maboullaite.fhemni.government.GovernmentViews.MandateView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GovernmentController {
    private final GovernmentService service;
    public GovernmentController(GovernmentService service){this.service=service;}
    @GetMapping("/api/catalog/government/{mandateId}")
    public ResponseEntity<MandateView> mandate(@PathVariable String mandateId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.mandate(mandateId));
    }
}
