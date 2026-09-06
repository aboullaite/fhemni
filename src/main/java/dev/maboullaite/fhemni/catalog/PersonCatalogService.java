package dev.maboullaite.fhemni.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import dev.maboullaite.fhemni.catalog.PersonDirectory.ResolvedPerson;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimVerdict;
import dev.maboullaite.fhemni.model.VideoReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only aggregation over <strong>published</strong> catalogue analyses.
 *
 * <p>Builds guest (person) and party sheets: in which episodes someone
 * appeared, what they said (claims attributed to them), on which topics
 * (nearest chapter), and passages to compare across episodes.
 *
 * <p>Trust boundaries preserved:
 * <ul>
 *   <li>only revisions linked through {@code catalog_videos.published_analysis_id}
 *       with status {@code PUBLISHED} and {@code listed = TRUE} are read — drafts
 *       and processing events never leak;</li>
 *   <li>a statement in a video stays a statement: "comparisons" surface pairs of
 *       exact statements from different episodes whose independent evidence
 *       assessments differ, without ever declaring that the person
 *       contradicted themselves;</li>
 *   <li>no AI call is made; aggregation is deterministic string matching.</li>
 * </ul>
 */
@Service
public class PersonCatalogService {

    private static final Logger log = LoggerFactory.getLogger(PersonCatalogService.class);

    /**
     * Matching algorithm tuning. Two statements are treated as related when
     * they share at least this many distinctive words (normalized, 5+ letters,
     * so short grammatical words never match), or share an identical chapter
     * title plus at least two such words. Short function words in Darija and
     * French are too short to ever count, which keeps generic chapters from
     * producing spurious pairs without any stop-word list.
     */
    private static final int MIN_SHARED_TOKENS = 3;
    private static final int MIN_SHARED_TOKENS_SAME_TOPIC = 2;
    private static final int MIN_TOKEN_LENGTH = 5;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PoliticalPartyRepository partyRepository;
    private final DirectoryPersonRepository personRepository;
    private final int maxComparisons;
    private final int maxPartyClaims;
    private final Duration cacheTtl;
    private volatile CachedLoaded cache;

    public PersonCatalogService(
            JdbcClient jdbc,
            ObjectMapper mapper,
            PoliticalPartyRepository partyRepository,
            DirectoryPersonRepository personRepository,
            @Value("${fhemni.catalog.people-max-comparisons:20}") int maxComparisons,
            @Value("${fhemni.catalog.party-max-claims:50}") int maxPartyClaims,
            @Value("${fhemni.catalog.people-cache-ttl:PT60S}") Duration cacheTtl) {
        if (maxComparisons < 0 || maxPartyClaims < 1) {
            throw new IllegalArgumentException("Catalogue people limits must be positive");
        }
        if (cacheTtl == null || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("Catalogue people cache TTL must not be negative");
        }
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.partyRepository = partyRepository;
        this.personRepository = personRepository;
        this.maxComparisons = maxComparisons;
        this.maxPartyClaims = maxPartyClaims;
        this.cacheTtl = cacheTtl;
    }

    public List<PersonSummary> searchPeople(String query, String partyCode) {
        String normalizedQuery = PersonDirectory.normalize(query == null ? "" : query);
        String wantedParty = partyCode == null || partyCode.isBlank()
                ? null
                : partyCode.strip().toUpperCase(Locale.ROOT);
        return load().people().values().stream()
                .filter(builder -> matches(builder, normalizedQuery, wantedParty))
                .map(Builder::summary)
                .sorted(Comparator.comparingInt(PersonSummary::appearances).reversed()
                        .thenComparing(PersonSummary::displayName))
                .toList();
    }

    private boolean matches(Builder builder, String normalizedQuery, String wantedParty) {
        if (wantedParty != null && !wantedParty.equals(builder.partyCode())) {
            return false;
        }
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        return builder.spellings().stream()
                .map(PersonDirectory::normalize)
                .anyMatch(spelling -> spelling.contains(normalizedQuery));
    }

