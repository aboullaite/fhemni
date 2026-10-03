import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Pin the actual vendored bytes, not merely the digest declared inside them.
const HISTORY_SHA256 = '3d2cea11a125ea9fa6cecfd0d4afd83b1b95bd9d2bf4d0a76feae0fe7e015323';
const SOURCE_SHA256 = '13378267af52eecba43d0bab4d3de5330351f48f4c9e3c580107da995f6b7ce1';
const SOURCE_URL = 'https://www.elections.ma/elections/legislatives/resultats.aspx?Id=8waOZwF4QzhMMKY7yKQzGQ==&IE=1';
const REGIONS = ['tanger-tetouan-al-hoceima', 'oriental', 'fes-meknes', 'rabat-sale-kenitra',
    'beni-mellal-khenifra', 'casablanca-settat', 'marrakech-safi', 'draa-tafilalet',
    'souss-massa', 'guelmim-oued-noun', 'laayoune-sakia-el-hamra', 'dakhla-oued-ed-dahab'];
// Explicit 2026 source IDs -> canonical database codes. Not cross-year continuity claims.
const PARTIES = {
    party_03de21a4f6ee: 'ALAMAL', party_040005b84836: 'RNI', party_24f6323d3344: 'ND',
    party_2ee36e3aa389: 'PDN', party_39839add54a7: 'FGD', party_41a003b0bb21: 'ANNAHDA',
    party_436bd8da3eac: 'PUD', party_44ee4fc77291: 'PA', party_48abf9bf0868: 'MP',
    party_4d1428aa3487: 'PVM', party_62c8ea4bcf76: 'USFP', party_6730200fc92a: 'PRD',
    party_6a5f7b3e337a: 'PCI', party_71662e25cf22: 'PE', party_8e4b86ab976a: 'IND',
    party_a77e5f9ed798: 'PI', party_b42379b4d45a: 'PAM', party_b659226b4897: 'FFD',
    party_b95eec033913: 'UC', party_bab1c39f7906: 'MDS', party_c16128d91ef1: 'PJD',
    party_d5463bfc3589: 'UMD', party_d5a42feecea3: 'PLJS', party_d5eaa38f2da4: 'PPS',
    party_d61a883a999d: 'PEDD', party_e4a2a0bd573e: 'PSD', party_eeaf0e919e4d: 'PCS',
    party_f7cacca1312c: 'PML'
};
const requireFact = (condition, message) => { if (!condition) throw new Error(message); };
const count = value => Number.isSafeInteger(value) && value >= 0;
const sum = (rows, key) => rows.reduce((total, row) => total + row[key], 0);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;

export function regionalLists(bytes) {
    requireFact(createHash('sha256').update(bytes).digest('hex') === HISTORY_SHA256, 'History checksum mismatch');
    const history = JSON.parse(bytes);
    requireFact(history.generation.sourceSha256 === SOURCE_SHA256, 'Source archive mismatch');
    const contests = history.regionalConstituencyResults.filter(row => row.year === 2026);
    requireFact(contests.length === 12 && new Set(contests.map(row => row.regionId)).size === 12,
        'Expected exactly 12 regional contests');
    const rows = [];
    for (const contest of contests) {
        const regionCode = REGIONS[Number(contest.regionId) - 1];
        requireFact(regionCode && contest.sourceUrl === SOURCE_URL && contest.constituencyType === 'regional', 'Unknown contest');
        const seen = new Set();
        for (const party of contest.parties) {
            const partyCode = PARTIES[party.partyId];
            requireFact(partyCode && !seen.has(partyCode), 'Unknown or duplicate party');
            seen.add(partyCode);
            requireFact(count(party.votes) && count(party.officialSeats)
                && party.voteFactStatus === 'REPORTED_ELECTIONS_MA'
                && party.officialSeatFactStatus === 'REPORTED_ELECTIONS_MA', 'Unverified list result');
            rows.push({ regionCode, partyCode, votes: party.votes, seats: party.officialSeats,
                sourceQueryId: contest.sourceQueryId });
        }
        const regionalRows = rows.filter(row => row.regionCode === regionCode);
        requireFact(sum(regionalRows, 'votes') === contest.totalVotes
            && sum(regionalRows, 'seats') === contest.allocatedSeats, 'Regional totals do not reconcile');
    }
    // Independents have no regional contest in the archive. Do not synthesize a zero row.
    const national = history.nationalPartyResults.filter(row => row.year === 2026
        && row.ballotVotes.some(ballot => ballot.type === 'regional')).map(party => {
        const regional = party.ballotVotes.find(row => row.type === 'regional');
        const partyCode = PARTIES[party.partyId];
        requireFact(partyCode && regional && count(regional.votes) && count(regional.seats), 'Missing national result');
        const lists = rows.filter(row => row.partyCode === partyCode);
        requireFact(sum(lists, 'votes') === regional.votes && sum(lists, 'seats') === regional.seats,
            `National regional-list totals do not reconcile: ${partyCode}`);
        return { partyCode, votes: regional.votes, seats: regional.seats };
    });
    requireFact(national.length === 27 && sum(rows, 'votes') === 4838149 && sum(rows, 'seats') === 90,
        'Snapshot totals do not reconcile');
    return { rows, national };
}

