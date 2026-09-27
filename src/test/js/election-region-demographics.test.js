const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const demographics = require('../../main/resources/static/data/elections/2026/urban-rural-constituencies.js');
const pinnedSource = require('../../../data/elections/2026/urban-rural-source-v1.8.0.json');
const electionFixturePath = path.join(__dirname, '../fixtures/elections/2026-constituencies.json');

test('the bundled HCP 2024 constituency snapshot is complete and internally consistent', () => {
    assert.equal(demographics.datasetVersion, '1.8.0');
    assert.equal(demographics.censusYear, 2024);
    assert.equal(demographics.localSeatTotal, 305);
    assert.equal(demographics.excludedRegionalSeatTotal, 90);
    assert.equal(demographics.constituencies.length, 92);
    assert.equal(new Set(demographics.constituencies.map(row => row.constituencyCode)).size, 92);
    assert.equal(demographics.constituencies.reduce((sum, row) => sum + row.allocatedSeats, 0), 305);
    assert.ok(demographics.constituencies.every(row => row.urbanShare >= 0 && row.urbanShare <= 100));
    assert.ok(demographics.constituencies.every(row => row.populationCoverage === 'exact'
        ? row.urbanPopulation + row.ruralPopulation === row.totalPopulation
        : row.populationCoverage === 'share-only' && row.urbanShare === 100));
});

test('the constituency snapshot carries both population and legal-boundary provenance', () => {
    assert.equal(demographics.sourceUrl, 'https://communes.pages.dev/data/v1/sources.json');
    assert.equal(pinnedSource.datasetVersion, demographics.datasetVersion);
    assert.equal(pinnedSource.sourceDatasetSha256,
        '7e9d3d402cc2fd0d9c4fc941c4f23c6a5e5ddcbefbba80b7540267f8e2728dca');
    assert.equal(demographics.decreeUrl, 'https://www.sgg.gov.ma/BO/bo_ar/2011/BO_5988_Ar.pdf');
    assert.match(demographics.revisedAt, /^\d{4}-\d{2}-\d{2}$/);
    assert.ok(demographics.constituencies.every(row => row.componentCodes.length > 0));
    assert.equal(demographics.constituencies.filter(row => row.populationCoverage === 'share-only').length, 2);
});

test('the demographic crosswalk exactly joins the real 2026 election constituency catalog', () => {
    assert.ok(fs.existsSync(electionFixturePath),
        'the test suite must carry the real 2026 constituency codes and allocations');
    const fixture = JSON.parse(fs.readFileSync(electionFixturePath, 'utf8'));
    assert.equal(fixture.localSeatTotal, 305);
    assert.equal(fixture.source.sourceUrl,
        'https://www.maroc.ma/fr/elections-legislatives-marocaines-2026');
    assert.match(fixture.source.sourceUpdatedAt, /^\d{4}-\d{2}-\d{2}T/);
    const electionConstituencies = fixture.constituencies;
    const demographicRows = demographics.constituencies
        .map(row => [row.constituencyCode, row.allocatedSeats])
        .sort((left, right) => left[0].localeCompare(right[0]));
    const electionRows = electionConstituencies
        .map(row => [row.code, row.allocatedSeats])
        .sort((left, right) => left[0].localeCompare(right[0]));
    assert.deepEqual(demographicRows, electionRows);
});
