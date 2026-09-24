package dev.maboullaite.fhemni.election;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ElectionResultRepository {

    private final JdbcClient jdbc;

    ElectionResultRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ElectionRow> election(String slug) {
        return jdbc.sql("""
                        SELECT id, slug, election_date, status, total_seats,
                               registered_voters, votes_cast, valid_votes, vote_basis, turnout_percent,
                               source_label_ar, source_label_fr, source_label_en,
                               source_url, source_updated_at, updated_at
                          FROM elections
                         WHERE slug = :slug
                        """)
                .param("slug", slug)
                .query(this::mapElection)
                .optional();
    }

    List<PartyResultRow> partyResults(UUID electionId) {
        return jdbc.sql("""
                        SELECT result.party_code, party.name_ar, party.name_fr,
                               party.color, party.symbol_asset, party.sort_order,
                               result.votes, result.local_seats,
                               result.regional_list_seats, result.total_seats
                          FROM election_party_results result
                          JOIN political_parties party ON party.code = result.party_code
                         WHERE result.election_id = :electionId
                           AND result.party_code <> 'UNKNOWN'
                         ORDER BY result.total_seats DESC,
                                  result.votes DESC NULLS LAST,
                                  party.sort_order
                        """)
                .param("electionId", electionId)
                .query(this::mapPartyResult)
                .list();
    }

    List<RegionRow> regions(UUID electionId) {
        return jdbc.sql("""
                        SELECT code, name_ar, name_fr, name_en, map_key,
                               allocated_seats, status, sort_order, updated_at
                          FROM election_regions
                         WHERE election_id = :electionId
                         ORDER BY sort_order
                        """)
                .param("electionId", electionId)
                .query(this::mapRegion)
                .list();
    }

    List<RegionPartyResultRow> regionPartyResults(UUID electionId) {
        return jdbc.sql("""
                        SELECT result.region_code, result.party_code,
                               party.name_ar, party.name_fr, party.color,
                               party.symbol_asset, party.sort_order,
                               result.local_seats, result.regional_list_seats,
                               result.total_seats
                          FROM election_region_party_results result
                          JOIN political_parties party ON party.code = result.party_code
                         WHERE result.election_id = :electionId
                           AND result.party_code <> 'UNKNOWN'
                         ORDER BY result.region_code,
                                  result.total_seats DESC,
                                  party.sort_order
                        """)
                .param("electionId", electionId)
                .query(this::mapRegionPartyResult)
                .list();
    }

    List<ConstituencyWinnerRow> constituencyWinners(UUID electionId) {
        return jdbc.sql("""
                        SELECT constituency.region_code,
                               winner.party_code,
                               constituency.code AS constituency_code,
                               constituency.name_ar,
                               constituency.name_fr,
                               constituency.name_en,
                               constituency.status,
                               constituency.sort_order AS constituency_sort_order,
                               winner.candidate_key,
                               winner.candidate_name,
                               winner.votes,
                               winner.sort_order AS winner_sort_order
                          FROM election_constituency_winners winner
                          JOIN election_constituencies constituency
                            ON constituency.election_id = winner.election_id
                           AND constituency.code = winner.constituency_code
                         WHERE winner.election_id = :electionId
                           AND winner.party_code <> 'UNKNOWN'
                         ORDER BY constituency.region_code,
                                  winner.party_code,
                                  constituency.sort_order,
                                  winner.sort_order
                        """)
                .param("electionId", electionId)
                .query(this::mapConstituencyWinner)
                .list();
    }

    private ElectionRow mapElection(ResultSet result, int rowNumber) throws SQLException {
        return new ElectionRow(
                result.getObject("id", UUID.class),
                result.getString("slug"),
                result.getObject("election_date", LocalDate.class),
                result.getString("status"),
                result.getInt("total_seats"),
                nullableLong(result, "registered_voters"),
                nullableLong(result, "votes_cast"),
                nullableLong(result, "valid_votes"),
                result.getString("vote_basis"),
                result.getBigDecimal("turnout_percent"),
                result.getString("source_label_ar"),
                result.getString("source_label_fr"),
                result.getString("source_label_en"),
                result.getString("source_url"),
                instant(result, "source_updated_at"),
                instant(result, "updated_at"));
    }

    private PartyResultRow mapPartyResult(ResultSet result, int rowNumber) throws SQLException {
        return new PartyResultRow(
                result.getString("party_code"),
                result.getString("name_ar"),
                result.getString("name_fr"),
                result.getString("color"),
                result.getString("symbol_asset"),
                result.getInt("sort_order"),
                nullableLong(result, "votes"),
                result.getInt("local_seats"),
                result.getObject("regional_list_seats", Integer.class),
                result.getInt("total_seats"));
    }

    private RegionRow mapRegion(ResultSet result, int rowNumber) throws SQLException {
        return new RegionRow(
                result.getString("code"),
                result.getString("name_ar"),
                result.getString("name_fr"),
                result.getString("name_en"),
                result.getString("map_key"),
                nullableInteger(result, "allocated_seats"),
                result.getString("status"),
                result.getInt("sort_order"),
                instant(result, "updated_at"));
    }

    private RegionPartyResultRow mapRegionPartyResult(ResultSet result, int rowNumber) throws SQLException {
        return new RegionPartyResultRow(
                result.getString("region_code"),
                result.getString("party_code"),
                result.getString("name_ar"),
                result.getString("name_fr"),
                result.getString("color"),
                result.getString("symbol_asset"),
                result.getInt("sort_order"),
                result.getInt("local_seats"),
                result.getInt("regional_list_seats"),
                result.getInt("total_seats"));
    }

    private ConstituencyWinnerRow mapConstituencyWinner(ResultSet result, int rowNumber) throws SQLException {
        return new ConstituencyWinnerRow(
                result.getString("region_code"),
                result.getString("party_code"),
                result.getString("constituency_code"),
                result.getString("name_ar"),
                result.getString("name_fr"),
                result.getString("name_en"),
                result.getString("status"),
                result.getInt("constituency_sort_order"),
                result.getString("candidate_key"),
                result.getString("candidate_name"),
                nullableLong(result, "votes"),
                result.getInt("winner_sort_order"));
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    record ElectionRow(
            UUID id,
            String slug,
            LocalDate electionDate,
            String status,
            int totalSeats,
            Long registeredVoters,
            Long votesCast,
            Long validVotes,
            String voteBasis,
            BigDecimal turnoutPercent,
            String sourceLabelAr,
            String sourceLabelFr,
            String sourceLabelEn,
            String sourceUrl,
            Instant sourceUpdatedAt,
            Instant updatedAt) {
    }

    record PartyResultRow(
            String code,
            String nameAr,
            String nameFr,
            String color,
            String symbolAsset,
            int sortOrder,
            Long votes,
            int localSeats,
            Integer regionalListSeats,
            int totalSeats) {
    }

    record RegionRow(
            String code,
            String nameAr,
            String nameFr,
            String nameEn,
            String mapKey,
            Integer allocatedSeats,
            String status,
            int sortOrder,
            Instant updatedAt) {
    }

    record RegionPartyResultRow(
            String regionCode,
            String code,
            String nameAr,
            String nameFr,
            String color,
            String symbolAsset,
            int sortOrder,
            int localSeats,
            int regionalListSeats,
            int totalSeats) {
    }

    record ConstituencyWinnerRow(
            String regionCode,
            String partyCode,
            String constituencyCode,
            String constituencyNameAr,
            String constituencyNameFr,
            String constituencyNameEn,
            String status,
            int constituencySortOrder,
            String candidateKey,
            String candidateName,
            Long votes,
            int winnerSortOrder) {
    }
}
