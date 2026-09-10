package dev.maboullaite.fhemni.programme;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PolicyTopicBackfill implements ApplicationRunner {

    private final PartyProgrammeRepository programmes;
    private final PolicyTopicRepository topics;
    private final PolicyTopicClassifier classifier;

    public PolicyTopicBackfill(
            PartyProgrammeRepository programmes,
            PolicyTopicRepository topics,
            PolicyTopicClassifier classifier) {
        this.programmes = programmes;
        this.topics = topics;
        this.classifier = classifier;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Instant now = Instant.now();
        programmes.findPromisesRequiringPolicyTopicMapping(PolicyTopicClassifier.VERSION).forEach(promise ->
                topics.replaceRuleAssignments(promise.id(), classifier.classify(promise), now));
    }
}