    public PersonProfile person(String slug) {
        Loaded loaded = load();
        Builder builder = loaded.people().get(slug == null ? "" : slug.strip().toLowerCase(Locale.ROOT));
        if (builder == null) {
            throw new NoSuchElementException("This guest was not found in the published catalogue.");
        }
        PersonProfile base = builder.profile();
        return new PersonProfile(
                base.person(), base.episodes(), base.claims(), base.comparisons(),
                crossComparisons(builder, loaded.people()));
    }

    public List<PartySummary> parties() {
        Loaded loaded = load();
        Map<String, Builder> people = loaded.people();
        List<PartySummary> result = new ArrayList<>();
        for (PoliticalParty party : loaded.parties().findAll()) {
            if (!party.visible()) {
                continue;
            }
            List<PersonSummary> members = people.values().stream()
                    .map(Builder::summary)
                    .filter(member -> party.code().equals(member.partyCode()))
                    .sorted(Comparator.comparingInt(PersonSummary::appearances).reversed()
                            .thenComparing(PersonSummary::displayName))
                    .toList();
            int appearances = members.stream().mapToInt(PersonSummary::appearances).sum();
            int claims = members.stream().mapToInt(PersonSummary::claims).sum();
            result.add(new PartySummary(
                    party.code(), party.nameFr(), party.nameAr(), party.color(),
                    members.size(), appearances, claims,
                    members.stream().limit(12).toList()));
        }
        return result;
    }

    public PartyProfile party(String code) {
        Loaded loaded = load();
        PoliticalParty party = loaded.parties().required(code);
        if (!party.visible()) {
            throw new NoSuchElementException("This party page is not available.");
        }
        Map<String, Builder> people = loaded.people();
        List<PersonSummary> members = people.values().stream()
                .map(Builder::summary)
                .filter(member -> party.code().equals(member.partyCode()))
                .sorted(Comparator.comparingInt(PersonSummary::appearances).reversed()
                        .thenComparing(PersonSummary::displayName))
                .toList();
        List<PersonClaim> recentClaims = people.values().stream()
                .flatMap(builder -> builder.claims.stream())
                .filter(claim -> {
                    Builder speaker = people.get(claim.personSlug());
                    return speaker != null && party.code().equals(speaker.partyCode());
                })
                .sorted(claimOrder())
                .limit(maxPartyClaims)
                .toList();
        int appearances = members.stream().mapToInt(PersonSummary::appearances).sum();
        return new PartyProfile(
                party.code(), party.nameFr(), party.nameAr(), party.color(),
                members.size(), appearances, recentClaims.size(), members, recentClaims);
    }

    /**
     * Pairs this guest's factual statements with statements by <em>other</em>
     * guests on a similar chapter topic, in different episodes. Matching is a
     * deterministic chapter-title comparison — never a semantic verdict — so
     * readers must compare the exact wordings themselves.
     */
    private List<TopicComparison> crossComparisons(Builder self, Map<String, Builder> people) {
        List<PersonClaim> mine = self.claims.stream().filter(this::comparable).toList();
        List<TopicComparison> pairs = new ArrayList<>();
        for (PersonClaim first : mine) {
            for (Builder other : people.values()) {
                if (other == self) {
                    continue;
                }
                if (pairs.size() >= maxComparisons) {
                    break;
                }
                for (PersonClaim second : other.claims) {
                    if (pairs.size() >= maxComparisons) {
                        break;
                    }
                    if (!comparable(second)
                            || first.episodeSlug().equals(second.episodeSlug())) {
                        continue;
                    }
                    if (relatedStatements(first, second)) {
                        pairs.add(new TopicComparison(
                                first.topic(), first, self.speakerRef(), second, other.speakerRef()));
                    }
                }
            }
        }
        return pairs.stream()
                .sorted(Comparator.comparing(
                        TopicComparison::second,
                        Comparator.comparing(PersonClaim::publishedOn,
                                Comparator.nullsLast(Comparator.reverseOrder()))))
                .limit(maxComparisons)
                .toList();
    }

