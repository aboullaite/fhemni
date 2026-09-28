const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const insights = require('../../main/resources/static/js/historical-election-insights.js');
const payload = () => JSON.parse(fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/history.json`));

test('incoming candidates versus elected candidates includes zero winners and respects the election scope', () => {
    const p = payload();
    const result = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026, { scope: 'candidates', limit: 100 });
    for (const [party, incoming, elected] of [['PAM', 9, 8], ['MP', 12, 5], ['UC', 10, 2], ['PPS', 9, 0], ['FFD', 8, 0]]) {
        const row = result.gains.find(row => row.abbreviation === party);
        assert.deepEqual([row.count, row.laterElectedCount], [incoming, elected], party);
    }
    const older = insights.deriveRepeatedNamePartyMovements(p, 2016, 2021, { scope: 'candidates', limit: 100 });
    const rni = older.gains.find(row => row.abbreviation === 'RNI');
    assert.deepEqual([rni.count, rni.laterElectedCount], [19, 17]);
    const narrow = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026, { scope: 'elected' });
    const pam = narrow.gains.find(row => row.abbreviation === 'PAM');
    assert.deepEqual([pam.count, pam.laterElectedCount], [4, 4]);
});

test('candidate scope includes losing list heads and separates later winners from the four incumbent arrivals', () => {
    const data = payload();
    const broader = insights.deriveRepeatedNamePartyMovements(data, 2021, 2026, { scope: 'candidates', limit: 100 });
    assert.equal(broader.available, true);
    const pam = broader.gains.find(row => row.abbreviation === 'PAM');
    assert.equal(pam.count, 9);
    assert.equal(pam.laterElectedCount, 8);
    assert.equal(insights.deriveRepeatedNamePartyMovements(data, 2021, 2026)
        .gains.find(row => row.abbreviation === 'PAM').count, 4);
    const names = ['عبد الكريم امين', 'محمد امغار', 'يوسف امنزو', 'كريم الزيادي'];
    for (const name of names) {
        const result = insights.deriveRepeatedNameGroups(data, 2021, 2026,
            { scope: 'candidates', query: name, differentPartyLabelsOnly: true });
        assert.equal(result.totalRows, 1, name);
        assert.equal(result.rows[0].occurrences.find(row => row.year === 2021).elected, false);
        assert.equal(result.rows[0].occurrences.find(row => row.year === 2026).elected, true);
    }
    const loser = insights.deriveRepeatedNameGroups(data, 2021, 2026,
        { scope: 'candidates', query: 'عبد الحي حرطون' });
    assert.equal(loser.rows[0].occurrences.find(row => row.year === 2026).elected, false);
});

test('candidate data fails closed on missing heads, false outcomes, unknown parties, or missing evidence', () => {
    for (const mutate of [
        data => { delete data.candidateRecords; },
        data => { data.candidateRecords.splice(0, 1); },
        data => { data.candidateRecords.find(row => row.listHead && !row.elected).elected = true; },
        data => { data.candidateRecords[0].comparisonKey = 'invented'; },
        data => { data.candidateRecords[0].sourceQueryId = ''; }
    ]) {
        const data = payload();
        mutate(data);
        const result = insights.deriveRepeatedNamePartyMovements(data, 2021, 2026, { scope: 'candidates' });
        assert.equal(result.available, false);
        assert.deepEqual(result.gains, []);
    }
});

test('candidate explorer and chart use the same pair, comparable affiliations, and ten-row pages', () => {
    const data = payload();
    const chart = insights.deriveRepeatedNamePartyMovements(data, 2021, 2026, { scope: 'candidates', limit: 100 });
    const first = insights.deriveRepeatedNameGroups(data, 2021, 2026,
        { scope: 'candidates', differentPartyLabelsOnly: true });
    assert.equal(first.available, true);
    assert.equal(first.totalRows, chart.totalMovements);
    assert.equal(first.rows.length, 10);
    assert.ok(first.rows.every(group => group.occurrences.length === 2
        && group.occurrences.every(row => [2021, 2026].includes(row.year))));
    const alliance = insights.deriveRepeatedNameGroups(data, 2021, 2026,
        { scope: 'candidates', query: 'نبيلة منيب', differentPartyLabelsOnly: true });
    assert.equal(alliance.totalRows, 0);
});

test('every party in every supported pair reconciles to the full filtered explorer, not a PAM special case', () => {
    const p = payload();
    for (const pair of [[2016, 2021], [2016, 2026], [2021, 2026]]) {
        for (const scope of ['candidates', 'elected']) {
            const chart = insights.deriveRepeatedNamePartyMovements(p, ...pair, { scope, limit: 100 });
            const rows = [];
            let page = 1, result;
            do {
                result = insights.deriveRepeatedNameGroups(p, ...pair, { scope, differentPartyLabelsOnly: true, page });
                assert.equal(result.available, true);
                rows.push(...result.rows);
                page += 1;
            } while (page <= result.pageCount);
            assert.equal(rows.length, chart.totalMovements);
            assert.equal(chart.gains.reduce((s,r) => s + r.count, 0), chart.totalMovements);
            assert.equal(chart.losses.reduce((s,r) => s + r.count, 0), chart.totalMovements);
            for (const [direction,year] of [['gains',pair[1]], ['losses',pair[0]]]) {
                for (const party of chart[direction]) {
                    const matching = rows.filter(g => g.occurrences.some(r => r.year === year && r.canonicalComparisonKey === party.comparisonKey));
                    assert.equal(party.count, matching.length);
                    assert.equal(party.laterElectedCount, matching.filter(g => g.occurrences.find(r => r.year === pair[1]).elected).length);
                }
            }
        }
    }
});
