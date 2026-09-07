package dev.maboullaite.fhemni.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import dev.maboullaite.fhemni.analysis.AnalysisRevisionRepository;
import dev.maboullaite.fhemni.model.AnalysisSnapshot;
import dev.maboullaite.fhemni.model.AnalysisStatus;
import dev.maboullaite.fhemni.model.Chapter;
import dev.maboullaite.fhemni.model.Claim;
import dev.maboullaite.fhemni.model.ClaimKind;
import dev.maboullaite.fhemni.model.ClaimVerdict;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.model.Participant;
import dev.maboullaite.fhemni.model.VideoReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.catalog.people-cache-ttl=PT0S",
        "spring.datasource.url=jdbc:h2:mem:person-catalog-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class PersonCatalogServiceTest {

    @Autowired
    private PersonCatalogService service;

    @Autowired
    private AnalysisRevisionRepository revisions;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void publishTwoEpisodesAndKeepADraftPrivate() {
        publish("n5B3boj2MFM", new VideoReport(
                "Episode one",
                "Summary one.",
                "Details one.",
                List.of(new Participant("Driss El Azami", "Guest")),
                List.of(
                        new Chapter("Introduction", 0, "Opening"),
                        new Chapter("Budget debate", 300, "Money talk")),
                List.of(
                        new Claim("c1", "The national deficit fell while desalination plants multiplied.", "Driss El Azami", 350,
                                ClaimKind.FACT, ClaimVerdict.SUPPORTED, "Confirmed by official data.", "HIGH", List.of()),
                        new Claim("c2", "I believe the reform will succeed.", "Nizar Baraka", 120,
                                ClaimKind.OPINION, ClaimVerdict.NOT_APPLICABLE, "", "", List.of())),
                List.of()));
        publish("14IF32HrTBs", new VideoReport(
                "Episode two",
                "Summary two.",
                "Details two.",
                List.of(new Participant("إدريس الأزمي", "ضيف")),
                List.of(
                        new Chapter("المقدمة", 0, "البداية"),
                        new Chapter("Budget debate", 100, "Money talk")),
                List.of(
                        new Claim("c3", "The national deficit rose although desalination plants multiplied.", "إدريس الأزمي", 200,
                                ClaimKind.FACT, ClaimVerdict.CONTRADICTED, "Denied by official data.", "HIGH", List.of()),
                        new Claim("c4", "Desalination plants need national budget oversight.", "Nizar Baraka", 150,
                                ClaimKind.FACT, ClaimVerdict.NEEDS_CONTEXT, "Needs context.", "MEDIUM", List.of()),
                        new Claim("c5", "While national parties face elections government.", "Stopword Guest", 50,
                                ClaimKind.FACT, ClaimVerdict.SUPPORTED, "Supported.", "LOW", List.of())),
                List.of()));

        jdbc.sql("""
                UPDATE catalog_videos
                   SET status = 'CATALOGUED',
                       short_summary = NULL,
                       published_analysis_id = NULL
                 WHERE youtube_video_id = 'quf5ok_tkB4'
                """).update();
        UUID draftId = UUID.randomUUID();
        revisions.create(
                new AnalysisSnapshot(
                        draftId,
                        "https://www.youtube.com/watch?v=quf5ok_tkB4",
                        "quf5ok_tkB4",
                        OutputLanguage.DARIJA,
                        AnalysisStatus.QUEUED,
                        2,
                        "Video accepted",
                        false,
                        Instant.now(),
                        null,
                        null,
                        List.of(),
                        false,
                        null),
                "test-model", "test-prompt", "test-fact-model", "test-fact-prompt", "test-credential");
        revisions.complete(draftId, new VideoReport(
                "Draft episode",
                "Draft summary.",
                "Draft details.",
                List.of(new Participant("Draft Only Person", "Guest")),
                List.of(),
                List.of(),
                List.of()), "interaction-draft");
    }

    @Test
    void aggregatesAppearancesAcrossPublishedEpisodesOnly() {
        var summaries = service.searchPeople("", null);

        assertThat(summaries).extracting(PersonCatalogService.PersonSummary::slug)
                .contains("driss-el-azami", "nizar-baraka")
                .doesNotContain("draft-only-person");

        var azami = summaries.stream()
                .filter(summary -> summary.slug().equals("driss-el-azami"))
                .findFirst()
                .orElseThrow();
        assertThat(azami.partyCode()).isEqualTo("PJD");
        assertThat(azami.appearances()).isEqualTo(2);
        assertThat(azami.claims()).isEqualTo(2);
        assertThat(azami.displayName()).isEqualTo("Driss El Azami");
        assertThat(azami.displayNameAr()).isEqualTo("إدريس الأزمي");
        assertThat(azami.spellings()).contains("Driss El Azami", "إدريس الأزمي");
    }

    @Test
    void searchesByNameInFrenchOrArabicAndFiltersByParty() {
        assertThat(service.searchPeople("azami", null))
                .extracting(PersonCatalogService.PersonSummary::slug)
                .containsExactly("driss-el-azami");
        assertThat(service.searchPeople("الأزمي", null))
                .extracting(PersonCatalogService.PersonSummary::slug)
                .containsExactly("driss-el-azami");
        assertThat(service.searchPeople("", "PJD"))
                .extracting(PersonCatalogService.PersonSummary::slug)
                .contains("driss-el-azami")
                .doesNotContain("nizar-baraka");
        assertThat(service.searchPeople("nobody matches this", null)).isEmpty();
    }

    @Test
    void buildsAGuestSheetWithTopicsAndPassagesToCompare() {
        var profile = service.person("driss-el-azami");

        assertThat(profile.episodes()).hasSize(2);
        assertThat(profile.claims()).hasSize(2);
        assertThat(profile.person().topics()).contains("Budget debate");

        // Same speaker, different episodes, different independent assessments.
        assertThat(profile.comparisons()).hasSizeGreaterThanOrEqualTo(1);
        var pair = profile.comparisons().get(0);
        assertThat(pair.first().episodeSlug()).isNotEqualTo(pair.second().episodeSlug());
        assertThat(pair.first().verdict()).isNotEqualTo(pair.second().verdict());
    }

    @Test
    void pairsStatementsWithOtherGuestsOnTheSameTopic() {
        var profile = service.person("driss-el-azami");

        assertThat(profile.crossComparisons()).hasSize(1);
        var pair = profile.crossComparisons().get(0);
        assertThat(pair.topic()).isEqualTo("Budget debate");
        assertThat(pair.firstSpeaker().slug()).isEqualTo("driss-el-azami");
        assertThat(pair.secondSpeaker().slug()).isEqualTo("nizar-baraka");
        assertThat(pair.secondSpeaker().partyCode()).isEqualTo("PI");
        assertThat(pair.first().episodeSlug()).isNotEqualTo(pair.second().episodeSlug());
    }

    @Test
    void ignoresPassagesSharingOnlyGenericPoliticalVocabulary() {
        // c5 shares five long words with c1, but three are stop-words and the
        // chapters differ: no pair in either direction.
        assertThat(service.person("stopword-guest").crossComparisons()).isEmpty();
        assertThat(service.person("driss-el-azami").crossComparisons()).hasSize(1);
    }

    @Test
    void buildsPartySheetsFromPublishedEpisodes() {
        var parties = service.parties();
        assertThat(parties).extracting(PersonCatalogService.PartySummary::code)
                .containsExactlyInAnyOrder("PI", "PJD");

        var pjd = service.party("pjd");
        assertThat(pjd.members()).isEqualTo(1);
        assertThat(pjd.appearances()).isEqualTo(2);
        assertThat(pjd.claims()).isEqualTo(2);
        assertThat(pjd.episodes()).extracting(PersonCatalogService.EpisodeAppearance::slug)
                .containsExactlyInAnyOrder("episode-n5B3boj2MFM", "episode-14IF32HrTBs");
    }

    @Test
    void deduplicatesPartyAppearancesWhenMultipleMembersAppearInSameEpisode() {
        publish("quf5ok_tkB4", new VideoReport(
                "Joint appearance episode",
                "Summary.",
                "Details.",
                List.of(
                        new Participant("Driss El Azami", "Guest"),
                        new Participant("Abdelilah Benkirane", "Guest")),
                List.of(),
                List.of(
                        new Claim("c6", "Statement 1", "Driss El Azami", 10,
                                ClaimKind.FACT, ClaimVerdict.SUPPORTED, "Note.", "HIGH", List.of()),
                        new Claim("c7", "Statement 2", "Abdelilah Benkirane", 20,
                                ClaimKind.FACT, ClaimVerdict.SUPPORTED, "Note.", "HIGH", List.of())),
                List.of()));

        var pjd = service.party("pjd");
        var pjdSummary = service.parties().stream()
                .filter(p -> p.code().equals("PJD"))
                .findFirst()
                .orElseThrow();

        // Driss appears in 3 episodes (n5B3boj2MFM, 14IF32HrTBs, quf5ok_tkB4).
        // Benkirane appears in 1 episode (quf5ok_tkB4).
        // Sum of per-member appearances would be 4, but distinct episode appearances is 3.
        assertThat(pjd.appearances()).isEqualTo(3);
        assertThat(pjdSummary.appearances()).isEqualTo(3);
        assertThat(pjd.claims()).isEqualTo(4);
        assertThat(pjdSummary.claims()).isEqualTo(4);
    }

    @Test
    void rejectsUnknownGuestsAndParties() {
        assertThatThrownBy(() -> service.person("no-such-guest"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.party("XX"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void hidesUnaffiliatedGuestsFromPartySheets() {
        assertThat(service.parties())
                .extracting(PersonCatalogService.PartySummary::code)
                .doesNotContain("UNKNOWN", "IND");
        assertThatThrownBy(() -> service.party("UNKNOWN"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.party("IND"))
                .isInstanceOf(NoSuchElementException.class);
    }

    private void publish(String youtubeVideoId, VideoReport report) {
        UUID analysisId = UUID.randomUUID();
        revisions.create(
                new AnalysisSnapshot(
                        analysisId,
                        "https://www.youtube.com/watch?v=" + youtubeVideoId,
                        youtubeVideoId,
                        OutputLanguage.DARIJA,
                        AnalysisStatus.QUEUED,
                        2,
                        "Video accepted",
                        false,
                        Instant.now(),
                        null,
                        null,
                        List.of(),
                        false,
                        null),
                "test-model", "test-prompt", "test-fact-model", "test-fact-prompt", "test-credential");
        revisions.complete(analysisId, report, "interaction-test");
        jdbc.sql("""
                        UPDATE catalog_videos
                           SET status = 'PUBLISHED',
                               short_summary = :summary,
                               published_analysis_id = :analysisId
                         WHERE youtube_video_id = :youtubeVideoId
                        """)
                .param("summary", report.summary())
                .param("analysisId", analysisId)
                .param("youtubeVideoId", youtubeVideoId)
                .update();
    }
}
