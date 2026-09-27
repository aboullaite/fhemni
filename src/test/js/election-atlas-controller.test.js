const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const actualInsights = require('../../main/resources/static/js/election-insights.js');

const controllerPath = path.join(__dirname, '../../main/resources/static/js/election-results.js');
const source = fs.readFileSync(controllerPath, 'utf8');

function controller(locale, localLead, concentration, ballotSnapshot) {
    const roots = new Map();
    function visit(node) { return [node, ...node.children.flatMap(visit)]; }
    function makeNode(tag) {
        return {
            tag, className: '', dataset: {}, children: [], textContent: '',
            classList: { add(...names) { this.className += names.join(' '); } },
            append(...children) { this.children.push(...children); },
            replaceChildren(...children) { this.children = children; },
            contains(other) { return visit(this).includes(other); },
            querySelector(selector) { return visit(this).find(node => node.className.split(' ').includes(selector.slice(1))) || null; },
            querySelectorAll(selector) { return selector === '[data-atlas-key]' ? visit(this).filter(node => node.dataset.atlasKey) : []; },
            setAttribute(name, value) { if (name === 'data-atlas-key') this.dataset.atlasKey = value; },
            addEventListener() {}, focus() {}
        };
    }
    const document = { activeElement: null, addEventListener() {}, createElement: makeNode,
        getElementById(id) { if (!roots.has(id)) roots.set(id, makeNode('div')); return roots.get(id); } };
    const insights = {
        deriveBallotComponents(input, options) { return ballotSnapshot
            ? actualInsights.deriveBallotComponents(input, options)
            : { available: true, rows: [], localTotal: 4900377,
            regionalTotal: 4838149, combinedTotal: 9738526,
            reversal: { rniLocalLead: localLead, pjdRegionalLead: 10, pjdCombinedLead: 20 } }; },
        deriveBallotSeatComparison() { return { available: true, rows: [], combinedTotal: 9738526, seatTotal: 395 }; },
        deriveConcentration() { return concentration; }
    };
    const script = source.replace("document.addEventListener('DOMContentLoaded', init);",
        `locale = '${locale}'; snapshot = globalThis.__fixtureSnapshot || { election: { status: 'FINAL' }, parties: [] }; globalThis.__renderBallots = renderBallotComponents; globalThis.__renderRepresentation = renderBallotSeatComparison;`);
    const sandbox = { document, HTMLInputElement: class {}, __fixtureSnapshot: ballotSnapshot, window: {
        queueMicrotask(callback) { callback(); }, FhemniElectionInsights: insights,
        FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; }, createDeferredAction() { return {}; } }
    } };
    vm.runInNewContext(script, sandbox);
    function takeaway(id) {
        return visit(roots.get(id)).find(node => node.className === 'election-atlas-takeaway')?.textContent;
    }
    return { sandbox, takeaway };
}

function correctedSnapshot(pjdRegional) {
    const codes = ['PAM', 'PI', 'PJD', 'RNI', 'MP', 'USFP', 'PPS', 'UC', 'MDS', 'FGD',
        'FFD', 'ND', 'PE', 'PML', 'PUD', 'PEDD', 'UMD', 'PDN', 'PLJS', 'ALAMAL',
        'PCI', 'PVM', 'PCS', 'PA', 'ANNAHDA', 'PSD', 'PRD', 'IND'];
    const parties = codes.map(code => ({ code, name: code, localVotes: 0, regionalVotes: 0, votes: 0 }));
    const byCode = new Map(parties.map(party => [party.code, party]));
    byCode.get('RNI').localVotes = 696036;
    byCode.get('RNI').regionalVotes = 640468;
    byCode.get('PJD').localVotes = 696031;
    byCode.get('PJD').regionalVotes = pjdRegional;
    byCode.get('PAM').localVotes = 4900377 - 696036 - 696031;
    byCode.get('PAM').regionalVotes = 4838149 - 640468 - pjdRegional;
    for (const party of parties) party.votes = party.localVotes + party.regionalVotes;
    return { election: { status: 'CORRECTED' }, parties };
}

