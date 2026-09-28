const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const {
    auditHistoricalPayload,
    listValidYearPairs,
    resolveYearPair,
    deriveNationalOverview,
    derivePartyDeltas,
    derivePartyTrajectory,
    deriveRegionComparison,
    deriveTurnoutTrend,
    deriveDemographicTrends,
    deriveRepeatedNameGroups
} = require('../../main/resources/static/js/historical-election-insights.js');

const payloadPath = path.join(__dirname,
    '../../main/resources/static/data/elections/history.json');

function payload() {
    return JSON.parse(fs.readFileSync(payloadPath, 'utf8'));
}

test('validation accepts the generated payload and fails closed when a complete party roster is broken', () => {
    const valid = payload();
    assert.deepEqual(auditHistoricalPayload(valid), {
        available: true,
        diagnostics: [],
        years: [2016, 2021, 2026]
    });

    const broken = structuredClone(valid);
    broken.nationalPartyResults.splice(broken.nationalPartyResults.findIndex(row =>
        row.year === 2021 && row.comparisonKey === 'exact-source-label:party_040005b84836'), 1);

    const audit = auditHistoricalPayload(broken);
    assert.equal(audit.available, false);
    assert.ok(audit.diagnostics.includes('national-party-roster'));
    assert.deepEqual(derivePartyDeltas(broken, 2016, 2021), {
        available: false,
        diagnostics: audit.diagnostics,
        rows: [],
        totalRows: 0
    });
});

test('validation fails closed when pinned archive provenance is missing or altered', () => {
    const missingGeneration = payload();
    delete missingGeneration.generation;
    const missingAudit = auditHistoricalPayload(missingGeneration);
    assert.equal(missingAudit.available, false);
    assert.ok(missingAudit.diagnostics.includes('source-provenance'));

    const missingDigest = payload();
    delete missingDigest.generation.sourceSha256;
    const digestAudit = auditHistoricalPayload(missingDigest);
    assert.equal(digestAudit.available, false);
    assert.ok(digestAudit.diagnostics.includes('source-provenance'));

    const changedUrl = payload();
    changedUrl.generation.sourceUrls[0] = 'https://example.com/not-elections-ma';
    const urlAudit = auditHistoricalPayload(changedUrl);
    assert.equal(urlAudit.available, false);
    assert.ok(urlAudit.diagnostics.includes('source-provenance'));
});

test('year pairs are chronological and unsupported selections visibly fall back to the newest pair', () => {
    const valid = payload();
    const pairs = listValidYearPairs(valid);
    assert.equal(pairs.available, true);
    assert.deepEqual(pairs.pairs, [
        { fromYear: 2016, toYear: 2021 },
        { fromYear: 2016, toYear: 2026 },
        { fromYear: 2021, toYear: 2026 }
    ]);
    assert.deepEqual(pairs.defaultPair, { fromYear: 2021, toYear: 2026 });
    assert.deepEqual(resolveYearPair(valid, '2016', '2026').pair,
        { fromYear: 2016, toYear: 2026 });
    assert.equal(resolveYearPair(valid, '2016', '2026').usedFallback, false);
    assert.deepEqual(resolveYearPair(valid, 2026, 2016).pair,
        { fromYear: 2021, toYear: 2026 });
    assert.equal(resolveYearPair(valid, 2026, 2016).usedFallback, true);
});

