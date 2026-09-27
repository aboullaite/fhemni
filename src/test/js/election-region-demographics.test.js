const test = require('node:test');
const assert = require('node:assert/strict');

const demographics = require('../../main/resources/static/data/elections/2026/urban-rural-constituencies.js');

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
    assert.equal(demographics.decreeUrl, 'https://www.sgg.gov.ma/BO/bo_ar/2011/BO_5988_Ar.pdf');
    assert.match(demographics.revisedAt, /^\d{4}-\d{2}-\d{2}$/);
    assert.ok(demographics.constituencies.every(row => row.componentCodes.length > 0));
    assert.equal(demographics.constituencies.filter(row => row.populationCoverage === 'share-only').length, 2);
});
