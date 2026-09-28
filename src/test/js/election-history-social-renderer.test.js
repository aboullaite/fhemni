const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const rendererUrl = pathToFileURL(path.join(__dirname,
    '../../../scripts/social/render-election-history-campaign.mjs')).href;

function validPngHeader(width, height) {
    const bytes = Buffer.alloc(33);
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]).copy(bytes, 0);
    bytes.writeUInt32BE(13, 8);
    bytes.write('IHDR', 12, 'ascii');
    bytes.writeUInt32BE(width, 16);
    bytes.writeUInt32BE(height, 20);
    bytes[24] = 8;
    bytes[25] = 6;
    return bytes;
}

function readyPage(assets) {
    return {
        fontsReady: true,
        campaignReady: true,
        dataError: null,
        logos: Array.from({ length: 6 }, () => ({
            complete: true,
            naturalWidth: 512,
            naturalHeight: 180
        })),
        roots: assets.map(asset => ({
            id: asset.rootId,
            ready: true,
            width: asset.width,
            height: asset.height,
            contentFits: true
        }))
    };
}

test('renderer maps every campaign root to the approved filename and dimensions', async () => {
    const { CAMPAIGN_ASSETS } = await import(rendererUrl);

    assert.deepEqual(CAMPAIGN_ASSETS, [
        { rootId: 'campaign-01-overview', filename: '01-overview.png', width: 1600, height: 900 },
        { rootId: 'campaign-02-parties', filename: '02-party-movement.png', width: 1600, height: 900 },
        { rootId: 'campaign-03-regions', filename: '03-regions.png', width: 1600, height: 900 },
        { rootId: 'campaign-04-profiles', filename: '04-profiles.png', width: 1600, height: 900 },
        { rootId: 'campaign-05-quotient', filename: '05-quotient.png', width: 1600, height: 900 },
        { rootId: 'campaign-story', filename: 'instagram-story.png', width: 1080, height: 1920 }
    ]);
});

test('PNG dimension reader rejects malformed files and reports exact dimensions', async () => {
    const { readPngDimensions } = await import(rendererUrl);

    assert.deepEqual(readPngDimensions(validPngHeader(1600, 900)), {
        width: 1600,
        height: 900
    });
    assert.deepEqual(readPngDimensions(validPngHeader(1080, 1920)), {
        width: 1080,
        height: 1920
    });
    assert.throws(() => readPngDimensions(Buffer.alloc(0)), /PNG/);
    assert.throws(() => readPngDimensions(Buffer.alloc(33)), /signature/);
    assert.throws(() => readPngDimensions(validPngHeader(0, 900)), /dimensions/);
    assert.throws(() => readPngDimensions(validPngHeader(1600, 0)), /dimensions/);
    assert.throws(() => readPngDimensions(validPngHeader(1600, 900).subarray(0, 24)), /IHDR/);
});

test('renderer refuses capture when fonts, logo, data, or ready flags are missing', async () => {
    const { CAMPAIGN_ASSETS, assertCaptureReady } = await import(rendererUrl);
    const valid = readyPage(CAMPAIGN_ASSETS);

    assert.doesNotThrow(() => assertCaptureReady(valid));
    assert.throws(() => assertCaptureReady({ ...valid, fontsReady: false }), /fonts/i);
    assert.throws(() => assertCaptureReady({ ...valid, campaignReady: false }), /ready flag/i);
    assert.throws(() => assertCaptureReady({ ...valid, dataError: 'campaign-data.json missing' }),
        /campaign data/i);
    assert.throws(() => assertCaptureReady({ ...valid, logos: [] }), /logo/i);
    assert.throws(() => assertCaptureReady({
        ...valid,
        logos: valid.logos.map((logo, index) => index ? logo : { ...logo, naturalWidth: 0 })
    }), /logo/i);
    assert.throws(() => assertCaptureReady({
        ...valid,
        roots: valid.roots.map((root, index) => index ? root : { ...root, ready: false })
    }), /ready/i);
    assert.throws(() => assertCaptureReady({
        ...valid,
        roots: valid.roots.map((root, index) => index ? root : { ...root, width: 0 })
    }), /bounds/i);
    assert.throws(() => assertCaptureReady({
        ...valid,
        roots: valid.roots.map((root, index) => index ? root : { ...root, contentFits: false })
    }), /content bounds/i);
});

test('renderer requires an isolated root at the viewport origin before capture', async () => {
    const { assertCaptureBounds } = await import(rendererUrl);
    const asset = { rootId: 'campaign-01-overview', width: 1600, height: 900 };

    assert.doesNotThrow(() => assertCaptureBounds({ x: 0, y: 0, width: 1600, height: 900 }, asset));
    assert.throws(() => assertCaptureBounds({ x: 48, y: 0, width: 1600, height: 900 }, asset),
        /viewport origin/i);
    assert.throws(() => assertCaptureBounds({ x: 0, y: 0, width: 800, height: 900 }, asset),
        /changed size/i);
});
