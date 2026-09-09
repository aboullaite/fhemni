package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.identity.AppUser;
import dev.maboullaite.fhemni.identity.CurrentUserService;
import dev.maboullaite.fhemni.programme.ProgrammeChatAnswer;
import dev.maboullaite.fhemni.programme.ProgrammeChatService;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/parties")
public class ProgrammeChatController {

    private final ProgrammeChatService chat;
    private final CurrentUserService currentUser;

    public ProgrammeChatController(ProgrammeChatService chat, CurrentUserService currentUser) {
        this.chat = chat;
        this.currentUser = currentUser;
    }

    @PostMapping("/{code}/programme/questions")
    public ProgrammeChatAnswer ask(
            @PathVariable String code,
            @RequestBody AskProgrammeQuestion request,
            Authentication authentication) {
        AppUser user = currentUser.find(authentication)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Sign in to ask about this programme."));
        return chat.ask(code, user.id(), request.question(), request.language());
    }

    public record AskProgrammeQuestion(String question, String language) {
    }
}
