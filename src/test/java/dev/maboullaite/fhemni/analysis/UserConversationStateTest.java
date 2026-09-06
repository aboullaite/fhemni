package dev.maboullaite.fhemni.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import dev.maboullaite.fhemni.model.FollowUpAnswer;
import dev.maboullaite.fhemni.model.QuestionMode;
import org.junit.jupiter.api.Test;

class UserConversationStateTest {

    @Test
    void resetsTheProviderChainWithoutDiscardingTheBoundedLocalTranscript() {
        UserConversationState state = new UserConversationState("analysis-base", 3, 2);

        state.add(answer("one"), "interaction-one");
        assertThat(state.interactionId()).isEqualTo("interaction-one");

        state.add(answer("two"), "interaction-two");
        assertThat(state.interactionId()).isEqualTo("analysis-base");

        state.add(answer("three"), "interaction-three");
        state.add(answer("four"), "interaction-four");

        assertThat(state.interactionId()).isEqualTo("analysis-base");
        assertThat(state.snapshot()).extracting(FollowUpAnswer::question)
                .containsExactly("two", "three", "four");
    }

    private FollowUpAnswer answer(String question) {
        return new FollowUpAnswer(question, QuestionMode.VIDEO, "answer", List.of(), Instant.now());
    }
}
