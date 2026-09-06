package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.identity.LoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    @GetMapping("/analyses/{analysisId}")
    public String analysisPage(@PathVariable String analysisId) {
        return "forward:/analysis.html";
    }

    @GetMapping({"/videos", "/videos/"})
    public String cataloguePage() {
        return "forward:/videos.html";
    }

    @GetMapping({"/videos/{slug}", "/videos/{slug}/"})
    public String videoPage(@PathVariable String slug) {
        return "forward:/video.html";
    }

    @GetMapping({"/catalog", "/catalog/"})
    public String catalogueAlias() {
        return "redirect:/videos";
    }

    @GetMapping({"/parties", "/parties/"})
    public String partiesPage() {
        return "forward:/parties.html";
    }

    @GetMapping({"/people/{slug}", "/people/{slug}/"})
    public String personPage(@PathVariable String slug) {
        return "forward:/person.html";
    }

    @GetMapping({"/parties/{code}", "/parties/{code}/"})
    public String partyPage(@PathVariable String code) {
        return "forward:/party.html";
    }

    @GetMapping({"/community", "/community/"})
    public String communityPage() {
        return "forward:/community.html";
    }

    @GetMapping({"/suggestions", "/suggestions/"})
    public String suggestionsAlias() {
        return "redirect:/community";
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(name = "continue", required = false) String returnTo,
            HttpServletRequest request) {
        if (LoginSuccessHandler.safeLocalPath(returnTo)) {
            request.getSession(true).setAttribute(LoginSuccessHandler.RETURN_TO_SESSION_ATTRIBUTE, returnTo);
        }
        return "forward:/login.html";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "forward:/admin.html";
    }
}
