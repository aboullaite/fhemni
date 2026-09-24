package dev.maboullaite.fhemni.election;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import dev.maboullaite.fhemni.election.ElectionResultRepository.ConstituencyWinnerRow;
import dev.maboullaite.fhemni.election.ElectionResultRepository.ElectionRow;
import dev.maboullaite.fhemni.election.ElectionResultRepository.PartyResultRow;
import dev.maboullaite.fhemni.election.ElectionResultRepository.RegionPartyResultRow;
import dev.maboullaite.fhemni.election.ElectionResultRepository.RegionRow;
import dev.maboullaite.fhemni.election.ElectionResultRepository.RegionalListWinnerRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ElectionResultService {

    public static final String ELECTION_2026 = "legislative-2026";

    private final ElectionResultRepository repository;

    ElectionResultService(ElectionResultRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ElectionResultSnapshot result(String slug, String requestedLanguage) {
        String language = language(requestedLanguage);
        ElectionRow election = repository.election(slug)
                .orElseThrow(() -> new NoSuchElementException("Election not found: " + slug));
        List<PartyResultRow> partyRows = repository.partyResults(election.id());
        List<RegionRow> regionRows = repository.regions(election.id());
        List<RegionPartyResultRow> regionPartyRows = repository.regionPartyResults(election.id());
        List<ConstituencyWinnerRow> winnerRows = repository.constituencyWinners(election.id());
        List<RegionalListWinnerRow> regionalWinnerRows = repository.regionalListWinners(election.id());

        long declaredSeats = partyRows.stream().mapToLong(PartyResultRow::totalSeats).sum();
        long localSeats = partyRows.stream().mapToLong(PartyResultRow::localSeats).sum();
        Long regionalListSeats = publishedRegionalListSeats(partyRows, regionPartyRows);
        if (declaredSeats > election.totalSeats()) {
            throw new IllegalStateException("Declared seats exceed the configured chamber size.");
        }

        List<PartyResult> parties = partyRows.stream()
                .map(row -> party(row, language, election.validVotes()))
                .toList();

        Map<RegionPartyKey, List<ConstituencyWinner>> winnersByRegionParty = new LinkedHashMap<>();
        for (ConstituencyWinnerRow row : winnerRows) {
            winnersByRegionParty.computeIfAbsent(
                            new RegionPartyKey(row.regionCode(), row.partyCode()),
                            ignored -> new java.util.ArrayList<>())
                    .add(constituencyWinner(row, language));
        }

        Map<RegionPartyKey, List<RegionalListWinner>> regionalWinnersByRegionParty = new LinkedHashMap<>();
        for (RegionalListWinnerRow row : regionalWinnerRows) {
            regionalWinnersByRegionParty.computeIfAbsent(
                            new RegionPartyKey(row.regionCode(), row.partyCode()),
                            ignored -> new java.util.ArrayList<>())
                    .add(regionalListWinner(row));
        }

        Map<String, List<RegionPartyResult>> partiesByRegion = new LinkedHashMap<>();
        for (RegionPartyResultRow row : regionPartyRows) {
            List<ConstituencyWinner> winners = winnersByRegionParty.getOrDefault(
                    new RegionPartyKey(row.regionCode(), row.code()), List.of());
            if (winners.size() > row.localSeats()) {
                throw new IllegalStateException("Constituency winners exceed the regional local-seat total.");
            }
            List<RegionalListWinner> regionalWinners = regionalWinnersByRegionParty.getOrDefault(
                    new RegionPartyKey(row.regionCode(), row.code()), List.of());
            if (regionalWinners.size() > row.regionalListSeats()) {
                throw new IllegalStateException("Named regional-list winners exceed the regional-list seat total.");
            }
            partiesByRegion.computeIfAbsent(row.regionCode(), ignored -> new java.util.ArrayList<>())
                    .add(regionParty(row, language, winners, regionalWinners));
        }
        List<RegionResult> regions = regionRows.stream()
                .map(row -> region(row, language, partiesByRegion.getOrDefault(row.code(), List.of())))
                .toList();

        ElectionOverview overview = new ElectionOverview(
                election.slug(),
                election.status(),
                election.electionDate(),
                election.totalSeats(),
                (election.totalSeats() / 2) + 1,
                declaredSeats,
                localSeats,
                regionalListSeats,
                election.registeredVoters(),
                election.votesCast(),
                election.validVotes(),
                election.voteBasis(),
                election.turnoutPercent() != null
                        ? election.turnoutPercent()
                        : percentage(election.votesCast(), election.registeredVoters()),
                sourceLabel(election, language),
                election.sourceUrl(),
                election.sourceUpdatedAt(),
                election.updatedAt());
        return new ElectionResultSnapshot(language, overview, parties, regions);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    CoalitionResultData coalitionResult(String slug) {
        ElectionRow election = repository.election(slug)
                .orElseThrow(() -> new NoSuchElementException("Election not found: " + slug));
        Map<String, Integer> seatsByParty = repository.partyResults(election.id()).stream()
                .collect(Collectors.toUnmodifiableMap(PartyResultRow::code, PartyResultRow::totalSeats));
        int declaredSeats = seatsByParty.values().stream().mapToInt(Integer::intValue).sum();
        if (declaredSeats > election.totalSeats()) {
            throw new IllegalStateException("Declared seats exceed the configured chamber size.");
        }
        return new CoalitionResultData(
                (election.totalSeats() / 2) + 1,
                election.updatedAt(),
                seatsByParty);
    }

    public static String language(String requestedLanguage) {
        if (requestedLanguage == null || requestedLanguage.isBlank()) return "ar";
        String normalized = requestedLanguage.strip().toLowerCase(Locale.ROOT);
        if (!List.of("ar", "fr", "en").contains(normalized)) {
            throw new IllegalArgumentException("Supported languages are ar, fr and en.");
        }
        return normalized;
    }

    private PartyResult party(PartyResultRow row, String language, Long validVotes) {
        return new PartyResult(
                row.code(),
                partyName(row.nameAr(), row.nameFr(), row.nameEn(), language),
                row.color(),
                row.symbolAsset(),
                row.votes(),
                percentage(row.votes(), validVotes),
                row.localSeats(),
                row.regionalListSeats(),
                row.totalSeats());
    }

    private RegionPartyResult regionParty(
            RegionPartyResultRow row,
            String language,
            List<ConstituencyWinner> winners,
            List<RegionalListWinner> regionalWinners) {
        return new RegionPartyResult(
                row.code(),
                partyName(row.nameAr(), row.nameFr(), row.nameEn(), language),
                row.color(),
                row.symbolAsset(),
                row.localSeats(),
                row.regionalListSeats(),
                row.totalSeats(),
                winners,
                regionalWinners);
    }

    private ConstituencyWinner constituencyWinner(ConstituencyWinnerRow row, String language) {
        String constituencyName = switch (language) {
            case "ar" -> row.constituencyNameAr();
            case "fr" -> row.constituencyNameFr();
            default -> row.constituencyNameEn();
        };
        return new ConstituencyWinner(
                row.constituencyCode(),
                constituencyName,
                row.candidateName(),
                row.votes(),
                row.status());
    }

    private RegionalListWinner regionalListWinner(RegionalListWinnerRow row) {
        return new RegionalListWinner(
                row.candidateKey(),
                row.candidateName(),
                row.resultStatus(),
                row.sourceLabel(),
                row.sourceUrl(),
                row.sourceUpdatedAt());
    }

    private RegionResult region(RegionRow row, String language, List<RegionPartyResult> parties) {
        long declaredSeats = parties.stream().mapToLong(RegionPartyResult::totalSeats).sum();
        return new RegionResult(
                row.code(),
                regionName(row, language),
                row.mapKey(),
                row.status(),
                row.allocatedSeats(),
                declaredSeats,
                parties);
    }

    private static String partyName(String arabic, String french, String english, String language) {
        return switch (language) {
            case "ar" -> arabic;
            case "fr" -> french;
            default -> english;
        };
    }

    private static String regionName(RegionRow row, String language) {
        return switch (language) {
            case "ar" -> row.nameAr();
            case "fr" -> row.nameFr();
            default -> row.nameEn();
        };
    }

    private static String sourceLabel(ElectionRow election, String language) {
        return switch (language) {
            case "ar" -> election.sourceLabelAr();
            case "fr" -> election.sourceLabelFr();
            default -> election.sourceLabelEn();
        };
    }

    private static BigDecimal percentage(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator <= 0) return null;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private static Long publishedRegionalListSeats(
            List<PartyResultRow> nationalResults,
            List<RegionPartyResultRow> regionalResults) {
        Map<String, Long> regionalSeatsByParty = regionalResults.stream()
                .collect(Collectors.groupingBy(
                        RegionPartyResultRow::code,
                        Collectors.summingLong(RegionPartyResultRow::regionalListSeats)));
        long identifiedRegionalSeats = nationalResults.stream()
                .mapToLong(row -> row.regionalListSeats() != null
                        ? row.regionalListSeats()
                        : regionalSeatsByParty.getOrDefault(row.code(), 0L))
                .sum();
        return identifiedRegionalSeats > 0 ? identifiedRegionalSeats : null;
    }

    public record ElectionResultSnapshot(
            String language,
            ElectionOverview election,
            List<PartyResult> parties,
            List<RegionResult> regions) {
    }

    record CoalitionResultData(
            int majoritySeats,
            Instant updatedAt,
            Map<String, Integer> seatsByParty) {
    }

    public record ElectionOverview(
            String slug,
            String status,
            LocalDate electionDate,
            int totalSeats,
            int majoritySeats,
            long declaredSeats,
            long localSeats,
            Long regionalListSeats,
            Long registeredVoters,
            Long votesCast,
            Long validVotes,
            String voteBasis,
            BigDecimal turnoutPercent,
            String sourceLabel,
            String sourceUrl,
            Instant sourceUpdatedAt,
            Instant updatedAt) {
    }

    public record PartyResult(
            String code,
            String name,
            String color,
            String symbolAsset,
            Long votes,
            BigDecimal voteShare,
            int localSeats,
            Integer regionalListSeats,
            int totalSeats) {
    }

    public record RegionResult(
            String code,
            String name,
            String mapKey,
            String status,
            Integer allocatedSeats,
            long declaredSeats,
            List<RegionPartyResult> parties) {
    }

    public record RegionPartyResult(
            String code,
            String name,
            String color,
            String symbolAsset,
            int localSeats,
            int regionalListSeats,
            int totalSeats,
            List<ConstituencyWinner> winners,
            List<RegionalListWinner> regionalListWinners) {
    }

    public record ConstituencyWinner(
            String constituencyCode,
            String constituencyName,
            String candidateName,
            Long votes,
            String status) {
    }

    public record RegionalListWinner(
            String candidateKey,
            String candidateName,
            String status,
            String sourceLabel,
            String sourceUrl,
            Instant sourceUpdatedAt) {
    }

    private record RegionPartyKey(String regionCode, String partyCode) {
    }
}
