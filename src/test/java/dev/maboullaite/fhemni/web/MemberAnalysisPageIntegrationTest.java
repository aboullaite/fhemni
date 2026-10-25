package dev.maboullaite.fhemni.web;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties={"fhemni.gemini.api-key=","spring.datasource.url=jdbc:h2:mem:member-pages;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"}) @AutoConfigureMockMvc
class MemberAnalysisPageIntegrationTest {
 @Autowired MockMvc mvc;
 @Test void shellsContainNoPrivateDataAndApisRequireAuthentication()throws Exception{
  mvc.perform(get("/account/analyses")).andExpect(status().isOk()).andExpect(forwardedUrl("/my-analyses.html"));
  mvc.perform(get("/account/analyses/00000000-0000-0000-0000-000000000001")).andExpect(status().isOk()).andExpect(forwardedUrl("/member-analysis.html"));
  mvc.perform(get("/api/account/analyses")).andExpect(status().isUnauthorized());
 }
}
