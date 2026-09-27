const test = require('node:test');
const assert = require('node:assert/strict');

const demographics = require('../../main/resources/static/data/elections/2026/urban-rural-regions.js');

test('the bundled HCP 2024 region snapshot is complete and internally consistent', () => {
    assert.equal(demographics.datasetVersion, '1.8.0');
    assert.equal(demographics.censusYear, 2024);
    assert.equal(demographics.regions.length, 12);
    assert.equal(new Set(demographics.regions.map(region => region.regionCode)).size, 12);
    assert.equal(new Set(demographics.regions.map(region => region.hcpCode)).size, 12);
    assert.ok(demographics.regions.every(region =>
        region.urbanPopulation + region.ruralPopulation === region.totalPopulation));
    assert.deepEqual(demographics.regions.reduce((totals, region) => ({
        total: totals.total + region.totalPopulation,
        urban: totals.urban + region.urbanPopulation,
        rural: totals.rural + region.ruralPopulation
    }), { total: 0, urban: 0, rural: 0 }), {
        total: 36828330,
        urban: 23110108,
        rural: 13718222
    });
});

test('the snapshot maps exactly to the 12 stable election region codes', () => {
    assert.deepEqual(demographics.regions.map(region => region.regionCode).sort(), [
        'beni-mellal-khenifra',
        'casablanca-settat',
        'dakhla-oued-ed-dahab',
        'draa-tafilalet',
        'fes-meknes',
        'guelmim-oued-noun',
        'laayoune-sakia-el-hamra',
        'marrakech-safi',
        'oriental',
        'rabat-sale-kenitra',
        'souss-massa',
        'tanger-tetouan-al-hoceima'
    ]);
});
