package dev.maboullaite.fhemni.web;
import dev.maboullaite.fhemni.government.*;
import dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/catalog/government/2026-2031")
public class ProgrammeDiscoveryController {
    private final ProgrammeDiscovery discovery;
    private final ProgrammeRegionalService regions;
    public ProgrammeDiscoveryController(ProgrammeDiscovery discovery,ProgrammeRegionalService regions){this.discovery=discovery;this.regions=regions;}
    @GetMapping("/programme-topics") public ResponseEntity<List<DiscoveryTopic>> topics(){return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(discovery.topics());}
    @GetMapping("/regional-programme") public ResponseEntity<RegionalProgrammePage> regions(@RequestParam(required=false)String region,@RequestParam(required=false)String topic,@RequestParam(required=false,name="q")String query,@RequestParam(defaultValue="1")int page){
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(regions.list("2026-2031",region,topic,query,page));
    }
}
