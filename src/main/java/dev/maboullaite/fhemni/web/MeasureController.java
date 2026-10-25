package dev.maboullaite.fhemni.web;
import dev.maboullaite.fhemni.government.*;
import dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/catalog/government")
public class MeasureController {
    private final MeasureService measures;private final GeographicDeliveryService geography;
    public MeasureController(MeasureService measures,GeographicDeliveryService geography){this.measures=measures;this.geography=geography;}
    @GetMapping("/{id}/measures") public ResponseEntity<Page<MeasureView>> list(@PathVariable String id,@RequestParam(defaultValue="1")int page){return publicData(measures.list(id,page));}
    @GetMapping("/measures/{slug}") public ResponseEntity<MeasureView> detail(@PathVariable String slug){return publicData(measures.detail(slug));}
    @GetMapping("/regions") public ResponseEntity<List<Scope>> regions(){return publicData(geography.regions());}
    @GetMapping("/{id}/observations") public ResponseEntity<Page<GeographicObservation>> observations(@PathVariable String id,@RequestParam(required=false)String region,@RequestParam(defaultValue="1")int page){return publicData(geography.list(id,region,page));}
    private <T>ResponseEntity<T> publicData(T body){return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(body);}
}
