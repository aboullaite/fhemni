package dev.maboullaite.fhemni.web;

import dev.maboullaite.fhemni.identity.LoginSuccessHandler;
import dev.maboullaite.fhemni.identity.LoginReturnTargetCookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final LoginReturnTargetCookie returnTarget;

    public PageController(LoginReturnTargetCookie returnTarget) {
        this.returnTarget = returnTarget;
    }

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

    @GetMapping({"/parties/compare", "/parties/compare/"})
    public String compareProgrammesPage() {
        return "forward:/compare-programmes.html";
    }

    @GetMapping({"/people/{slug}", "/people/{slug}/"})
    public String personPage(@PathVariable String slug) {
        return "forward:/person.html";
    }

    @GetMapping({"/parties/{code}", "/parties/{code}/"})
    public String partyPage(@PathVariable String code) {
        return "forward:/party.html";
    }

    @GetMapping({"/promises/{slug}", "/promises/{slug}/"})
    public String promisePage(@PathVariable String slug) {
        return "forward:/promise.html";
    }

    @GetMapping({"/community", "/community/"})
    public String communityPage() {
        return "forward:/community.html";
    }

    @GetMapping({"/methodology", "/methodology/"})
    public String methodologyPage() {
        return "forward:/methodology.html";
    }

    @GetMapping({"/suggestions", "/suggestions/"})
    public String suggestionsAlias() {
        return "redirect:/community";
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(name = "continue", required = false) String returnTo,
            HttpServletResponse response) {
        if (LoginSuccessHandler.safeLocalPath(returnTo)) {
            returnTarget.save(response, returnTo);
        }
        return "forward:/login.html";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "forward:/admin.html";
    }

    @GetMapping({"/admin/episodes", "/admin/episodes/"})
    public String adminEpisodesPage() {
        return "forward:/admin-episodes.html";
    }

    @GetMapping({"/admin/suggestions", "/admin/suggestions/"})
    public String adminSuggestionsPage() {
        return "forward:/admin-suggestions.html";
    }

    @GetMapping({"/admin/programmes", "/admin/programmes/"})
    public String adminProgrammesPage() {
        return "forward:/admin-programmes.html";
    }

    @GetMapping({"/admin/people", "/admin/people/"})
    public String adminPeoplePage() {
        return "forward:/admin-people.html";
    }
}
