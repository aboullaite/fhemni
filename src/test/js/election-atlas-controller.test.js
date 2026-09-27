const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const controllerPath = path.join(__dirname, '../../main/resources/static/js/election-results.js');
const source = fs.readFileSync(controllerPath, 'utf8');

function controller(locale, localLead, concentration) {
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
        deriveBallotComponents() { return { available: true, rows: [], localTotal: 4900377,
            regionalTotal: 4838149, combinedTotal: 9738526,
            reversal: { rniLocalLead: localLead, pjdRegionalLead: 10, pjdCombinedLead: 20 } }; },
        deriveBallotSeatComparison() { return { available: true, rows: [], combinedTotal: 9738526, seatTotal: 395 }; },
        deriveConcentration() { return concentration; }
    };
    const script = source.replace("document.addEventListener('DOMContentLoaded', init);",
        `locale = '${locale}'; snapshot = { election: { status: 'FINAL' }, parties: [] }; globalThis.__renderBallots = renderBallotComponents; globalThis.__renderRepresentation = renderBallotSeatComparison;`);
    const sandbox = { document, HTMLInputElement: class {}, window: {
        queueMicrotask(callback) { callback(); }, FhemniElectionInsights: insights,
        FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; }, createDeferredAction() { return {}; } }
    } };
    vm.runInNewContext(script, sandbox);
    function takeaway(id) {
        return visit(roots.get(id)).find(node => node.className === 'election-atlas-takeaway')?.textContent;
    }
    return { sandbox, takeaway };
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
