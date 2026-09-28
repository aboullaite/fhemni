const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const pagePath = path.join(__dirname,
    '../../main/resources/static/historical-elections.html');
const controllerPath = path.join(__dirname,
    '../../main/resources/static/js/historical-elections.js');
const cssPath = path.join(__dirname,
    '../../main/resources/static/css/app.css');

const {
    COPY,
    readUrlState,
    writeUrlState,
    listMeasureAvailable,
    listSeatLabelKey,
    totalMeasureCaveatKey,
    partyBarPercent,
    partyBarMaximum,
    regionSeatBarPercent,
    resolveAvailableSelection,
    evidenceReference,
    formatSigned
} = require(controllerPath);

test('history page exposes the progressive comparison sections and accessible status regions', () => {
    const page = fs.readFileSync(pagePath, 'utf8');
    const orderedIds = [
        'historyOverview',
        'historyParties',
        'historyTrajectory',
        'historyQuotient',
        'historyRegions',
        'historyEvidence',
        'historyPeople',
        'historySources'
    ];
    let previous = -1;
    for (const id of orderedIds) {
        const index = page.indexOf(`id="${id}"`);
        assert.ok(index > previous, `${id} should appear in reading order`);
        previous = index;
    }
    assert.match(page, /id="historyStatus"[^>]*role="status"[^>]*aria-live="polite"/);
    assert.match(page, /id="historyPairNotice"[^>]*role="status"/);
    assert.match(page, /id="historySelectionNotice"[^>]*role="status"/);
    assert.match(page, /id="historyPeopleTable"[\s\S]*?<caption/);
    assert.match(page, /id="historyPeopleCards"/);
    assert.match(page, /id="historyPeopleCount"[^>]*tabindex="-1"/);
    assert.match(page, /class="history-pagination"[^>]*aria-label="[^"]+"/);
    assert.match(page, /id="historyExactFigures"/);
    assert.match(page, /<script src="\/js\/i18n\.js[^>]*defer/);
    assert.match(page, /<script src="\/js\/historical-election-insights\.js[^>]*defer/);
    assert.match(page, /<script src="\/js\/historical-electoral-quotient\.js[^>]*defer/);
    assert.match(page, /<script src="\/js\/historical-elections\.js[^>]*defer/);
});

test('all page copy is complete in Darija, French, and English and states the evidence limits', () => {
    const keys = Object.keys(COPY.en).sort();
    assert.ok(keys.length > 70);
    for (const locale of ['ar', 'fr', 'en']) {
        assert.deepEqual(Object.keys(COPY[locale]).sort(), keys);
        for (const [key, value] of Object.entries(COPY[locale])) {
            assert.equal(typeof value, 'string', `${locale}.${key}`);
            assert.ok(value.trim().length > 0, `${locale}.${key}`);
        }
    }
    assert.match(COPY.en.regionCaveat, /local seats only/i);
    assert.match(COPY.en.listUnavailable, /unavailable.*2016|2016.*unavailable/i);
    assert.match(COPY.en.measureTotalChangedCaveat, /national list.*2016.*regional list/i);
    assert.match(COPY.en.nationalListSeats, /national list/i);
    assert.match(COPY.en.regionalListSeats, /regional list/i);
    assert.match(COPY.en.selectionFallback, /party.*region/i);
    assert.match(COPY.en.sourcesIntro, /election-level records/i);
    assert.doesNotMatch(COPY.en.sourcesIntro, /every result/i);
    assert.match(COPY.en.paginationLabel, /pages/i);
    assert.match(COPY.en.nameMatchCaveat, /not verified identity/i);
    assert.match(COPY.en.nameMatchCaveat, /party switch/i);
    assert.match(COPY.fr.nameMatchCaveat, /identit[ée].*v[ée]rifi[ée]e/i);
    assert.match(COPY.ar.nameMatchCaveat, /ماشي.*هوية/);
    assert.match(COPY.en.quotientWarning, /counterfactual/i);
    assert.match(COPY.en.quotientLocalOnly, /305 local seats/i);
    assert.match(COPY.fr.quotientWarning, /contrefactuelle/i);
    assert.match(COPY.ar.quotientWarning, /محاكاة/);
});

test('URL state accepts all shareable controls, drops invalid values, and serializes deterministically', () => {
    assert.deepEqual(readUrlState('?from=2016&to=2026&measure=total&sort=name&party=p1&partyq=green&all=1&constituency=c9&region=4&demographic=education&people=amina&different=1&page=2'), {
        from: '2016', to: '2026', measure: 'total', sort: 'name',
        party: 'p1', partyQuery: 'green', showAllParties: true, quotientConstituency: 'c9', region: '4',
        demographicDimension: 'education', peopleQuery: 'amina', differentOnly: true, page: 2
    });
    assert.deepEqual(readUrlState('?from=nope&to=2026&measure=votes&sort=random&demographic=unknown&page=-3'), {
        from: null, to: '2026', measure: 'local', sort: 'delta-desc',
        party: '', partyQuery: '', showAllParties: false, quotientConstituency: '', region: '',
        demographicDimension: 'gender', peopleQuery: '', differentOnly: false, page: 1
    });
    assert.equal(writeUrlState({
        from: '2016', to: '2026', measure: 'list', sort: 'delta-asc',
        party: 'party key', partyQuery: 'search words', showAllParties: true,
        quotientConstituency: 'contest key',
        region: '7', demographicDimension: 'age', peopleQuery: '', differentOnly: true, page: 1
    }), '?from=2016&to=2026&measure=list&sort=delta-asc&party=party+key&partyq=search+words&all=1&constituency=contest+key&region=7&demographic=age&different=1');
});

test('presentation helpers preserve direction, exact values, and zero baselines', () => {
    assert.equal(listMeasureAvailable(2016, 2021), false);
    assert.equal(listMeasureAvailable(2016, 2026), false);
    assert.equal(listMeasureAvailable(2021, 2026), true);
    assert.equal(listSeatLabelKey(2016), 'nationalListSeats');
    assert.equal(listSeatLabelKey(2021), 'regionalListSeats');
    assert.equal(listSeatLabelKey(2026), 'regionalListSeats');
    assert.equal(totalMeasureCaveatKey(2016, 2026), 'measureTotalChangedCaveat');
    assert.equal(totalMeasureCaveatKey(2021, 2026), 'measureTotalCaveat');
    assert.equal(formatSigned(12, 'en'), '+12');
    assert.equal(formatSigned(-4, 'fr'), '−4');
    assert.equal(formatSigned(0, 'ar'), '0');
    assert.equal(partyBarPercent(-30, 60), 50);
    assert.equal(partyBarPercent(0, 0), 0);
    assert.equal(partyBarPercent(90, 60), 100);
    assert.equal(partyBarMaximum([{ selectedDelta: 2 }, { selectedDelta: -17 }, { selectedDelta: 8 }]), 17);
    assert.equal(regionSeatBarPercent(1, 20), 5);
    assert.equal(regionSeatBarPercent(20, 20), 100);
    assert.equal(regionSeatBarPercent(30, 20), 100);
    assert.deepEqual(resolveAvailableSelection('stale', ['a', 'b']), { value: 'a', usedFallback: true });
    assert.deepEqual(resolveAvailableSelection('b', ['a', 'b']), { value: 'b', usedFallback: false });
    assert.equal(evidenceReference('query-42', 'Record reference'), 'Record reference: query-42');
});

test('page script renders demographic provenance visibly and restores focus after paging', () => {
    const controller = fs.readFileSync(controllerPath, 'utf8');
    assert.match(controller, /node\('small',[\s\S]{0,160}evidenceReference\(point\.sourceQueryId/);
    assert.match(controller, /point\.sourcePercentageText/);
    assert.match(controller, /regionSeatBarPercent\(point\.localSeats, regionCapacity\)/);
    assert.match(controller, /value\.setAttribute\('aria-label'/);
    assert.match(controller, /heading\.append\(bdi\(group\.occurrences\[0\]\?\.nameAr/);
    assert.match(controller, /derive2026LocalSeatCounterfactual\(payload\)/);
    assert.match(controller, /derive2026Full2016SystemCounterfactual\(payload\)/);
    assert.match(controller, /historyQuotientConstituency/);
    assert.match(controller, /validation\.exactConstituencyMatches/);
    assert.match(controller, /listValidation\.exactPartyMatches/);
    assert.match(controller, /historyPeopleCount'\)\.focus\(\)/);
    assert.match(controller, /historyPagination.*setAttribute\('aria-label', t\('paginationLabel'\)\)/);
});

test('visible demographic evidence wraps inside the mobile viewport', () => {
    const css = fs.readFileSync(cssPath, 'utf8');
    assert.match(css, /\.history-demographic-points span\s*\{[^}]*min-width:\s*0;/);
    assert.match(css, /\.history-demographic-points small\s*\{[^}]*overflow-wrap:\s*anywhere;/);
});