export function backfillSql(bytes) {
    const { rows, national } = regionalLists(bytes);
    return `-- Regional list votes; shared by every elected member of that list, never personal votes.
-- Evidence: elections.ma ${SOURCE_URL}
-- Source archive SHA-256: ${SOURCE_SHA256}
-- Verified history bytes SHA-256: ${HISTORY_SHA256}
-- Query IDs below identify the archived regional source pages. No publication timestamp is inferred.
-- Run AFTER V60, with psql -v ON_ERROR_STOP=1, on a backed-up database.
BEGIN;
LOCK TABLE elections, election_party_results, election_region_party_results, election_regional_list_winners IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE incoming_regional_votes (
    region_code TEXT, party_code TEXT, votes BIGINT, seats INTEGER, source_query_id TEXT,
    PRIMARY KEY (region_code, party_code)
) ON COMMIT DROP;
INSERT INTO incoming_regional_votes VALUES
${rows.map(row => `(${quote(row.regionCode)}, ${quote(row.partyCode)}, ${row.votes}, ${row.seats}, ${quote(row.sourceQueryId)})`).join(',\n')};
CREATE TEMP TABLE expected_national_regional_votes (party_code TEXT PRIMARY KEY, votes BIGINT, seats INTEGER) ON COMMIT DROP;
INSERT INTO expected_national_regional_votes VALUES
${national.map(row => `(${quote(row.partyCode)}, ${row.votes}, ${row.seats})`).join(',\n')};
DO $verify$
DECLARE target UUID;
BEGIN
    SELECT id INTO STRICT target FROM elections WHERE slug = 'legislative-2026';
    IF (SELECT SUM(regional_votes) FROM election_party_results WHERE election_id = target) IS DISTINCT FROM 4838149::BIGINT
        OR (SELECT SUM(regional_list_seats) FROM election_party_results WHERE election_id = target) IS DISTINCT FROM 90::BIGINT
    THEN RAISE EXCEPTION 'National totals changed'; END IF;
    IF EXISTS (
        SELECT 1 FROM expected_national_regional_votes incoming
        FULL JOIN (SELECT * FROM election_party_results WHERE election_id = target AND party_code <> 'IND') live USING (party_code)
        WHERE incoming.party_code IS NULL OR live.party_code IS NULL
            OR live.regional_votes IS DISTINCT FROM incoming.votes
            OR live.regional_list_seats IS DISTINCT FROM incoming.seats
    ) THEN RAISE EXCEPTION 'National regional-list results differ from verified evidence'; END IF;
    IF EXISTS (
        SELECT 1 FROM incoming_regional_votes incoming
        LEFT JOIN election_region_party_results live ON live.election_id = target
            AND live.region_code = incoming.region_code AND live.party_code = incoming.party_code
        WHERE (incoming.seats > 0 AND live.party_code IS NULL)
            OR (live.party_code IS NOT NULL AND live.regional_list_seats <> incoming.seats)
            OR (live.regional_votes IS NOT NULL AND live.regional_votes <> incoming.votes)
    ) THEN RAISE EXCEPTION 'Regional allocation or existing vote counts differ from verified evidence'; END IF;
    IF (SELECT COUNT(*) FROM election_regional_list_winners WHERE election_id = target) <> 90
       OR EXISTS (
        SELECT 1 FROM election_regional_list_winners winner
        LEFT JOIN incoming_regional_votes incoming USING (region_code, party_code)
        WHERE winner.election_id = target AND (incoming.party_code IS NULL OR incoming.seats = 0)
       ) OR EXISTS (
        SELECT 1 FROM incoming_regional_votes incoming WHERE incoming.seats <>
          (SELECT COUNT(*) FROM election_regional_list_winners winner WHERE winner.election_id = target
            AND winner.region_code = incoming.region_code AND winner.party_code = incoming.party_code)
       ) THEN RAISE EXCEPTION 'Regional winners do not map exactly to their verified lists'; END IF;
END $verify$;
UPDATE election_region_party_results result SET regional_votes = incoming.votes
FROM incoming_regional_votes incoming, elections election
WHERE result.election_id = election.id AND election.slug = 'legislative-2026'
    AND result.region_code = incoming.region_code AND result.party_code = incoming.party_code
    AND result.regional_votes IS DISTINCT FROM incoming.votes;
-- No seats, winners, national totals, timestamps or public source labels are changed.
COMMIT;
`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const historyPath = process.argv[2] || fileURLToPath(new URL('../../src/main/resources/static/data/elections/history.json', import.meta.url));
    process.stdout.write(backfillSql(readFileSync(historyPath)));
}
