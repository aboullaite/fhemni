const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const controllerPath = path.join(__dirname, '../../main/resources/static/js/election-results.js');
const pagePath = path.join(__dirname, '../../main/resources/static/election-results.html');
const source = fs.readFileSync(controllerPath, 'utf8');
const page = fs.readFileSync(pagePath, 'utf8');

function view(locale) {
    const roots = new Map();
    function visit(node) { return [node, ...node.children.flatMap(visit)]; }
    function makeNode(tag) {
        return {
            tag, className: '', dataset: {}, children: [], textContent: '', style: {}, value: '',
            classList: { add(...names) { this.className += ` ${names.join(' ')}`; } },
            append(...children) { this.children.push(...children); },
            replaceChildren(...children) { this.children = children; },
            contains(other) { return visit(this).includes(other); },
            querySelector(selector) {
                return visit(this).find(node => node.className.split(' ').includes(selector.slice(1))) || null;
            },
            querySelectorAll(selector) {
                return selector === '[data-atlas-key]' ? visit(this).filter(node => node.dataset.atlasKey) : [];
            },
            setAttribute(name, value) { if (name === 'data-atlas-key') this.dataset.atlasKey = value; },
            addEventListener() {}, focus() {}
        };
    }
    const document = {
        activeElement: null,
        addEventListener() {},
        createElement: makeNode,
        getElementById(id) { if (!roots.has(id)) roots.set(id, makeNode('div')); return roots.get(id); }
    };
    const result = {
        available: true,
        diagnostics: [],
        source: { datasetVersion: '1.8.0', censusYear: 2024, sourceUrl: 'https://communes.pages.dev/data/v1/sources.json' },
        national: { totalPopulation: 36828330, urbanPopulation: 23110108, ruralPopulation: 13718222,
            urbanShare: 62.75089856, ruralShare: 37.24910144 },
        rows: [
            { code: 'PAM', name: 'PAM', totalSeats: 97, urbanizationIndex: 66.25,
                ruralityIndex: 33.75, differenceFromNational: 3.49910144,
                regionRows: [
                    { code: 'casablanca-settat', name: 'Casablanca-Settat', totalPopulation: 7688967,
                        urbanPopulation: 5633748, ruralPopulation: 2055219, urbanShare: 73.27,
                        ruralShare: 26.73, partySeats: 14, delegationSeats: 65 }
                ] },
            { code: 'PUD', name: 'PUD', totalSeats: 1, urbanizationIndex: 36.71,
                ruralityIndex: 63.29, differenceFromNational: -26.04089856,
                regionRows: [
                    { code: 'draa-tafilalet', name: 'Drâa-Tafilalet', totalPopulation: 1655623,
                        urbanPopulation: 607724, ruralPopulation: 1047899, urbanShare: 36.71,
                        ruralShare: 63.29, partySeats: 1, delegationSeats: 24 }
                ] }
        ]
    };
    const script = source.replace("document.addEventListener('DOMContentLoaded', init);",
        `locale = '${locale}'; copy = COPY[locale]; snapshot = { election: { status: 'FINAL' }, parties: [] }; globalThis.__renderUrbanization = renderUrbanizationRepresentation;`);
    const sandbox = {
        document,
        HTMLInputElement: class {},
        window: {
            queueMicrotask(callback) { callback(); },
            FhemniElectionRegionDemographics: {},
            FhemniElectionInsights: { deriveUrbanizationRepresentation() { return result; } },
            FhemniElectionRegionFilters: {
                SEAT_TYPES: {}, normalizeState() { return {}; }, createDeferredAction() { return {}; }
            }
        }
    };
    vm.runInNewContext(script, sandbox);
    sandbox.__renderUrbanization();
    return {
        nodes(className) {
            return visit(roots.get('electionGraphUrbanizationContent'))
                .filter(node => node.className.split(' ').includes(className));
        }
    };
}

test('the graphs page exposes a dedicated urban and rural context section', () => {
    assert.match(page, /id="electionGraphsJumpUrbanization"/);
    assert.match(page, /id="electionGraphUrbanization"/);
    assert.match(page, /urban-rural-regions\.js\?v=/);
});

for (const [locale, disclaimer] of Object.entries({
    ar: 'ما كيقيسش أصوات المدن ولا القرى',
    fr: 'ne mesure pas les votes urbains ou ruraux',
    en: 'does not measure urban or rural votes'
})) {
    test(`${locale} urbanization graph labels the measure and its vote limitation`, () => {
        const rendered = view(locale);
        assert.equal(rendered.nodes('election-atlas-urbanization-row').length, 2);
        assert.equal(rendered.nodes('election-atlas-urbanization-marker').length, 2);
        assert.equal(rendered.nodes('election-atlas-urbanization-baseline').length, 2);
        assert.match(rendered.nodes('election-atlas-intro')[0].textContent, new RegExp(disclaimer, 'i'));
        assert.equal(rendered.nodes('election-atlas-urbanization-source')[0].children[0].href,
            'https://communes.pages.dev/data/v1/sources.json');
        const seatLabels = rendered.nodes('election-atlas-urbanization-seats').map(node => node.textContent);
        assert.equal(seatLabels[1], { ar: 'مقعد واحد', fr: '1 siège', en: '1 seat' }[locale]);
    });
}
