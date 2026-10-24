package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.government.ProgrammePreview;
import dev.maboullaite.fhemni.government.ProgrammePreviewData;
import dev.maboullaite.fhemni.identity.MagicLinkRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("programme-preview")
@RequestMapping("/api/catalog/government/2026-2031/preview")
public class ProgrammePreviewController {
    private final ProgrammePreview preview;
    private final MagicLinkRepository links;
    public ProgrammePreviewController(ProgrammePreview preview,MagicLinkRepository links){this.preview=preview;this.links=links;}
    @GetMapping
    public ResponseEntity<?> metadata(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
        "localPreview",true,"sourceStatus","MIRROR_UNAUTHENTICATED","snapshotDate","2026-10-23",
        "publicSourceUrl",ProgrammePreviewData.PUBLIC_SOURCE,"pdfUrl",ProgrammePreviewData.PDF_URL,
        "entryCount",preview.snapshot().commitments().size(),"coalitionPartyCodes",preview.snapshot().coalitionPartyCodes(),
        "sessionUrl","/api/catalog/government/2026-2031/preview/session"));}
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(){return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=programme-research-preview.pdf")
            .body(preview.snapshot().pdf());}
    /** Normal one-use magic-link login, fixed local identity, CSRF protected; no real email or administrator access. */
    @PostMapping("/session")
    public ResponseEntity<?> session(jakarta.servlet.http.HttpServletRequest request){
        if(!java.util.Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(request.getRemoteAddr()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        var token=links.create("reviewer@fhemni.local","/government?tab=programme",Instant.now().plusSeconds(900));
        return token.<ResponseEntity<?>>map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url","/auth/magic-link?token="+value)))
                .orElseGet(()->ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
    }
}
