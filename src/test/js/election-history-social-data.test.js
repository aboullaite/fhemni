const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');

const insights = require('../../main/resources/static/js/historical-election-insights.js');
const quotient = require('../../main/resources/static/js/historical-electoral-quotient.js');
const { buildCampaignData } = require('../../../scripts/social/election-history-campaign-data.js');

const sourcePath = path.join(__dirname,
    '../../main/resources/static/data/elections/history.json');
const sourceSha256 = crypto.createHash('sha256').update(fs.readFileSync(sourcePath)).digest('hex');

function payload() {
    return JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
}

function campaignData() {
    return buildCampaignData(payload(), { insights, quotient, sourceSha256 });
}

test('campaign projection fails closed when the historical audit fails', () => {
    const broken = payload();
    broken.nationalPartyResults.splice(broken.nationalPartyResults.findIndex(row =>
        row.year === 2021 && row.comparisonKey === 'exact-source-label:party_040005b84836'), 1);
    const diagnostics = insights.auditHistoricalPayload(broken).diagnostics;
    assert.ok(diagnostics.includes('national-party-roster'));
    assert.throws(() => buildCampaignData(broken, { insights, quotient, sourceSha256 }), error =>
        diagnostics.every(diagnostic => error.message.includes(diagnostic)));
});

test('campaign projection preserves exact-label party gains and losses for 2021 to 2026', () => {
    const data = campaignData();
    assert.deepEqual(data.overview, {
        years: [2016, 2021, 2026],
        electedRecordCount: 1185,
        electionCount: 3
    });
    assert.deepEqual(data.partyMovement.gains.slice(0, 2).map(row =>
        [row.abbreviation, row.earlierSeats, row.laterSeats, row.delta]), [
        ['PJD', 13, 54, 41],
        ['PAM', 87, 97, 10]
    ]);
    assert.deepEqual(data.partyMovement.losses.slice(0, 3).map(row =>
        [row.abbreviation, row.earlierSeats, row.laterSeats, row.delta]), [
        ['RNI', 102, 66, -36],
        ['PI', 81, 65, -16],
        ['USFP', 34, 26, -8]
    ]);
    assert.equal(data.partyMovement.measure, 'total');
    assert.ok([...data.partyMovement.gains, ...data.partyMovement.losses].every(row =>
        row.continuityBasis === 'same_exact_source_label'));
    assert.deepEqual([data.partyMovement.fromYear, data.partyMovement.toYear], [2021, 2026]);
    assert.equal(Object.hasOwn(data, 'turnout'), false);
});

test('campaign projection derives the 2021 to 2026 political transhumance poster data', () => {
    const movement = campaignData().politicalTranshumance;

    assert.deepEqual([movement.fromYear, movement.toYear, movement.totalMovements], [2021, 2026, 12]);
    assert.equal(movement.evidenceStatus, 'name_match_only');
    assert.equal(movement.qualifier, 'رصد بالأسماء المنشورة فـ elections.ma');
    assert.deepEqual(movement.gains.slice(0, 3).map(row => [row.abbreviation, row.count]), [
        ['PAM', 4], ['MP', 4], ['PI', 3]
    ]);
    assert.deepEqual(movement.losses.slice(0, 3).map(row => [row.abbreviation, row.count]), [
        ['RNI', 3], ['UC', 2], ['PI', 2]
    ]);
    assert.equal(movement.gains.some(row => row.abbreviation === 'AG'), false);
    assert.equal(movement.losses.some(row => row.abbreviation === 'PSU'), false);
    assert.ok([...movement.gains, ...movement.losses].every(row =>
        row.evidenceStatus === 'name_match_only'
        && row.continuityBasis === 'same_exact_source_label'));
});

test('campaign projection labels Casablanca-Settat as local seats only', () => {
    const region = campaignData().region;
    assert.equal(region.regionId, '6');
    assert.equal(region.regionNameAr, 'الدار البيضاء-سطات');
    assert.equal(region.ballotType, 'local');
    assert.equal(region.qualifier, 'المقاعد المحلية فقط');
    assert.deepEqual(region.years, [2016, 2021, 2026]);
    assert.equal(region.rows.length, 6);
    assert.ok(region.rows.every(row => row.points.length === 3 && row.points.every(point =>
        Number.isInteger(point.localSeats) && !Object.hasOwn(point, 'totalSeats'))));
    const maxima = region.rows.map(row => Math.max(...row.points.map(point => point.localSeats)));
    assert.ok(maxima.every((value, index) => index === 0 || maxima[index - 1] >= value));
});

test('campaign projection preserves reported women percentages without inferred counts', () => {
    const women = campaignData().demographics.women;
    assert.deepEqual(women.map(point => [point.year, point.percentage]), [
        [2016, 20.51], [2021, 24.3], [2026, 27.09]
    ]);
    assert.ok(women.every(point => point.valueStatus === 'reported'
        && point.factStatus === 'REPORTED_ELECTIONS_MA'
        && !Object.hasOwn(point, 'count')));
});

test('campaign projection preserves the full-house simulation qualifier and 395-seat totals', () => {
    const simulation = campaignData().quotient;
    assert.equal(simulation.officialSeatTotal, 395);
    assert.equal(simulation.simulatedSeatTotal, 395);
    assert.equal(simulation.qualifier, 'محاكاة، ماشي نتيجة رسمية');
    assert.equal(simulation.year, 2026);
    assert.ok(simulation.rows.length > 0 && simulation.rows.every(row =>
        row.delta !== 0 && row.simulatedSeats - row.officialSeats === row.delta));
});

test('campaign generation hashes exact source bytes and preserves existing output on audit failure', async t => {
    const { writeCampaignData } = await import('../../../scripts/social/generate-election-history-campaign-data.mjs');
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'fhemni-campaign-data-'));
    t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
    const input = path.join(directory, 'history.json');
    const output = path.join(directory, 'campaign-data.json');
    const bytes = fs.readFileSync(sourcePath);
    fs.writeFileSync(input, bytes);
    const data = await writeCampaignData({ sourcePath: input, outputPath: output });
    assert.equal(data.provenance.sourceSha256, sourceSha256);
    assert.equal(fs.readFileSync(output, 'utf8'), JSON.stringify(data, null, 2) + '\n');
    const validOutput = fs.readFileSync(output);
    await writeCampaignData({ sourcePath: input, outputPath: output });
    assert.deepEqual(fs.readFileSync(output), validOutput);

    const broken = payload();
    broken.nationalPartyResults.splice(broken.nationalPartyResults.findIndex(row =>
        row.year === 2021 && row.comparisonKey === 'exact-source-label:party_040005b84836'), 1);
    fs.writeFileSync(input, JSON.stringify(broken));
    await assert.rejects(writeCampaignData({ sourcePath: input, outputPath: output }),
        /national-party-roster/);
    assert.deepEqual(fs.readFileSync(output), validOutput);
    assert.deepEqual(fs.readdirSync(directory).sort(), ['campaign-data.json', 'history.json']);
});
