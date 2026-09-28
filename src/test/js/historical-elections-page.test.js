const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const regionFilters = require('../../main/resources/static/js/election-region-filters.js');

const pagePath = path.join(__dirname,
    '../../main/resources/static/historical-elections.html');
const currentPagePath = path.join(__dirname,
    '../../main/resources/static/election-results.html');
const controllerPath = path.join(__dirname,
    '../../main/resources/static/js/historical-elections.js');
const currentControllerPath = path.join(__dirname,
    '../../main/resources/static/js/election-results.js');
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
    stackedSeatSeries,
    quotientViewState,
    demographicChartModel,
    createEvidenceStatusBadge,
    createAffiliationSourceLink,
    appendMethodologyLink,
    regionSeatBarPercent,
    seatSharePercent,
    resolveAvailableSelection,
    formatSigned,
    normalizeHistoryTab,
    historyTabIndex,
    historyTabPanelState,
    historicalPartyLogoAsset,
    repeatedNameTransition,
    partySearchRows,
    historyCountText,
    createHistoryAnnouncer
} = require(controllerPath);

function fakeDocument() {
    return {
        createElement(tagName) {
            return {
                tagName: tagName.toUpperCase(),
                className: '',
                textContent: '',
                href: '',
                children: [],
                append(...children) { this.children.push(...children); }
            };
        },
        createTextNode(textContent) { return { nodeType: 3, textContent }; }
    };
}

