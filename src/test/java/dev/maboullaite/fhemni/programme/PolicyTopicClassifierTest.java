package dev.maboullaite.fhemni.programme;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText;
import dev.maboullaite.fhemni.programme.PromisePolicyTopic.Relationship;
import org.junit.jupiter.api.Test;

class PolicyTopicClassifierTest {

    private final PolicyTopicClassifier classifier = new PolicyTopicClassifier();

    @Test
    void mapsJobCreationToTheCanonicalEmploymentNuance() {
        var assignments = classifier.classify(promise(
                "إحداث 500 ألف منصب شغل",
                "Créer 500 000 emplois",
                "Create 500,000 jobs",
                "The programme promises new jobs over the five-year term."));

        assertThat(assignments)
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("EMPLOYMENT_JOB_CREATION");
                    assertThat(item.relationship()).isEqualTo(Relationship.DIRECT);
                });
    }

    @Test
    void preservesEducationAndRegionalNuanceForRuralSchools() {
        var assignments = classifier.classify(promise(
                "بناء مدارس جديدة فالعالم القروي",
                "Construire des écoles dans le monde rural",
                "Build schools in rural areas",
                "New education infrastructure in underserved regions."));

        assertThat(assignments).extracting(PromisePolicyTopic.Assignment::code)
                .contains("EDUCATION_SCHOOLS", "REGIONAL_DEVELOPMENT");
    }

    @Test
    void canAttachASecondBroadTopicWithoutFlatteningTheEducationSubtopic() {
        var assignments = classifier.classify(promise(
                "توظيف أطر التعليم",
                "Recruter des enseignants",
                "Recruit teachers",
                "Recruitment through public employment examinations."));

        assertThat(assignments).extracting(PromisePolicyTopic.Assignment::code)
                .contains("EDUCATION_TEACHERS", "EMPLOYMENT");
    }

    @Test
    void keepsUnclassifiedPromisesDiscoverableWithoutGuessing() {
        var assignments = classifier.classify(promise(
                "تبسيط المساطر",
                "Simplifier les procédures",
                "Simplify procedures",
                "A generic administrative measure."));

        assertThat(assignments).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("OTHER");
            assertThat(item.relationship()).isEqualTo(Relationship.DIRECT);
        });
    }

    @Test
    void doesNotTurnHealthWorkersIntoAWorkerRightsPromise() {
        var assignments = classifier.classify(promise(
                "تعميم أعوان الصحة فالمجال القروي",
                "Généraliser les agents de santé en milieu rural",
                "Expand the rural health workforce",
                "Recruit and deploy health workers through regional health groups."));

        assertThat(assignments).extracting(PromisePolicyTopic.Assignment::code)
                .contains("HEALTH", "REGIONAL_DEVELOPMENT")
                .doesNotContain("EMPLOYMENT", "EMPLOYMENT_WORKER_RIGHTS");
    }

    @Test
    void doesNotTurnAUniversityHospitalIntoHigherEducation() {
        var assignments = classifier.classify(promise(
                "إنجاز أربعة مستشفيات جامعية",
                "Construire quatre hôpitaux universitaires",
                "Build four university hospitals",
                "Complete hospital construction before 2029."));

        assertThat(assignments).extracting(PromisePolicyTopic.Assignment::code)
                .contains("HEALTH")
                .doesNotContain("EDUCATION", "EDUCATION_HIGHER");
    }

    private PartyPromise promise(String ar, String fr, String en, String text) {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        return new PartyPromise(
                UUID.randomUUID(), UUID.randomUUID(), "test-promise", "policy",
                new LocalizedText(ar, fr, en), text, "Page 1", "", "",
                EditorialStatus.DRAFT, now, now, null);
    }
}
