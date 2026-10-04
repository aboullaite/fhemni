package dev.maboullaite.fhemni.government;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.maboullaite.fhemni.government.GovernmentViews.*;

class GovernmentValidationTest {
    @Test void unconfiguredMandateContainsNoAppointmentsOrVerificationDate() {
        var view=GovernmentViews.pending("2026-2031");
        assertThat(view.verifiedAt()).isNull();
        assertThat(view.membership()).isEmpty();
        assertThat(view.programme()).isNull();
        assertThat(view.reviewCoverage()).isNull();
        assertThat(view.lifecycle()).isEqualTo(Lifecycle.FORMATION);
    }
    @Test void evidenceDigestIsComputedFromTheStoredBytesNotItsDeclaredShape() {
        byte[] bytes="abc".getBytes(StandardCharsets.UTF_8);
        assertThat(GovernmentValidation.archiveDigest(bytes,null))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThatThrownBy(()->GovernmentValidation.archiveDigest(bytes,"0".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->GovernmentValidation.archiveDigest(null,"0".repeat(64))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void publishingRequiresTranslatedSourceBackedFactsAndAValidLifecycle() {
        var pending=GovernmentViews.pending("2026-2031");
        assertThatThrownBy(()->GovernmentValidation.mandate(pending,null))
            .isInstanceOf(IllegalArgumentException.class);
        var falseFormed=new MandateView("2026-2031",new LocalText("حكومة","Gouvernement","Government"),Lifecycle.FORMED_PROGRAMME_PENDING,Instant.now(),List.of(),null,null,List.of());
        assertThatThrownBy(()->GovernmentValidation.mandate(falseFormed,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->GovernmentValidation.transition(Lifecycle.PROGRAMME_PUBLISHED,Lifecycle.FORMATION)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->GovernmentValidation.url("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->GovernmentValidation.url("https://user:secret@example.org/report")).isInstanceOf(IllegalArgumentException.class);
    }
}