test('affiliation evidence links never backdate an undated publication from its observation year', () => {
    const doc = fakeDocument();
    const undated = createAffiliationSourceLink(doc, {
        publisher: 'Identity corroboration', publishedAt: null,
        observedAt: '2021-09-08', year: 2021, url: 'https://example.org/2026/profile'
    }, 'Identity check');
    assert.equal(undated.textContent.trim(), 'Identity check');
    assert.equal(undated.children.length, 0);
    assert.equal(undated.href, 'https://example.org/2026/profile');
    const dated = createAffiliationSourceLink(doc, {
        publisher: 'Official roster', publishedAt: '2021-12-17', url: 'https://example.org/roster'
    }, 'Identity check');
    assert.equal(dated.children[0].textContent, '2021-12-17');
    assert.equal(dated.children[0].dir, 'ltr');
});

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
    assert.match(page, /id="historyPeopleCount"[^>]*class="[^"]*sr-only[^"]*"[^>]*tabindex="-1"/);
    assert.match(page, /<caption id="historyPeopleCaption" class="sr-only"><\/caption>/);
    assert.match(page, /class="history-pagination"[^>]*aria-label="[^"]+"/);
    assert.match(page, /id="historyExactFigures"/);
    assert.match(page, /id="historyTabs"[^>]*role="tablist"/);
    for (const id of ['historyOverviewPanel', 'historyPartiesPanel', 'historyQuotientPanel',
        'historyRegionsPanel', 'historyPeoplePanel', 'historySourcesPanel']) {
        assert.match(page, new RegExp(`id="${id}"[^>]*role="tabpanel"`));
    }
    assert.match(page, /id="historyPeopleMovements"/);
    assert.match(page, /id="historyMovementGains"/);
    assert.match(page, /id="historyMovementLosses"/);
    assert.doesNotMatch(page, /id="historyPartyNotComparable"/);
    assert.match(page, /id="historyMeasureTabs"[^>]*role="group"/);
    assert.match(page, /id="historyMeasureTotal"[^>]*aria-pressed="true"/);
    assert.match(page, /id="historyMeasureLocal"[^>]*aria-pressed="false"/);
    assert.match(page, /id="historyMeasureList"[^>]*aria-pressed="false"/);
    assert.match(page, /<script src="\/js\/i18n\.js[^>]*defer/);
    assert.match(page, /<script src="\/js\/election-region-filters\.js[^>]*defer/);
    assert.match(page, /<script src="\/js\/historical-election-insights\.js[^>]*defer/);
    assert.match(page, /<script src="\/js\/historical-electoral-quotient\.js[^>]*defer/);
    assert.match(page, /<script src="\/js\/historical-elections\.js[^>]*defer/);
    assert.match(page, /\/css\/dist\.css\?v=\d{8}-\d+/);
    assert.match(page, /\/js\/historical-election-insights\.js\?v=\d{8}-\d+/);
    assert.match(page, /\/js\/historical-data-integrity\.js\?v=\d{8}-\d+/);
    assert.match(page, /\/js\/historical-elections\.js\?v=\d{8}-\d+/);
});

test('current and historical election heroes cross-link with compact green actions', () => {
    const currentPage = fs.readFileSync(currentPagePath, 'utf8');
    const historyPage = fs.readFileSync(pagePath, 'utf8');
    const currentController = fs.readFileSync(currentControllerPath, 'utf8');
    const historyController = fs.readFileSync(controllerPath, 'utf8');
    const css = fs.readFileSync(cssPath, 'utf8');

    assert.match(currentPage,
        /class="election-hero-copy"[\s\S]*?id="electionHistoryLink"[^>]*class="priority-primary-button button-link election-history-link"/);
    assert.match(currentPage, /\/css\/dist\.css\?v=20260928-7/);
    assert.match(currentPage, /\/js\/election-results\.js\?v=20260928-1/);
    assert.match(currentController, /historyLink: 'قارن مع الانتخابات السابقة'/);
    assert.match(currentController, /historyLink: 'Comparer avec les élections précédentes'/);
    assert.match(currentController, /historyLink: 'Compare with previous elections'/);

    assert.match(historyPage,
        /id="historyCurrentResultsLink" class="priority-primary-button button-link history-current-results-link"/);
    assert.doesNotMatch(historyPage, /id="historyPairHelp"/);
    assert.doesNotMatch(historyController, /pairHelp:/);
    assert.match(css, /\.election-hero \.election-history-link\s*\{[^}]*min-height:\s*40px;/);
    assert.match(css, /\.history-hero \.history-current-results-link\s*\{[^}]*min-height:\s*40px;/);
    assert.match(css, /\.history-pair-fields label > span\s*\{[^}]*white-space:\s*nowrap;/);
    assert.match(css, /\.history-pair-arrow\s*\{[^}]*min-height:\s*44px;[^}]*place-items:\s*center;[^}]*padding:\s*0;/);
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
    assert.match(COPY.en.nameMatchCaveat, /do not verify identity/i);
    assert.match(COPY.en.nameMatchCaveat, /does not cover every candidate/i);
    assert.match(COPY.fr.nameMatchCaveat, /ne certifie pas l’identité/i);
    assert.match(COPY.ar.nameMatchCaveat, /ما كيأكدش الهوية/);
    assert.match(COPY.en.quotientWarning, /counterfactual/i);
    for (const lang of ['ar', 'fr', 'en']) {
        assert.match(COPY[lang].quotientWarning, /60\s*\+\s*30/);
        assert.match(COPY[lang].quotientIntro, /3%/);
    }
    assert.match(COPY.en.quotientIntro, /registered voters/i);
    assert.match(COPY.fr.quotientIntro, /inscrit/i);
    assert.match(COPY.ar.quotientIntro, /المسجلين/);
    assert.equal(Object.hasOwn(COPY.en, 'partiesIntro'), false);
    assert.equal(Object.hasOwn(COPY.fr, 'partiesIntro'), false);
    assert.equal(Object.hasOwn(COPY.ar, 'partiesIntro'), false);
    assert.match(COPY.en.methodText, /305 local seats/i);
    assert.match(COPY.en.methodText, /395-seat/i);
    assert.match(COPY.en.methodText, /3% exclusion threshold/i);
    assert.match(COPY.en.methodText, /not.*quotient alone/i);
    assert.match(COPY.fr.quotientWarning, /contrefactuelle/i);
    assert.match(COPY.ar.quotientWarning, /محاكاة/);
    assert.match(COPY.en.fullMethodology, /methodology/i);
    assert.equal(COPY.ar.overviewTitle, 'الأصوات والتمثيل البرلماني');
    assert.equal(COPY.ar.gender, 'الجنس');
    assert.equal(COPY.ar.analysisLabel, 'تحليل فهّمني مبني على معطيات elections.ma');
    assert.match(COPY.en.evidenceIntro, /elections\.ma/i);
    assert.doesNotMatch(COPY.en.evidenceIntro, /reference/i);
    assert.doesNotMatch(COPY.fr.evidenceIntro, /référence/i);
    assert.doesNotMatch(COPY.ar.evidenceIntro, /مرجع/);
    assert.equal(COPY.fr.overviewTitle, 'Voix et représentation parlementaire');
    assert.equal(COPY.en.overviewTitle, 'Votes and parliamentary representation');
    assert.match(COPY.ar.peopleTitle, /الترحال السياسي/);
    assert.match(COPY.fr.peopleTitle, /Mobilité politique/);
    assert.match(COPY.en.peopleTitle, /Political mobility/);
    assert.equal(readUrlState('').peopleScope, 'candidates');
    assert.equal(readUrlState(writeUrlState({ peopleScope: 'elected' })).peopleScope, 'elected');
    assert.equal(readUrlState('?scope=invented').peopleScope, 'candidates');
    assert.match(COPY.ar.measureLocalCaveat, /المجموع.*الافتراضي/);
    assert.match(COPY.fr.measureLocalCaveat, /total.*défaut/i);
    assert.match(COPY.en.measureLocalCaveat, /total.*default/i);
    for (const locale of ['ar', 'fr', 'en']) {
        assert.equal(Object.hasOwn(COPY[locale], 'quotientFullAssumption'), false);
        assert.equal(Object.hasOwn(COPY[locale], 'quotientLocalOnly'), false);
        assert.equal(Object.hasOwn(COPY[locale], 'sourceQuery'), false);
        assert.equal(Object.hasOwn(COPY[locale], 'partyNotComparableTitle'), false);
        assert.equal(Object.hasOwn(COPY[locale], 'partyNotComparableIntro'), false);
    }
    assert.match(COPY.ar.sourceLabelOnly, /التحالفات اللي تأكد/);
    assert.match(COPY.fr.sourceLabelOnly, /compositions d’alliance vérifiées/);
    assert.match(COPY.en.sourceLabelOnly, /verified alliance compositions/);
    assert.doesNotMatch(COPY.en.sourceLabelOnly, /distinct alliances are not merged/);
});

test('Arabic history counts render the noun instead of interpolating a raw number', () => {
    assert.deepEqual([0, 1, 2, 3, 11, 102].map(count =>
        historyCountText('ar', 'movementCount', count)), [
        'لا تطابقات',
        'تطابق واحد',
        'تطابقان',
        '3 تطابقات',
        '11 تطابقاً',
        '102 تطابق'
    ]);
    assert.equal(historyCountText('ar', 'peopleCount', 2), 'تطابقا اسم');
    assert.equal(historyCountText('ar', 'partyStatus', 2), 'حزبان أو لائحتان ظاهرين.');
    assert.equal(historyCountText('ar', 'peopleStatus', 2, { page: 1, pages: 4 }),
        'تطابقان؛ الصفحة 1 من 4.');
    assert.equal(historyCountText('ar', 'showAll', 2), 'بين الكل (حزبان أو لائحتان)');
    assert.equal(historyCountText('en', 'movementCount', 2), '2 matches');
});

test('history status re-announces an identical control result', () => {
    const changes = [];
    const scheduled = [];
    const status = {
        value: '',
        get textContent() { return this.value; },
        set textContent(value) { this.value = value; changes.push(value); }
    };
    const announce = createHistoryAnnouncer(status, regionFilters,
        callback => scheduled.push(callback));

    announce('2 parties shown.');
    announce('2 parties shown.');

    assert.deepEqual(changes, ['2 parties shown.', '']);
    scheduled.shift()();
    assert.deepEqual(changes, ['2 parties shown.', '', '2 parties shown.']);
});

test('party search includes source-only official results without treating them as gains or losses', () => {
    const result = {
        rows: [{ comparisonKey: 'exact-source-label:pam', nameAr: 'PAM' }],
        notComparableRows: [{ comparisonKey: 'source:2016:pgvm', nameAr: 'PGVM' }]
    };

    assert.deepEqual(partySearchRows(result, ''), [
        { comparisonKey: 'exact-source-label:pam', nameAr: 'PAM', comparisonAvailable: true }
    ]);
    assert.deepEqual(partySearchRows(result, 'PGVM'), [
        { comparisonKey: 'exact-source-label:pam', nameAr: 'PAM', comparisonAvailable: true },
        { comparisonKey: 'source:2016:pgvm', nameAr: 'PGVM', comparisonAvailable: false }
    ]);
});

test('generic section subtitles are removed while evidence and methodology notes remain', () => {
    const page = fs.readFileSync(pagePath, 'utf8');

    for (const id of ['historyOverviewIntro', 'historyPartiesIntro', 'historyTrajectoryIntro',
        'historyRegionsIntro', 'historyEvidenceIntro', 'historyPeopleIntro']) {
        assert.doesNotMatch(page, new RegExp(`id="${id}"`));
    }
    for (const id of ['historyMeasureCaveat', 'historyQuotientIntro', 'historyQuotientWarning',
        'historyRegionCaveat', 'historyDemographicCaveat', 'historyNameMatchCaveat',
        'historyMovementIntro', 'historyMethodText']) {
        assert.match(page, new RegExp(`id="${id}"`));
    }
});

test('URL state keeps non-default seat tabs shareable and defaults to total seats', () => {
    assert.deepEqual(readUrlState('?from=2016&to=2026&tab=people&measure=total&sort=name&party=p1&partyq=green&all=1&constituency=c9&region=4&demographic=education&people=amina&different=1&page=2'), {
        from: '2016', to: '2026', tab: 'people', measure: 'total', sort: 'name',
        party: 'p1', partyQuery: 'green', showAllParties: true, quotientConstituency: 'c9', region: '4',
        demographicDimension: 'education', peopleQuery: 'amina', differentOnly: true, peopleScope: 'candidates', page: 2
    });
    assert.deepEqual(readUrlState('?from=nope&to=2026&measure=votes&sort=random&demographic=unknown&page=-3'), {
        from: null, to: '2026', tab: 'overview', measure: 'total', sort: 'delta-desc',
        party: '', partyQuery: '', showAllParties: false, quotientConstituency: '', region: '',
        demographicDimension: 'gender', peopleQuery: '', differentOnly: false, peopleScope: 'candidates', page: 1
    });
    assert.equal(writeUrlState({
        from: '2016', to: '2026', tab: 'regions', measure: 'list', sort: 'delta-asc',
        party: 'party key', partyQuery: 'search words', showAllParties: true,
        quotientConstituency: 'contest key',
        region: '7', demographicDimension: 'age', peopleQuery: '', differentOnly: true, page: 1
    }), '?from=2016&to=2026&tab=regions&measure=list&sort=delta-asc&party=party+key&partyq=search+words&all=1&constituency=contest+key&region=7&demographic=age&different=1');
});

test('history tabs normalize state and follow RTL keyboard order', () => {
    assert.equal(normalizeHistoryTab('people'), 'people');
    assert.equal(normalizeHistoryTab('unknown'), 'overview');
    assert.deepEqual(historyTabPanelState('people'), [
        { tab: 'overview', panelId: 'historyOverviewPanel', active: false },
        { tab: 'parties', panelId: 'historyPartiesPanel', active: false },
        { tab: 'quotient', panelId: 'historyQuotientPanel', active: false },
        { tab: 'regions', panelId: 'historyRegionsPanel', active: false },
        { tab: 'people', panelId: 'historyPeoplePanel', active: true },
        { tab: 'sources', panelId: 'historySourcesPanel', active: false }
    ]);
    assert.equal(historyTabIndex(0, 'ArrowLeft', 6, 'rtl'), 1);
    assert.equal(historyTabIndex(0, 'ArrowRight', 6, 'rtl'), 5);
    assert.equal(historyTabIndex(5, 'ArrowRight', 6, 'ltr'), 0);
    assert.equal(historyTabIndex(3, 'Home', 6, 'rtl'), 0);
    assert.equal(historyTabIndex(3, 'End', 6, 'rtl'), 5);
});

test('presentation helpers preserve direction, exact values, and zero baselines', () => {
    assert.equal(historicalPartyLogoAsset('RNI'), '/assets/parties/rni-display.png');
    assert.equal(historicalPartyLogoAsset('PAM'), '/assets/parties/pam-display.png');
    assert.equal(historicalPartyLogoAsset('P.EQUITE'), '/assets/parties/pe-display.png');
    assert.equal(historicalPartyLogoAsset('AG'), '/assets/parties/party.svg');
    assert.equal(historicalPartyLogoAsset('AG', 'source_observation_only'), '/assets/parties/party.svg');
    assert.equal(historicalPartyLogoAsset('AG', 'same_exact_source_label'), '/assets/parties/fgd-official-2026.png');
    assert.equal(historicalPartyLogoAsset('PSU', 'source_observation_only'), '/assets/parties/party.svg');
    assert.equal(historicalPartyLogoAsset('PSU', 'same_exact_source_label'), '/assets/parties/psu-display.png');
    assert.equal(historicalPartyLogoAsset('unknown-code'), '/assets/parties/party.svg');
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
    assert.deepEqual(Array.from(stackedSeatSeries(2021,
        { localSeats: 45, listSeats: 10, totalSeats: 55 }, 2026,
        { localSeats: 54, listSeats: 6, totalSeats: 60 }, 60), row => ({ ...row })), [
        { label: '2021', localSeats: 45, listSeats: 10, totalSeats: 55, localPercent: 75, listPercent: 16.67 },
        { label: '2026', localSeats: 54, listSeats: 6, totalSeats: 60, localPercent: 90, listPercent: 10 }
    ]);
    assert.deepEqual(Array.from(stackedSeatSeries('Official 2026',
        { localSeats: 54, listSeats: 11, totalSeats: 65 }, '2016 rules',
        { localSeats: 52, listSeats: 10, totalSeats: 62 }, 65), row => ({ ...row })), [
        { label: 'Official 2026', localSeats: 54, listSeats: 11, totalSeats: 65, localPercent: 83.08, listPercent: 16.92 },
        { label: '2016 rules', localSeats: 52, listSeats: 10, totalSeats: 62, localPercent: 80, listPercent: 15.38 }
    ]);
    assert.deepEqual(Array.from(stackedSeatSeries(2021,
        { localSeats: 45, listSeats: 10, totalSeats: 55 }, 2026,
        { localSeats: 54, listSeats: 6, totalSeats: 60 }, 54, 'local'), row => ({ ...row })), [
        { label: '2021', localSeats: 45, listSeats: 0, totalSeats: 45, localPercent: 83.33, listPercent: 0 },
        { label: '2026', localSeats: 54, listSeats: 0, totalSeats: 54, localPercent: 100, listPercent: 0 }
    ]);
    assert.deepEqual(Array.from(stackedSeatSeries(2021,
        { localSeats: 45, listSeats: 10, totalSeats: 55 }, 2026,
        { localSeats: 54, listSeats: 6, totalSeats: 60 }, 10, 'list'), row => ({ ...row })), [
        { label: '2021', localSeats: 0, listSeats: 10, totalSeats: 10, localPercent: 0, listPercent: 100 },
        { label: '2026', localSeats: 0, listSeats: 6, totalSeats: 6, localPercent: 0, listPercent: 60 }
    ]);
    assert.equal(regionSeatBarPercent(1, 20), 5);
    assert.equal(regionSeatBarPercent(20, 20), 100);
    assert.equal(regionSeatBarPercent(30, 20), 100);
    assert.equal(seatSharePercent(305, 395), 77.22);
    assert.equal(seatSharePercent(90, 395), 22.78);
    assert.equal(seatSharePercent(10, 0), 0);
    assert.deepEqual(resolveAvailableSelection('stale', ['a', 'b']), { value: 'a', usedFallback: true });
    assert.deepEqual(resolveAvailableSelection('b', ['a', 'b']), { value: 'b', usedFallback: false });
});

test('Al Amal uses its verified logo when the archive has no abbreviation', () => {
    assert.equal(historicalPartyLogoAsset(null, 'same_exact_source_label', 'حزب الأمل'),
        '/assets/parties/alamal-display.png');
    assert.equal(historicalPartyLogoAsset('حزب الأمل', 'same_exact_source_label', 'حزب الأمل'),
        '/assets/parties/alamal-display.png');
    assert.equal(historicalPartyLogoAsset('ALAMAL'), '/assets/parties/alamal-display.png');
    assert.equal(historicalPartyLogoAsset(null, 'same_exact_source_label', 'حزب آخر'),
        '/assets/parties/party.svg');
});

test('the identified left alliance keeps its logo without claiming cross-election continuity', () => {
    assert.equal(historicalPartyLogoAsset('AG', null, 'تحالف اليسار'),
        '/assets/parties/fgd-official-2026.png');
    assert.equal(historicalPartyLogoAsset('AG', 'source_observation_only', 'تحالف اليسار'),
        '/assets/parties/fgd-official-2026.png');
    assert.equal(historicalPartyLogoAsset('AG', null, 'Different alliance'), '/assets/parties/party.svg');
    assert.equal(historicalPartyLogoAsset('unknown', null, 'تحالف اليسار'), '/assets/parties/party.svg');
});

test('quotient presentation hides national totals unless the full simulation validates', () => {
    assert.deepEqual(quotientViewState({ available: true }, { available: true }), {
        showNational: true,
        showConstituency: true
    });
    assert.deepEqual(quotientViewState({ available: true }, { available: false }), {
        showNational: false,
        showConstituency: true
    });
    assert.deepEqual(quotientViewState({ available: false }, { available: true }), {
        showNational: false,
        showConstituency: false
    });
});

test('demographic chart model keeps reported percentages exact and exposes unreported remainder', () => {
    const gender = demographicChartModel({
        dimension: 'gender',
        years: [2021, 2026],
        rows: [
            { categoryAr: 'رجال', points: [
                { year: 2021, percentage: 75.7, sourcePercentageText: '75.70%', sourceQueryId: 'a' },
                { year: 2026, percentage: 72.91, sourcePercentageText: '72.91%', sourceQueryId: 'b' }
            ] },
            { categoryAr: 'نساء', points: [
                { year: 2021, percentage: 24.3, sourcePercentageText: '24.30%', sourceQueryId: 'c' },
                { year: 2026, percentage: 27.09, sourcePercentageText: '27.09%', sourceQueryId: 'd' }
            ] }
        ]
    });
    assert.equal(gender.kind, 'donut');
    assert.deepEqual(gender.years[1], {
        year: 2026,
        segments: [
            { categoryAr: 'رجال', percentage: 72.91, sourcePercentageText: '72.91%', sourceQueryId: 'b', colorIndex: 0 },
            { categoryAr: 'نساء', percentage: 27.09, sourcePercentageText: '27.09%', sourceQueryId: 'd', colorIndex: 1 }
        ],
        reportedTotal: 100,
        unreportedPercentage: 0
    });

    const education = demographicChartModel({
        dimension: 'education', years: [2016], rows: [
            { categoryAr: 'ثانوي', points: [{ year: 2016, percentage: 19.49, sourcePercentageText: '19.49%', sourceQueryId: 'e' }] },
            { categoryAr: 'عالي', points: [{ year: 2016, percentage: 74.68, sourcePercentageText: '74.68%', sourceQueryId: 'f' }] }
        ]
    });
    assert.equal(education.kind, 'stacked');
    assert.equal(education.years[0].reportedTotal, 94.17);
    assert.equal(education.years[0].unreportedPercentage, 5.83);
});

test('evidence status stays an intrinsic badge instead of styling its table cell', () => {
    const badge = createEvidenceStatusBadge(fakeDocument(), 'Name match only');

    assert.equal(badge.tagName, 'SPAN');
    assert.equal(badge.className, 'history-evidence-status');
    assert.equal(badge.textContent, 'Name match only');
});

test('the simulation warning links to the full methodology on the same page', () => {
    const document = fakeDocument();
    const container = document.createElement('p');

    appendMethodologyLink(container, document, 'Full methodology');

    assert.equal(container.children.length, 2);
    assert.equal(container.children[0].textContent, ' ');
    assert.equal(container.children[1].tagName, 'A');
    assert.equal(container.children[1].href, '#historySources');
    assert.equal(container.children[1].textContent, 'Full methodology');
});

test('page script keeps archive query ids out of the UI and restores focus after paging', () => {
    const controller = fs.readFileSync(controllerPath, 'utf8');
    const page = fs.readFileSync(pagePath, 'utf8');
    assert.doesNotMatch(controller, /history-demographic-source|evidenceReference\(/);
    assert.match(controller, /segment\.sourcePercentageText/);
    assert.match(controller, /history-demographic-donut/);
    assert.match(controller, /history-demographic-stack/);
    assert.match(controller, /regionSeatBarPercent\(point\.localSeats, regionCapacity\)/);
    assert.match(controller, /value\.setAttribute\('aria-label'/);
    assert.match(controller, /heading\.append\(bdi\(group\.occurrences\[0\]\?\.nameAr/);
    assert.match(controller, /derive2026Full2016SystemCounterfactual\(payload\)/);
    assert.match(controller, /derive2026LocalSeatCounterfactual\(payload\)/);
    assert.match(controller, /historyQuotientConstituency/);
    assert.match(controller, /historyPeopleCount'\)\.focus\(\)/);
    assert.match(controller, /historyPagination.*setAttribute\('aria-label', t\('paginationLabel'\)\)/);
    assert.match(controller, /deriveRepeatedNamePartyMovements\(payload, Number\(state\.from\), Number\(state\.to\),/);
    assert.match(controller, /deriveRepeatedNameGroups\(payload, Number\(state\.from\), Number\(state\.to\), \{/);
    assert.match(controller, /historyMovementGains/);
    assert.match(controller, /historyMovementLosses/);
    assert.doesNotMatch(controller, /historyPartyNotComparable/);
    assert.doesNotMatch(page, /historyPartyNotComparable/);
    assert.match(controller, /function partyIdentity\(/);
    assert.match(controller, /history-party-logo/);
});

test('party identities use logos and official names without visible abbreviations', () => {
    const controller = fs.readFileSync(controllerPath, 'utf8');
    const css = fs.readFileSync(cssPath, 'utf8');

    assert.doesNotMatch(controller, /history-party-code/);
    assert.doesNotMatch(controller, /\$\{party\.abbreviation \|\| '—'\} · \$\{party\.nameAr\}/);
    assert.match(controller, /identity\.append\(logo,\s*bdi\(nameAr, 'history-party-name'\)\)/);
    assert.match(controller, /option\(party\.comparisonKey, party\.nameAr\)/);
    assert.doesNotMatch(css, /\.history-party-code\s*\{/);
});

test('repeated-name rows show one name with a centered party transition and constituency below', () => {
    const changed = repeatedNameTransition({ occurrences: [
        { year: 2021, nameAr: 'شخص واحد', abbreviation: 'PI',
            comparisonKey: 'exact-source-label:pi', constituencyNameAr: 'طانطان' },
        { year: 2026, nameAr: 'شخص واحد', abbreviation: 'PJD',
            comparisonKey: 'exact-source-label:pjd', constituencyNameAr: 'طانطان' }
    ] });
    assert.deepEqual(changed, {
        fromYear: 2021,
        toYear: 2026,
        fromParty: 'PI',
        toParty: 'PJD',
        fromPartyLogo: '/assets/parties/pi-display.png',
        toPartyLogo: '/assets/parties/pjd-display.png',
        sameParty: false,
        fromConstituency: 'طانطان',
        toConstituency: 'طانطان',
        sameConstituency: true
    });

    const unchanged = repeatedNameTransition({ occurrences: [
        { year: 2021, abbreviation: 'PAM', comparisonKey: 'exact-source-label:pam', constituencyNameAr: 'طانطان' },
        { year: 2026, abbreviation: 'PAM', comparisonKey: 'exact-source-label:pam', constituencyNameAr: 'طانطان' }
    ] });
    assert.equal(unchanged.sameParty, true);

    const verifiedAlliance = repeatedNameTransition({ occurrences: [
        { year: 2021, abbreviation: 'PSU', comparisonKey: 'source:2021:psu',
            canonicalComparisonKey: 'verified-alliance:left-alliance',
            canonicalAbbreviation: 'AG', continuityBasis: 'verified_alliance_composition',
            constituencyNameAr: 'الدار البيضاء-سطات' },
        { year: 2026, abbreviation: 'AG', comparisonKey: 'source:2026:ag',
            canonicalComparisonKey: 'verified-alliance:left-alliance',
            canonicalAbbreviation: 'AG', continuityBasis: 'verified_alliance_composition',
            constituencyNameAr: 'الدار البيضاء - أنفا' }
    ] });
    assert.equal(verifiedAlliance.sameParty, true);
    assert.equal(verifiedAlliance.fromParty, 'AG');
    assert.equal(verifiedAlliance.toParty, 'AG');
    assert.equal(verifiedAlliance.fromPartyLogo, '/assets/parties/fgd-official-2026.png');
    assert.equal(verifiedAlliance.toPartyLogo, '/assets/parties/fgd-official-2026.png');

    const controller = fs.readFileSync(controllerPath, 'utf8');
    const css = fs.readFileSync(cssPath, 'utf8');
    assert.doesNotMatch(controller, /partyIdentity\(item\.abbreviation, item\.nameAr/);
    assert.match(controller, /history-party-transition-arrow/);
    assert.match(css, /\.history-party-transition\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s+36px\s+minmax\(0,\s*1fr\);/);
    assert.match(css, /\.history-party-transition-arrow\s*\{[^}]*place-items:\s*center;[^}]*justify-self:\s*center;/);
});

test('visible demographic evidence wraps inside the mobile viewport', () => {
    const css = fs.readFileSync(cssPath, 'utf8');
    assert.match(css, /\.history-demographic-values > span\s*\{[^}]*min-width:\s*0;/);
    assert.doesNotMatch(css, /\.history-demographic-source\s*\{/);
    assert.match(css, /\.history-party-logo\s*\{[^}]*object-fit:\s*contain;/);
    assert.match(css, /\.history-demographic-donut\s*\{[^}]*border-radius:\s*50%;/);
    assert.match(css, /\.history-demographic-stack\s*\{[^}]*overflow:\s*hidden;/);
});

test('historical tables stay contained and the mobile jump navigation scrolls in one row', () => {
    const css = fs.readFileSync(cssPath, 'utf8');
    assert.match(css, /\.history-table-scroll\s*\{[^}]*width:\s*100%;[^}]*min-width:\s*0;[^}]*overflow-x:\s*auto;/);
    assert.match(css, /\.history-exact-figures\s*\{[^}]*min-width:\s*0;/);
    assert.match(css, /\.history-quotient-national,\s*\.history-quotient-constituency\s*\{[^}]*min-width:\s*0;/);
    assert.match(css, /@media \(max-width:\s*680px\)[\s\S]*?\.election-graphs-jumps\.history-jumps\s*\{[^}]*flex-wrap:\s*nowrap;[^}]*overflow-x:\s*auto;/);
    assert.match(css, /@media \(max-width:\s*680px\)[\s\S]*?\.election-graphs-jumps\.history-jumps a\s*\{[^}]*flex:\s*0 0 auto;/);
    assert.match(css, /\.history-movement-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/);
    assert.match(css, /@media \(max-width:\s*680px\)[\s\S]*?\.history-movement-grid\s*\{[^}]*grid-template-columns:\s*1fr;/);
});

test('dense quotient decomposition starts collapsed', () => {
    const page = fs.readFileSync(pagePath, 'utf8');
    assert.doesNotMatch(page, /<details class="history-exact-figures" open>/);
});

test('the national overview uses an accessible part-to-whole seat chart', () => {
    const controller = fs.readFileSync(controllerPath, 'utf8');
    const css = fs.readFileSync(cssPath, 'utf8');
    assert.match(controller, /history-ballot-donut/);
    assert.match(controller, /setAttribute\('role',\s*'img'\)/);
    assert.match(css, /\.history-ballot-donut\s*\{[^}]*conic-gradient/);
});
