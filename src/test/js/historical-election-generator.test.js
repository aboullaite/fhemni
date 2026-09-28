const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');

const repositoryRoot = path.join(__dirname, '../../..');
const generatorPath = path.join(repositoryRoot,
    'scripts/elections/generate-historical-election-data.mjs');
const generatorUrl = pathToFileURL(generatorPath).href;
const pinnedSourcePath = path.join(repositoryRoot,
    'data/elections/history/morocco-legislative-results.json');
const manifestPath = path.join(repositoryRoot, 'data/elections/history/manifest.json');
const committedOutputPath = path.join(repositoryRoot,
    'src/main/resources/static/data/elections/history.json');

const PINNED_DIGEST = '13378267af52eecba43d0bab4d3de5330351f48f4c9e3c580107da995f6b7ce1';
const EXPECTED_BALLOTS = {
    2016: {
        local: { seats: 305, votes: 5790552 },
        national: { seats: 90, votes: 5806004 }
    },
    2021: {
        local: { seats: 305, votes: 7588505 },
        regional: { seats: 90, votes: 7571623 }
    },
    2026: {
        local: { seats: 305, votes: 4900377 },
        regional: { seats: 90, votes: 4838149 }
    }
};
const EXPECTED_TURNOUT = { 2016: 42.29, 2021: 50.86, 2026: 38.08 };

function runGenerator(output, source = pinnedSourcePath, manifest = manifestPath) {
    return spawnSync(process.execPath, [generatorPath, output, source, manifest], {
        cwd: repositoryRoot,
        encoding: 'utf8'
    });
}

function assertUnique(rows, keyOf, label) {
    const keys = rows.map(keyOf);
    assert.equal(new Set(keys).size, keys.length, `${label} keys must be unique`);
}

function collectHttpUrls(value, urls = []) {
    if (Array.isArray(value)) {
        value.forEach(item => collectHttpUrls(item, urls));
    } else if (value && typeof value === 'object') {
        Object.values(value).forEach(item => collectHttpUrls(item, urls));
    } else if (typeof value === 'string' && /^https?:\/\//.test(value)) {
        urls.push(value);
    }
    return urls;
}

test('generation rejects archive bytes that do not match the pinned SHA-256', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'fhemni-history-integrity-'));
    const source = path.join(directory, 'tampered.json');
    const output = path.join(directory, 'history.json');
    const original = fs.readFileSync(pinnedSourcePath, 'utf8');
    fs.writeFileSync(source, original.replace('2016-10-07', '2016-10-08'));

    const generated = runGenerator(output, source);

    assert.notEqual(generated.status, 0, 'tampered archive bytes must fail generation');
    assert.match(generated.stderr || generated.stdout, /sha-?256|digest|integrity/i);
    assert.equal(fs.existsSync(output), false, 'no payload should be written from unpinned bytes');
});

test('in-memory validation rejects missing elections and broken 395 or 305/90 seat totals', async () => {
    const generator = await import(generatorUrl).catch(() => null);
    assert.equal(typeof generator?.buildHistoricalPayload, 'function',
        'the generator must expose the validation path used by the CLI');
    const source = JSON.parse(fs.readFileSync(pinnedSourcePath, 'utf8'));

    const missingElection = structuredClone(source);
    missingElection.elections.pop();
    assert.throws(() => generator.buildHistoricalPayload(missingElection), /2016.*2021.*2026|three elections/i);

    const missingElected = structuredClone(source);
    missingElected.elected_candidates.pop();
    assert.throws(() => generator.buildHistoricalPayload(missingElected), /395 elected|contest allocation/i);

    const brokenBallotSplit = structuredClone(source);
    brokenBallotSplit.elected_candidates.find(row => row.constituency_type === 'local')
        .constituency_type = 'regional';
    assert.throws(() => generator.buildHistoricalPayload(brokenBallotSplit),
        /305.*90|ballot.*seats|mandate.*constituency/i);

    const brokenDemographics = structuredClone(source);
    brokenDemographics.demographics.find(row => row.query_id === '2021:r0_p0_c0'
        && row.dimension === 'gender').count_inferred_from_percentage -= 1;
    assert.throws(() => generator.buildHistoricalPayload(brokenDemographics), /demographic.*395|395.*demographic/i);

    const brokenPartySeats = structuredClone(source);
    const winningVoteRow = brokenPartySeats.votes.find(row => row.election_id.includes('2016')
        && row.constituency_type === 'local' && row.seats > 0);
    const losingVoteRow = brokenPartySeats.votes.find(row => row.election_id.includes('2016')
        && row.constituency_type === 'local' && row.seats === 0
        && row.party_id !== winningVoteRow.party_id);
    winningVoteRow.seats -= 1;
    losingVoteRow.seats += 1;
    assert.throws(() => generator.buildHistoricalPayload(brokenPartySeats),
        /party.*seat|seat.*party|party.*allocation/i);
});

