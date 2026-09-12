package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminProgrammeView;
import dev.maboullaite.fhemni.programme.PartyProgrammeService.AdminPromiseView;
import dev.maboullaite.fhemni.programme.PartyPromise;
import dev.maboullaite.fhemni.programme.EditorialStatus;
import dev.maboullaite.fhemni.programme.PromiseAssessment;
import org.junit.jupiter.api.Test;

class ProgrammeMediaScriptPolicyTest {

    private final ProgrammeMediaScriptPolicy policy = new ProgrammeMediaScriptPolicy();

    @Test
    void acceptsABroadSourceLockedFiveMinuteScriptAndStrengthensTheBrandPronunciation() {
        Dossier dossier = dossier();
        ProgrammeMediaScript script = script(dossier.promiseIds());

        ProgrammeMediaScript validated = policy.validate(script, dossier.programme());

        assertThat(validated.segments()).hasSize(14);
        assertThat(policy.ttsText("هاد الخدمة من فهمني ومن فهّمني ومن فَهَّمْنِي"))
                .isEqualTo("هاد الخدمة من فَهَّمْنِي ومن فَهَّمْنِي ومن فَهَّمْنِي");
    }

    @Test
    void rejectsUnknownSourcesAndInternalVerdictLabels() {
        Dossier dossier = dossier();
        ProgrammeMediaScript valid = script(dossier.promiseIds());
        List<ProgrammeMediaScript.Segment> unknown = new ArrayList<>(valid.segments());
        unknown.set(0, new ProgrammeMediaScript.Segment(
                "عنوان واضح", narration(), List.of("PROMISE:" + UUID.randomUUID())));

        assertThatThrownBy(() -> policy.validate(
                new ProgrammeMediaScript(valid.headline(), unknown), dossier.programme()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid programme source references");

        List<ProgrammeMediaScript.Segment> leaked = new ArrayList<>(valid.segments());
        leaked.set(0, new ProgrammeMediaScript.Segment(
                "HARD عنوان", narration(), List.of("PROMISE:" + dossier.promiseIds().getFirst())));
        assertThatThrownBy(() -> policy.validate(
                new ProgrammeMediaScript(valid.headline(), leaked), dossier.programme()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal labels");
    }

    @Test
    void requiresEveryAssessmentToStayPairedWithItsPromise() {
        Dossier dossier = dossierWithAssessment();
        ProgrammeMediaScript valid = script(dossier.promiseIds());
        List<ProgrammeMediaScript.Segment> mismatched = new ArrayList<>(valid.segments());
        mismatched.set(0, new ProgrammeMediaScript.Segment(
                "عنوان واضح",
                narration(),
                List.of(
                        "PROMISE:" + dossier.promiseIds().get(1),
                        "ASSESSMENT:" + dossier.assessmentId())));

        assertThatThrownBy(() -> policy.validate(
                new ProgrammeMediaScript(valid.headline(), mismatched), dossier.programme()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paired with its programme promise");
    }

    @Test
    void rejectsAScriptThatWouldRunPastTheFiveMinuteTarget() {
        Dossier dossier = dossier();
        List<ProgrammeMediaScript.Segment> longSegments = java.util.stream.IntStream.range(0, 14)
                .mapToObj(index -> new ProgrammeMediaScript.Segment(
                        "عنوان واضح " + index,
                        "كلمة ".repeat(40).strip(),
                        List.of("PROMISE:" + dossier.promiseIds().get(index % dossier.promiseIds().size()))))
                .toList();

        assertThatThrownBy(() -> policy.validate(
                new ProgrammeMediaScript("البرنامج فخمسة دقايق", longSegments), dossier.programme()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 460 and 500 spoken words");
    }

    @Test
    void rejectsAHeadlineThatWouldWrapOntoThreeVideoLines() {
        Dossier dossier = dossier();

        assertThatThrownBy(() -> policy.validate(
                new ProgrammeMediaScript(
                        "الاستراتيجيات/الوطنية الإصلاحات/الاقتصادية الانتظارات/الاجتماعية",
                        script(dossier.promiseIds()).segments()),
                dossier.programme()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no more than two video lines");
    }

    private static Dossier dossier() {
        AdminProgrammeView programme = mock(AdminProgrammeView.class);
        List<UUID> ids = java.util.stream.IntStream.range(0, 5)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        List<AdminPromiseView> promises = ids.stream().map(id -> {
            PartyPromise promise = mock(PartyPromise.class);
            when(promise.id()).thenReturn(id);
            AdminPromiseView item = mock(AdminPromiseView.class);
            when(item.promise()).thenReturn(promise);
            when(item.assessments()).thenReturn(List.of());
            return item;
        }).toList();
        when(programme.promises()).thenReturn(promises);
        return new Dossier(programme, ids, null);
    }

    private static Dossier dossierWithAssessment() {
        AdminProgrammeView programme = mock(AdminProgrammeView.class);
        List<UUID> ids = java.util.stream.IntStream.range(0, 5)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        UUID assessmentId = UUID.randomUUID();
        List<AdminPromiseView> promises = java.util.stream.IntStream.range(0, ids.size())
                .mapToObj(index -> {
                    PartyPromise promise = mock(PartyPromise.class);
                    when(promise.id()).thenReturn(ids.get(index));
                    AdminPromiseView item = mock(AdminPromiseView.class);
                    when(item.promise()).thenReturn(promise);
                    if (index == 0) {
                        PromiseAssessment assessment = mock(PromiseAssessment.class);
                        when(assessment.id()).thenReturn(assessmentId);
                        when(assessment.status()).thenReturn(EditorialStatus.PUBLISHED);
                        when(item.assessments()).thenReturn(List.of(assessment));
                    } else {
                        when(item.assessments()).thenReturn(List.of());
                    }
                    return item;
                }).toList();
        when(programme.promises()).thenReturn(promises);
        return new Dossier(programme, ids, assessmentId);
    }

    private static ProgrammeMediaScript script(List<UUID> ids) {
        List<ProgrammeMediaScript.Segment> segments = java.util.stream.IntStream.range(0, 14)
                .mapToObj(index -> new ProgrammeMediaScript.Segment(
                        "عنوان واضح " + index,
                        narration(),
                        List.of("PROMISE:" + ids.get(index % ids.size()))))
                .toList();
        return new ProgrammeMediaScript("البرنامج فخمسة دقايق", segments);
    }

    private static String narration() {
        return "كلمة ".repeat(34).strip();
    }

    private record Dossier(AdminProgrammeView programme, List<UUID> promiseIds, UUID assessmentId) {
    }
}
