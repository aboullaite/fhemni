const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const engine = require('../../main/resources/static/js/historical-electoral-quotient.js');
const data = () => JSON.parse(fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/history.json`));

test('quotient-only scenario retains all 104 contests and the four left-alliance seats', () => {
    assert.equal(typeof engine.derive2026QuotientOnlyCounterfactual, 'function');
    const result = engine.derive2026QuotientOnlyCounterfactual(data());
    assert.equal(result.available, true, result.diagnostics.join(','));
    assert.equal(result.constituencies.length, 104);
    assert.equal(result.simulatedSeatTotal, 395);
    assert.equal(result.partyDeltas.reduce((sum, row) => sum + row.simulatedLocalSeats, 0), 305);
    assert.equal(result.partyDeltas.reduce((sum, row) => sum + row.simulatedRegionalListSeats, 0), 90);
    const alliance = result.partyDeltas.find(row => row.abbreviation === 'AG');
    assert.equal(alliance.simulatedLocalSeats, 2);
    assert.equal(alliance.simulatedRegionalListSeats, 2);
    assert.equal(alliance.simulatedTotalSeats, 4);
    const winners = result.constituencies.filter(c => c.parties.some(p => p.abbreviation === 'AG' && p.simulatedSeats));
    assert.deepEqual(winners.map(c => c.constituencyId).sort(), ['1', '62', '903', '906']);
    for (const c of result.constituencies) {
        assert.equal(c.eligibilityThreshold.percent, 0);
        assert.equal(c.electoralQuotient.numerator, c.totalVotes);
        assert.equal(c.electoralQuotient.denominator, c.allocatedSeats);
        assert.equal(c.parties.reduce((sum,p) => sum + p.simulatedSeats, 0), c.allocatedSeats);
    }
});

test('quotient-only refuses incomplete or inconsistent regional evidence instead of showing a partial total', () => {
    assert.equal(typeof engine.derive2026QuotientOnlyCounterfactual, 'function');
    for (const mutate of [
        p => { delete p.regionalConstituencyResults; },
        p => { p.regionalConstituencyResults.pop(); },
        p => { p.regionalConstituencyResults[0].parties[0].votes += 1; },
        p => { p.regionalConstituencyResults[0].parties[0].officialSeats += 1; },
        p => { p.nationalPartyResults.find(row => row.year === 2026).seats += 1; },
        p => { p.nationalPartyResults.find(row => row.year === 2026).ballotVotes[0].seats += 1; },
        p => { p.regionalConstituencyResults[0].sourceUrl = 'https://example.com/'; }
    ]) {
        const p = data(); mutate(p);
        const result = engine.derive2026QuotientOnlyCounterfactual(p);
        assert.equal(result.available, false);
        assert.deepEqual(result.partyDeltas, []);
    }
});
