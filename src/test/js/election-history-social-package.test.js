const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const campaignDirectory = path.join(__dirname,
    '../../../docs/social/election-history-launch');
const threadPath = path.join(campaignDirectory, 'thread.json');
const threadMarkdownPath = path.join(campaignDirectory, 'thread.md');
const manifestPath = path.join(campaignDirectory, 'campaign-manifest.json');
const campaignDataPath = path.join(campaignDirectory, 'campaign-data.json');

function readJson(filePath) {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function sha256(filePath) {
    return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
}

function pngDimensions(filePath) {
    const bytes = fs.readFileSync(filePath);
    assert.ok(bytes.length >= 24, `${filePath} is too short to be a PNG`);
    assert.deepEqual([...bytes.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
    return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}

function renderThreadMarkdown(thread) {
    const sections = thread.posts.map(post => [
        `### ${post.number}/6`,
        '',
        post.text,
        '',
        `- Asset: \`${post.asset}\``,
        `- Alt text: ${post.altText}`
    ].join('\n'));
    return [
        '# Fhemni election-history launch',
        '',
        '## X thread',
        '',
        sections.join('\n\n'),
        '',
        '## Instagram Story',
        '',
        thread.instagramStory.copy,
        '',
        `- Asset: \`${thread.instagramStory.asset}\``,
        `- Alt text: ${thread.instagramStory.altText}`,
        ''
    ].join('\n');
}

test('X thread has six numbered posts at or below 280 code points', () => {
    const thread = readJson(threadPath);
    assert.deepEqual(thread.posts.map(post => post.number), [1, 2, 3, 4, 5, 6]);
    assert.equal(new Set(thread.posts.map(post => post.number)).size, 6);
    assert.ok(thread.posts.every(post => Array.from(post.text).length <= 280));
    assert.ok(thread.posts.every(post => post.characterCount === Array.from(post.text).length));
    assert.ok(thread.posts.every(post => post.text.startsWith(`${post.number}/6\n`)));
    const allCopy = [
        ...thread.posts.map(post => post.text),
        thread.instagramStory.copy
    ].join('\n');
    assert.doesNotMatch(allCopy, /turnout|participation|المشاركة|نسبة التصويت/iu);
});

test('thread maps one accessible asset to every post', () => {
    const thread = readJson(threadPath);
    const expectedAssets = [
        'assets/01-overview.png',
        'assets/02-party-movement.png',
        'assets/03-political-transhumance.png',
        'assets/04-regions.png',
        'assets/05-profiles.png',
        'assets/06-quotient.png'
    ];
    assert.deepEqual(thread.posts.map(post => post.asset), expectedAssets);
    assert.equal(new Set(thread.posts.map(post => post.asset)).size, 6);
    assert.ok(thread.posts.every(post => fs.existsSync(path.join(campaignDirectory, post.asset))));
    assert.ok(thread.posts.every(post => post.altText.length >= 40 && /\p{Script=Arabic}/u.test(post.altText)));
    assert.equal(thread.instagramStory.asset, 'assets/instagram-story.png');
    assert.ok(fs.existsSync(path.join(campaignDirectory, thread.instagramStory.asset)));
    assert.ok(thread.instagramStory.altText.length >= 40
        && /\p{Script=Arabic}/u.test(thread.instagramStory.altText));
});

test('closing post uses at most two hashtags and links to the historical comparison', () => {
    const closingPost = readJson(threadPath).posts.at(-1).text;
    assert.match(closingPost, /https:\/\/fhemni\.ma\/elections\/history/);
    assert.ok((closingPost.match(/#[\p{L}\p{N}_]+/gu) || []).length <= 2);
    assert.doesNotMatch(readJson(threadPath).posts.slice(0, -1).map(post => post.text).join('\n'),
        /https:\/\/fhemni\.ma\/elections\/history/);
});

test('simulation keeps its qualifier while political-transhumance copy avoids removed labels', () => {
    const thread = readJson(threadPath);
    const closingPost = thread.posts.at(-1).text;
    assert.match(closingPost, /محاكاة، ماشي نتيجة رسمية/);
    const allCopy = [...thread.posts.map(post => post.text), thread.instagramStory.copy].join('\n');
    assert.match(allCopy, /الترحال السياسي/u);
    assert.match(allCopy, /رصد بالأسماء المنشورة فـ elections\.ma/u);
    assert.match(thread.posts[2].text, /12 حالة/);
    assert.doesNotMatch(allCopy, /تطابقات الأسامي|تطابق الاسم/u);
    assert.doesNotMatch(allCopy,
        /المؤشر مبني على مقارنة الأسماء المنشورة، وماشي إثبات قانوني نهائي للهوية أو الانتقال/u);
    assert.match(thread.instagramStory.copy, /محاكاة، ماشي نتيجة رسمية/);
});

test('human-readable thread is generated consistently from the JSON package', () => {
    const thread = readJson(threadPath);
    assert.equal(fs.readFileSync(threadMarkdownPath, 'utf8'), renderThreadMarkdown(thread));
});

test('manifest digest matches campaign data and every PNG has the approved dimensions', () => {
    const data = readJson(campaignDataPath);
    const manifest = readJson(manifestPath);
    const expectedAssets = [
        ['overview', 'assets/01-overview.png', 1600, 900],
        ['partyMovement', 'assets/02-party-movement.png', 1600, 900],
        ['politicalTranshumance', 'assets/03-political-transhumance.png', 1600, 900],
        ['region', 'assets/04-regions.png', 1600, 900],
        ['demographics', 'assets/05-profiles.png', 1600, 900],
        ['quotient', 'assets/06-quotient.png', 1600, 900],
        ['instagramStory', 'assets/instagram-story.png', 1080, 1920]
    ];

    assert.equal(manifest.sourceSha256, data.provenance.sourceSha256);
    assert.equal(manifest.campaignDataSha256, sha256(campaignDataPath));
    assert.deepEqual(manifest.designReview, { status: 'approved', verdict: 'SHIP' });
    assert.deepEqual(manifest.assets.map(asset => [
        asset.key, asset.file, asset.width, asset.height
    ]), expectedAssets);
    for (const asset of manifest.assets) {
        const assetPath = path.join(campaignDirectory, asset.file);
        assert.deepEqual(pngDimensions(assetPath), {
            width: asset.width,
            height: asset.height
        });
        assert.equal(asset.sha256, sha256(assetPath));
        assert.ok(Array.isArray(asset.claimKeys) && asset.claimKeys.length > 0);
    }
});
