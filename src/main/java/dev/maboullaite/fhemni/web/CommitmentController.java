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
    public ResponseEntity<Page<CommitmentView>> list(@PathVariable String id,@RequestParam(required=false)String origin,@RequestParam(required=false)String topic,@RequestParam(required=false)String party,@RequestParam(required=false)String q,@RequestParam(required=false)String assessment,@RequestParam(defaultValue="1")int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.list(id,origin,topic,party,q,assessment,page));
    }
    @GetMapping("/commitments/{slug}")
    public ResponseEntity<CommitmentView> detail(@PathVariable String slug){return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.detail(slug));}
    @GetMapping("/{id}/programme-insights")
    public ResponseEntity<ProgrammeInsights> insights(@PathVariable String id,@RequestParam(required=false)String origin,@RequestParam(required=false)String topic,@RequestParam(required=false)String party,@RequestParam(required=false)String q,@RequestParam(required=false)String assessment) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.insights(id,origin,topic,party,q,assessment));
    }
    @GetMapping("/{id}/deadlines")
    public ResponseEntity<java.util.List<CommitmentView>> deadlines(@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.upcoming(id));}
}
