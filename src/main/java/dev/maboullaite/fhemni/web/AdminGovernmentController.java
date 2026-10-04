package dev.maboullaite.fhemni.web;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import dev.maboullaite.fhemni.government.*;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.identity.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/government/{mandate}")
public class AdminGovernmentController {
    private final GovernmentRepository repository;
    private final GovernmentEvidenceRepository evidence;
    private final GovernmentEditorialService editorial;
    private final CurrentUserService users;
    public AdminGovernmentController(GovernmentRepository repository,GovernmentEvidenceRepository evidence,GovernmentEditorialService editorial,CurrentUserService users){this.repository=repository;this.evidence=evidence;this.editorial=editorial;this.users=users;}
    public record Draft(SubjectType type,String slug,JsonNode payload,String reason){}
    public record Action(long expectedVersion){}
    public record EvidenceEntry(Evidence evidence,byte[] archive){}
    @GetMapping("/drafts")
    public ResponseEntity<List<GovernmentRepository.Revision>> drafts(@PathVariable String mandate,Authentication auth){actor(auth);return noStore(repository.drafts(mandate));}
    @PostMapping("/drafts")
    public ResponseEntity<GovernmentRepository.Revision> draft(@PathVariable String mandate,@RequestBody Draft draft,Authentication auth){
        var user=actor(auth);
        if(draft.type()==null||draft.payload()==null)throw new IllegalArgumentException("Type and payload are required.");
        Object value;
        try { value=switch(draft.type()) {
                case MANDATE->repository.decode(draft.payload().toString(),MandateView.class);
                case COMMITMENT->repository.decode(draft.payload().toString(),CommitmentView.class);
                case MEASURE->throw new IllegalArgumentException("Measures are not yet enabled.");
            };
        } catch(tools.jackson.core.JacksonException invalid) {throw new IllegalArgumentException("Invalid typed draft. Check the field names and values.");}
        return noStore(repository.createDraft(mandate,draft.type(),draft.slug(),value,user,draft.reason()));
    }
    @PostMapping("/drafts/{id}/review")
    public ResponseEntity<GovernmentRepository.Revision> review(@PathVariable String mandate,@PathVariable UUID id,@RequestBody Action action,Authentication auth){var user=actor(auth);checkMandate(mandate,id);return noStore(editorial.review(id,action.expectedVersion(),user));}
    @PostMapping("/drafts/{id}/publish")
    public ResponseEntity<GovernmentRepository.Revision> publish(@PathVariable String mandate,@PathVariable UUID id,@RequestBody Action action,Authentication auth){var user=actor(auth);checkMandate(mandate,id);return noStore(editorial.publish(id,action.expectedVersion(),user));}
    @PostMapping("/evidence") @Transactional
    public ResponseEntity<Evidence> evidence(@PathVariable String mandate,@RequestBody EvidenceEntry entry,Authentication auth){actor(auth);repository.ensureMandate(mandate);return noStore(evidence.save(mandate,entry.evidence(),entry.archive()));}
    @GetMapping("/evidence")
    public ResponseEntity<List<Evidence>> evidenceList(@PathVariable String mandate,Authentication auth){actor(auth);return noStore(evidence.list(mandate));}
    private void checkMandate(String mandate,UUID id){if(!repository.revision(id).orElseThrow().mandateId().equals(mandate))throw new java.util.NoSuchElementException();}
    private UUID actor(Authentication auth){var user=users.find(auth).orElseThrow(()->new AccessDeniedException("A persisted administrator account is required."));if(user.role()!=UserRole.ADMIN)throw new AccessDeniedException("Administrator required.");return user.id();}
    private <T>ResponseEntity<T> noStore(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
