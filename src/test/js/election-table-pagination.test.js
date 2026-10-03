const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/election-results.js'), 'utf8');
const visit = node => [node, ...node.children.flatMap(visit)];

function controller(locale) {
    const document = { activeElement: null, addEventListener() {}, createElement(tag) {
        return { tag, children: [], textContent: '', className: '', dataset: {}, attributes: {}, events: {},
            append(...children) { this.children.push(...children); },
            replaceChildren(...children) { this.children = children; },
            setAttribute(name, value) { this.attributes[name] = value; },
            addEventListener(name, callback) { this.events[name] = callback; },
            focus() { document.activeElement = this; },
            click() { if (!this.disabled) { this.focus(); this.events.click?.(); } }
        };
    } };
    const script = source.replace("document.addEventListener('DOMContentLoaded', init);", `
        locale = '${locale}'; copy = COPY[locale]; snapshot = { parties: [] };
        globalThis.figures = atlasFigures;
        globalThis.urban = selected => urbanizationRegionTable(selected, atlasCopy());
        globalThis.matrix = matrix => geographyMatrixTable(matrix, atlasCopy());`);
    const context = { document, window: { queueMicrotask(fn) { fn(); }, FhemniElectionRegionFilters: {
        SEAT_TYPES: {}, normalizeState() { return {}; }, createDeferredAction() { return {}; }
    } } };
    vm.runInNewContext(script, context);
    return context;
}

const builders = {
    figures: (c, length) => c.figures('Ballots', ['Party', 'Votes'],
        Array.from({ length }, (_, i) => [`Party ${i + 1}`, String(i + 1)]), ['Total', '276']),
    urban: (c, length) => c.urban({ name: 'PAM', code: 'PAM', constituencyRows:
        Array.from({ length }, (_, i) => ({ code: `C${i}`, name: `Constituency ${i + 1}`,
            partyLocalSeats: length - i, urbanShare: 50, allocatedSeats: 6 })) }),
    matrix: (c, length) => c.matrix({ partyCodes: ['PAM'], maxSeats: 5,
        rows: Array.from({ length }, (_, i) => ({ name: `Region ${i + 1}`, code: `R${i}`,
            cells: [{ seats: 5 }] })) })
};

for (const locale of ['ar', 'fr', 'en']) for (const [kind, build] of Object.entries(builders)) {
    test(`${locale} ${kind} table pages all rows in groups of ten with working boundaries`, () => {
        const c = controller(locale);
        const root = build(c, 23);
        const body = visit(root).find(n => n.tag === 'tbody');
        assert.equal(body.children.length, 10);
        const nav = visit(root).find(n => n.tag === 'nav');
        assert.ok(nav.attributes['aria-label']);
        const [previous, status, next] = nav.children;
        assert.equal(previous.disabled, true);
        assert.equal(next.disabled, false);
        assert.match(status.textContent, /1–10/);
        const first = body.children[0];
        next.click();
        assert.equal(body.children.length, 10);
        assert.notEqual(body.children[0], first);
        assert.match(status.textContent, /11–20/);
        next.click();
        assert.equal(body.children.length, 3);
        assert.match(status.textContent, /21–23/);
        assert.equal(next.disabled, true);
        assert.equal(c.document.activeElement, previous);
        next.click();
        assert.equal(body.children.length, 3);
        previous.click(); previous.click();
        assert.equal(body.children[0], first);
        assert.equal(previous.disabled, true);
        assert.equal(c.document.activeElement, next);
        if (kind === 'figures') assert.equal(visit(root).find(n => n.tag === 'tfoot').children[0].children[1].textContent, '276');
        // A new filter/party selection builds a new table at its first page.
        const replacement = build(c, 11);
        assert.equal(visit(replacement).find(n => n.tag === 'tbody').children.length, 10);
        assert.match(visit(replacement).find(n => n.tag === 'nav').children[1].textContent, /1–10/);
        assert.match(status.textContent, /1–10/);
    });
}

for (const [kind, build] of Object.entries(builders)) for (const length of [0, 1, 10]) {
    test(`${kind} table with ${length} rows needs no pagination controls`, () => {
        const root = build(controller('en'), length);
        assert.equal(visit(root).find(n => n.tag === 'tbody').children.length, length);
        assert.equal(visit(root).filter(n => n.tag === 'nav').length, 0);
    });
}
