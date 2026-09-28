const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const {
    allocateLocalSeatsUnder2016Rules,
    verify2016LocalSeatRuleReproduction,
    derive2026LocalSeatCounterfactual,
    verify2016NationalListRuleReproduction,
    derive2026Full2016SystemCounterfactual
} = require('../../main/resources/static/js/historical-electoral-quotient.js');

const repositoryRoot = path.join(__dirname, '../../..');
const generatorUrl = pathToFileURL(path.join(repositoryRoot,
    'scripts/elections/generate-historical-election-data.mjs')).href;
const sourcePath = path.join(repositoryRoot,
    'data/elections/history/morocco-legislative-results.json');
const manifestPath = path.join(repositoryRoot, 'data/elections/history/manifest.json');

async function generatedPayload() {
    const generator = await import(generatorUrl);
    const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    return generator.buildHistoricalPayload(source, manifest);
}

test('2016 allocator applies the 3% threshold, eligible-vote quotient, and largest remainder exactly', () => {
    const result = allocateLocalSeatsUnder2016Rules({
        year: 2026,
        constituencyId: 'fixture',
        constituencyNameAr: 'دائرة اختبار',
        allocatedSeats: 4,
        totalVotes: 1000,
        parties: [
            { partyId: 'a', votes: 500 },
            { partyId: 'b', votes: 300 },
            { partyId: 'c', votes: 171 },
            { partyId: 'd', votes: 29 }
        ]
    });

    assert.equal(result.available, true);
    assert.deepEqual(result.eligibilityThreshold, {
        percent: 3,
        numerator: 3000,
        denominator: 100,
        value: 30
    });
    assert.deepEqual(result.electoralQuotient, {
        numerator: 971,
        denominator: 4,
        value: 242.75
    });
    assert.equal(result.eligibleVotes, 971);
    assert.equal(result.firstPassSeatTotal, 3);
    assert.equal(result.largestRemainderSeatTotal, 1);
    assert.deepEqual(result.parties.map(row => ({
        partyId: row.partyId,
        eligible: row.eligible,
        firstPassSeats: row.firstPassSeats,
        remainder: row.remainder,
        largestRemainderSeats: row.largestRemainderSeats,
        simulatedSeats: row.simulatedSeats
    })), [
        { partyId: 'a', eligible: true, firstPassSeats: 2,
            remainder: { numerator: 58, denominator: 4, value: 14.5 },
            largestRemainderSeats: 0, simulatedSeats: 2 },
        { partyId: 'b', eligible: true, firstPassSeats: 1,
            remainder: { numerator: 229, denominator: 4, value: 57.25 },
            largestRemainderSeats: 0, simulatedSeats: 1 },
        { partyId: 'c', eligible: true, firstPassSeats: 0,
            remainder: { numerator: 684, denominator: 4, value: 171 },
            largestRemainderSeats: 1, simulatedSeats: 1 },
        { partyId: 'd', eligible: false, firstPassSeats: 0,
            remainder: null, largestRemainderSeats: 0, simulatedSeats: 0 }
    ]);
    assert.equal(result.simulatedSeatTotal, 4);
    assert.equal(result.analysisFactStatus,
        'FHEMNI_COUNTERFACTUAL_ANALYSIS_BASED_ON_ELECTIONS_MA');
});

test('allocator fails closed when a largest-remainder boundary tie needs an unverified tie-break', () => {
    const result = allocateLocalSeatsUnder2016Rules({
        constituencyId: 'tie',
        allocatedSeats: 1,
        totalVotes: 1000,
        parties: [
            { partyId: 'a', votes: 500 },
            { partyId: 'b', votes: 500 }
        ]
    });

    assert.equal(result.available, false);
    assert.deepEqual(result.diagnostics, ['largest-remainder-boundary-tie']);
    assert.deepEqual(result.parties, []);
});

