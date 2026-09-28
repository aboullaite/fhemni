const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const campaignDirectory = path.join(__dirname,
    '../../../docs/social/election-history-launch');
const campaignData = JSON.parse(fs.readFileSync(path.join(campaignDirectory,
    'campaign-data.json'), 'utf8'));

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

test('every card contains the Fhemni logo without a visible source footer', () => {
    const html = readCampaignFile('campaign.html');
    const logoPath = '../../../src/main/resources/static/assets/brand/fhemni-logo.png';
    const source = 'المصدر: elections.ma · التحليل: فهّمني';

    assert.equal(count(html, `src="${logoPath}"`), 6);
    assert.equal(count(html, 'class="campaign-logo"'), 6);
    assert.equal(count(html, source), 0);
    assert.equal(count(html, 'class="campaign-attribution"'), 0);
});

test('X cards carry one bottom-left website signature while Story keeps one CTA', () => {
    const html = readCampaignFile('campaign.html');
    const script = readCampaignFile('campaign.js');
    const css = readCampaignFile('campaign.css');
    const story = html.slice(html.indexOf('id="campaign-story"'));

    assert.equal(count(html, 'class="campaign-signature"'), 5);
    assert.equal(count(html, '<bdi>fhemni.ma</bdi>'), 5);
    assert.equal(count(story, 'campaign-signature'), 0);
    assert.equal(count(script, "appendBdi(action, 'fhemni.ma')"), 1);
    assert.match(css, /\.campaign-signature\s*\{[^}]*justify-self:\s*left;[^}]*color:\s*var\(--deep-teal\);[^}]*font-size:\s*25px;[^}]*font-weight:\s*700;[^}]*direction:\s*ltr;/s);
});

test('overview gives the full content area to the three-election comparison', () => {
    const script = readCampaignFile('campaign.js');

    assert.doesNotMatch(script, /overview-stat|overview-count|electedRecordCount/);
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

test('regional zero values render with no fill while positive values retain a minimum', () => {
    const { scaledPercentage } = require('../../../docs/social/election-history-launch/campaign.js');

    assert.equal(scaledPercentage(0, 22), 0);
    assert.equal(scaledPercentage(1, 100), 2);
    assert.equal(scaledPercentage(11, 22), 50);
});

test('profile cues use exact published age and education endpoint percentages', () => {
    const { publishedComparison } = require('../../../docs/social/election-history-launch/campaign.js');

    assert.deepEqual(publishedComparison(campaignData.demographics.age, 'أكبر من 55'), {
        fromYear: 2016,
        fromPercentage: 30.63,
        toYear: 2026,
        toPercentage: 42.78
    });
    assert.deepEqual(publishedComparison(campaignData.demographics.education, 'عالي'), {
        fromYear: 2016,
        fromPercentage: 74.68,
        toYear: 2026,
        toPercentage: 69.87
    });
});

test('party movement title keeps the compared years together', () => {
    const script = readCampaignFile('campaign.js');
    const css = readCampaignFile('campaign.css');

    assert.match(script, /element\(document, 'span', 'nowrap'\)/);
    assert.match(script, /appendBdi\(years, '2021'\)/);
    assert.match(script, /appendBdi\(years, '2026'\)/);
    assert.match(css, /\.nowrap\s*\{[^}]*white-space:\s*nowrap;/s);
});