    /**
     * Statement relatedness: two passages are paired only when their exact
     * wordings share distinctive vocabulary — at least {@value #MIN_SHARED_TOKENS}
     * normalized words of {@value #MIN_TOKEN_LENGTH}+ letters, or an identical
     * chapter title plus {@value #MIN_SHARED_TOKENS_SAME_TOPIC} such words.
     * Short grammatical words can never match, so unrelated passages that only
     * share generic vocabulary (government, party, elections…) are rejected
     * without any stop-word list. Deterministic text comparison only — never a
     * semantic verdict.
     */
    private boolean relatedStatements(PersonClaim first, PersonClaim second) {
        if (first.statement() == null || second.statement() == null) {
            return false;
        }
        List<String> left = statementTokens(first.statement());
        List<String> right = statementTokens(second.statement());
        long shared = left.stream().filter(right::contains).count();
        if (shared >= MIN_SHARED_TOKENS) {
            return true;
        }
        return shared >= MIN_SHARED_TOKENS_SAME_TOPIC && sameChapter(first.topic(), second.topic());
    }

    private boolean sameChapter(String first, String second) {
        if (first == null || second == null || first.isBlank() || second.isBlank()) {
            return false;
        }
        String left = PersonDirectory.normalize(first);
        String right = PersonDirectory.normalize(second);
        return !left.isEmpty() && left.equals(right);
    }

    private List<String> statementTokens(String statement) {
        return Arrays.stream(PersonDirectory.normalize(statement).split(" "))
                .filter(token -> token.length() >= MIN_TOKEN_LENGTH)
                .distinct()
                .toList();
    }

    private boolean comparable(PersonClaim claim) {
        return "FACT".equals(claim.kind())
                && claim.verdict() != null
                && (ClaimVerdict.SUPPORTED.name().equals(claim.verdict())
                        || ClaimVerdict.CONTRADICTED.name().equals(claim.verdict())
                        || ClaimVerdict.NEEDS_CONTEXT.name().equals(claim.verdict()));
    }

    /**
     * The aggregation re-reads every published report, so the assembled snapshot
     * is held briefly in memory. Newly published episodes appear after the TTL
     * at the latest (the public catalogue API already caches for 5 minutes).
     */
    private Loaded load() {
        CachedLoaded hit = cache;
        if (hit != null && Instant.now().isBefore(hit.expiresAt())) {
            return hit.loaded();
        }
        synchronized (this) {
            hit = cache;
            if (hit != null && Instant.now().isBefore(hit.expiresAt())) {
                return hit.loaded();
            }
            Loaded fresh = loadUncached();
            cache = new CachedLoaded(fresh, Instant.now().plus(cacheTtl));
            return fresh;
        }
    }

    private record CachedLoaded(Loaded loaded, Instant expiresAt) {
    }

    private Loaded loadUncached() {
        PersonDirectory directory = new PersonDirectory(
                personRepository.findAll(), personRepository.findHonorifics());
        PartyDirectory parties = new PartyDirectory(partyRepository.findAll());
        Map<String, Builder> people = new LinkedHashMap<>();
        for (PublishedItem item : loadPublished()) {
            for (var participant : item.report().participants()) {
                if (participant == null || participant.name() == null || participant.name().isBlank()) {
                    continue;
                }
                builder(people, directory.resolve(participant.name()), parties)
                        .addAppearance(item, participant.role());
            }
            for (Claim claim : item.report().claims()) {
                if (claim == null || claim.speaker() == null || claim.speaker().isBlank()) {
                    continue;
                }
                builder(people, directory.resolve(claim.speaker()), parties)
                        .addClaim(item, claim);
            }
        }
        return new Loaded(directory, parties, people);
    }

    private record Loaded(PersonDirectory directory, PartyDirectory parties, Map<String, Builder> people) {
    }

    private Builder builder(Map<String, Builder> people, ResolvedPerson identity, PartyDirectory parties) {
        return people.computeIfAbsent(identity.slug(), ignored -> new Builder(identity, parties));
    }

    private List<PublishedItem> loadPublished() {
        List<PublishedItem> items = jdbc.sql("""
                        SELECT cv.slug, cv.title, cv.youtube_video_id, cv.published_on,
                               cv.thumbnail_url, ar.report_json
                          FROM catalog_videos cv
                          JOIN analysis_revisions ar
                            ON ar.id = cv.published_analysis_id
                         WHERE cv.status = 'PUBLISHED'
                           AND cv.listed = TRUE
                           AND cv.published_analysis_id IS NOT NULL
                           AND ar.report_json IS NOT NULL
                         ORDER BY cv.published_on DESC NULLS LAST, cv.created_at DESC
                        """)
                .query(this::mapPublished)
                .list();
        List<PublishedItem> parsed = new ArrayList<>();
        for (PublishedItem item : items) {
            try {
                VideoReport report = mapper.readValue(item.reportJson(), VideoReport.class);
                if (report != null) {
                    parsed.add(item.withReport(report));
                }
            } catch (RuntimeException exception) {
                log.warn("Skipping a published report that could not be read for {}", item.slug());
            }
        }
        return parsed;
    }

