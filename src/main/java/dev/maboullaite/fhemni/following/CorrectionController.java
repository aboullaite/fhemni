package dev.maboullaite.fhemni.following;
import dev.maboullaite.fhemni.identity.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
@RestController
public class CorrectionController {
    private final CorrectionService corrections;private final CurrentUserService users;
    public CorrectionController(CorrectionService corrections,CurrentUserService users){this.corrections=corrections;this.users=users;}
    @PostMapping("/api/account/corrections") public ResponseEntity<?> submit(@RequestBody CorrectionService.Submission value,Authentication auth){return privateData(corrections.submit(actor(auth,false),value));}
    @GetMapping("/api/account/corrections/{id}") public ResponseEntity<?> own(@PathVariable UUID id,Authentication auth){return privateData(corrections.own(actor(auth,false),id));}
    @GetMapping("/api/admin/government/corrections") public ResponseEntity<?> queue(@RequestParam(defaultValue="1")int page,Authentication auth){actor(auth,true);return privateData(corrections.queue(page));}
    public record Review(String status,String note) {}
    @PostMapping("/api/admin/government/corrections/{id}/review") public ResponseEntity<?> review(@PathVariable UUID id,@RequestBody Review value,Authentication auth){corrections.review(id,actor(auth,true),value.status(),value.note());return privateData(Map.of("reviewed",true));}
    private UUID actor(Authentication auth,boolean admin){var user=users.find(auth).orElseThrow(()->new AuthenticationCredentialsNotFoundException("Sign in to submit a correction."));if(admin&&user.role()!=UserRole.ADMIN)throw new AccessDeniedException("Administrator required.");return user.id();}
    private <T>ResponseEntity<T> privateData(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