const groups = { available: true,
    topFour: { counts: { ballots: 100, seats: 30 }, ballotShare: 25.25, seatShare: 30.5 },
    topTen: { counts: { ballots: 200, seats: 35 }, ballotShare: 60.1, seatShare: 75.75 },
    remaining: { counts: { ballots: 50, seats: 5 }, ballotShare: 39.9, seatShare: 24.25 },
    zeroSeat: { counts: { ballots: 10, seats: 0 }, ballotShare: 2.5, seatShare: 0 } };

for (const [locale, expected] of Object.entries({
    en: [/RNI leads PJD by 5 local ballots/, /PJD leads RNI by 7 local ballots/, /RNI and PJD are tied locally/],
    fr: [/RNI devance le PJD de 5 voix locales/, /PJD devance le RNI de 7 voix locales/, /RNI et le PJD sont à égalité/],
    ar: [/RNI.*PJD.*5/, /PJD.*RNI.*7/, /RNI.*PJD.*تعادل/]
})) {
    test(`${locale} local ballot takeaway follows positive, negative, and tied leads`, () => {
        for (const [index, lead] of [5, -7, 0].entries()) {
            const view = controller(locale, lead, groups);
            view.sandbox.__renderBallots();
            const text = view.takeaway('electionGraphBallotsContent');
            assert.match(text, expected[index]);
            assert.doesNotMatch(text, /-7/);
        }
    });
}

for (const [locale, expected] of Object.entries({
    en: [
        ['RNI leads PJD by 7 regional ballots', 'RNI leads PJD by 12 combined ballots'],
        ['RNI and PJD are tied regionally', 'RNI leads PJD by 5 combined ballots'],
        ['PJD leads RNI by 5 regional ballots', 'RNI and PJD are tied overall'],
        ['PJD leads RNI by 10 regional ballots', 'PJD leads RNI by 5 combined ballots']
    ],
    fr: [
        ['Le RNI devance le PJD de 7 voix régionales', 'Le RNI devance le PJD de 12 voix au total'],
        ['Le RNI et le PJD sont à égalité régionalement', 'Le RNI devance le PJD de 5 voix au total'],
        ['Le PJD devance le RNI de 5 voix régionales', 'Le RNI et le PJD sont à égalité au total'],
        ['Le PJD devance le RNI de 10 voix régionales', 'Le PJD devance le RNI de 5 voix au total']
    ],
    ar: [
        ['تقدم RNI جهوياً على PJD بـ7 صوت', 'تقدم RNI فالمجموع على PJD بـ12 صوت'],
        ['RNI وPJD عندهم تعادل جهوياً', 'تقدم RNI فالمجموع على PJD بـ5 صوت'],
        ['تقدم PJD جهوياً على RNI بـ5 صوت', 'RNI وPJD عندهم تعادل فالمجموع'],
        ['تقدم PJD جهوياً على RNI بـ10 صوت', 'تقدم PJD فالمجموع على RNI بـ5 صوت']
    ]
})) {
    test(`${locale} corrected ballot snapshot follows regional and combined reversals or ties`, () => {
        for (const [index, pjdRegional] of [640461, 640468, 640473, 640478].entries()) {
            const input = correctedSnapshot(pjdRegional);
            assert.equal(actualInsights.auditSnapshot(input).available, true);
            const view = controller(locale, 5, groups, input);
            view.sandbox.__renderBallots();
            const text = view.takeaway('electionGraphBallotsContent');
            for (const phrase of expected[index]) assert.ok(text.includes(phrase), `${phrase} missing from ${text}`);
            assert.doesNotMatch(text, /\b-\d/);
        }
    });
}

for (const [locale, values] of Object.entries({
    en: ['25.25%', '30.50%', '60.10%', '75.75%', '39.90%', '24.25%', '2.50%', '0.00%'],
    fr: ['25,25%', '30,50%', '60,10%', '75,75%', '39,90%', '24,25%', '2,50%', '0,00%'],
    ar: ['25,25', '30,50', '60,10', '75,75', '39,90', '24,25', '2,50', '0,00']
})) {
    test(`${locale} concentration takeaway includes ballot and seat shares for all four groups`, () => {
        const view = controller(locale, 5, groups);
        view.sandbox.__renderRepresentation();
        const text = view.takeaway('electionGraphRepresentationContent');
        for (const value of values) assert.ok(text.includes(value), `${value} missing from ${text}`);
    });
}
