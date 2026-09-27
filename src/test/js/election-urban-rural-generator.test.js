const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');

const validatorUrl = pathToFileURL(path.join(__dirname,
    '../../../scripts/elections/urban-rural-crosswalk.mjs')).href;

test('split coverage rejects omitted, injected, overlapping, and duplicate administrative codes', async () => {
    const validator = await import(validatorUrl).catch(() => null);
    assert.equal(typeof validator?.validateExactPartition, 'function',
        'the generator must expose a real partition-coverage validator');

    const parent = ['unit-a', 'unit-b', 'unit-c'];
    assert.doesNotThrow(() => validator.validateExactPartition(
        'test-province', parent, [['left', ['unit-a']], ['right', ['unit-b', 'unit-c']]]));
    assert.throws(() => validator.validateExactPartition(
        'test-province', parent, [['left', ['unit-a']], ['right', ['unit-b']]]), /coverage/i);
    assert.throws(() => validator.validateExactPartition(
        'test-province', [...parent, 'unit-d'], [['left', ['unit-a']], ['right', ['unit-b', 'unit-c']]]), /coverage/i);
    assert.throws(() => validator.validateExactPartition(
        'test-province', parent, [['left', ['unit-a', 'unit-b']], ['right', ['unit-b', 'unit-c']]]), /duplicate|overlap/i);
    assert.throws(() => validator.validateExactPartition(
        'test-province', parent, [['left', ['unit-a', 'unit-a']], ['right', ['unit-b', 'unit-c']]]), /duplicate|overlap/i);
});

test('the pinned crosswalk rejects drift in any explicit constituency component list', async () => {
    const validator = await import(validatorUrl);
    assert.equal(typeof validator.validatePinnedCrosswalk, 'function',
        'the generator must validate every explicit crosswalk row against the pinned source');
    const pinned = [
        { constituencyCode: 'left', allocatedSeats: 2, populationCoverage: 'exact', componentCodes: ['unit-a'] },
        { constituencyCode: 'right', allocatedSeats: 3, populationCoverage: 'exact', componentCodes: ['unit-b', 'unit-c'] }
    ];
    assert.doesNotThrow(() => validator.validatePinnedCrosswalk(pinned, structuredClone(pinned)));
    const omitted = structuredClone(pinned);
    omitted[1].componentCodes.pop();
    assert.throws(() => validator.validatePinnedCrosswalk(omitted, pinned), /crosswalk mismatch/i);
    const injected = structuredClone(pinned);
    injected[0].componentCodes.push('unit-d');
    assert.throws(() => validator.validatePinnedCrosswalk(injected, pinned), /crosswalk mismatch/i);
});

test('the committed constituency snapshot regenerates byte-identically with network access disabled', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'fhemni-urban-rural-'));
    const output = path.join(directory, 'urban-rural-constituencies.js');
    const disableNetwork = path.join(directory, 'disable-network.mjs');
    fs.writeFileSync(disableNetwork,
        "globalThis.fetch = async () => { throw new Error('network access is disabled by the test'); };\n");
    const generator = path.join(__dirname, '../../../scripts/elections/generate-urban-rural-constituencies.mjs');
    const generated = spawnSync(process.execPath, [generator, output], {
        cwd: path.join(__dirname, '../../..'),
        env: { ...process.env, NODE_OPTIONS: `--import=${pathToFileURL(disableNetwork).href}` },
        encoding: 'utf8'
    });
    assert.equal(generated.status, 0, generated.stderr || generated.stdout);
    assert.equal(fs.readFileSync(output, 'utf8'), fs.readFileSync(path.join(__dirname,
        '../../main/resources/static/data/elections/2026/urban-rural-constituencies.js'), 'utf8'));
});