test('generated data retains the complete 2016 and 2026 local contest evidence', async () => {
    const payload = await generatedPayload();

    assert.equal(payload.localConstituencyResults.length, 184);
    for (const year of [2016, 2026]) {
        const contests = payload.localConstituencyResults.filter(row => row.year === year);
        assert.equal(contests.length, 92);
        assert.equal(contests.reduce((total, row) => total + row.allocatedSeats, 0), 305);
        assert.equal(contests.reduce((total, row) => total + row.totalVotes, 0),
            year === 2016 ? 5790552 : 4900377);
    }
    assert.ok(payload.localConstituencyResults.every(contest =>
        contest.constituencyType === 'local'
        && contest.aggregateFactStatus === 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA'
        && /^https:\/\/www\.elections\.ma\//.test(contest.sourceUrl)
        && contest.parties.every(party =>
            party.voteFactStatus === 'REPORTED_ELECTIONS_MA'
            && party.officialSeatFactStatus === 'REPORTED_ELECTIONS_MA'
            && typeof party.votesSourceText === 'string'
            && party.votesSourceText.length > 0)));
});

test('2016 proof gate reproduces all 92 official local contests and all 305 seats exactly', async () => {
    const payload = await generatedPayload();
    const proof = verify2016LocalSeatRuleReproduction(payload);

    assert.deepEqual(proof, {
        available: true,
        diagnostics: [],
        ruleId: 'MOROCCO_2016_LOCAL_3_PERCENT_ELIGIBLE_QUOTIENT_LARGEST_REMAINDER',
        year: 2016,
        constituencyCount: 92,
        seatCount: 305,
        exactConstituencyMatches: 92,
        exactSeatMatches: 305,
        boundaryTieCount: 0,
        proofStatus: 'VERIFIED_BY_EXACT_2016_LOCAL_SEAT_REPRODUCTION',
        sourceUrl: payload.elections.find(row => row.year === 2016).sourceUrl
    });
});

test('2026 counterfactual is local-only and exposes exact national deltas after the 2016 proof gate', async () => {
    const payload = await generatedPayload();
    const result = derive2026LocalSeatCounterfactual(payload);

    assert.equal(result.available, true);
    assert.equal(result.ballotType, 'local');
    assert.equal(result.officialSeatTotal, 305);
    assert.equal(result.simulatedSeatTotal, 305);
    assert.equal(result.constituencies.length, 92);
    assert.equal(result.validation.exactConstituencyMatches, 92);
    assert.equal(result.validation.proofStatus,
        'VERIFIED_BY_EXACT_2016_LOCAL_SEAT_REPRODUCTION');
    assert.deepEqual(result.partyDeltas.filter(row => row.delta !== 0)
        .map(row => [row.abbreviation, row.officialLocalSeats,
            row.simulatedLocalSeats, row.delta]), [
        ['PI', 52, 54, 2],
        ['PAM', 85, 86, 1],
        ['PJD', 44, 45, 1],
        ['AG', 3, 2, -1],
        ['RNI', 57, 54, -3]
    ]);
    assert.equal(result.partyDeltas.reduce((total, row) => total + row.delta, 0), 0);
    assert.ok(result.constituencies.every(contest =>
        contest.analysisFactStatus === 'FHEMNI_COUNTERFACTUAL_ANALYSIS_BASED_ON_ELECTIONS_MA'
        && contest.parties.every(party => Number.isInteger(party.firstPassSeats)
            && Number.isInteger(party.simulatedSeats)
            && (party.remainder === null || Number.isFinite(party.remainder.value)))));
});

test('counterfactual fails closed when the 2016 proof or source-evidence contract is broken', async () => {
    const payload = await generatedPayload();
    const brokenProof = structuredClone(payload);
    const contest = brokenProof.localConstituencyResults.find(row => row.year === 2016);
    const winner = contest.parties.find(row => row.officialSeats > 0);
    const loser = contest.parties.find(row => row.officialSeats === 0);
    winner.officialSeats -= 1;
    loser.officialSeats += 1;
    const proof = verify2016LocalSeatRuleReproduction(brokenProof);
    assert.equal(proof.available, false);
    assert.ok(proof.diagnostics.includes('official-allocation-mismatch'));
    assert.equal(derive2026LocalSeatCounterfactual(brokenProof).available, false);

    const brokenEvidence = structuredClone(payload);
    brokenEvidence.localConstituencyResults.find(row => row.year === 2026)
        .parties[0].voteFactStatus = 'UNVERIFIED';
    const simulation = derive2026LocalSeatCounterfactual(brokenEvidence);
    assert.equal(simulation.available, false);
    assert.ok(simulation.diagnostics.includes('local-source-evidence'));
});

