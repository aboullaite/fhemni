package dev.maboullaite.fhemni.web;
import dev.maboullaite.fhemni.government.CommitmentService;
import dev.maboullaite.fhemni.government.GovernmentViews.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/catalog/government")
public class CommitmentController {
    private final CommitmentService service;
    public CommitmentController(CommitmentService service){this.service=service;}
    @GetMapping("/{id}/commitments")
    public ResponseEntity<Page<CommitmentView>> list(@PathVariable String id,@RequestParam(required=false)String origin,@RequestParam(required=false)String topic,@RequestParam(required=false)String party,@RequestParam(defaultValue="1")int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.list(id,origin,topic,party,page));
    }
    @GetMapping("/commitments/{slug}")
    public ResponseEntity<CommitmentView> detail(@PathVariable String slug){return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.detail(slug));}
}