test('mandates reconcile to their exact contest geography, party result, and allocation', async () => {
    const generator = await import(generatorUrl);
    const source = JSON.parse(fs.readFileSync(pinnedSourcePath, 'utf8'));

    const changedParty = structuredClone(source);
    const candidate = changedParty.elected_candidates.find(row => row.election_id.includes('2021')
        && row.constituency_type === 'local');
    const otherWinningParty = changedParty.votes.find(row => row.election_id === candidate.election_id
        && row.source_query_id === candidate.source_query_id && row.seats > 0
        && row.party_id !== candidate.party_id);
    const otherParty = changedParty.parties.find(row => row.election_id === candidate.election_id
        && row.party_id === otherWinningParty.party_id);
    candidate.party_id = otherParty.party_id;
    candidate.party_abbreviation = otherParty.abbreviation;
    assert.throws(() => generator.buildHistoricalPayload(changedParty), /mandate.*party|party.*allocation/i,
        'moving a mandate to another valid party must break the contest allocation');

    const changedRegion = structuredClone(source);
    const regionalCandidate = changedRegion.elected_candidates.find(row => row.election_id.includes('2026')
        && row.constituency_type === 'local');
    regionalCandidate.region_id = changedRegion.regions.find(row => row.election_id === regionalCandidate.election_id
        && row.region_id !== regionalCandidate.region_id).region_id;
    assert.throws(() => generator.buildHistoricalPayload(changedRegion), /mandate.*region|region.*constituency/i,
        'moving a mandate to another valid region must break contest geography');
});

test('archive fact tables reject duplicate natural keys', async () => {
    const generator = await import(generatorUrl);
    const source = JSON.parse(fs.readFileSync(pinnedSourcePath, 'utf8'));
    for (const table of ['votes', 'seat_results', 'turnout', 'demographics', 'queries']) {
        const mutated = structuredClone(source);
        mutated[table].push(structuredClone(mutated[table][0]));
        assert.throws(() => generator.buildHistoricalPayload(mutated), /duplicate/i,
            `${table} must reject its natural-key duplicate`);
    }
});

test('archive numeric facts and reported source text fail closed on invalid values', async () => {
    const generator = await import(generatorUrl);
    const source = JSON.parse(fs.readFileSync(pinnedSourcePath, 'utf8'));
    const mutations = [
        ['negative votes', data => { data.votes[0].votes = -1; }],
        ['fractional seats', data => { data.seat_results[0].seats = 1.5; }],
        ['turnout over 100', data => { data.turnout[0].turnout_percentage = 101; }],
        ['demographic percentage below zero', data => {
            data.demographics.find(row => row.value_status === 'reported').percentage = -0.01;
        }],
        ['negative query count', data => { data.queries[0].vote_rows = -1; }],
        ['missing parsed-vote source text', data => { data.votes[0].votes_source_text = ''; }],
        ['missing reported demographic source text', data => {
            data.demographics.find(row => row.value_status === 'reported').source_percentage_text = null;
        }],
        ['missing turnout source text', data => { data.turnout[0].source_percentage_text = ''; }]
    ];
    for (const [label, mutate] of mutations) {
        const data = structuredClone(source);
        mutate(data);
        assert.throws(() => generator.buildHistoricalPayload(data),
            /nonnegative integer|0.*100|source.*text|literal/i, label);
    }
});

