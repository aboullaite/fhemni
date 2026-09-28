const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const insights = require('../../main/resources/static/js/historical-election-insights.js');
const page = require('../../main/resources/static/js/historical-elections.js');
const enrichment = require('../../main/resources/static/js/historical-affiliation-backfill.js');
const data = () => JSON.parse(fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/history.json`));
function fixture(p) {
    return { schemaVersion: 1, records: [{
        id: '2021-othmane-badel', year: 2021,
        target: { year: 2026, nameAr: 'عثمان بادل', constituencyId: '53' },
        partyId: p.partyObservations.find(r => r.year === 2021 && r.abbreviation === 'RNI').partyId,
        nameAr: 'عثمان بادل', constituencyNameAr: 'برشيد',
        observedAt: '2021-09-28', kind: 'local_office', elected: null,
        identityBasis: 'Exact full name and provincial council office, corroborated by the later candidate statement.',
        evidence: [{ url: 'https://www.berrechidnews.com/2021/09/69752.html', publisher: 'Berrechid News',
            publishedAt: '2021-09-28', claim: 'Elected provincial council president with RNI.' }]
    }] };
}

function disambiguationFixture() {
    return { schemaVersion: 1, records: [{
        id: '2021-hussein-jamal-reviewed-identity', year: 2021,
        target: { year: 2026, nameAr: 'الحسين جمال', constituencyId: '20' },
        partyId: 'party_62c8ea4bcf76', nameAr: 'الحسين جمال', constituencyNameAr: 'بني عياط، أزيلال',
        observedAt: '2021-09-27', kind: 'local_office', elected: null,
        identityBasis: 'Reviewed Bni Ayat deputy-office and 2026 Bzou-Ouawizaght candidacy bridge; the Tan-Tan name-only link is a different person.',
        evidence: [{ url: 'https://region.anwarpress.com/?p=54889', publisher: 'Anwarpress Régions',
            publishedAt: '2021-09-28', claim: 'Bni Ayat sixth deputy Hussein Jamal belongs to USFP.' }],
        supersedesNameMatch: { year: 2021, nameAr: 'الحسين جمال', constituencyId: '29',
            partyId: 'party_24f6323d3344', sourceQueryId: '2021:r10_p36_c29' }
    }] };
}

test('reviewed disambiguation replaces only the inferred match and shows USFP to PI in chart and explorer', () => {
    const p = data(), before = JSON.stringify(p), backfill = disambiguationFixture();
    const table = insights.deriveRepeatedNameGroups(p, 2021, 2026,
        {scope: 'candidates', backfill, query: 'الحسين جمال', differentPartyLabelsOnly: true});
    assert.equal(table.available, true);
    assert.equal(table.totalRows, 1);
    assert.equal(table.rows[0].occurrences.find(r => r.year === 2021).abbreviation, 'USFP');
    assert.equal(table.rows[0].occurrences.find(r => r.year === 2026).abbreviation, 'PI');
    assert.equal(table.rows[0].evidenceStatus, 'sourced_affiliation');
    assert.equal(table.rows[0].occurrences.find(r => r.year === 2021).elected, null);
    const transition = page.repeatedNameTransition(table.rows[0]);
    assert.equal(transition.fromParty, 'USFP');
    assert.equal(transition.toParty, 'PI');
    const chart = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026,
        {scope: 'candidates', backfill, limit: 100});
    assert.equal(chart.available, true);
    assert.equal(chart.gains.find(r => r.abbreviation === 'PI').count, 6);
    assert.equal(chart.gains.find(r => r.abbreviation === 'PI').evidenceCounts.sourced_affiliation, 1);
    assert.equal(chart.losses.find(r => r.abbreviation === 'USFP').count, 10);
    assert.equal(chart.losses.find(r => r.abbreviation === 'USFP').evidenceCounts.sourced_affiliation, 1);
    assert.equal(JSON.stringify(p), before);
});

test('disambiguation leaves other years, other occurrences and parliamentary-only views unchanged', () => {
    const p = data(), backfill = disambiguationFixture();
    for (const [from, to, scope] of [[2016, 2021, 'candidates'], [2016, 2026, 'candidates'],
        [2021, 2026, 'elected']]) {
        const options = {scope, limit: 100, backfill: {schemaVersion: 1, records: []}};
        assert.deepEqual(insights.deriveRepeatedNamePartyMovements(p, from, to, {...options, backfill}),
            insights.deriveRepeatedNamePartyMovements(p, from, to, options));
        assert.deepEqual(insights.deriveRepeatedNameGroups(p, from, to, {...options, backfill}),
            insights.deriveRepeatedNameGroups(p, from, to, options));
    }
    const other = p.candidateRecords.find(r => r.year === 2016);
    const older = {...other, nameAr: 'الحسين جمال', normalizedName: 'الحسين جمال'};
    const groups = [
        {normalizedName: 'الحسين جمال', evidenceStatus: 'name_match_only',
            occurrences: [older, ...p.candidateRecords.filter(r => r.nameAr === 'الحسين جمال')]},
        {normalizedName: other.normalizedName, occurrences: [other]}
    ];
    const before = JSON.stringify(groups);
    const result = enrichment.augmentGroups(p, groups, backfill, 2021, 2026, 'candidates');
    assert.deepEqual(result.diagnostics, []);
    assert.deepEqual(result.groups[0].occurrences.find(r => r.year === 2016), older);
    assert.deepEqual(result.groups[1], groups[1]);
    assert.equal(JSON.stringify(groups), before);
});

test('disambiguation rejects stale, forged, incomplete, other-target and alias replacement references', () => {
    for (const mutate of [
        r => { r.supersedesNameMatch.partyId = 'party_62c8ea4bcf76'; },
        r => { r.supersedesNameMatch.sourceQueryId = '2021:stale'; },
        r => { r.supersedesNameMatch.constituencyId = '20'; },
        r => { r.supersedesNameMatch.year = 2016; },
        r => { delete r.supersedesNameMatch.sourceQueryId; },
        r => { delete r.supersedesNameMatch.partyId; },
        r => { r.target = {year: 2026, nameAr: 'عثمان بادل', constituencyId: '53'}; },
        r => { r.kind = 'name_alias'; r.aliasOf = {year: 2021, nameAr: 'الحسين جمال', constituencyId: '29'};
            r.partyId = 'party_24f6323d3344'; },
        r => { r.evidence = []; },
        r => { r.identityBasis = ''; }
    ]) {
        const p = data(), backfill = disambiguationFixture();
        mutate(backfill.records[0]);
        // Even an unselected comparison must reject a malformed correction.
        const result = insights.deriveRepeatedNameGroups(p, 2016, 2026, {scope: 'candidates', backfill});
        assert.equal(result.available, false);
        assert.deepEqual(result.diagnostics, ['affiliation-backfill']);
    }
});

test('a disproven elected-to-elected name link is removed without treating municipal evidence as an MP result', () => {
    const p = data(), backfill = disambiguationFixture();
    const prior = p.candidateRecords.find(r => r.year === 2021 && r.nameAr === 'الحسين جمال');
    const target = p.candidateRecords.find(r => r.year === 2026 && r.nameAr === 'الحسين جمال');
    // Exercise enrichment directly: both archived namesakes were parliamentary winners.
    prior.elected = true;
    target.elected = true;
    const older = {...prior, year: 2016, sourceQueryId: '2016:other'};
    const groups = [{normalizedName: target.normalizedName, evidenceStatus: 'name_match_only',
        occurrences: [older, {...prior}, {...target}]}];
    const before = JSON.stringify({p, groups});
    const result = enrichment.augmentGroups(p, groups, backfill, 2021, 2026, 'elected');
    assert.deepEqual(result.diagnostics, []);
    assert.deepEqual(result.groups[0].occurrences, [older, target]);
    assert.equal(result.groups[0].occurrences.some(r => r.affiliationKind), false);
    assert.equal(JSON.stringify({p, groups}), before);
});

test('disambiguation fails closed on duplicate archive references, ambiguous derived matches and unflagged collisions', () => {
    for (const scenario of ['archive-duplicate', 'derived-duplicate', 'other-prior', 'missing-derived', 'unflagged']) {
        const p = data(), backfill = disambiguationFixture();
        const prior = p.candidateRecords.find(r => r.year === 2021 && r.nameAr === 'الحسين جمال');
        const target = p.candidateRecords.find(r => r.year === 2026 && r.nameAr === 'الحسين جمال');
        const groups = [{normalizedName: target.normalizedName, evidenceStatus: 'name_match_only',
            occurrences: [{...prior}, {...target}]}];
        if (scenario === 'archive-duplicate') p.candidateRecords.push({...prior});
        if (scenario === 'derived-duplicate') groups[0].occurrences.push({...prior});
        if (scenario === 'other-prior') groups[0].occurrences.push({...prior, sourceQueryId: '2021:other'});
        if (scenario === 'missing-derived') groups[0].occurrences.shift();
        if (scenario === 'unflagged') delete backfill.records[0].supersedesNameMatch;
        const before = JSON.stringify(groups);
        const result = enrichment.augmentGroups(p, groups, backfill, 2021, 2026, 'candidates');
        assert.deepEqual(result.diagnostics, ['affiliation-backfill'], scenario);
        assert.deepEqual(result.groups, []);
        assert.equal(JSON.stringify(groups), before);
    }
});

test('sourced prior affiliations reach chart and explorer without fabricating an earlier election result', () => {
    const p = data(), before = JSON.stringify(p), backfill = fixture(p);
    const result = insights.deriveRepeatedNameGroups(p, 2021, 2026,
        { scope: 'candidates', backfill, query: 'عثمان بادل', differentPartyLabelsOnly: true });
    assert.equal(result.totalRows, 1);
    const earlier = result.rows[0].occurrences.find(r => r.year === 2021);
    assert.equal(earlier.abbreviation, 'RNI');
    assert.equal(earlier.elected, null);
    assert.equal(earlier.evidence[0].url, backfill.records[0].evidence[0].url);
    const pam = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026,
        { scope: 'candidates', backfill, limit: 100 }).gains.find(r => r.abbreviation === 'PAM');
    assert.deepEqual([pam.count, pam.laterElectedCount], [10, 9]);
    assert.equal(JSON.stringify(p), before);
});

test('local-office evidence never becomes an MP elected in both years', () => {
    const p = data();
    const result = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026,
        { scope: 'elected', backfill: fixture(p), limit: 100 });
    assert.equal(result.gains.find(r => r.abbreviation === 'PAM').count, 4);
});

test('movement totals retain mixed evidence instead of relabeling sourced affiliations as name matches', () => {
    const p = data();
    const result = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026,
        { scope: 'candidates', backfill: fixture(p), limit: 100 });
    const pam = result.gains.find(r => r.abbreviation === 'PAM');
    assert.equal(result.evidenceStatus, 'mixed_evidence');
    assert.equal(pam.evidenceStatus, 'mixed_evidence');
    assert.deepEqual(pam.evidenceCounts, { name_match_only: 9, sourced_affiliation: 1 });
    assert.equal(result.evidenceCounts.sourced_affiliation, 1);
    const original = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026, { scope: 'candidates' });
    assert.equal(original.evidenceStatus, 'name_match_only');
});

test('backfill rejects unknown parties, duplicate identities, missing evidence and dates outside the stated year', () => {
    for (const mutate of [
        b => { b.records[0].partyId = 'invented'; },
        b => { b.records.push({ ...b.records[0], id: 'duplicate' }); },
        b => { b.records[0].evidence = []; },
        b => { b.records[0].observedAt = '2025-01-01'; },
        b => { b.records[0].evidence[0].url = 'javascript:alert(1)'; },
        b => { b.records[0].target.constituencyId = '9999'; }
    ]) {
        const p = data(), backfill = fixture(p); mutate(backfill);
        const result = insights.deriveRepeatedNamePartyMovements(p, 2021, 2026, { scope: 'candidates', backfill });
        assert.equal(result.available, false);
        assert.deepEqual(result.gains, []);
    }
});

test('reviewed spelling aliases retain the original elected outcome without duplicating a person', () => {
    const p = data(), prior = p.candidateRecords.find(r => r.year === 2021 && r.nameAr === 'عبد اللـه بيلات');
    const backfill = fixture(p), r = backfill.records[0];
    Object.assign(r, { id: 'bilat-alias', target: {year: 2026, nameAr: 'عبد الله بيلات', constituencyId: '25'},
        partyId: prior.partyId, nameAr: prior.nameAr, constituencyNameAr: prior.constituencyNameAr,
        kind: 'name_alias', aliasOf: { year: 2021, nameAr: prior.nameAr, constituencyId: prior.constituencyId } });
    r.target.constituencyId = p.candidateRecords.find(x => x.year === 2026 && x.nameAr === r.target.nameAr).constituencyId;
    const result = insights.deriveRepeatedNameGroups(p, 2021, 2026,
        { scope: 'elected', backfill, query: 'عبد الله بيلات' });
    assert.equal(result.totalRows, 1);
    assert.equal(result.rows[0].occurrences.find(x => x.year === 2021).elected, true);
});

test('parties with no abbreviation display their full official names rather than dashes', () => {
    const result = insights.deriveRepeatedNameGroups(data(), 2021, 2026,
        { scope: 'candidates', query: 'احسان بوشحمة' });
    const model = page.repeatedNameTransition(result.rows[0]);
    assert.equal(model.fromParty, 'حزب النهضة');
    assert.equal(model.toParty, 'حزب الحرية والعدالة الاجتماعية');
});

test('unknown parliamentary election status is never rendered as not elected', () => {
    assert.equal(page.occurrenceOutcomeKey({elected: null, affiliationKind: 'local_office'}), null);
    assert.equal(page.occurrenceOutcomeKey({elected: false}), 'candidateNotElected');
    assert.equal(page.occurrenceOutcomeKey({elected: true}), 'candidateElected');
});

test('invalid calendar dates fail closed without crashing the comparison', () => {
    const p = data(), backfill = fixture(p);
    backfill.records[0].observedAt = '2021-99-99';
    const result = insights.deriveRepeatedNameGroups(p, 2021, 2026, {scope: 'candidates', backfill});
    assert.equal(result.available, false);
});

test('explicit retrospective year evidence does not invent a historical day or backdate publication', () => {
    const p = data(), backfill = fixture(p);
    Object.assign(backfill.records[0], {kind:'retrospective_local_office',observedAt:null});
    backfill.records[0].evidence[0].publishedAt = '2026-07-13';
    const result = insights.deriveRepeatedNameGroups(p,2021,2026,{scope:'candidates',backfill,query:'عثمان بادل'});
    assert.equal(result.totalRows,1);
    const earlier = result.rows[0].occurrences.find(r => r.year===2021);
    assert.equal(earlier.observedAt,null);
    assert.equal(earlier.evidence[0].publishedAt,'2026-07-13');
});

test('the shipped enrichment is authenticated independently of any declared checksum', async () => {
    const integrity = require('../../main/resources/static/js/historical-data-integrity.js');
    const bytes = fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/affiliation-backfill.json`);
    assert.equal(typeof integrity.parseVerifiedAffiliationBackfill, 'function');
    const backfill = await integrity.parseVerifiedAffiliationBackfill(bytes);
    assert.ok(backfill.records.length > 0);
    const changed = JSON.parse(bytes);
    changed.records[0].partyId = 'invented';
    await assert.rejects(integrity.parseVerifiedAffiliationBackfill(Buffer.from(JSON.stringify(changed))), /checksum/);
    const result = insights.deriveRepeatedNamePartyMovements(data(), 2021, 2026, {scope:'candidates', backfill, limit:100});
    assert.equal(result.available, true);
    assert.ok(result.gains.find(r => r.abbreviation === 'PAM').count > 9);
});

