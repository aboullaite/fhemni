package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftProgramme;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.DraftPromise;
import dev.maboullaite.fhemni.programme.PromisePolicyTopic.Relationship;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:policy-topic-repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class PolicyTopicRepositoryIntegrationTest {

    @Autowired
    private PartyProgrammeService programmes;

    @Autowired
    private PolicyTopicRepository topics;

    @Test
    void preservesADirectParentAlongsideARelatedChild() {
        LocalizedText title = localized("إصلاح التشغيل", "Réforme de l'emploi", "Employment reform");
        var programme = programmes.createProgramme(new DraftProgramme(
                "RNI", title, title, "https://example.org/programme", "Official programme", "fr",
                "Frozen official programme text.", true));
        var promise = programmes.createPromise(programme.id(), new DraftPromise(
                "employment-reform", "employment", title,
                "The delivery plan includes support for self-employment.",
                "Page 1", "Mechanism", "Financing"));

        assertThat(topics.findPromiseTopics(List.of(promise.promise().id()))
                .get(promise.promise().id()))
                .anySatisfy(topic -> {
                    assertThat(topic.code()).isEqualTo("EMPLOYMENT");
                    assertThat(topic.relationship()).isEqualTo(Relationship.DIRECT);
                })
                .anySatisfy(topic -> {
                    assertThat(topic.code()).isEqualTo("EMPLOYMENT_SELF_EMPLOYMENT");
                    assertThat(topic.relationship()).isEqualTo(Relationship.RELATED);
                });
    }

    @Test
    void suppressesADirectParentWhenADirectChildExists() {
        LocalizedText title = localized(
                "إصلاح التشغيل والعمل الحر",
                "Réforme de l'emploi et de l'auto-emploi",
                "Employment and self-employment reform");
        var programme = programmes.createProgramme(new DraftProgramme(
                "PJD", title, title, "https://example.org/second-programme", "Official programme", "fr",
                "Frozen official programme text.", true));
        var promise = programmes.createPromise(programme.id(), new DraftPromise(
                "employment-and-self-employment-reform", "employment", title,
                "Support for business creation.",
                "Page 2", "Mechanism", "Financing"));

        assertThat(topics.findPromiseTopics(List.of(promise.promise().id()))
                .get(promise.promise().id()))
                .anySatisfy(topic -> {
                    assertThat(topic.code()).isEqualTo("EMPLOYMENT_SELF_EMPLOYMENT");
                    assertThat(topic.relationship()).isEqualTo(Relationship.DIRECT);
                })
                .noneSatisfy(topic -> assertThat(topic.code()).isEqualTo("EMPLOYMENT"));
    }

    private static LocalizedText localized(String ar, String fr, String en) {
        return new LocalizedText(ar, fr, en);
    }
}