    private PublishedItem mapPublished(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublishedItem(
                resultSet.getString("slug"),
                resultSet.getString("title"),
                resultSet.getString("youtube_video_id"),
                resultSet.getObject("published_on", LocalDate.class),
                resultSet.getString("thumbnail_url"),
                resultSet.getString("report_json"),
                null);
    }

    private Comparator<PersonClaim> claimOrder() {
        return Comparator.comparing(PersonClaim::publishedOn, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PersonClaim::episodeSlug);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private record PublishedItem(
            String slug,
            String title,
            String youtubeVideoId,
            LocalDate publishedOn,
            String thumbnailUrl,
            String reportJson,
            VideoReport report) {

        PublishedItem withReport(VideoReport parsed) {
            return new PublishedItem(slug, title, youtubeVideoId, publishedOn, thumbnailUrl, reportJson, parsed);
        }
    }

    private final class Builder {
        private final ResolvedPerson identity;
        private final PartyDirectory parties;
        private final Map<String, EpisodeAppearance> episodes = new LinkedHashMap<>();
        private final List<PersonClaim> claims = new ArrayList<>();
        private final Set<String> topics = new TreeSet<>();

        private Builder(ResolvedPerson identity, PartyDirectory parties) {
            this.identity = identity;
            this.parties = parties;
        }

        void addAppearance(PublishedItem item, String role) {
            episodes.computeIfAbsent(item.slug(), ignored -> new EpisodeAppearance(
                    item.slug(), item.title(), item.youtubeVideoId(),
                    item.publishedOn(), item.thumbnailUrl(), role));
        }

        void addClaim(PublishedItem item, Claim claim) {
            episodes.computeIfAbsent(item.slug(), ignored -> new EpisodeAppearance(
                    item.slug(), item.title(), item.youtubeVideoId(),
                    item.publishedOn(), item.thumbnailUrl(), null));
            String topic = topicFor(item.report(), claim.startSeconds());
            if (topic != null) {
                topics.add(topic);
            }
            claims.add(new PersonClaim(
                    identity.slug(),
                    claim.statement(), claim.speaker(),
                    item.slug(), item.title(), item.youtubeVideoId(), item.publishedOn(),
                    claim.startSeconds(), topic,
                    claim.kind() == null ? null : claim.kind().name(),
                    claim.verdict() == null ? null : claim.verdict().name(),
                    claim.explanation()));
        }

        String getSlug() {
            return identity.slug();
        }

        List<String> spellings() {
            return identity.spellings();
        }

        String partyCode() {
            return identity.partyCode();
        }

        PersonSummary summary() {
            PoliticalParty party = parties.findByCode(identity.partyCode()).orElseGet(parties::fallback);
            LocalDate last = episodes.values().stream()
                    .map(EpisodeAppearance::publishedOn)
                    .filter(date -> date != null)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            return new PersonSummary(
                    identity.slug(), identity.displayName(), orEmpty(identity.displayNameAr()),
                    identity.curated(),
                    party.code(), party.nameFr(), party.nameAr(), party.color(),
                    episodes.size(), claims.size(), last, List.copyOf(topics),
                    List.copyOf(identity.spellings()));
        }

        PersonProfile profile() {
            PersonSummary summary = summary();
            List<EpisodeAppearance> episodeList = episodes.values().stream()
                    .sorted(Comparator.comparing(
                            EpisodeAppearance::publishedOn, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            List<PersonClaim> orderedClaims = claims.stream().sorted(claimOrder()).toList();
            return new PersonProfile(
                    summary, episodeList, orderedClaims, comparisons(orderedClaims), List.of());
        }

        SpeakerRef speakerRef() {
            PoliticalParty party = parties.findByCode(identity.partyCode()).orElseGet(parties::fallback);
            return new SpeakerRef(
                    identity.slug(), identity.displayName(), orEmpty(identity.displayNameAr()),
                    party.code(), party.nameFr(), party.nameAr(), party.color());
        }

        private List<ComparisonPair> comparisons(List<PersonClaim> orderedClaims) {
            List<PersonClaim> factual = orderedClaims.stream()
                    .filter(claim -> "FACT".equals(claim.kind())
                            && claim.verdict() != null
                            && (ClaimVerdict.SUPPORTED.name().equals(claim.verdict())
                                    || ClaimVerdict.CONTRADICTED.name().equals(claim.verdict())
                                    || ClaimVerdict.NEEDS_CONTEXT.name().equals(claim.verdict())))
                    .toList();
            List<ComparisonPair> pairs = new ArrayList<>();
            for (int index = 0; index < factual.size() && pairs.size() < maxComparisons; index++) {
                for (int other = index + 1; other < factual.size() && pairs.size() < maxComparisons; other++) {
                    PersonClaim first = factual.get(index);
                    PersonClaim second = factual.get(other);
                    if (first.episodeSlug().equals(second.episodeSlug())) {
                        continue;
                    }
                    if (first.verdict().equals(second.verdict())) {
                        continue;
                    }
                    if (!relatedStatements(first, second)) {
                        continue;
                    }
                    pairs.add(new ComparisonPair(
                            "Independent evidence assessments differ across these two episodes. "
                                    + "Compare the exact statements, their timestamps and their sources — "
                                    + "this is not an automated contradiction verdict.",
                            first, second));
                }
            }
            return pairs;
        }

        private String topicFor(VideoReport report, int startSeconds) {
            if (report == null || report.chapters() == null || report.chapters().isEmpty()) {
                return null;
            }
            String current = null;
            for (var chapter : report.chapters()) {
                if (chapter == null) {
                    continue;
                }
                if (chapter.startSeconds() <= startSeconds) {
                    current = chapter.title();
                } else {
                    break;
                }
            }
            return current;
        }
    }

    public record PersonSummary(
            String slug,
            String displayName,
            String displayNameAr,
            boolean curated,
            String partyCode,
            String partyNameFr,
            String partyNameAr,
            String partyColor,
            int appearances,
            int claims,
            LocalDate lastAppearance,
            List<String> topics,
            List<String> spellings) {
    }

    public record EpisodeAppearance(
            String slug,
            String title,
            String youtubeVideoId,
            LocalDate publishedOn,
            String thumbnailUrl,
            String role) {
    }

    public record PersonClaim(
            String personSlug,
            String statement,
            String speaker,
            String episodeSlug,
            String episodeTitle,
            String youtubeVideoId,
            LocalDate publishedOn,
            int startSeconds,
            String topic,
            String kind,
            String verdict,
            String explanation) {
    }

    public record ComparisonPair(String notice, PersonClaim first, PersonClaim second) {
    }

    public record PersonProfile(
            PersonSummary person,
            List<EpisodeAppearance> episodes,
            List<PersonClaim> claims,
            List<ComparisonPair> comparisons,
            List<TopicComparison> crossComparisons) {
    }

    public record SpeakerRef(
            String slug,
            String displayName,
            String displayNameAr,
            String partyCode,
            String partyNameFr,
            String partyNameAr,
            String partyColor) {
    }

    public record TopicComparison(
            String topic,
            PersonClaim first,
            SpeakerRef firstSpeaker,
            PersonClaim second,
            SpeakerRef secondSpeaker) {
    }

    public record PartySummary(
            String code,
            String nameFr,
            String nameAr,
            String color,
            int members,
            int appearances,
            int claims,
            List<PersonSummary> topMembers) {
    }

    public record PartyProfile(
            String code,
            String nameFr,
            String nameAr,
            String color,
            int members,
            int appearances,
            int claims,
            /**
             * Every affiliated member (unbounded by design: the only member
             * discovery page left after the guest list was removed), while
             * {@link PartySummary#topMembers} carries the first 12 for cards.
             */
            List<PersonSummary> topMembers,
            List<PersonClaim> recentClaims) {
    }
}