test('national overview keeps ballot measures separate and never presents their sum as voters', () => {
    const overview = deriveNationalOverview(payload(), 2016, 2026);
    assert.equal(overview.available, true);
    assert.deepEqual(overview.earlier, {
        year: 2016,
        turnoutPercentage: 42.29,
        turnoutSourcePercentageText: '42.29%',
        turnoutSourceQueryId: '2016:r0_p0_c0',
        turnoutFactStatus: 'REPORTED_ELECTIONS_MA',
        totalSeats: 395,
        electedCount: 395,
        ballots: [
            { type: 'local', seats: 305, votes: 5790552,
                factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA' },
            { type: 'national', seats: 90, votes: 5806004,
                factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA' }
        ]
    });
    assert.deepEqual(overview.later.ballots, [
        { type: 'local', seats: 305, votes: 4900377,
            factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA' },
        { type: 'regional', seats: 90, votes: 4838149,
            factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA' }
    ]);
    assert.equal(overview.turnoutDelta, -4.21);
    assert.equal(Object.hasOwn(overview.earlier, 'votes'), false);
});

test('party deltas expose total, local, and list seats and support deterministic sorting, search, and show-all', () => {
    const valid = payload();
    const result = derivePartyDeltas(valid, 2016, 2026, {
        measure: 'local', sort: 'delta-desc', query: 'RNI'
    });
    assert.equal(result.available, true);
    assert.equal(result.totalRows, 1);
    assert.deepEqual(result.rows[0], {
        comparisonKey: 'exact-source-label:party_040005b84836',
        nameAr: 'حزب التجمع الوطني للأحرار',
        abbreviation: 'RNI',
        abbreviationStatus: 'ARCHIVE_FIELD_NOT_VERIFIED_IDENTITY',
        continuityBasis: 'same_exact_source_label',
        comparisonFactStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA',
        earlier: {
            status: 'present_in_complete_roster', totalSeats: 37, localSeats: 28, listSeats: 9,
            listBallotType: 'national', localVotes: 558875, listVotes: 544118,
            factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA'
        },
        later: {
            status: 'present_in_complete_roster', totalSeats: 66, localSeats: 57, listSeats: 9,
            listBallotType: 'regional', localVotes: 718630, listVotes: 640468,
            factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA'
        },
        delta: { totalSeats: 29, localSeats: 29, listSeats: 0 },
        selectedMeasure: 'local', selectedDelta: 29
    });

    const collapsed = derivePartyDeltas(valid, 2016, 2026);
    const expanded = derivePartyDeltas(valid, 2016, 2026, { showAll: true });
    assert.equal(collapsed.rows.length, 10);
    assert.equal(expanded.rows.length, expanded.totalRows);
    assert.deepEqual(collapsed.rows.map(row => row.comparisonKey),
        derivePartyDeltas(valid, 2016, 2026).rows.map(row => row.comparisonKey));
});

test('list-seat deltas are unavailable when endpoint list ballot types differ', () => {
    const valid = payload();
    const incompatible = derivePartyDeltas(valid, 2016, 2021, { measure: 'list' });
    assert.equal(incompatible.available, false);
    assert.ok(incompatible.diagnostics.includes('list-ballot-type'));
    assert.equal(derivePartyDeltas(valid, 2016, 2021, { measure: 'total' }).available, true);
    assert.equal(derivePartyDeltas(valid, 2016, 2021, { measure: 'local' }).available, true);
    assert.equal(derivePartyDeltas(valid, 2021, 2026, { measure: 'list' }).available, true);
});

test('source-only alliance observations remain separate and absent observations become zero only after roster validation', () => {
    const result = derivePartyDeltas(payload(), 2016, 2021, {
        query: 'PSU', showAll: true, sort: 'name'
    });
    assert.equal(result.available, true);
    assert.equal(result.totalRows, 2);
    assert.deepEqual(result.rows.map(row => row.comparisonKey), [
        'source:2021:party_ab8aeb368dd5',
        'source:2016:party_c02711490e0c'
    ]);
    assert.deepEqual(result.rows.map(row => [row.earlier.status, row.later.status]), [
        ['established_zero_from_complete_roster', 'present_in_complete_roster'],
        ['present_in_complete_roster', 'established_zero_from_complete_roster']
    ]);
    assert.deepEqual(result.rows.map(row => row.delta.totalSeats), [1, -2]);
});

test('party trajectory spans every election without merging a similarly labelled alliance', () => {
    const rni = derivePartyTrajectory(payload(), 'exact-source-label:party_040005b84836');
    assert.equal(rni.available, true);
    assert.deepEqual(rni.points.map(point => [point.year, point.totalSeats, point.localSeats, point.listSeats]), [
        [2016, 37, 28, 9],
        [2021, 102, 86, 16],
        [2026, 66, 57, 9]
    ]);

    const alliance = derivePartyTrajectory(payload(), 'source:2016:party_c02711490e0c');
    assert.deepEqual(alliance.points.map(point => [point.year, point.status, point.totalSeats]), [
        [2016, 'present_in_complete_roster', 2],
        [2021, 'established_zero_from_complete_roster', 0],
        [2026, 'established_zero_from_complete_roster', 0]
    ]);
});

test('region comparison is local-only and covers all three elections', () => {
    const region = deriveRegionComparison(payload(), '1');
    assert.equal(region.available, true);
    assert.equal(region.ballotType, 'local');
    assert.equal(region.regionNameAr, 'طنجة- تطوان -الحسيمة');
    assert.deepEqual(region.years, [2016, 2021, 2026]);
    const pam = region.rows.find(row => row.comparisonKey === 'exact-source-label:party_b42379b4d45a');
    assert.deepEqual(pam.points, [
        { year: 2016, localSeats: 8, factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA' },
        { year: 2021, localSeats: 7, factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA' },
        { year: 2026, localSeats: 8, factStatus: 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA' }
    ]);
    assert.equal(region.rows.some(row => Object.hasOwn(row, 'listSeats')), false);
});

test('turnout and demographic trends are chronological and retain evidence statuses', () => {
    const turnout = deriveTurnoutTrend(payload());
    assert.deepEqual(turnout.points, [
        { year: 2016, turnoutPercentage: 42.29, deltaFromPrevious: null,
            sourcePercentageText: '42.29%', sourceQueryId: '2016:r0_p0_c0',
            factStatus: 'REPORTED_ELECTIONS_MA' },
        { year: 2021, turnoutPercentage: 50.86, deltaFromPrevious: 8.57,
            sourcePercentageText: '50.86%', sourceQueryId: '2021:r0_p0_c0',
            factStatus: 'REPORTED_ELECTIONS_MA' },
        { year: 2026, turnoutPercentage: 38.08, deltaFromPrevious: -12.78,
            sourcePercentageText: '38.08%', sourceQueryId: '2026:r0_p0_c0',
            factStatus: 'REPORTED_ELECTIONS_MA' }
    ]);

    const sparse = payload();
    sparse.nationalDemographics = sparse.nationalDemographics
        .filter(row => row.valueStatus === 'reported')
        .map(row => {
            const observation = { ...row };
            delete observation.count;
            return observation;
        });
    const demographics = deriveDemographicTrends(sparse, { dimension: 'education' });
    assert.equal(demographics.available, true);
    assert.deepEqual(demographics.years, [2016, 2021, 2026]);
    assert.ok(demographics.rows.some(row => row.points.some(point =>
        point.valueStatus === 'reported')));
    for (const row of demographics.rows) {
        for (const point of row.points) {
            assert.equal(typeof point.valueStatus, 'string');
            assert.equal(point.factStatus, 'REPORTED_ELECTIONS_MA');
            assert.match(point.sourceQueryId, /^20(16|21|26):/);
            assert.equal(Object.hasOwn(point, 'count'), false);
        }
    }
});

test('repeated-name explorer labels evidence, filters different party labels, and paginates by stable pages of 10', () => {
    const valid = payload();
    const first = deriveRepeatedNameGroups(valid, { page: 1 });
    const second = deriveRepeatedNameGroups(valid, { page: 2 });
    assert.equal(first.available, true);
    assert.equal(first.pageSize, 10);
    assert.equal(first.rows.length, 10);
    assert.equal(second.rows.length, 10);
    assert.equal(first.rows.at(-1).normalizedName < second.rows[0].normalizedName, true);
    assert.ok(first.rows.every(row => row.evidenceStatus === 'name_match_only'));

    const changed = deriveRepeatedNameGroups(valid, {
        differentPartyLabelsOnly: true, page: 1
    });
    assert.ok(changed.totalRows > 10);
    assert.ok(changed.rows.every(row => new Set(row.occurrences.map(item =>
        item.comparisonKey)).size > 1));
    assert.ok(changed.rows.every(row => row.evidenceStatus === 'name_match_only'));

    const beyondEnd = deriveRepeatedNameGroups(valid, { page: 999 });
    assert.equal(beyondEnd.page, beyondEnd.pageCount);
    assert.ok(beyondEnd.rows.length > 0 && beyondEnd.rows.length <= 10);
});

test('tampered evidence labels and regional totals make dependent analysis unavailable', () => {
    const invalidNames = payload();
    invalidNames.repeatedNames[0].evidenceStatus = 'verified_identity';
    assert.equal(deriveRepeatedNameGroups(invalidNames).available, false);

    const invalidRegions = payload();
    invalidRegions.regionalLocalSeats[0].parties[0].seats += 1;
    const region = deriveRegionComparison(invalidRegions, '1');
    assert.equal(region.available, false);
    assert.ok(region.diagnostics.includes('regional-local-seat-total'));

    const invalidProvenance = payload();
    invalidProvenance.elections[0].ballots[0].factStatus = 'UNVERIFIED';
    assert.equal(auditHistoricalPayload(invalidProvenance).available, false);

    const invalidLabels = payload();
    invalidLabels.nationalPartyResults[0].abbreviationStatus = 'IDENTITY_VERIFIED';
    invalidLabels.elected[0].normalizedNameStatus = 'VERIFIED_IDENTITY';
    assert.equal(auditHistoricalPayload(invalidLabels).available, false);
});

test('audit reconciles elected party totals and exact repeated-name projections', () => {
    const changedParty = payload();
    const elected = changedParty.elected.find(row => row.year === 2021
        && row.comparisonKey === 'exact-source-label:party_040005b84836');
    const replacement = changedParty.partyObservations.find(row => row.year === 2021
        && row.comparisonKey === 'exact-source-label:party_b42379b4d45a');
    elected.partyId = replacement.partyId;
    elected.abbreviation = replacement.abbreviation;
    elected.comparisonKey = replacement.comparisonKey;
    const partyAudit = auditHistoricalPayload(changedParty);
    assert.equal(partyAudit.available, false);
    assert.ok(partyAudit.diagnostics.includes('elected-party-seat-total'));

    const changedRepeated = payload();
    changedRepeated.repeatedNames[0].factStatus = 'UNVERIFIED';
    changedRepeated.repeatedNames[1].occurrences[0].constituencyNameAr += ' changed';
    const repeatedAudit = auditHistoricalPayload(changedRepeated);
    assert.equal(repeatedAudit.available, false);
    assert.ok(repeatedAudit.diagnostics.includes('repeated-name-roster'));
});

test('audit binds elected party labels to the year and comparison-key observation', () => {
    const changed = payload();
    const occurrence = changed.repeatedNames[0].occurrences[0];
    const elected = changed.elected.find(row => row.electedId === occurrence.electedId);
    elected.partyId = 'party_fake';
    elected.abbreviation = 'FAKE';
    elected.abbreviationStatus = 'FAKE_STATUS';
    occurrence.partyId = elected.partyId;
    occurrence.abbreviation = elected.abbreviation;
    occurrence.abbreviationStatus = elected.abbreviationStatus;

    const audit = auditHistoricalPayload(changed);
    assert.equal(audit.available, false);
    assert.ok(audit.diagnostics.includes('elected-party-observation'));
});

test('audit rejects balanced region-party swaps that disagree with the local elected roster', () => {
    const changed = payload();
    const region1 = changed.regionalLocalSeats.find(row => row.year === 2021 && row.regionId === '1');
    const region2 = changed.regionalLocalSeats.find(row => row.year === 2021 && row.regionId === '2');
    const rni = 'exact-source-label:party_040005b84836';
    const pi = 'exact-source-label:party_a77e5f9ed798';
    region1.parties.find(row => row.comparisonKey === rni).seats -= 1;
    region2.parties.find(row => row.comparisonKey === rni).seats += 1;
    region1.parties.find(row => row.comparisonKey === pi).seats += 1;
    region2.parties.find(row => row.comparisonKey === pi).seats -= 1;

    const audit = auditHistoricalPayload(changed);
    assert.equal(audit.available, false);
    assert.ok(audit.diagnostics.includes('regional-elected-seat-total'));
});

test('audit rejects inferred demographic counts in the reported-percentage schema', () => {
    const changed = payload();
    changed.nationalDemographics[0].count = 314;
    const audit = auditHistoricalPayload(changed);
    assert.equal(audit.available, false);
    assert.ok(audit.diagnostics.includes('demographic-roster'));
});

test('overview and party deltas return the newest valid pair with visible fallback state', () => {
    const valid = payload();
    const overview = deriveNationalOverview(valid, 2026, 2016);
    assert.equal(overview.available, true);
    assert.equal(overview.usedFallback, true);
    assert.deepEqual(overview.pair, { fromYear: 2021, toYear: 2026 });
    assert.deepEqual([overview.earlier.year, overview.later.year], [2021, 2026]);

    const deltas = derivePartyDeltas(valid, 1999, 2026, { measure: 'local' });
    assert.equal(deltas.available, true);
    assert.equal(deltas.usedFallback, true);
    assert.deepEqual(deltas.pair, { fromYear: 2021, toYear: 2026 });
    assert.ok(deltas.rows.length > 0);
});

test('audit enforces year-specific capability semantics and exact-label names', () => {
    const changedCapability = payload();
    changedCapability.elections[0].capabilities.listBallotAssignableToRegions = true;
    const capabilityAudit = auditHistoricalPayload(changedCapability);
    assert.equal(capabilityAudit.available, false);
    assert.ok(capabilityAudit.diagnostics.includes('election-capabilities'));

    const changedName = payload();
    const observation = changedName.partyObservations.find(row => row.year === 2021
        && row.comparisonKey === 'exact-source-label:party_040005b84836');
    const result = changedName.nationalPartyResults.find(row => row.year === 2021
        && row.comparisonKey === observation.comparisonKey);
    observation.nameAr += ' changed';
    result.nameAr = observation.nameAr;
    const nameAudit = auditHistoricalPayload(changedName);
    assert.equal(nameAudit.available, false);
    assert.ok(nameAudit.diagnostics.includes('party-continuity'));
});

test('malformed nested collections fail closed instead of throwing', () => {
    const malformed = payload();
    malformed.regionalLocalSeats[0].parties = null;
    assert.doesNotThrow(() => auditHistoricalPayload(malformed));
    assert.equal(auditHistoricalPayload(malformed).available, false);
    assert.doesNotThrow(() => deriveRegionComparison(malformed, '1'));
    assert.equal(deriveRegionComparison(malformed, '1').available, false);

    for (const collection of ['elections', 'partyObservations', 'nationalPartyResults',
        'regionalLocalSeats', 'nationalDemographics', 'elected', 'repeatedNames']) {
        const withNullRow = payload();
        withNullRow[collection][0] = null;
        assert.doesNotThrow(() => auditHistoricalPayload(withNullRow), collection);
        assert.equal(auditHistoricalPayload(withNullRow).available, false, collection);
    }
});
