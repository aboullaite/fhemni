package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyInterestService {

    public static final int MAX_SELECTED_TOPICS = 3;

    private final PolicyTopicRepository topics;

    public PolicyInterestService(PolicyTopicRepository topics) {
        this.topics = topics;
    }

    public List<PolicyTopic> topics() {
        return topics.findAllActive();
    }

    public List<String> preferences(UUID userId) {
        return topics.findUserPreferences(userId);
    }

    @Transactional
    public List<String> replacePreferences(UUID userId, List<String> requestedCodes) {
        List<String> codes = normalize(requestedCodes);
        if (codes.size() > MAX_SELECTED_TOPICS) {
            throw new IllegalArgumentException("Choose no more than three policy topics.");
        }
        Set<String> available = topics.findActiveSelectableCodes(codes);
        if (available.size() != codes.size()) {
            throw new IllegalArgumentException("Choose topics from the public policy topic list.");
        }
        topics.replaceUserPreferences(userId, codes, Instant.now());
        return codes;
    }

    private List<String> normalize(List<String> requestedCodes) {
        if (requestedCodes == null) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String value : requestedCodes) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Topic codes cannot be blank.");
            }
            codes.add(value.strip().toUpperCase(Locale.ROOT));
        }
        if (codes.size() != requestedCodes.size()) {
            throw new IllegalArgumentException("Choose each policy topic only once.");
        }
        return List.copyOf(codes);
    }
}
