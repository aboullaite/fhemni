package dev.maboullaite.fhemni.following;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;
@RestController
public class DigestController {
    private final DigestService service;private final CurrentUserService users;
    public DigestController(DigestService service,CurrentUserService users){this.service=service;this.users=users;}
    public record PreferenceInput(boolean enabled,String locale) {}
    @GetMapping("/api/account/digest") public ResponseEntity<?> get(Authentication auth){return response(service.preferences(actor(auth)));}
    @PutMapping("/api/account/digest") public ResponseEntity<?> update(Authentication auth,@RequestBody PreferenceInput input){return response(service.update(actor(auth),input.enabled(),input.locale()));}
    private UUID actor(Authentication auth){return users.find(auth).orElseThrow(()->new AuthenticationCredentialsNotFoundException("Sign in to manage your digest.")).id();}
    private <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