test('the payload preserves exact ballot totals and exposes comparison-ready observations', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'fhemni-history-payload-'));
    const output = path.join(directory, 'history.json');
    const generated = runGenerator(output);
    assert.equal(generated.status, 0, generated.stderr || generated.stdout);
    const payload = JSON.parse(fs.readFileSync(output, 'utf8'));

    assert.equal(payload.schemaVersion, 1);
    assert.equal(payload.generation.sourceSha256, PINNED_DIGEST);
    assert.deepEqual(payload.elections.map(election => election.year), [2016, 2021, 2026]);
    for (const election of payload.elections) {
        assert.equal(election.totalSeats, 395);
        assert.equal(election.electedCount, 395);
        assert.equal(election.turnoutPercentage, EXPECTED_TURNOUT[election.year]);
        assert.equal(typeof election.turnoutSourcePercentageText, 'string');
        assert.match(election.turnoutSourceQueryId, new RegExp(`^${election.year}:`));
        assert.equal(election.turnoutFactStatus, 'REPORTED_ELECTIONS_MA');
        assert.deepEqual(Object.fromEntries(election.ballots.map(ballot => [ballot.type, {
            seats: ballot.seats,
            votes: ballot.votes
        }])), EXPECTED_BALLOTS[election.year]);
        assert.equal(election.ballots.reduce((sum, ballot) => sum + ballot.seats, 0), 395);
    }

    assert.equal(payload.elected.length, 1185, 'all official elected observations must be retained');
    assertUnique(payload.elected, row => row.electedId, 'elected observation');
    assertUnique(payload.partyObservations, row => `${row.year}:${row.partyId}`, 'party observation');
    assertUnique(payload.nationalPartyResults, row => `${row.year}:${row.partyId}`, 'national result');
    assertUnique(payload.regionalLocalSeats, row => `${row.year}:${row.regionId}`, 'regional vector');

    const pam = payload.partyObservations.filter(row => row.abbreviation === 'PAM');
    assert.deepEqual(pam.map(row => row.year), [2016, 2021, 2026]);
    assert.equal(new Set(pam.map(row => row.comparisonKey)).size, 1,
        'an unchanged exact source label may have one analysis comparison key');
    const psu = payload.partyObservations.filter(row => row.abbreviation === 'PSU');
    assert.equal(psu.length, 2);
    assert.equal(new Set(psu.map(row => row.comparisonKey)).size, 2,
        'the 2016 alliance and 2021 party must remain separate source identities');

    for (const year of [2016, 2021, 2026]) {
        const vectors = payload.regionalLocalSeats.filter(row => row.year === year);
        assert.equal(vectors.length, 12);
        assert.equal(vectors.flatMap(row => row.parties)
            .reduce((sum, row) => sum + row.seats, 0), 305,
        'regional vectors must contain local seats only');
    }

    const pamResults = payload.nationalPartyResults.filter(row => row.comparisonKey === pam[0].comparisonKey);
    assert.deepEqual(pamResults.map(row => row.year), [2016, 2021, 2026],
        'stable keys must support all-election trajectories');
    assert.ok(pamResults.every(row => Array.isArray(row.ballotVotes)
        && row.ballotVotes.every(ballot => ['local', 'national', 'regional'].includes(ballot.type)
            && Number.isInteger(ballot.seats) && Number.isInteger(ballot.votes))),
    'seats and votes must remain separated by ballot type');
    assert.ok(pamResults.every(row => !Object.hasOwn(row, 'votes')),
        'the payload must not imply that ballot vote totals are unique voters');

    assert.equal(payload.nationalDemographics.length, 26);
    assert.ok(payload.nationalDemographics.every(row => row.valueStatus === 'reported'
        && row.factStatus === 'REPORTED_ELECTIONS_MA'
        && typeof row.sourcePercentageText === 'string' && row.sourcePercentageText.length > 0
        && typeof row.sourceQueryId === 'string'
        && row.percentage >= 0 && row.percentage <= 100
        && !Object.hasOwn(row, 'count')),
    'public demographics must contain only literal reported percentages and no inferred counts');

    const aggregateFacts = [
        ...payload.elections.flatMap(row => row.ballots),
        ...payload.nationalPartyResults,
        ...payload.regionalLocalSeats
    ];
    assert.ok(aggregateFacts.every(row => row.factStatus === 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA'),
        'every public aggregate must carry explicit Fhemni-analysis provenance');

    assert.ok(payload.partyObservations.every(row =>
        row.abbreviationStatus === 'ARCHIVE_FIELD_NOT_VERIFIED_IDENTITY'
        && !row.continuityBasis.includes('official')),
    'abbreviations must be identified as archive fields rather than official identity keys');
    const abbreviationBearingRows = [
        ...payload.nationalPartyResults,
        ...payload.elected,
        ...payload.regionalLocalSeats.flatMap(row => row.parties),
        ...payload.repeatedNames.flatMap(row => row.occurrences)
    ];
    assert.ok(abbreviationBearingRows.every(row =>
        row.abbreviationStatus === 'ARCHIVE_FIELD_NOT_VERIFIED_IDENTITY'));
    assert.ok(payload.elected.every(row =>
        row.normalizedNameStatus === 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA'));
    assert.doesNotMatch(JSON.stringify(payload.partyObservations), /stable_official_abbreviation/);
});

test('repeated-name evidence contains only cross-election names unique within each election', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'fhemni-history-names-'));
    const output = path.join(directory, 'history.json');
    const generated = runGenerator(output);
    assert.equal(generated.status, 0, generated.stderr || generated.stdout);
    const payload = JSON.parse(fs.readFileSync(output, 'utf8'));
    assert.ok(payload.repeatedNames.length > 0);
    assertUnique(payload.repeatedNames, row => row.normalizedName, 'repeated-name group');

    for (const group of payload.repeatedNames) {
        assert.equal(group.evidenceStatus, 'name_match_only');
        assert.ok(new Set(group.occurrences.map(row => row.year)).size >= 2);
        for (const occurrence of group.occurrences) {
            const sameNameInElection = payload.elected.filter(row => row.year === occurrence.year
                && row.normalizedName === group.normalizedName);
            assert.equal(sameNameInElection.length, 1,
                `${group.normalizedName} must be unique within ${occurrence.year}`);
        }
    }
});

