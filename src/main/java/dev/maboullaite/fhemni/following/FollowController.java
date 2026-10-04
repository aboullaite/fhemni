package dev.maboullaite.fhemni.following;
import static dev.maboullaite.fhemni.following.FollowRepository.*;
import dev.maboullaite.fhemni.identity.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/account")
public class FollowController {
    private final FollowService follows;private final UpdateFeedService updates;private final CurrentUserService users;
    public FollowController(FollowService follows,UpdateFeedService updates,CurrentUserService users){this.follows=follows;this.updates=updates;this.users=users;}
    @GetMapping("/follows") public ResponseEntity<?> list(Authentication auth){return privateData(follows.list(actor(auth)));}
    @GetMapping("/follows/{type}/{id}") public ResponseEntity<?> status(@PathVariable String type,@PathVariable String id,Authentication auth){return privateData(Map.of("following",follows.following(actor(auth),new FollowTarget(type,id))));}
    @PutMapping("/follows/{type}/{id}") public ResponseEntity<?> follow(@PathVariable String type,@PathVariable String id,Authentication auth){return change(type,id,auth,true);}
    @DeleteMapping("/follows/{type}/{id}") public ResponseEntity<?> unfollow(@PathVariable String type,@PathVariable String id,Authentication auth){return change(type,id,auth,false);}
    @GetMapping("/updates") public ResponseEntity<?> updates(@RequestParam(defaultValue="1")int page,Authentication auth){return privateData(updates.list(actor(auth),page));}
    private ResponseEntity<?> change(String type,String id,Authentication auth,boolean enabled){return privateData(Map.of("following",follows.change(actor(auth),new FollowTarget(type,id),enabled)));}
    private UUID actor(Authentication auth){return users.find(auth).orElseThrow(()->new AuthenticationCredentialsNotFoundException("Sign in to follow updates.")).id();}
    private <T>ResponseEntity<T> privateData(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
