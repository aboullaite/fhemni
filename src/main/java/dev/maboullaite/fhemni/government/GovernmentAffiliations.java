package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Sourced current-roster enrichment; archived publication payloads remain immutable. */
@Component
public class GovernmentAffiliations {
    private record Key(String name, LocalDate from, LocalDate until) {}
    private record Entry(String code, List<UUID> evidenceIds) {}
    private final Map<Key, Entry> entries;
    private final Map<UUID, Evidence> sources;

    public GovernmentAffiliations(ObjectMapper mapper) throws IOException {
        var resource = new ClassPathResource("government/cabinet-affiliations.json");
        try (var input = resource.getInputStream()) {
            var document = mapper.readTree(input);
            if (document.path("version").asInt() != 1
                    || !"2026-2031".equals(document.path("mandate").asText())) {
                throw new IllegalArgumentException("Unsupported cabinet affiliations index.");
            }
            var retrieved = Instant.parse(document.path("retrievedAt").asText());
            var evidence = new HashMap<UUID, Evidence>();
            for (var row : document.path("sources")) {
                var id = sourceId(row.path("key").asText());
                var source = new Evidence(id, row.path("url").asText(), row.path("summary").asText(),
                        row.path("label").asText(), "ar",
                        LocalDate.parse(row.path("publishedDate").asText()).atStartOfDay().toInstant(ZoneOffset.UTC),
                        retrieved, null);
                if (!source.url().startsWith("https://") || source.passage().isBlank()
                        || evidence.put(id, source) != null) {
                    throw new IllegalArgumentException("Invalid cabinet affiliation evidence.");
                }
            }
            var indexed = new HashMap<Key, Entry>();
            for (var row : document.path("entries")) {
                var key = new Key(row.path("nameAr").asText(), LocalDate.parse(row.path("from").asText()),
                        row.hasNonNull("until") ? LocalDate.parse(row.path("until").asText()) : null);
                var code = row.path("partyCode").asText();
                var ids = new ArrayList<UUID>();
                for (var reference : row.path("sources")) {
                    var id = sourceId(reference.asText());
                    if (!evidence.containsKey(id)) throw new IllegalArgumentException("Unknown affiliation evidence.");
                    ids.add(id);
                }
                if (key.name().isBlank() || ids.isEmpty()
                        || !Set.of("PAM", "PI", "USFP", "PPS", "INDEPENDENT").contains(code)
                        || indexed.put(key, new Entry(code, List.copyOf(ids))) != null) {
                    throw new IllegalArgumentException("Invalid cabinet affiliation entry.");
                }
            }
            entries = Map.copyOf(indexed);
            sources = Map.copyOf(evidence);
        }
    }

    public List<Membership> enrich(List<Membership> membership) {
        return membership.stream().map(member -> {
            var entry = entries.get(new Key(member.name().ar(), member.effectiveFrom(), member.effectiveUntil()));
            if (entry == null || member.partyCode() != null && !member.partyCode().isBlank()
                    && !member.partyCode().equals(entry.code())) return member;
            var references = new LinkedHashSet<>(member.evidenceIds());
            references.addAll(entry.evidenceIds());
            return new Membership(member.id(), member.name(), member.role(), entry.code(),
                    member.effectiveFrom(), member.effectiveUntil(), List.copyOf(references));
        }).toList();
    }

    public Optional<Evidence> source(UUID id) { return Optional.ofNullable(sources.get(id)); }

    private static UUID sourceId(String key) {
        return UUID.nameUUIDFromBytes(("government-affiliation:" + key).getBytes(StandardCharsets.UTF_8));
    }
}