test('the manifest and public payload cite elections.ma only and regenerate byte-identically', () => {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    const source = JSON.parse(fs.readFileSync(pinnedSourcePath, 'utf8'));
    assert.equal(manifest.sha256, PINNED_DIGEST);
    assert.equal(manifest.archive, 'morocco-legislative-results.json');
    assert.deepEqual(manifest.sourceUrls, source.elections
        .toSorted((left, right) => left.election_date.localeCompare(right.election_date))
        .map(election => election.source_url),
    'manifest URLs must exactly match election URLs in year order');
    assert.equal(new Set(manifest.sourceUrls).size, manifest.sourceUrls.length,
        'manifest source URLs must be unique');
    for (const sourceUrl of manifest.sourceUrls) {
        assert.equal(new URL(sourceUrl).hostname, 'www.elections.ma');
    }

    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'fhemni-history-determinism-'));
    const firstOutput = path.join(directory, 'first.json');
    const secondOutput = path.join(directory, 'second.json');
    const first = runGenerator(firstOutput);
    const second = runGenerator(secondOutput);
    assert.equal(first.status, 0, first.stderr || first.stdout);
    assert.equal(second.status, 0, second.stderr || second.stdout);
    assert.equal(fs.readFileSync(firstOutput, 'utf8'), fs.readFileSync(secondOutput, 'utf8'));
    assert.equal(fs.readFileSync(firstOutput, 'utf8'), fs.readFileSync(committedOutputPath, 'utf8'));

    const payload = JSON.parse(fs.readFileSync(firstOutput, 'utf8'));
    const urls = collectHttpUrls(payload);
    assert.ok(urls.length >= 3);
    for (const url of urls) {
        assert.equal(new URL(url).hostname, 'www.elections.ma');
    }
    assert.ok(fs.statSync(firstOutput).size < fs.statSync(pinnedSourcePath).size,
        'the browser payload must be smaller than the source archive');
});

test('generation rejects reordered, duplicated, or substituted manifest election URLs', () => {
    const original = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'fhemni-history-manifest-'));
    const output = path.join(directory, 'history.json');
    const mutations = [
        manifest => manifest.sourceUrls.reverse(),
        manifest => { manifest.sourceUrls[1] = manifest.sourceUrls[0]; },
        manifest => { manifest.sourceUrls[1] = 'https://www.elections.ma/elections/legislatives/resultats.aspx'; }
    ];
    for (const [index, mutate] of mutations.entries()) {
        const manifest = structuredClone(original);
        mutate(manifest);
        const changedManifestPath = path.join(directory, `manifest-${index}.json`);
        fs.writeFileSync(changedManifestPath, JSON.stringify(manifest));
        const generated = runGenerator(output, pinnedSourcePath, changedManifestPath);
        assert.notEqual(generated.status, 0);
        assert.match(generated.stderr || generated.stdout, /manifest.*source.*url|source.*url.*manifest/i);
    }
});
