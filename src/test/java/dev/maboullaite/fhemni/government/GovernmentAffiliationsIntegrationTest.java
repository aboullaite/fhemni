package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"fhemni.gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:government-affiliations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Transactional
class GovernmentAffiliationsIntegrationTest {
    @Autowired GovernmentRepository repository;
    @Autowired GovernmentEditorialService editorial;
    @Autowired GovernmentEvidenceRepository evidence;
    @Autowired GovernmentService government;

    @Test
    void missingPartyIsFilledWithSourcedAffiliationWithoutRewritingThePublishedSnapshot() {
        publish("نزار بركة", null, "2026-10-16");
        var member = government.mandate("2026-2031").membership().getFirst();
        assertThat(member.partyCode()).isEqualTo("PI");
        assertThat(member.evidenceIds()).hasSizeGreaterThan(1);
        assertThat(member.evidenceIds().stream().map(id -> government.source("2026-2031", id).url()))
                .anyMatch(url -> url.startsWith("https://thevoice.ma/"));
        assertThat(repository.published(SubjectType.MANDATE, "2026-2031", MandateView.class)
                .orElseThrow().membership().getFirst().partyCode()).isNull();
    }

    @Test
    void explicitlySourcedIndependentsAreDistinctFromMissingAffiliations() {
        publish("ناصر بوريطة", null, "2026-10-16");
        assertThat(government.mandate("2026-2031").membership().getFirst().partyCode())
                .isEqualTo("INDEPENDENT");
    }

    @Test
    void anUnknownMemberIsNeverInferredToBeIndependent() {
        publish("عضو تجريبي غير معروف", null, "2026-10-16");
        assertThat(government.mandate("2026-2031").membership().getFirst().partyCode()).isNull();
    }

    @Test
    void aDifferentMembershipPeriodIsNotBackfilledFromThe2026Roster() {
        publish("نزار بركة", null, "2027-01-01");
        assertThat(government.mandate("2026-2031").membership().getFirst().partyCode()).isNull();
    }

    @Test
    void anExplicitPublishedPartyTakesPrecedenceOverSupplementalMetadata() {
        publish("نزار بركة", "PAM", "2026-10-16");
        var member = government.mandate("2026-2031").membership().getFirst();
        assertThat(member.partyCode()).isEqualTo("PAM");
        assertThat(member.evidenceIds()).hasSize(1);
    }

    private void publish(String name, String party, String start) {
        repository.ensureMandate("2026-2031");
        var proof = evidence.save("2026-2031", new Evidence(UUID.randomUUID(),
                "https://example.org/fixture", "Test-only appointment", null, "en", null,
                Instant.now().minusSeconds(1), null), null);
        var member = new Membership(UUID.randomUUID(), text(name), text("Test-only portfolio"),
                party, LocalDate.parse(start), null, List.of(proof.id()));
        var view = new MandateView("2026-2031", text("Test-only mandate"),
                Lifecycle.FORMED_PROGRAMME_PENDING, Instant.now().minusSeconds(1),
                List.of(member), null, null, List.of());
        var actor = UUID.randomUUID();
        var draft = repository.createDraft("2026-2031", SubjectType.MANDATE, "2026-2031",
                view, actor, "Test-only membership");
        var reviewed = editorial.review(draft.id(), draft.rowVersion(), actor);
        editorial.publish(reviewed.id(), reviewed.rowVersion(), actor);
    }

    private static LocalText text(String value) { return new LocalText(value, value, value); }
}
