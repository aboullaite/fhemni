const test = require('node:test');
const assert = require('node:assert/strict');
const page = require('../../main/resources/static/js/historical-elections.js');

test('paired movement bars use one count scale and distinguish incoming from outgoing election outcomes', () => {
    assert.equal(typeof page.movementOutcomeSeries, 'function');
    const values = page.movementOutcomeSeries({ count: 9, laterElectedCount: 8 }, 12, 'gain');
    assert.deepEqual(values.map(row => [row.count, row.percent, row.labelKey]),
        [[9, 75, 'movementIncoming'], [8, 66.67, 'movementElected']]);
    const outgoing = page.movementOutcomeSeries({ count: 9, laterElectedCount: 8 }, 12, 'loss');
    assert.deepEqual(outgoing.map(row => row.labelKey), ['movementOutgoing', 'movementElectedElsewhere']);
});

test('paired movement bars show genuine zero winners but reject unknown or impossible outcomes', () => {
    assert.equal(typeof page.movementOutcomeSeries, 'function');
    assert.deepEqual(page.movementOutcomeSeries({ count: 9, laterElectedCount: 0 }, 12, 'gain')
        .map(row => [row.count, row.percent]), [[9, 75], [0, 0]]);
    for (const row of [{count:9}, {count:9,laterElectedCount:null}, {count:9,laterElectedCount:10},
        {count:9,laterElectedCount:-1}, {count:9,laterElectedCount:1.5}]) {
        assert.deepEqual(page.movementOutcomeSeries(row, 12, 'gain'), []);
    }
});