test('proof and simulation bind local facts back to the complete party and vote rosters', async () => {
    const payload = await generatedPayload();
    const brokenParty = structuredClone(payload);
    brokenParty.localConstituencyResults.find(row => row.year === 2016)
        .parties[0].comparisonKey = 'source:2016:fake';
    const proof = verify2016LocalSeatRuleReproduction(brokenParty);
    assert.equal(proof.available, false);
    assert.ok(proof.diagnostics.includes('local-party-observation'));

    const brokenVote = structuredClone(payload);
    const contest = brokenVote.localConstituencyResults.find(row => row.year === 2026);
    contest.parties[0].votes += 1;
    contest.totalVotes += 1;
    const simulation = derive2026LocalSeatCounterfactual(brokenVote);
    assert.equal(simulation.available, false);
    assert.ok(simulation.diagnostics.includes('national-local-vote-mismatch'));
});

test('2016 national-list proof gate reproduces all 24 party rows and all 90 seats exactly', async () => {
    const payload = await generatedPayload();
    const proof = verify2016NationalListRuleReproduction(payload);

    assert.equal(proof.available, true);
    assert.equal(proof.partyCount, 24);
    assert.equal(proof.seatCount, 90);
    assert.equal(proof.exactPartyMatches, 24);
    assert.equal(proof.exactSeatMatches, 90);
    assert.equal(proof.boundaryTieCount, 0);
    assert.equal(proof.proofStatus,
        'VERIFIED_BY_EXACT_2016_NATIONAL_LIST_SEAT_REPRODUCTION');
});

test('full 2016-system scenario decomposes local, list, and total seats without conflating ballots', async () => {
    const payload = await generatedPayload();
    const result = derive2026Full2016SystemCounterfactual(payload);

    assert.equal(result.available, true);
    assert.equal(result.officialSeatTotal, 395);
    assert.equal(result.simulatedSeatTotal, 395);
    assert.equal(result.localValidation.exactSeatMatches, 305);
    assert.equal(result.listValidation.exactSeatMatches, 90);
    assert.equal(result.listScenario, 'AGGREGATED_2026_REGIONAL_VOTES_AS_2016_NATIONAL_LIST');
    assert.deepEqual(result.partyDeltas.filter(row => row.delta !== 0).map(row => [
        row.abbreviation,
        row.officialLocalSeats,
        row.simulatedLocalSeats,
        row.officialRegionalListSeats,
        row.simulatedNationalListSeats,
        row.officialTotalSeats,
        row.simulatedTotalSeats,
        row.delta
    ]), [
        ['PAM', 85, 86, 12, 21, 97, 107, 10],
        ['PJD', 44, 45, 10, 15, 54, 60, 6],
        ['PI', 52, 54, 13, 15, 65, 69, 4],
        ['RNI', 57, 54, 9, 13, 66, 67, 1],
        ['MDS', 4, 4, 4, 3, 8, 7, -1],
        ['P.EQUITE', 1, 1, 1, 0, 2, 1, -1],
        ['PND', 0, 0, 1, 0, 1, 0, -1],
        ['MP', 20, 20, 9, 7, 29, 27, -2],
        ['FFD', 0, 0, 2, 0, 2, 0, -2],
        ['USFP', 15, 15, 11, 7, 26, 22, -4],
        ['PPS', 10, 10, 9, 5, 19, 15, -4],
        ['AG', 3, 2, 5, 0, 8, 2, -6]
    ]);
    assert.equal(result.partyDeltas.reduce((sum, row) => sum + row.delta, 0), 0);
});

test('full scenario fails closed when the 2016 national-list proof is broken', async () => {
    const payload = await generatedPayload();
    const broken = structuredClone(payload);
    const party = broken.nationalPartyResults.find(row => row.year === 2016
        && row.ballotVotes.some(ballot => ballot.type === 'national' && ballot.seats > 0));
    party.ballotVotes.find(ballot => ballot.type === 'national').seats -= 1;

    const proof = verify2016NationalListRuleReproduction(broken);
    assert.equal(proof.available, false);
    assert.ok(proof.diagnostics.includes('national-list-seat-total')
        || proof.diagnostics.includes('official-allocation-mismatch'));
    assert.equal(derive2026Full2016SystemCounterfactual(broken).available, false);
});
