package dev.maboullaite.fhemni.memberanalysis;
import java.net.URI;
import java.util.*;
import dev.maboullaite.fhemni.identity.*;
import dev.maboullaite.fhemni.model.QuestionMode;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
@RestController @RequestMapping("/api/account/analyses")
public class MemberAnalysisController {
    private final MemberAnalysisAdmission admission;private final MemberAnalysisPolicy policy;private final MemberAnalysisAccess access;private final MemberAnalysisRepository repository;private final MemberAnalysisService service;private final MemberQuestionService questions;private final CurrentUserService users;private final ObjectMapper mapper;
    public MemberAnalysisController(MemberAnalysisAdmission admission,MemberAnalysisPolicy policy,MemberAnalysisAccess access,MemberAnalysisRepository repository,MemberAnalysisService service,MemberQuestionService questions,CurrentUserService users,ObjectMapper mapper){this.admission=admission;this.policy=policy;this.access=access;this.repository=repository;this.service=service;this.questions=questions;this.users=users;this.mapper=mapper;}
    public record Input(String url,String language,String idempotencyKey) {} public record Question(String question,QuestionMode mode) {} public record PublishInput(boolean confirmed) {}
    @GetMapping("/policy") public ResponseEntity<?> policy(Authentication auth){return response(policy.forUser(actor(auth)));}
    @PostMapping public ResponseEntity<?> create(Authentication auth,@RequestBody Input input){var job=admission.submit(actor(auth),input.url(),input.language(),input.idempotencyKey());return ResponseEntity.accepted().location(URI.create("/api/account/analyses/"+job.id())).cacheControl(CacheControl.noStore()).body(job);}
    @GetMapping public ResponseEntity<?> history(Authentication auth,@RequestParam(defaultValue="1")int page){return response(repository.history(actor(auth),page));}
    @GetMapping("/{id}") public ResponseEntity<?> get(Authentication auth,@PathVariable UUID id){return response(access.requireOwner(id,actor(auth)));}
    @GetMapping(value="/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public ResponseEntity<?> events(Authentication auth,@PathVariable UUID id){var job=access.requireOwner(id,actor(auth));return response("event: state\ndata: "+mapper.writeValueAsString(Map.of("id",job.id(),"state",job.state()))+"\n\n");}
    @GetMapping("/{id}/questions") public ResponseEntity<?> answers(Authentication auth,@PathVariable UUID id){return response(questions.answers(id,actor(auth)));}
    @GetMapping("/{id}/question-status") public ResponseEntity<?> questionStatus(Authentication auth,@PathVariable UUID id){return response(Map.of("state",questions.status(id,actor(auth))));}
    @PostMapping("/{id}/questions") public ResponseEntity<?> ask(Authentication auth,@PathVariable UUID id,@RequestBody Question input){return response(questions.ask(id,actor(auth),input.question(),input.mode()));}
    @GetMapping("/{id}/publication-request") public ResponseEntity<?> publication(Authentication auth,@PathVariable UUID id){return response(Map.of("state",service.publicationStatus(id,actor(auth))));}
    @PostMapping("/{id}/publication-request") public ResponseEntity<?> request(Authentication auth,@PathVariable UUID id,@RequestBody PublishInput input){UUID user=actor(auth);service.requestPublication(id,user,input.confirmed());return response(Map.of("state",service.publicationStatus(id,user)));}
    private UUID actor(Authentication auth){return users.find(auth).orElseThrow(()->new AuthenticationCredentialsNotFoundException("Sign in to your private analyses.")).id();}
    private <T>ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
