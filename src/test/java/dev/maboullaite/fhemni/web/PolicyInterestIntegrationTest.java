package dev.maboullaite.fhemni.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.maboullaite.fhemni.identity.ExternalIdentityProfile;
import dev.maboullaite.fhemni.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:policy-interest-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class PolicyInterestIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserAccountRepository users;

    @Test
    void exposesTenPublicBroadTopicsAndKeepsNuanceTopicsReadOnly() throws Exception {
        mvc.perform(get("/api/catalog/policy-topics"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=3600")))
                .andExpect(jsonPath("$.maxSelections").value(3))
                .andExpect(jsonPath("$.topics[?(@.selectable == true)]", hasSize(10)))
                .andExpect(jsonPath("$.topics[?(@.code == 'EDUCATION_RESEARCH')].parentCode")
                        .value(contains("EDUCATION")))
                .andExpect(jsonPath("$.topics[?(@.code == 'EMPLOYMENT_FIRST_JOB')].selectable")
                        .value(contains(false)));
    }

    @Test
    void synchronizesAtMostThreeOrderedInterestsForAnAuthenticatedAccount() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "priority-user", null, "Priority User", null, false, null), false);
        var account = oauth2Login().attributes(attributes -> attributes.put("sub", "priority-user"));

        mvc.perform(get("/api/account/policy-topics"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/account/policy-topics")
                        .with(account)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicCodes":["EMPLOYMENT","EDUCATION","HEALTH"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.topicCodes", contains("EMPLOYMENT", "EDUCATION", "HEALTH")));

        mvc.perform(get("/api/account/policy-topics").with(account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicCodes", contains("EMPLOYMENT", "EDUCATION", "HEALTH")));

        mvc.perform(put("/api/account/policy-topics")
                        .with(account)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicCodes":["EMPLOYMENT","EDUCATION","HEALTH","HOUSING"]}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/account/policy-topics").with(account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicCodes", contains("EMPLOYMENT", "EDUCATION", "HEALTH")));
    }

    @Test
    void rejectsSubtopicsAndDuplicateTopicsAsAccountPreferences() throws Exception {
        users.recordLogin(new ExternalIdentityProfile(
                "test", "invalid-priority-user", null, "Invalid User", null, false, null), false);
        var account = oauth2Login().attributes(attributes -> attributes.put("sub", "invalid-priority-user"));

        mvc.perform(put("/api/account/policy-topics")
                        .with(account)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicCodes":["EDUCATION_RESEARCH"]}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/account/policy-topics")
                        .with(account)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicCodes":["EDUCATION","education"]}
                                """))
                .andExpect(status().isBadRequest());
    }
}