test('year-only affiliation evidence preserves its precision without inventing a date', () => {
    const p = data(), backfill = fixture(p);
    Object.assign(backfill.records[0], { observedAt: null, datePrecision: 'year' });
    backfill.records[0].evidence[0].publishedAt = null;
    const result = insights.deriveRepeatedNameGroups(p, 2021, 2026,
        { scope: 'candidates', backfill, query: 'عثمان بادل' });
    assert.equal(result.available, true);
    assert.equal(result.rows[0].occurrences.find(r => r.year === 2021).observedAt, null);
    delete backfill.records[0].datePrecision;
    assert.equal(insights.deriveRepeatedNameGroups(p, 2021, 2026,
        { scope: 'candidates', backfill }).available, false);
});

test('enriched charts reconcile with the explorer for every election pair and scope', () => {
    const p = data(), before = JSON.stringify(p);
    const backfill = JSON.parse(fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/affiliation-backfill.json`));
    for (const [from, to] of [[2016,2021],[2016,2026],[2021,2026]]) for (const scope of ['candidates','elected']) {
        const options = {scope, backfill, limit:100, differentPartyLabelsOnly:true};
        const chart = insights.deriveRepeatedNamePartyMovements(p,from,to,options);
        const table = insights.deriveRepeatedNameGroups(p,from,to,options);
        assert.equal(chart.available,true,JSON.stringify(chart.diagnostics));
        assert.equal(table.available,true);
        assert.equal(table.totalRows,chart.totalMovements);
        assert.equal(chart.gains.reduce((sum,row)=>sum+row.count,0),chart.totalMovements);
        assert.equal(chart.losses.reduce((sum,row)=>sum+row.count,0),chart.totalMovements);
        assert.ok(chart.gains.every(row=>row.laterElectedCount<=row.count));
        assert.ok(table.rows.length<=10);
    }
    assert.equal(JSON.stringify(p),before);
});

test('reviewed municipal backfills retain source rows, attribution, year precision and identity bridges', () => {
    const backfill = JSON.parse(fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/affiliation-backfill.json`));
    for (const name of ['صابر الجبار', 'عثمان المرسي', 'رشيد القاضي', 'محسن أزمي حسني', 'صلاح الدين بلحسن']) {
        const row = backfill.records.find(r => r.year === 2021 && r.target.nameAr === name);
        assert.ok(row, name);
        assert.equal(row.elected, null);
        assert.equal(row.observedAt, null);
        assert.equal(row.datePrecision, 'year');
        const source = row.evidence.find(e => e.sourceAuthor === 'TAFRA');
        assert.ok(source, name);
        assert.equal(source.sourceLicense, 'CC BY 4.0');
        assert.equal(source.sourceSheet, 'données');
        assert.ok(Number.isInteger(source.sourceRow) && source.sourceRow > 1);
        assert.equal(source.sourceSha256, '1bf7bab06808da63545996a160e8b95c164bf76973582499e585e2ddbc99a9b9');
        assert.equal(source.publishedAt, null);
        assert.ok(row.evidence.some(e => e.publishedAt?.startsWith('2026-') && !e.url.includes('open.africa')));
    }
});

