package dev.maboullaite.fhemni.election;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ElectionResultSnapshotSqlTest {

    private static final String MARKER = "-- Add the complete current snapshot to the two incoming result tables.";
    private static final String MIGRATION = read("src/main/resources/db/migration/V51__legislative_election_results.sql");
    private static final String SNAPSHOT = read("data/elections/2026/results.sql")
            .replaceFirst("(?m)^\\\\set ON_ERROR_STOP on\\R", "");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("fhemni")
            .withUsername("fhemni")
            .withPassword("test");

    @BeforeAll
    static void createElectionSchema() throws SQLException {
        execute("""
                CREATE TABLE political_parties (code VARCHAR(10) PRIMARY KEY);
                INSERT INTO political_parties (code) VALUES
                    ('RNI'), ('PAM'), ('PJD'), ('PI'), ('USFP'), ('MP'), ('UC'),
                    ('FGD'), ('MDS'), ('PPS'), ('UNKNOWN');
                """);
        execute(MIGRATION);
        execute(read("src/main/resources/db/migration/V52__add_election_turnout_percent.sql"));
        execute(read("src/main/resources/db/migration/V53__add_election_constituency_winners.sql"));
        execute(read("src/main/resources/db/migration/V54__allow_unknown_national_seat_breakdown.sql"));
        execute(SNAPSHOT);
    }

    @Test
    void canonicalSnapshotRemainsReplayable() throws SQLException {
        execute(SNAPSHOT);

        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM election_regions
                      WHERE election_id = '20260000-0000-4000-8000-000000000001'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(12);
        }
    }

    @Test
    void rejectsIncompletePreliminaryVoteBreakdownWhenValidVotesIsPresent() {
        String invalid = withSourceTimestamp(SNAPSHOT, "2099-09-23 18:00:00+00")
                .replace("CAST('COUNTING' AS VARCHAR(16)) AS status",
                        "CAST('PRELIMINARY' AS VARCHAR(16)) AS status")
                .replace("CAST(NULL AS BIGINT) AS valid_votes",
                        "CAST(1000 AS BIGINT) AS valid_votes");
        invalid = insertResults(invalid, """
                INSERT INTO incoming_election_party_results
                    (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'RNI', 900, 0, 0, 0);
                """);

        assertRejected(invalid, "A snapshot with valid_votes must reconcile every party vote exactly");
    }

    @Test
    void rejectsPartyVotesAboveValidVotes() {
        String invalid = withSourceTimestamp(SNAPSHOT, "2099-09-23 18:00:30+00")
                .replace("CAST(NULL AS BIGINT) AS valid_votes",
                        "CAST(1000 AS BIGINT) AS valid_votes");
        invalid = insertResults(invalid, """
                INSERT INTO incoming_election_party_results
                    (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'RNI', 1001, 0, 0, 0);
                """);

        assertRejected(invalid, "Declared party votes (1001) exceed valid votes (1000)");
    }

    @Test
    void rejectsNullPartyVoteWhenValidVotesIsPresent() {
        String invalid = withSourceTimestamp(SNAPSHOT, "2099-09-23 18:01:00+00")
                .replace("CAST(NULL AS BIGINT) AS valid_votes",
                        "CAST(1000 AS BIGINT) AS valid_votes");
        invalid = insertResults(invalid, """
                INSERT INTO incoming_election_party_results
                    (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'RNI', 1000, 0, 0, 0),
                    ('20260000-0000-4000-8000-000000000001', 'PAM', NULL, 0, 0, 0);
                """);

        assertRejected(invalid, "A snapshot with valid_votes must reconcile every party vote exactly");
    }

    @Test
    void rejectsCompleteRegionalAllocationBelowChamberSizeBeforeNationalFinal() {
        String invalid = withSourceTimestamp(SNAPSHOT, "2099-09-23 18:02:00+00")
                .replace("CAST('COUNTING' AS VARCHAR(16)) AS status",
                        "CAST('PRELIMINARY' AS VARCHAR(16)) AS status")
                .replace("'MA-01', NULL, 'PARTIAL', 1)", "'MA-01', 394, 'FINAL', 1)")
                .replace("'MA-01', NULL, 'PENDING', 1)", "'MA-01', 394, 'FINAL', 1)")
                .replace("NULL, 'PARTIAL'", "0, 'FINAL'")
                .replace("NULL, 'PENDING'", "0, 'FINAL'");
        invalid = insertResults(invalid, """
                INSERT INTO incoming_election_party_results
                    (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'RNI', NULL, 394, 0, 394);
                INSERT INTO incoming_election_region_party_results
                    (election_id, region_code, party_code, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'tanger-tetouan-al-hoceima', 'RNI', 394, 0, 394);
                """);

        assertRejected(invalid, "Complete regional allocations (394) must equal chamber size (395)");
    }

    @Test
    void rejectsRegionalSeatComponentsThatExceedNationalComponents() {
        String invalid = withSourceTimestamp(SNAPSHOT, "2099-09-23 18:03:00+00");
        invalid = insertResults(invalid, """
                INSERT INTO incoming_election_party_results
                    (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'RNI', NULL, 1, 1, 2);
                INSERT INTO incoming_election_region_party_results
                    (election_id, region_code, party_code, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'RNI', 2, 0, 2);
                """);

        assertRejected(invalid, "A regional party result exceeds a national seat component");
    }

    @Test
    void rejectsAConstituencyWinnerWithoutARegionalPartySeat() {
        String invalid = withSourceTimestamp(SNAPSHOT, "2099-09-23 18:04:00+00");
        invalid = insertResults(invalid, """
                INSERT INTO incoming_election_party_results
                    (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'RNI', NULL, 1, 0, 1);
                INSERT INTO incoming_election_constituencies
                    (election_id, code, region_code, name_ar, name_fr, name_en,
                     allocated_seats, status, sort_order)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'mediouna',
                     'casablanca-settat', 'مديونة', 'Médiouna', 'Mediouna',
                     1, 'PROVISIONAL', 1);
                INSERT INTO incoming_election_constituency_winners
                    (election_id, constituency_code, candidate_key, candidate_name,
                     party_code, votes, sort_order)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'mediouna',
                     'amine-nokta', 'Amine Nokta', 'RNI', NULL, 1);
                """);

        assertRejected(invalid, "Every constituency winner must belong to a declared regional party result");
    }

    @Test
    void rejectsNamedWinnersAboveAPartialRegionAllocation() {
        String invalid = withSourceTimestamp(SNAPSHOT, "2099-09-23 18:04:30+00")
                .replace(
                        "'casablanca-settat', 'الدار البيضاء - سطات', 'Casablanca-Settat', 'Casablanca-Settat', 'MA-06', NULL, 'PENDING', 6)",
                        "'casablanca-settat', 'الدار البيضاء - سطات', 'Casablanca-Settat', 'Casablanca-Settat', 'MA-06', 1, 'PARTIAL', 6)");
        invalid = insertResults(invalid, """
                INSERT INTO incoming_election_party_results
                    (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'RNI', NULL, 1, 0, 1),
                    ('20260000-0000-4000-8000-000000000001', 'PAM', NULL, 1, 0, 1);
                INSERT INTO incoming_election_region_party_results
                    (election_id, region_code, party_code, local_seats, regional_list_seats, total_seats)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'RNI', 1, 0, 1),
                    ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'PAM', 1, 0, 1);
                INSERT INTO incoming_election_constituencies
                    (election_id, code, region_code, name_ar, name_fr, name_en,
                     allocated_seats, status, sort_order)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'district-one',
                     'casablanca-settat', 'الدائرة 1', 'Circonscription 1', 'District 1',
                     1, 'PROVISIONAL', 1),
                    ('20260000-0000-4000-8000-000000000001', 'district-two',
                     'casablanca-settat', 'الدائرة 2', 'Circonscription 2', 'District 2',
                     1, 'PROVISIONAL', 2);
                INSERT INTO incoming_election_constituency_winners
                    (election_id, constituency_code, candidate_key, candidate_name,
                     party_code, votes, sort_order)
                VALUES
                    ('20260000-0000-4000-8000-000000000001', 'district-one',
                     'candidate-one', 'Candidate One', 'RNI', NULL, 1),
                    ('20260000-0000-4000-8000-000000000001', 'district-two',
                     'candidate-two', 'Candidate Two', 'PAM', NULL, 1);
                """);

        assertRejected(invalid, "Constituency winners exceed a region seat allocation");
    }

    private static String insertResults(String snapshot, String inserts) {
        int markerStart = snapshot.indexOf(MARKER);
        int resultsStart = snapshot.indexOf('\n', markerStart) + 1;
        int guardsStart = snapshot.indexOf("DO $$", resultsStart);
        if (markerStart < 0 || resultsStart == 0 || guardsStart < 0) {
            throw new IllegalStateException("Could not locate the snapshot result staging section");
        }
        return snapshot.substring(0, resultsStart)
                + inserts
                + System.lineSeparator()
                + snapshot.substring(guardsStart);
    }

    private static String withSourceTimestamp(String snapshot, String timestamp) {
        String updated = snapshot.replaceFirst(
                "(?:CAST\\(NULL AS TIMESTAMP WITH TIME ZONE\\)|TIMESTAMP WITH TIME ZONE '[^']+')"
                        + " AS source_updated_at",
                "TIMESTAMP WITH TIME ZONE '" + timestamp + "' AS source_updated_at");
        if (updated.equals(snapshot)) {
            throw new IllegalStateException("Could not replace the snapshot source timestamp");
        }
        return updated;
    }

    private static void assertRejected(String snapshot, String message) {
        assertThatThrownBy(() -> execute(snapshot))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(message);
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
