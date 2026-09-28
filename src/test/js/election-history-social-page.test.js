const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const campaignDirectory = path.join(__dirname,
    '../../../docs/social/election-history-launch');

function readCampaignFile(name) {
    return fs.readFileSync(path.join(campaignDirectory, name), 'utf8');
}

function count(haystack, needle) {
    return haystack.split(needle).length - 1;
}

test('campaign page exposes five X cards and one global Story in narrative order', () => {
    const html = readCampaignFile('campaign.html');
    const ids = [
        'campaign-01-overview',
        'campaign-02-parties',
        'campaign-03-regions',
        'campaign-04-profiles',
        'campaign-05-quotient',
        'campaign-story'
    ];

    assert.match(html, /<html[^>]+dir="rtl"/);
    assert.deepEqual(ids.map(id => html.indexOf(`id="${id}"`)),
        [...ids].map((_, index) => html.indexOf(`id="${ids[index]}"`))
            .sort((left, right) => left - right));
    assert.ok(ids.every(id => html.includes(`id="${id}"`)));
    assert.equal(count(html, 'class="campaign-card x-card"'), 5);
    assert.equal(count(html, 'class="campaign-card story-card"'), 1);
    assert.doesNotMatch(html, /turnout|نسبة المشاركة/u);
});

test('campaign cards use fixed export dimensions and the approved brand tokens', () => {
    const html = readCampaignFile('campaign.html');
    const css = readCampaignFile('campaign.css');

    assert.equal(count(html, 'data-export-width="1600" data-export-height="900"'), 5);
    assert.equal(count(html, 'data-export-width="1080" data-export-height="1920"'), 1);
    for (const token of ['#F5F1E8', '#FFFDF7', '#132C2B', '#176B63',
        '#0B4F49', '#E86F3C', '#BC4B51']) {
        assert.ok(css.includes(token), `missing brand token ${token}`);
    }
    assert.match(css, /\.x-card\s*\{[^}]*width:\s*1600px;[^}]*height:\s*900px;/s);
    assert.match(css, /\.story-card\s*\{[^}]*width:\s*1080px;[^}]*height:\s*1920px;/s);
    assert.match(css, /\.story-safe-area\s*\{[^}]*top:\s*240px;[^}]*height:\s*1400px;/s);
});

test('every card contains the Fhemni logo and elections.ma attribution', () => {
    const html = readCampaignFile('campaign.html');
    const logoPath = '../../../src/main/resources/static/assets/brand/fhemni-logo.png';
    const source = 'المصدر: elections.ma · التحليل: فهّمني';

    assert.equal(count(html, `src="${logoPath}"`), 6);
    assert.equal(count(html, source), 6);
    assert.equal(count(html, 'class="campaign-logo"'), 6);
    assert.equal(count(html, 'class="campaign-attribution"'), 6);
});

test('regional and quotient cards retain their mandatory qualifiers', () => {
    const script = readCampaignFile('campaign.js');

    assert.ok(script.includes('المقاعد المحلية فقط'));
    assert.ok(script.includes('محاكاة، ماشي نتيجة رسمية'));
});

test('campaign script isolates Latin numbers and abbreviations inside RTL copy', () => {
    const script = readCampaignFile('campaign.js');

    assert.match(script, /createElement\(['"]bdi['"]\)/);
    assert.match(script, /appendBdi\([^,]+,\s*row\.abbreviation\)/);
    assert.match(script, /appendBdi\([^,]+,\s*formatNumber\(/);
});

test('campaign election years remain ungrouped Latin labels', () => {
    const { formatYear } = require('../../../docs/social/election-history-launch/campaign.js');

    assert.equal(formatYear(2016), '2016');
    assert.equal(formatYear(2021), '2021');
    assert.equal(formatYear(2026), '2026');
});
