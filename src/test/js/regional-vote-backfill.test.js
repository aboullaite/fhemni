const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const archive = fs.readFileSync(path.join(__dirname, '../../main/resources/static/data/elections/history.json'));

test('backfill selects the specific list and reconciles all regional ballots', async () => {
    const { regionalLists } = await import('../../../scripts/elections/backfill-regional-votes.mjs');
    const result = regionalLists(archive);
    assert.equal(result.rows.reduce((sum, row) => sum + row.votes, 0), 4838149);
    assert.equal(result.rows.reduce((sum, row) => sum + row.seats, 0), 90);
    assert.equal(new Set(result.rows.map(row => row.regionCode)).size, 12);
    assert.deepEqual(result.rows.find(row => row.regionCode === 'tanger-tetouan-al-hoceima' && row.partyCode === 'PAM'), {
        regionCode: 'tanger-tetouan-al-hoceima', partyCode: 'PAM', votes: 86557, seats: 1,
        sourceQueryId: '2026:r1_p0_c901'
    });
    assert.equal(result.national.find(row => row.partyCode === 'PAM').votes, 1065371);
    assert.equal(result.national.find(row => row.partyCode === 'FGD').votes, 136982);
});

test('backfill rejects changed data even with an unchanged declared archive digest', async () => {
    const { regionalLists } = await import('../../../scripts/elections/backfill-regional-votes.mjs');
    const changed = JSON.parse(archive);
    changed.regionalConstituencyResults[0].parties[0].votes += 1;
    assert.throws(() => regionalLists(Buffer.from(JSON.stringify(changed))), /checksum/);
});
