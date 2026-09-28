const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const insights = require('../../main/resources/static/js/historical-election-insights.js');
const quotient = require('../../main/resources/static/js/historical-electoral-quotient.js');
const modulePath = path.join(__dirname, '../../main/resources/static/js/historical-data-integrity.js');
const bytes = () => fs.readFileSync(path.join(__dirname, '../../main/resources/static/data/elections/history.json'));
const encode = value => Buffer.from(JSON.stringify(value) + '\n');
function loader() {
    assert.ok(fs.existsSync(modulePath), 'the page needs a served-artifact integrity gate');
    return require(modulePath).parseVerifiedHistoricalData;
}

test('CI verifies the actual shipped history artifact, independently of its declared source archive hash', async () => {
    const parse = loader();
    const result = await parse(bytes());
    assert.deepEqual(result, JSON.parse(bytes()));
    assert.equal(insights.auditHistoricalPayload(result).available, true);
});

test('name mutation that passes reconciliations and invents a 96th movement is rejected before display', async () => {
    const parse = loader();
    const changed = JSON.parse(bytes());
    const row = changed.candidateRecords.find(row => row.year === 2026 && row.nameAr === 'فؤاد المودني');
    row.nameAr = 'حامدي وايسي';
    row.normalizedName = 'حامدي وايسي';
    assert.equal(insights.auditHistoricalPayload(changed).available, true);
    assert.equal(insights.deriveRepeatedNamePartyMovements(changed, 2021, 2026, { scope: 'candidates' }).totalMovements, 96);
    await assert.rejects(parse(encode(changed)), /historical-data-integrity/);
});

test('balanced 20,000-vote regional swap is rejected even when every arithmetic reconciliation passes', async () => {
    const parse = loader();
    const changed = JSON.parse(bytes());
    for (const [id, delta] of [['901', 20000], ['902', -20000]]) {
        const contest = changed.regionalConstituencyResults.find(row => row.constituencyId === id);
        contest.parties.find(row => row.abbreviation === 'PAM').votes += delta;
        contest.parties.find(row => row.abbreviation === 'RNI').votes -= delta;
    }
    const simulation = quotient.derive2026QuotientOnlyCounterfactual(changed);
    assert.equal(simulation.available, true);
    assert.equal(simulation.partyDeltas.find(row => row.abbreviation === 'PAM').simulatedTotalSeats, 106);
    await assert.rejects(parse(encode(changed)), /historical-data-integrity/);
});

test('missing cryptography, truncated bytes, and a false declared digest cannot bypass verification', async () => {
    const parse = loader();
    await assert.rejects(parse(bytes(), null), /historical-data-integrity/);
    await assert.rejects(parse(bytes().subarray(0, 100)), /historical-data-integrity/);
    const changed = JSON.parse(bytes());
    changed.generation.sourceSha256 = '0'.repeat(64);
    await assert.rejects(parse(encode(changed)), /historical-data-integrity/);
});

test('ArrayBuffer input is verified and parsed from an owned snapshot across the async boundary', async () => {
    const parse = loader();
    const input = Uint8Array.from(bytes()).buffer;
    const pending = parse(input);
    new Uint8Array(input).fill(0);
    assert.deepEqual(await pending, JSON.parse(bytes()));
});

test('the page verifies response bytes before running reconciliation or assigning its payload', () => {
    const controller = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/historical-elections.js'), 'utf8');
    assert.match(controller, /const nextPayload = await root\.FhemniHistoricalDataIntegrity\.parseVerifiedHistoricalData\(await response\.arrayBuffer\(\)\);\s+const audit = insights\.auditHistoricalPayload\(nextPayload\);/);
    assert.doesNotMatch(controller, /response\.json\(\)/);
    const html = fs.readFileSync(path.join(__dirname, '../../main/resources/static/historical-elections.html'), 'utf8');
    assert.match(html, /<script src="\/js\/historical-data-integrity\.js[^>]+defer><\/script>/);
    assert.ok(html.indexOf('/js/historical-data-integrity.js') < html.indexOf('/js/historical-elections.js'));
});