test('expanded 2021 evidence reaches current winners without inventing earlier parliamentary outcomes', () => {
    const p = data();
    const backfill = JSON.parse(fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/affiliation-backfill.json`));
    for (const [name, party] of [['عبدالله اعمارة', 'PAM'], ['هشام موجود', 'RNI'],
        ['هشام لعسل', 'USFP'], ['احمد فضلي', 'PAM'], ['زهرة برحيوي', 'PPS'],
        ['قمر عيدودي', 'MP'], ['امينة سالك', 'USFP'], ['ابتسام طارق', 'PI'], ['سناء عكى', 'PJD']]) {
        const result = insights.deriveRepeatedNameGroups(p, 2021, 2026,
            {scope: 'candidates', backfill, query: name});
        assert.equal(result.available, true, name);
        assert.equal(result.totalRows, 1, name);
        const earlier = result.rows[0].occurrences.find(r => r.year === 2021);
        assert.equal(earlier.abbreviation, party, name);
        assert.equal(earlier.elected, null, name);
        assert.equal(earlier.observedAt, null, name);
        assert.ok(earlier.evidence.some(e => e.url.includes('open.africa')), name);
        assert.ok(earlier.evidence.some(e => !e.url.includes('open.africa')), name);
        const electedOnly = insights.deriveRepeatedNameGroups(p, 2021, 2026,
            {scope: 'elected', backfill, query: name});
        assert.equal(electedOnly.totalRows, 0, name);
    }
});

test('retrospective 2021 affiliation keeps publication in 2026 and does not create a 2021 candidacy', () => {
    const p = data();
    const backfill = JSON.parse(fs.readFileSync(`${__dirname}/../../main/resources/static/data/elections/affiliation-backfill.json`));
    for (const name of ['رضوان النوينو', 'للاهدى المغاري المبرض']) {
        const row = backfill.records.find(r => r.year === 2021 && r.target.nameAr === name);
        assert.ok(row, name);
        assert.equal(row.datePrecision, 'year');
        assert.equal(row.observedAt, null);
        assert.equal(row.elected, null);
        assert.ok(row.evidence[0].publishedAt.startsWith('2026-'));
        const result = insights.deriveRepeatedNameGroups(p, 2021, 2026,
            {scope: 'candidates', backfill, query: name});
        assert.equal(result.available, true, name);
        assert.equal(result.totalRows, 1, name);
        assert.equal(result.rows[0].occurrences.find(r => r.year === 2021).elected, null);
    }
});
