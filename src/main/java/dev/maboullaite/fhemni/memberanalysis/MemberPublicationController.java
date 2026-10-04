package dev.maboullaite.fhemni.memberanalysis;
import java.util.*;
import dev.maboullaite.fhemni.identity.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/member-analysis-publication-requests")
public class MemberPublicationController {
    private final MemberAnalysisService service;private final CurrentUserService users;
    public MemberPublicationController(MemberAnalysisService service,CurrentUserService users){this.service=service;this.users=users;}
    public record Review(boolean accept,String note) {}
    @GetMapping public ResponseEntity<?> list(Authentication auth,@RequestParam(defaultValue="1")int page){actor(auth);return response(service.requests(page));}
    @GetMapping("/{id}") public ResponseEntity<?> get(Authentication auth,@PathVariable UUID id){actor(auth);return response(service.reviewView(id));}
    @PostMapping("/{id}/review") public ResponseEntity<?> review(Authentication auth,@PathVariable UUID id,@RequestBody Review value){service.review(id,actor(auth),value.accept(),value.note());return response(service.reviewView(id));}
    @PostMapping("/{id}/publish") public ResponseEntity<?> publish(Authentication auth,@PathVariable UUID id){return response(Map.of("derivativeId",service.publish(id,actor(auth))));}
    private UUID actor(Authentication auth){if(!users.isAdministrator(auth))throw new AccessDeniedException("Administrator required.");return users.find(auth).orElseThrow().id();}
    private <T>ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
