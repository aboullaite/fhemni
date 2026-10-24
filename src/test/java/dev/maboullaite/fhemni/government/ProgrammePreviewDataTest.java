package dev.maboullaite.fhemni.government;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProgrammePreviewDataTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test void localPreviewCannotWriteToProductionOrBindPublicly() {
        assertDoesNotThrow(() -> ProgrammePreviewData.requireLocal("jdbc:h2:file:./data/government-preview;MODE=PostgreSQL", "127.0.0.1", List.of("programme-preview")));
        assertThrows(IllegalStateException.class, () -> ProgrammePreviewData.requireLocal("jdbc:postgresql://localhost/fhemni", "127.0.0.1", List.of("programme-preview")));
        assertThrows(IllegalStateException.class, () -> ProgrammePreviewData.requireLocal("jdbc:h2:file:./data/fhemni", "127.0.0.1", List.of("programme-preview")));
        assertThrows(IllegalStateException.class, () -> ProgrammePreviewData.requireLocal("jdbc:h2:file:./data/government-preview", "0.0.0.0", List.of("programme-preview")));
        assertThrows(IllegalStateException.class, () -> ProgrammePreviewData.requireLocal("jdbc:h2:file:./data/government-preview", "127.0.0.1", List.of("programme-preview", "prod")));
    }

    @Test void provenanceChecksActualBytesNotJustADeclaredDigest() {
        byte[] original = "source bytes".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> ProgrammePreviewData.verify(original, GovernmentValidation.sha256(original), "fixture"));
        assertThrows(IllegalArgumentException.class, () -> ProgrammePreviewData.verify(original, "0".repeat(64), "fixture"));
        assertThrows(IllegalArgumentException.class, () -> ProgrammePreviewData.verify("changed".getBytes(StandardCharsets.UTF_8), GovernmentValidation.sha256(original), "fixture"));
    }

    @Test void preservesLiteralTargetsAndUnassessedSourceStatusWithoutInventingPreciseDates() {
        var row = mapper.readTree("""
          {"id":"p050-example","pages":[50,51],"titleAr":"زيادة التغطية","summaryEn":"Expand coverage.",
           "sourceAnchor":"التغطية","sourceStatus":"MIRROR_UNAUTHENTICATED","deliveryStatus":"UNASSESSED",
           "kind":"service","target":{"value":75,"unit":"percent","operator":"at_least","periodText":"by 2031","baseline":null},
           "deadlineText":"2031","scopeText":"National ambition","caveats":["Coverage is not delivery."]}
          """);
        var view = ProgrammePreviewData.commitment(row, List.of(), List.of());
        assertNull(view.target());
        assertNull(view.deadline());
        assertNull(view.deliveryAssessment());
        assertEquals("2031", view.programmeContext().deadlineText());
        assertTrue(view.programmeContext().targetText().contains("at_least"));
        assertEquals(List.of(50,51), view.programmeContext().printedPages());
        assertEquals("MIRROR_UNAUTHENTICATED", view.programmeContext().sourceStatus());
        assertEquals("التغطية", view.originalText());
        assertEquals(view.id(), ProgrammePreviewData.commitment(row, List.of(), List.of()).id());
        assertThrows(IllegalArgumentException.class, () -> ProgrammePreviewData.commitment(mapper.readTree(row.toString().replace("UNASSESSED","DELIVERED")), List.of(), List.of()));
    }
    @Test void editorialTopicsSupportDiscoveryWithoutAssigningMinisterOwnership() {
        assertEquals(List.of("HEALTH"), ProgrammePreviewData.topics("تطوير المستشفيات", "Improve hospital care."));
        assertTrue(ProgrammePreviewData.topics("التعليم الأولي", "Expand preschool.").contains("EDUCATION"));
        assertEquals(List.of("OTHER"), ProgrammePreviewData.topics("عنوان", "No classified topic."));
    }
    @Test void restartGuardRejectsChangedMandateAsWellAsChangedCommitments() {
        var events=new GovernmentPublicationEvents(null,null,mapper);
        var mandate=GovernmentViews.pending("2026-2031");
        var snapshot=new ProgrammePreviewData.Snapshot(new byte[0],mandate,List.of(),List.of(),List.of());
        assertTrue(ProgrammePreview.matchesExisting(snapshot,mandate,List.of(),events));
        var altered=new GovernmentViews.MandateView(mandate.id(),new GovernmentViews.LocalText("تغيير","Modifié","Changed"),mandate.lifecycle(),null,List.of(),null,null,List.of());
        assertFalse(ProgrammePreview.matchesExisting(snapshot,altered,List.of(),events));
    }
    @Test void localTestIdentityCannotInheritAdministratorRights() {
        assertDoesNotThrow(()->ProgrammePreviewData.requireTestIdentity(new dev.maboullaite.fhemni.identity.AuthProperties(java.util.Set.of(),null,null,null,null)));
        assertThrows(IllegalStateException.class,()->ProgrammePreviewData.requireTestIdentity(new dev.maboullaite.fhemni.identity.AuthProperties(java.util.Set.of(),"reviewer@fhemni.local",null,null,null)));
        String subject=GovernmentValidation.sha256("reviewer@fhemni.local".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,()->ProgrammePreviewData.requireTestIdentity(new dev.maboullaite.fhemni.identity.AuthProperties(java.util.Set.of("magic-link:"+subject),null,null,null,null)));
    }
}
