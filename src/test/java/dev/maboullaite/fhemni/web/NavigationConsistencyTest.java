package dev.maboullaite.fhemni.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class NavigationConsistencyTest {

    private static final List<String> ADMIN_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html");

    private static final List<String> SECONDARY_PAGES = List.of(
            "404.html",
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html",
            "community.html",
            "compare-programmes.html",
            "election-results.html",
            "methodology.html",
            "parties.html",
            "party.html",
            "person.html",
            "promise.html",
            "priorities.html",
            "video.html",
            "videos.html");

    private static final List<String> ALL_PAGES = List.of(
            "404.html",
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html",
            "analysis.html",
            "community.html",
            "compare-programmes.html",
            "election-results.html",
            "index.html",
            "login.html",
            "methodology.html",
            "parties.html",
            "party.html",
            "person.html",
            "promise.html",
            "priorities.html",
            "video.html",
            "videos.html");

    @Test
    void everyPrimaryNavigationUsesTheCanonicalLinks() throws IOException {
        assertCanonicalPrimaryNavigation("index.html");
        for (String page : SECONDARY_PAGES) {
            assertCanonicalPrimaryNavigation(page);
        }
    }

    @Test
    void everyPrimaryNavigationLinksToElectionResults() throws IOException {
        assertThat(html("index.html"))
                .contains("href=\"/elections/2026\" data-i18n=\"common.electionResults\"")
                .contains("class=\"primary-button button-link\" href=\"/elections/2026\"")
                .contains("data-i18n=\"landing.electionResultsCta\"")
                .contains("/js/i18n.js?v=20260923-1");
        for (String page : SECONDARY_PAGES) {
            assertThat(html(page))
                    .as("election result navigation in %s", page)
                    .contains("href=\"/elections/2026\"")
                    .contains("data-i18n=\"common.electionResults\"");
        }
    }

    @Test
    void electionResultPageProvidesAccessibleDataDrivenViews() throws IOException {
        assertThat(html("election-results.html"))
                .contains("role=\"tablist\"")
                .contains("id=\"electionMapPanel\"")
                .contains("id=\"electionNationalPanel\"")
                .contains("id=\"electionNationalVoteTotal\"")
                .contains("id=\"electionCoalitionPanel\"")
                .contains("id=\"electionRegionSelect\"")
                .contains("id=\"electionRegionFilterStatus\"")
                .contains("role=\"status\" aria-live=\"polite\" aria-atomic=\"true\"")
                .contains("/js/election-region-filters.js?v=20260925-1")
                .contains("/js/election-results.js?v=20260927-3")
                .doesNotContain("style=\"");
        assertThat(html("js/election-region-filters.js"))
                .contains("function filterRegion(region, state = {})")
                .contains("missingNameCount");
        assertThat(html("js/election-results.js"))
                .contains("/api/catalog/elections/2026/results")
                .contains("/api/catalog/elections/2026/coalitions/evaluate")
                .contains("/assets/maps/morocco-regions-2026.svg")
                .contains("election.updatedAt || election.sourceUpdatedAt")
                .contains("minimumFractionDigits: 2, maximumFractionDigits: 2")
                .contains("document.addEventListener('fhemni:localechange', handleLocaleChange)")
                .contains("load({ fresh: true })")
                .contains("load({ fresh: true });\n        scheduleCoalitionEvaluation();")
                .contains("cache: options.fresh ? 'no-store' : 'default'")
                .contains("const controller = new AbortController();")
                .contains("requestId !== coalitionRequest || controller.signal.aborted")
                .contains("signal: controller.signal")
                .contains("}, REQUEST_TIMEOUT_MS);")
                .contains("const previousSelection = selectedPartyCodes;")
                .contains("if (selectionChanged) scheduleCoalitionEvaluation();")
                .contains("coalitionRequest++;\n        coalitionAbortController?.abort();")
                .contains("const MAX_COALITION_PARTIES = 5;")
                .contains("function leadingPartyCode()")
                .contains("button.classList.toggle('is-locked', locked)")
                .contains("element('span', 'sr-only', `${copy.winner}: `)")
                .contains("element('span', 'sr-only', `${copy.constituency}: `)")
                .contains("filterWinnerOne: 'منتخب'")
                .contains("filterWinnerMany: 'منتخبين'")
                .contains("event.key === 'Enter' || event.key === ' '")
                .contains("election-seat-count-number")
                .contains("election-seat-count-label")
                .contains("function flushDeferredRegionRender()")
                .contains("chips.setAttribute('role', 'group')")
                .contains("announceRegionFilterStatus(constituencyWasCleared ? copy.constituencyCleared : '', regionFilterSummary(result))")
                .contains("voteSummary:")
                .contains("local and regional-list ballots, not unique voters")
                .contains("bulletSeparator")
                .doesNotContain(".style.");
        assertThat(html("assets/maps/morocco-regions-2026.svg"))
                .contains("data-region-key=\"MA-01\"")
                .contains("data-region-key=\"MA-12\"");
    }

    @Test
    void electionAtlasHasOneAccessibleTabAndUsesTheExistingResultLifecycle() throws IOException {
        String page = html("election-results.html");
        String controller = html("js/election-results.js");
        assertThat(page)
                .containsPattern("(?s)id=\"electionNationalTab\".*id=\"electionGraphsTab\".*id=\"electionCoalitionTab\"")
                .contains("id=\"electionGraphsTab\" type=\"button\" role=\"tab\" aria-controls=\"electionGraphsPanel\"")
                .contains("id=\"electionGraphsPanel\" class=\"election-panel")
                .contains("role=\"tabpanel\" aria-labelledby=\"electionGraphsTab\"")
                .contains("href=\"#electionGraphBallots\"")
                .contains("href=\"#electionGraphGeography\"")
                .contains("href=\"#electionGraphConstituencies\"")
                .contains("href=\"#electionGraphRepresentatives\"")
                .contains("id=\"electionGraphBallots\"")
                .contains("id=\"electionGraphRepresentation\"")
                .contains("id=\"electionGraphGeography\"")
                .contains("id=\"electionGraphConstituencies\"")
                .contains("id=\"electionGraphRepresentatives\"")
                .contains("id=\"electionAtlasStatus\" class=\"sr-only\" role=\"status\" aria-live=\"polite\"")
                .contains("class=\"election-source-note\"")
                .containsPattern("(?s)/js/election-insights\\.js\\?v=[^\"]+\" defer></script><script src=\"/js/election-results\\.js\\?v=[^\"]+\"")
                .doesNotContain("style=\"")
                .doesNotContain(" onclick=", " onkeydown=", " onchange=");
        assertThat(controller)
                .contains("['map', 'national', 'graphs', 'coalition']")
                .contains("function renderAtlas(options = {})")
                .contains("renderNational(); renderAtlas(options); renderCoalitionParties()")
                .contains("const atlasState = {")
                .contains("event.key === 'ArrowLeft'", "event.key === 'ArrowRight'", "event.key === 'Home'", "event.key === 'End'")
                .contains("tabindex', selected ? '0' : '-1'")
                .contains("election_results_tab_selected")
                .contains("load({ fresh: true })")
                .contains("title: 'النتيجة النهائية بالأرقام'")
                .contains("title: 'Le résultat final, expliqué'")
                .contains("title: 'The final result, explained'")
                .contains("constituencySeats: null")
                .contains("seatType: 'all', page: 1")
                .contains("ATLAS_SECTION_IDS.has(requested)")
                .doesNotContain("setInterval(");
        assertThat(page.split("id=\"electionAtlasStatus\"", -1)).hasSize(2);
        assertThat(controller.split("/api/catalog/elections/2026/results", -1)).hasSize(2);
        assertThat(controller.split("function schedulePoll\\(", -1)).hasSize(2);
        assertThat(controller.split("\\['map', 'national', 'graphs', 'coalition'\\]", -1)).hasSize(3);
    }

    @Test
    void electionAtlasFitsFourTabsAndKeepsMobileFiguresReachable() throws IOException {
        String styles = html("css/app.css");
        String compiled = html("css/dist.css");
        String page = html("election-results.html");

        assertThat(styles)
                .contains(".election-tabs { display: grid; grid-template-columns: repeat(4,minmax(0,1fr))")
                .contains(".election-graphs-jumps")
                .contains(".election-graphs-panel, .election-graph-section")
                .contains(".election-atlas-table-scroll { min-width: 0; max-width: 100%; overflow-x: auto;")
                .contains(".election-atlas-representative-cards { display: grid;")
                .contains(".election-atlas-control:focus-visible")
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains("[dir=\"rtl\"] .election-graphs-jumps");
        assertThat(styles).containsPattern("(?s)@media \\(max-width: 640px\\).*?\\.election-tabs \\{[^}]*overflow-x: auto;[^}]*grid-template-columns: repeat\\(4,minmax\\(118px,1fr\\)\\)");
        assertThat(compiled).contains(".election-graphs-jumps").contains(".election-atlas-representative-cards");
        assertThat(page).contains("/css/dist.css?v=20260927-2");
    }

    @Test
    void electionAtlasKeepsExactTextAndDecorativeChartsAccessible() throws IOException {
        String page = html("election-results.html");
        String controller = html("js/election-results.js");

        assertThat(page)
                .contains("id=\"electionAtlasStatus\" class=\"sr-only\" role=\"status\"")
                .contains("class=\"election-source-note\"")
                .doesNotContain("style=\"", " onclick=", " onkeydown=", " onchange=")
                .doesNotContain("https://cdn", "<script src=\"http");
        assertThat(controller)
                .contains("track.setAttribute('aria-hidden', 'true')")
                .contains("bar.setAttribute('aria-hidden', 'true')")
                .contains("element('bdi', '', record.candidateName)")
                .contains("element('bdi', '', value)")
                .contains("element('bdi', '', code)")
                .contains("const atlasState = {")
                .contains("renderNational(); renderAtlas(options); renderCoalitionParties()")
                .contains("load({ fresh: true })")
                .doesNotContain("setInterval(");
    }

    @Test
    void electionResultTabGroupUsesSelectedLanguage() throws Exception {
        String controllerPath = new ClassPathResource("static/js/election-results.js").getFile().getAbsolutePath();
        String harness = """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const script = fs.readFileSync(process.argv[1], 'utf8').replace(
                    "document.addEventListener('DOMContentLoaded', init);",
                    "globalThis.__test = { applyCopy, use(next) { locale = next; copy = COPY[next]; } };"
                );
                const nodes = new Map();
                const node = id => {
                    if (!nodes.has(id)) nodes.set(id, {
                        attrs: {}, setAttribute(name, value) { this.attrs[name] = value; }
                    });
                    return nodes.get(id);
                };
                const document = { documentElement: {}, getElementById: node, addEventListener() {} };
                const window = { FhemniElectionRegionFilters: {
                    SEAT_TYPES: {}, normalizeState() { return {}; }, createDeferredAction() { return {}; }
                } };
                const sandbox = { document, window };
                vm.runInNewContext(script, sandbox);
                for (const [locale, expected, direction] of [
                    ['ar', 'طرق عرض نتائج الانتخابات', 'rtl'],
                    ['fr', 'Vues des résultats électoraux', 'ltr'],
                    ['en', 'Election result views', 'ltr']
                ]) {
                    sandbox.__test.use(locale);
                    sandbox.__test.applyCopy();
                    assert.equal(node('electionTabs').attrs['aria-label'], expected);
                    assert.equal(document.documentElement.dir, direction);
                }
                """;
        Process process = new ProcessBuilder("node", "-e", harness, controllerPath)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        assertThat(html("election-results.html"))
                .contains("class=\"election-tabs\" role=\"tablist\" aria-label=\"طرق عرض نتائج الانتخابات\"");
    }

    @Test
    void electionAtlasSectionDeepLinksScrollOnceAfterContentBecomesVisible() throws Exception {
        String controllerPath = new ClassPathResource("static/js/election-results.js").getFile().getAbsolutePath();
        String harness = """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const script = fs.readFileSync(process.argv[1], 'utf8').replace(
                    "document.addEventListener('DOMContentLoaded', init);",
                    "globalThis.__test = { bindTabs, showState };"
                );
                function visit(hash) {
                    const scrolled = [];
                    const elements = new Map();
                    const node = id => {
                        if (!elements.has(id)) elements.set(id, {
                            id, hidden: true, attrs: {}, listeners: {},
                            setAttribute(key, value) { this.attrs[key] = value; },
                            addEventListener(type, listener) { this.listeners[type] = listener; },
                            scrollIntoView() { scrolled.push(id); }
                        });
                        return elements.get(id);
                    };
                    const tabs = ['map', 'national', 'graphs', 'coalition'].map(name => {
                        const button = node(`election${name[0].toUpperCase()}${name.slice(1)}Tab`);
                        button.dataset = { electionTab: name };
                        return button;
                    });
                    node('electionTabs').querySelectorAll = () => tabs;
                    const location = { hash, pathname: '/elections/2026', search: '' };
                    const sandbox = {
                        window: {
                            location,
                            history: { replaceState(_state, _title, url) { location.hash = url.slice(url.indexOf('#')); } },
                            queueMicrotask(callback) { callback(); },
                            FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; }, createDeferredAction() { return {}; } }
                        },
                        document: {
                            documentElement: { dir: 'ltr' },
                            addEventListener() {},
                            getElementById: node
                        }
                    };
                    vm.runInNewContext(script, sandbox);
                    sandbox.__test.bindTabs();
                    return { node, location, scrolled, show: sandbox.__test.showState };
                }
                for (const id of [
                    'electionGraphBallots', 'electionGraphRepresentation', 'electionGraphGeography',
                    'electionGraphConstituencies', 'electionGraphRepresentatives'
                ]) {
                    const page = visit(`#${id}`);
                    assert.equal(page.node('electionGraphsPanel').hidden, false);
                    assert.equal(page.node('electionContent').hidden, true);
                    assert.deepEqual(page.scrolled, []);
                    page.show('content');
                    assert.deepEqual(page.scrolled, [id], `must scroll to ${id} after reveal`);
                    page.show('content');
                    assert.deepEqual(page.scrolled, [id], `must not scroll twice to ${id}`);
                    assert.equal(page.location.hash, `#${id}`);
                }
                const invalid = visit('#electionGraphWhatever');
                invalid.show('content');
                assert.equal(invalid.node('electionMapPanel').hidden, false);
                assert.equal(invalid.node('electionGraphsPanel').hidden, true);
                assert.equal(invalid.location.hash, '#map');
                assert.deepEqual(invalid.scrolled, []);
                const graphs = visit('#graphs');
                graphs.show('content');
                assert.equal(graphs.node('electionGraphsPanel').hidden, false);
                assert.equal(graphs.location.hash, '#graphs');
                assert.deepEqual(graphs.scrolled, []);
                const switched = visit('#electionGraphBallots');
                switched.node('electionNationalTab').listeners.click();
                switched.show('content');
                assert.deepEqual(switched.scrolled, []);
                switched.node('electionGraphsTab').listeners.click();
                switched.show('content');
                assert.deepEqual(switched.scrolled, [], 'a user tab change cancels the old deep-link scroll');
                """;
        Process process = new ProcessBuilder("node", "-e", harness, controllerPath)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    @Test
    void electionAtlasBallotSectionsExposeLabeledControlsAndExactFigures() throws IOException {
        String controller = html("js/election-results.js");
        String css = html("css/app.css");
        assertThat(controller)
                .contains("function renderBallotComponents(")
                .contains("function renderBallotSeatComparison(")
                .contains("FhemniElectionInsights.deriveBallotComponents(")
                .contains("FhemniElectionInsights.deriveBallotSeatComparison(")
                .contains("FhemniElectionInsights.deriveConcentration(")
                .contains("data-atlas-key")
                .contains("atlas.ballotsDenominator", "atlas.representationDenominator")
                .contains("atlas.ballotCaption", "atlas.representationCaption")
                .contains("election-atlas-comparison-bar")
                .contains("element('tfoot'")
                .contains("widthClass(")
                .contains("element('label'")
                .contains("function announceAtlasControls(")
                .contains("function announceAtlasStatus(")
                .contains("atlasStatusAnnouncer.announce(message, { repeat: true })")
                .contains("if (options.poll && changed) announceAtlasPollUpdate();")
                .doesNotContain("setText('electionAtlasStatus'")
                .contains("controlChange: action")
                .contains("ballotsIntro: 'مقارنة الأصوات لدوائر المحلية واللوائح الجهوية لكل حزب أو لائحة، من مجموع 9.738.526 صوت محلي وجهوي.'")
                .contains("representationIntro: 'حصة الأصوات من 9.738.526 صوت محلي وجهوي؛ حصة المقاعد من 395 مقعد.'")
                .contains("ballotsIntro: 'Comparaison des voix des circonscriptions locales et des listes régionales pour chaque parti ou liste, sur un total de 9 738 526 voix locales et régionales.'")
                .contains("representationIntro: 'Part des voix sur 9 738 526 voix locales et régionales ; part des sièges sur 395 sièges.'")
                .contains("ballotsIntro: 'Compare local-constituency and regional-list votes for each party or list, out of 9,738,526 local and regional ballots.'")
                .contains("representationIntro: 'Vote share out of 9,738,526 local and regional ballots; seat share out of 395 seats.'")
                .contains("ballotsDenominator: ''", "representationDenominator: ''")
                .doesNotContain(".style.width =");
        assertThat(css)
                .contains(".election-atlas-ballot-bar")
                .contains(".election-atlas-exact-value { display: inline-flex; align-items: baseline; gap: 5px; font-size: 11px; font-weight: 400;")
                .contains(".election-atlas-value-separator { color: var(--muted); font-size: 14px;")
                .contains(".election-atlas-compare-marker")
                .contains(".election-atlas-comparison-bar")
                .contains(".election-atlas-figures")
                .contains("inset-inline-start")
                .doesNotContain(".election-atlas-ballot-bar { width:");
    }

    @Test
    void electionAtlasGeographyOffersCompleteLabeledViewsAndRepresentativeHandoff() throws IOException {
        String controller = html("js/election-results.js");
        String css = html("css/app.css");
        assertThat(controller)
                .contains("function renderGeography(")
                .contains("FhemniElectionInsights.deriveRegionDelegation(")
                .contains("FhemniElectionInsights.derivePartyGeography(")
                .contains("FhemniElectionInsights.buildRegionMatrix(")
                .contains("geographyByRegion", "geographyByParty", "geographyAllFigures")
                .contains("atlas.geographySelectRegion", "atlas.geographySelectParty")
                .contains("data-atlas-key", "geography-region", "geography-party")
                .contains("atlas.geographyRegionDenominator", "atlas.geographyPartyDenominator")
                .contains("atlas.geographyMatrixCaption")
                .contains("atlas.geographySeeRepresentatives")
                .contains("atlasState.representatives.regionCode", "atlasState.representatives.partyCode")
                .contains("atlasState.representatives.page = 1")
                .contains("electionGraphRepresentativesTitle")
                .contains("scope = 'col'", "scope = 'row'")
                .contains("election-atlas-matrix-level-")
                .doesNotContain(".style.backgroundColor =");
        assertThat(css)
                .contains(".election-atlas-geography")
                .contains(".election-atlas-matrix-level-0")
                .contains(".election-atlas-matrix-level-4")
                .contains("overflow-x: auto");
    }

    @Test
    void electionAtlasOffersExactConstituencyFiguresAndACompleteRepresentativeDirectory() throws IOException {
        String controller = html("js/election-results.js");
        assertThat(controller)
                .contains("FhemniElectionInsights.deriveConstituencyDistribution(snapshot)")
                .contains("FhemniElectionInsights.indexRepresentatives(snapshot")
                .contains("function renderConstituencies(")
                .contains("function renderRepresentativeHandoff(")
                .contains("election-atlas-constituency-figures")
                .contains("election-atlas-constituency-bin")
                .contains("atlas.constituencyFigureSeats", "atlas.constituencyFigureCount")
                .contains("representatives-search", "representatives-region", "representatives-constituency")
                .contains("representatives-party", "representatives-seat-type")
                .contains("election-atlas-representative-count", "election-atlas-representative-chips")
                .contains("representatives-reset", "representatives-load-more")
                .contains("election-atlas-representative-table", "election-atlas-representative-card")
                .contains("atlas.representativesNotPublished")
                .contains("atlas.representativesPage");
    }

    @Test
    void electionAtlasDirectoryKeepsFocusAfterChipResetAndFinalPageActions() throws Exception {
        String controllerPath = new ClassPathResource("static/js/election-results.js").getFile().getAbsolutePath();
        String harness = """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const script = fs.readFileSync(process.argv[1], 'utf8').replace(
                    "document.addEventListener('DOMContentLoaded', init);",
                    "locale = 'en'; snapshot = { regions: [{ code: 'R01', name: 'Region' }], parties: [{ code: 'PAM', name: 'Party', totalSeats: 26 }] }; globalThis.__renderDirectory = renderRepresentativeHandoff; globalThis.__renderConstituencies = renderConstituencies; globalThis.__atlasState = atlasState;"
                );
                let document;
                function visit(node) { return [node, ...node.children.flatMap(visit)]; }
                function makeNode(tag) {
                    const node = { tag, className: '', dataset: {}, children: [], listeners: {}, textContent: '',
                        classList: { add(...names) { node.className += ` ${names.join(' ')}`; } },
                        append(...children) { this.children.push(...children); },
                        replaceChildren(...children) { this.children = children; },
                        contains(target) { return visit(this).includes(target); },
                        querySelector(selector) { return visit(this).find(item => item.className.includes(selector.slice(1))) || null; },
                        querySelectorAll(selector) { return visit(this).filter(item => item.dataset.atlasKey && selector === '[data-atlas-key]'); },
                        setAttribute(name, value) { if (name === 'data-atlas-key') this.dataset.atlasKey = value; },
                        addEventListener(name, callback) { this.listeners[name] = callback; },
                        focus() { if (!this.disabled) document.activeElement = this; }
                    };
                    return node;
                }
                const root = makeNode('div');
                const status = makeNode('p');
                const constituencyRoot = makeNode('div');
                const heading = makeNode('h3');
                heading.scrollIntoView = () => {};
                document = { activeElement: null, addEventListener() {}, createElement: makeNode,
                    createElementNS(namespace, tag) { return makeNode(tag); },
                    getElementById(id) { return id === 'electionAtlasStatus' ? status
                        : id === 'electionGraphConstituenciesContent' ? constituencyRoot
                        : id === 'electionGraphRepresentativesTitle' ? heading : root; } };
                const records = Array.from({ length: 26 }, (_, index) => ({
                    candidateName: `Candidate ${index + 1}`, partyCode: 'PAM', partyName: 'Party',
                    seatType: 'LOCAL', regionName: 'Region', constituencyName: 'Constituency',
                    votes: index === 0 ? null : index === 1 ? 0 : index + 100
                }));
                const insights = {
                    indexRepresentatives() { return { available: true, totalRecords: 395, localCount: 305,
                        regionalCount: 90, filteredCount: records.length, records }; },
                    deriveConstituencyDistribution() { return { available: true, constituencyCount: 92,
                        localSeats: 305, bins: [21, 38, 22, 5, 6].map((count, index) => ({
                            seats: index + 2, constituencies: count,
                            items: Array.from({ length: count }, (_, item) => ({ code: `C${index}-${item}`, name: `Place ${index}-${item}` }))
                        })) }; }
                };
                const sandbox = { document, HTMLInputElement: class {}, window: {
                    queueMicrotask(callback) { callback(); },
                    FhemniElectionInsights: insights,
                    FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; },
                        createDeferredAction() { return {}; },
                        createLiveRegionAnnouncer(target) { return { announce(message) { target.textContent = message; } }; } }
                } };
                vm.runInNewContext(script, sandbox);
                sandbox.__renderConstituencies();
                const bin = visit(constituencyRoot).find(node => node.dataset.atlasKey === 'constituency-bin-3');
                assert.ok(bin);
                bin.listeners.click();
                assert.equal(sandbox.__atlasState.constituencySeats, null, 'previewing a bin does not prefilter the directory');
                assert.equal(visit(constituencyRoot).filter(node => node.tag === 'li').length, 38);
                const applyBin = visit(constituencyRoot).find(node => node.dataset.atlasKey === 'constituency-representatives');
                applyBin.listeners.click();
                assert.equal(sandbox.__atlasState.constituencySeats, 3);
                assert.equal(sandbox.__atlasState.representatives.seatType, 'LOCAL');
                assert.equal(document.activeElement, heading);
                sandbox.__atlasState.constituencySeats = null;
                sandbox.__atlasState.representatives.seatType = 'all';
                sandbox.__atlasState.representatives.page = 1;
                sandbox.__renderDirectory();
                const find = key => visit(root).find(node => node.dataset.atlasKey === key);
                const count = className => visit(root).filter(node => node.className.split(' ').includes(className)).length;
                assert.equal(count('election-atlas-representative-card'), 25);
                assert.ok(visit(root).some(node => node.textContent === 'Showing 25 of 26'));
                const more = find('representatives-load-more');
                assert.ok(more);
                document.activeElement = more;
                more.listeners.click();
                assert.equal(count('election-atlas-representative-card'), 26);
                assert.equal(find('representatives-load-more').disabled, true);
                assert.equal(document.activeElement, find('representatives-result-count'));
                assert.match(status.textContent, /Showing 26 of 26/);
                assert.ok(visit(root).some(node => node.textContent === 'Not published'));
                assert.ok(visit(root).some(node => node.textContent === '0'));
                sandbox.__atlasState.representatives.query = 'Candidate';
                sandbox.__renderDirectory();
                assert.match(find('representatives-chip-query').textContent, /Candidate/);
                const onlyChip = find('representatives-chip-query');
                document.activeElement = onlyChip;
                onlyChip.listeners.click();
                assert.equal(sandbox.__atlasState.representatives.query, '');
                assert.equal(document.activeElement, find('representatives-search'));
                sandbox.__atlasState.representatives.query = 'Candidate';
                sandbox.__renderDirectory();
                const reset = find('representatives-reset');
                document.activeElement = reset;
                reset.listeners.click();
                assert.equal(sandbox.__atlasState.representatives.query, '');
                assert.equal(find('representatives-reset').disabled, true);
                assert.equal(document.activeElement, find('representatives-search'));
                """;
        Process process = new ProcessBuilder("node", "-e", harness, controllerPath)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    @Test
    void electionAtlasGeographyMatrixUsesOneGlobalIntensityScale() throws Exception {
        String controllerPath = new ClassPathResource("static/js/election-results.js").getFile().getAbsolutePath();
        String harness = """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const script = fs.readFileSync(process.argv[1], 'utf8').replace(
                    "document.addEventListener('DOMContentLoaded', init);",
                    "globalThis.__level = geographyMatrixLevel;"
                );
                const sandbox = {
                    document: { addEventListener() {} },
                    window: { queueMicrotask(callback) { callback(); },
                        FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; },
                            createDeferredAction() { return {}; } } }
                };
                vm.runInNewContext(script, sandbox);
                const level = sandbox.__level;
                assert.deepEqual([0, 1, 3, 6, 9, 12].map(seats => level(seats, 12)),
                    [0, 1, 1, 2, 3, 4]);
                assert.equal(level(1, 24), 1, 'tiny positive cells stay visible');
                """;
        Process process = new ProcessBuilder("node", "-e", harness, controllerPath)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    @Test
    void electionAtlasRepresentativeHandoffAnnouncesVisibleFiltersAndKeepsFocus() throws Exception {
        String controllerPath = new ClassPathResource("static/js/election-results.js").getFile().getAbsolutePath();
        String harness = """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const script = fs.readFileSync(process.argv[1], 'utf8')
                    .replace("document.addEventListener('DOMContentLoaded', init);",
                        "locale = 'en'; snapshot = { regions: [{ code: 'MA-01', name: 'Tanger-Tétouan-Al Hoceïma' }], parties: [] }; globalThis.__test = { geographyRepresentativesAction, atlasCopy, atlasState };")
                    .replace('renderConstituencies({ handoff: true });', 'globalThis.__constituenciesRendered = true;')
                    .replace('renderRepresentativeHandoff();', 'globalThis.__handoffRendered = true;');
                const heading = { tabIndex: 0, scrolled: false, focused: false,
                    scrollIntoView() { this.scrolled = true; }, focus() { this.focused = true; } };
                const status = { textContent: '' };
                const document = {
                    addEventListener() {},
                    getElementById(id) { return id === 'electionAtlasStatus' ? status : heading; },
                    createElement(tag) { return { tag, dataset: {}, classList: { add() {} },
                        setAttribute() {}, addEventListener(type, callback) { this[type] = callback; } }; }
                };
                const sandbox = { document, window: { queueMicrotask(callback) { callback(); },
                    FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; },
                        createDeferredAction() { return {}; },
                        createLiveRegionAnnouncer(target) { return { announce(message) { target.textContent = message; } }; } } } };
                vm.runInNewContext(script, sandbox);
                const { geographyRepresentativesAction, atlasCopy, atlasState } = sandbox.__test;
                atlasState.constituencySeats = 2;
                const action = geographyRepresentativesAction(atlasCopy(), 'MA-01', '');
                action.click();
                assert.equal(atlasState.representatives.regionCode, 'MA-01');
                assert.equal(atlasState.representatives.partyCode, '');
                assert.equal(atlasState.representatives.page, 1);
                assert.equal(atlasState.constituencySeats, null);
                assert.equal(sandbox.__constituenciesRendered, true);
                assert.match(status.textContent, /Tanger-Tétouan-Al Hoceïma/);
                assert.match(status.textContent, /representatives/i);
                assert.equal(sandbox.__handoffRendered, true);
                assert.equal(heading.tabIndex, -1);
                assert.equal(heading.scrolled, true);
                assert.equal(heading.focused, true);
                """;
        Process process = new ProcessBuilder("node", "-e", harness, controllerPath)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    @Test
    void electionAtlasZeroSeatRegionShowsLocalizedEmptyStateWithoutLargestDelegation() throws Exception {
        String controllerPath = new ClassPathResource("static/js/election-results.js").getFile().getAbsolutePath();
        String harness = """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const script = fs.readFileSync(process.argv[1], 'utf8').replace(
                    "document.addEventListener('DOMContentLoaded', init);",
                    "locale = 'en'; snapshot = { regions: [], parties: [] }; globalThis.__renderGeography = renderGeography;"
                );
                const root = { children: [], contains() { return false; }, querySelector() { return null; },
                    replaceChildren(...nodes) { this.children = nodes; } };
                const document = {
                    activeElement: null, addEventListener() {}, getElementById() { return root; },
                    createElement(tag) { return { tag, className: '', textContent: '', dataset: {}, children: [],
                        setAttribute() {}, addEventListener() {}, append(...nodes) { this.children.push(...nodes); } }; }
                };
                const matrix = { available: true, rows: [{ code: 'MA-01', name: 'Zero', totalSeats: 0 }],
                    partyCodes: ['PAM'], maxSeats: 4 };
                const empty = { available: true, delegationSeats: 0, representedPartyCount: 0,
                    largestPartyCodes: [], rows: [] };
                const sandbox = { document, window: { queueMicrotask(callback) { callback(); },
                    FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; },
                        createDeferredAction() { return {}; } },
                    FhemniElectionInsights: { buildRegionMatrix() { return matrix; },
                        deriveRegionDelegation() { return empty; } } } };
                vm.runInNewContext(script, sandbox);
                assert.doesNotThrow(() => sandbox.__renderGeography());
                const nodes = [];
                function visit(node) { nodes.push(node); (node.children || []).forEach(visit); }
                root.children.forEach(visit);
                assert.ok(nodes.some(node => node.className === 'election-atlas-unavailable'
                    && /no seats/i.test(node.textContent)), 'localized empty region notice is rendered');
                assert.ok(!nodes.some(node => node.className === 'election-atlas-takeaway'),
                    'there is no misleading largest-delegation takeaway');
                """;
        Process process = new ProcessBuilder("node", "-e", harness, controllerPath)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    @Test
    void electionAtlasAppliesLatestDeferredPollAfterFocusLeavesAndShowsTinyPositiveShares() throws Exception {
        String controllerPath = new ClassPathResource("static/js/election-results.js").getFile().getAbsolutePath();
        String harness = """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const script = fs.readFileSync(process.argv[1], 'utf8').replace(
                    "document.addEventListener('DOMContentLoaded', init);",
                    "globalThis.__atlasTest = { replaceAtlasSection, widthClass };"
                );
                const firstControl = { dataset: {} };
                const secondControl = { dataset: {} };
                const root = {
                    current: { version: 'old' }, replacements: 0, listeners: [],
                    contains(node) { return node === firstControl || node === secondControl; },
                    querySelector() { return null; },
                    querySelectorAll() { return []; },
                    addEventListener(type, handler) { if (type === 'focusout') this.listeners.push(handler); },
                    replaceChildren(content) { this.current = content; this.replacements++; }
                };
                const document = {
                    activeElement: firstControl,
                    addEventListener() {},
                    getElementById() { return root; }
                };
                const sandbox = {
                    document, HTMLInputElement: class {},
                    window: {
                        queueMicrotask(callback) { callback(); },
                        FhemniElectionRegionFilters: { SEAT_TYPES: {}, normalizeState() { return {}; }, createDeferredAction() { return {}; } }
                    }
                };
                vm.runInNewContext(script, sandbox);
                const { replaceAtlasSection, widthClass } = sandbox.__atlasTest;
                const content = version => ({ version, querySelector() { return null; } });
                replaceAtlasSection('ballots', content('first poll'), { poll: true });
                replaceAtlasSection('ballots', content('latest poll'), { poll: true });
                assert.equal(root.current.version, 'old');
                assert.equal(root.listeners.length, 1, 'queue one focus-leave listener');
                document.activeElement = secondControl;
                root.listeners[0]();
                assert.equal(root.current.version, 'old', 'moving inside the section keeps current controls');
                document.activeElement = {};
                root.listeners[0]();
                assert.equal(root.current.version, 'latest poll');
                assert.equal(root.replacements, 1, 'use the newest poll once');
                assert.equal(widthClass(0), 'priority-width-0');
                assert.equal(widthClass(0.01), 'priority-width-1');
                assert.equal(widthClass(0.49), 'priority-width-1');
                assert.equal(widthClass(1000), 'priority-width-100');
                assert.equal(widthClass(Infinity), 'priority-width-0');
                """;
        Process process = new ProcessBuilder("node", "-e", harness, controllerPath)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    @Test
    void everyPrimaryNavigationLinksToThePriorityCompass() throws IOException {
        assertThat(html("index.html"))
                .contains("href=\"/priorities\" data-i18n=\"common.prioritiesQuiz\"");

        for (String page : SECONDARY_PAGES) {
            assertThat(html(page))
                    .as("priority compass navigation in %s", page)
                    .contains("href=\"/priorities\"")
                    .contains("data-i18n=\"common.prioritiesQuiz\"");
        }
    }

    @Test
    void priorityQuestionsUseFormScaleTypeAndMatchingActions() throws IOException {
        assertThat(html("priorities.html"))
                .contains("id=\"priorityPrevious\" class=\"priority-secondary-button\"")
                .contains("id=\"prioritySkip\" class=\"priority-secondary-button\"")
                .doesNotContain("priority-skip-button");
        assertThat(html("js/priorities.js"))
                .contains("context: 'علاش الاختيار ماشي ساهل؟'")
                .doesNotContain("context: 'شنو المفاضلة هنا؟'");
        assertThat(html("css/app.css"))
                .contains("font-size: clamp(26px, 2.4vw, 30px)")
                .contains("font-weight: 500; line-height: 1.5; text-align: center")
                .contains("font-size: clamp(21px, 5.5vw, 23px)")
                .contains("grid-template-columns: repeat(2, minmax(0, 160px))")
                .contains(".priority-theme-result { display: grid; grid-template-columns: 38px minmax(0,1fr); align-items: start;")
                .contains(".priority-result-rank { display: grid; width: 34px; height: 34px; place-items: center; margin-top: 1px;")
                .contains("font-variant-numeric: tabular-nums;")
                .contains("font-weight: 900; line-height: 1;");
    }

    @Test
    void priorityResultsEmphasizeTheCompassPartiesAndRankingWithoutSecondaryAnswerDetails() throws IOException {
        assertThat(html("priorities.html"))
                .contains("data-priority-orbit-tag=\"8\"")
                .contains("class=\"priority-result-section priority-compass-section\"")
                .contains("class=\"priority-compass-primary\"")
                .contains("class=\"compass-party-column\"")
                .contains("id=\"priorityRadarChart\"")
                .contains("class=\"compass-remaining\"")
                .contains("id=\"priorityShareDialog\"")
                .contains("id=\"priorityCompassShareHint\"")
                .contains("data-priority-share-kind=\"compass\"")
                .contains("data-priority-share-kind=\"parties\"")
                .contains("id=\"priorityShareLink\"")
                .contains("data-priority-share-action=\"link\"")
                .contains("data-priority-social=\"facebook\"")
                .contains("data-priority-social=\"whatsapp\"")
                .contains("data-priority-social=\"x\"")
                .contains("data-priority-social=\"linkedin\"")
                .contains("data-priority-social=\"copy\"")
                .contains("M12 2v13")
                .doesNotContain("M8 12h8M13 7l5 5-5 5")
                .contains("/js/priorities.js?v=20260917-16")
                .contains("/webjars/html-to-image/1.11.13/dist/html-to-image.js")
                .contains("class=\"priority-result-section priority-summary-section\"")
                .doesNotContain("id=\"priorityResultsIntro\"")
                .doesNotContain("priority-result-details")
                .doesNotContain("priority-programme-proof-points")
                .doesNotContain("priorityProgrammeOfficial")
                .doesNotContain("priorityResultDetailsLabel")
                .doesNotContain("priorityPositionResults")
                .doesNotContain("priorityTensionResults")
                .doesNotContain("priorityMethodologyText");
        assertThat(html("js/priorities.js"))
                .contains("orbitTags: ['الأسعار', 'الصحة', 'الماء', 'السكن', 'الحماية الاجتماعية', 'الحكامة', 'الشغل', 'التعليم', 'المساواة']")
                .contains("compassShareHint: 'شاركها فالسوشيال ميديا ولا حمّلها عندك'")
                .contains("compassShareHint: 'Partagez-le sur les réseaux sociaux ou téléchargez-le'")
                .contains("compassShareHint: 'Share it on social media or download it'")
                .contains("compass-profile-radar")
                .contains("const radarMaximum = Math.max(")
                .contains("const scaledRadarRadius = score =>")
                .contains("compass-match-list")
                .contains("compass-match-row")
                .contains("compass-match-bar")
                .contains("function widthClass(value)")
                .contains("window.htmlToImage.toBlob")
                .contains("height: 1920")
                .contains("fhemni-priority-compass-story.png")
                .contains("navigator.canShare?.({ files: [asset.file] })")
                .contains("navigator.share({ title: asset.title, text: asset.text, files: [asset.file] })")
                .contains("/api/catalog/questionnaires/current/shares")
                .contains("navigator.share({ title: asset.title, text, url })")
                .contains("function isMobileShareDevice()")
                .contains("https://www.facebook.com/sharer/sharer.php")
                .contains("https://wa.me/")
                .contains("['facebook', 'linkedin'].includes(network)")
                .contains("navigator.clipboard.writeText(text.trim())")
                .contains("navigator.clipboard.writeText(url)")
                .contains("shareSocialTextCopied")
                .contains("نسخنا لك النص. لصقو فالمنشور ديالك.")
                .contains("Texte copié. Collez-le dans votre publication.")
                .contains("Text copied. Paste it into your post.")
                .doesNotContain("shareLinkedInCopied")
                .doesNotContain("priorityShareCopyImage")
                .doesNotContain("priorityShareCopyText")
                .doesNotContain(".style.")
                .doesNotContain("style=\"")
                .doesNotContain("resultsIntro:")
                .doesNotContain("programmeOfficial:")
                .doesNotContain("#priorityProgrammeOfficial")
                .doesNotContain("شوف تفاصيل الأجوبة")
                .doesNotContain("Voir le détail des réponses")
                .doesNotContain("See answer details")
                .doesNotContain("#priorityResultDetailsLabel")
                .doesNotContain("renderPositionResults(")
                .doesNotContain("renderTensions(")
                .doesNotContain("compass-radar-svg");
        assertThat(html("css/app.css"))
                .contains("text-wrap: balance;")
                .contains(".priority-compass-primary { display: grid;")
                .contains(".priority-summary-section > h2")
                .contains(".priority-theme-results { display: grid; grid-template-columns: repeat(2, minmax(0,1fr));")
                .contains(".priority-theme-result + .priority-theme-result { border-top: 1px solid var(--line);")
                .doesNotContain(".priority-result-details")
                .doesNotContain(".priority-programme-proof-points")
                .contains(".compass-match-list { display: grid;")
                .contains(".compass-match-row { display: grid;")
                .contains(".compass-match-bar > span")
                .contains(".compass-profile-chart { display: grid; place-items: center; min-height: 390px;")
                .contains(".compass-profile-shape { fill: rgba(15,81,69,.26);")
                .contains("font-family: \"Tajawal Heading Numerals\", \"Tajawal\", Tahoma, Arial, sans-serif;")
                .contains(".priority-answer-button[aria-pressed=\"true\"] { border-color: var(--teal-dark);");
    }

    @Test
    void homePagePublishesACompleteLargeSocialCard() throws IOException {
        String home = html("index.html");
        assertThat(home)
                .contains("property=\"og:title\"")
                .contains("property=\"og:description\"")
                .contains("دقق فوعود الأحزاب المغربية وقارن البرامج والتقييمات والمصادر")
                .contains("property=\"og:site_name\"")
                .contains("name=\"twitter:card\" content=\"summary_large_image\"")
                .contains("https://fhemni.ma/assets/social/fhemni-og.png?v=20260910-2")
                .doesNotContain("fhemni.aboullaite.me");

        var imageResource = new ClassPathResource("static/assets/social/fhemni-og.png");
        var image = ImageIO.read(imageResource.getInputStream());
        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(630);
    }

    @Test
    void everyPageUsesTheVersionedFhemniFavicon() throws IOException {
        for (String page : ALL_PAGES) {
            assertThat(html(page))
                    .as("favicon in %s", page)
                    .contains("href=\"/favicon.ico?v=20260911\" sizes=\"32x32\"");
        }

        var favicon = new ClassPathResource("static/favicon.ico");
        assertThat(favicon.exists()).isTrue();

        var touchIcon = ImageIO.read(new ClassPathResource(
                "static/assets/brand/apple-touch-icon.png").getInputStream());
        assertThat(touchIcon.getWidth()).isEqualTo(180);
        assertThat(touchIcon.getHeight()).isEqualTo(180);
    }

    @Test
    void loginAndLegalPagesExposeStableProviderNeutralPolicyUrls() throws IOException {
        assertThat(html("login.html"))
                .contains("href=\"/terms\"")
                .contains("href=\"/privacy\"");
        assertThat(html("terms.html"))
                .contains("Terms of Service")
                .contains("href=\"/privacy\"")
                .contains("an available authentication method")
                .contains("any identity-provider account")
                .doesNotContain("Discord or Google account");
        assertThat(html("privacy.html"))
                .contains("Privacy Policy")
                .contains("privacy@fhemni.ma")
                .contains("third-party identity provider")
                .contains("authentication-provider data")
                .contains("request access, correction, export, objection, restriction, or deletion")
                .contains("public share link")
                .contains("individual answers used to create it are not uploaded")
                .doesNotContain("Discord user ID")
                .doesNotContain("associated Discord data")
                .doesNotContain("Discord's Authorized Apps");
        assertThat(html("js/i18n.js"))
                .contains("ensureLegalFooterLinks(root)")
                .contains("href=\"/terms\" data-i18n=\"common.terms\"")
                .contains("href=\"/privacy\" data-i18n=\"common.privacyPolicy\"");
    }

    @Test
    void everyPageUsesThePinnedWebFontStylesheet() throws IOException {
        for (String page : ALL_PAGES) {
            String version = switch (page) {
                case "priorities.html" -> "20260917-9";
                case "404.html" -> "20260917-1";
                case "election-results.html" -> "20260927-2";
                default -> "20260916-22";
            };
            assertThat(html(page))
                    .as("stylesheet in %s", page)
                    .containsOnlyOnce("/css/dist.css?v=" + version);
        }
    }

    @Test
    void notFoundPageStaysFocusedLocalizedAndDirectionSafe() throws IOException {
        assertThat(html("404.html"))
                .contains("class=\"not-found-main\"")
                .contains("class=\"not-found-visual\" dir=\"ltr\" aria-hidden=\"true\"")
                .contains("data-i18n=\"notFound.title\"")
                .contains("data-i18n=\"notFound.body\"")
                .contains("data-i18n=\"notFound.home\"")
                .contains("data-i18n=\"notFound.parties\"")
                .doesNotContain("notFound.confidence")
                .doesNotContain("notFound.verdict");
        assertThat(html("js/i18n.js"))
                .contains("'notFound.pageTitle': 'Page not found — Fhemni'")
                .contains("'notFound.pageTitle': 'Page introuvable — Fhemni'")
                .contains("'notFound.pageTitle': 'الصفحة ما لقايناش — فهّمني'")
                .contains("'notFound.title': 'قلّبنا عليها حتى فالمصادر… والو.'");
        assertThat(html("css/app.css"))
                .contains(".not-found-visual")
                .contains("direction: ltr;")
                .contains("unicode-bidi: isolate;");
    }

    @Test
    void compactPartyBadgesOpticallyAlignTheirColourDot() throws IOException {
        assertThat(html("css/app.css"))
                .contains(".party-badge .party-dot { transform: translateY(-1px); }");
    }

    @Test
    void publicPromiseCardsDoNotRepeatTheLocalizedVerdictInTheirSummary() throws IOException {
        assertThat(html("js/catalog-ui.js"))
                .contains("function assessmentSummary(value, verdict)")
                .contains("DIFFICILE")
                .contains("DONN[ÉE]ES\\s+INSUFFISANTES")
                .contains("المعطيات\\s+ما\\s+كافياش");
        assertThat(html("js/landing.js"))
                .contains("FhemniCatalog.assessmentSummary(");
        assertThat(html("js/party.js"))
                .contains("FhemniCatalog.assessmentSummary(");
        assertThat(html("js/promise.js"))
                .contains("FhemniCatalog.assessmentSummary(")
                .doesNotContain("function cleanSummary(value)");
        assertThat(html("index.html"))
                .contains("/js/catalog-ui.js?v=20260914-1")
                .contains("/js/landing.js?v=20260914-1");
        assertThat(html("promise.html"))
                .contains("/js/catalog-ui.js?v=20260914-1")
                .contains("/js/promise.js?v=20260914-1");
    }

    @Test
    void catalogPlayButtonUsesAFontIndependentCenteredTriangle() throws IOException {
        assertThat(html("css/app.css"))
                .contains(".catalog-thumbnail::after { content: \"\";")
                .contains(".catalog-thumbnail::before { content: \"\";")
                .contains("border-left: 10px solid var(--orange)")
                .doesNotContain(".catalog-thumbnail::after { content: \"▶\"");
    }

    @Test
    void everyPageUsesCampaignAwarePrivacySafeAnalytics() throws IOException {
        for (String page : ALL_PAGES) {
            String version = page.equals("election-results.html") ? "20260923-1" : "20260911-1";
            assertThat(html(page))
                    .as("analytics asset in %s", page)
                    .contains("/js/analytics.js?v=" + version);
        }

        assertThat(html("js/analytics.js"))
                .contains("page_location: campaignLocation()")
                .contains("CAMPAIGN_PARAMETER")
                .contains("key === 'page_location' ? MAX_PAGE_LOCATION_LENGTH : 100")
                .doesNotContain("page_location: `${window.location.origin}${window.location.pathname}`")
                .doesNotContain("page_location: window.location.href");
    }

    @Test
    void loginPageUsesClearDarijaCopyAndBrandedProviders() throws IOException {
        assertThat(html("login.html"))
                .contains("ستافد أكثر من فهّمني")
                .contains("دخل لحسابك باش تسول فهّمني فالشات، تقترح وتصوّت.")
                .contains("/js/login.js?v=20260913-1");
        assertThat(html("js/i18n.js"))
                .contains("'login.emailAction': 'بغيت الرابط'")
                .contains("'login.emailAction': 'Get link'")
                .contains("'login.emailAction': 'Recevoir le lien'")
                .contains("new URLSearchParams(window.location.search).get('lang')")
                .contains("storeLocale(requested);")
                .doesNotContain("'login.emailAction': 'Email me a sign-in link'")
                .doesNotContain("'login.emailAction': 'Recevoir un lien de connexion'");
        assertThat(html("js/login.js"))
                .contains("auth-provider--email")
                .contains("locale: window.FhemniI18n?.locale?.() || document.documentElement.lang")
                .contains("['google', 'discord'].includes(providerId)")
                .contains("`auth-provider--${providerId}`")
                .contains("providerIcon(provider.id)");
        assertThat(html("css/dist.css"))
                .contains(".auth-provider--google")
                .contains("#1a73e8")
                .contains(".auth-provider--discord")
                .contains("#5865f2")
                .contains(".auth-main{min-height:0;padding:22px 0 72px;display:block}")
                .contains(".auth-card{width:100%;padding-inline:20px}")
                .contains(".magic-link-fields{grid-template-columns:minmax(0,1fr)}");
    }

    @Test
    void nonFactualClaimExplanationIsLocalizedAtRenderTime() throws IOException {
        assertThat(html("js/i18n.js"))
                .contains("'analysis.nonFactualExplanation': 'Opinions, proposals, and predictions")
                .contains("'analysis.nonFactualExplanation': 'Les opinions, propositions et prévisions")
                .contains("'analysis.nonFactualExplanation': 'الآراء والاقتراحات والتوقعات كيبانو بوحدهم")
                .contains("'analysis.whatDiscussed': 'على شنو هضروا؟'")
                .doesNotContain("'analysis.whatDiscussed': 'على شنو تهضروا؟'");
        assertThat(html("js/analysis.js"))
                .contains("claim.kind === 'FACT'")
                .contains("t('analysis.nonFactualExplanation')");
        assertThat(html("analysis.html"))
                .contains("/js/analysis.js?v=20260912-2")
                .contains("/js/i18n.js?v=20260915-1")
                .doesNotContain("analysis.evidenceKicker");
        assertThat(html("js/i18n.js"))
                .doesNotContain("analysis.evidenceKicker")
                .doesNotContain("الأدلة، ماشي غير نقط");
    }

    @Test
    void analysisBadgesContainArabicLabelsOnNarrowCards() throws IOException {
        assertThat(html("css/dist.css"))
                .contains(".claim-card .claim-meta{align-items:flex-start")
                .contains(".claim-card .claim-badges{min-width:0;max-width:100%}")
                .contains(".claim-card .claim-badges .badge{text-align:center;white-space:normal;height:auto;min-height:24px;padding-block:3px;line-height:1.35}");
    }

    @Test
    void compactPartyBadgesAlignLatinCodesWithTheirPartyDot() throws IOException {
        assertThat(html("css/dist.css"))
                .contains("border-radius:999px;align-items:center;gap:7px")
                .contains("font-size:10px;font-weight:850;line-height:1;text-decoration:none;display:inline-flex}");
    }

    @Test
    void homeFactCheckPanelUsesTheSharedRadiusWithoutADoubleDivider() throws IOException {
        assertThat(html("css/dist.css"))
                .contains(".fact-check-feature{")
                .contains("border-radius:var(--radius-panel)")
                .contains(".fact-check-feature+.featured-section{border-top:0}")
                .doesNotContain(".fact-check-feature{padding:44px clamp(20px,3vw,36px);border:1px solid #176b6333;border-radius:28px");
    }

    @Test
    void homePageUsesOneConciseFactCheckHeading() throws IOException {
        assertThat(html("index.html"))
                .contains("id=\"factCheckTitle\" data-i18n=\"landing.factCheckTitle\"")
                .doesNotContain("landing.factCheckKicker")
                .doesNotContain("landing.factCheckIntro");
        assertThat(html("js/i18n.js"))
                .contains("'landing.promiseCheck': 'حلل الأدلة'")
                .contains("اللحظة اللي تقالت فيها")
                .contains("'landing.promiseAskText': 'قرا الخلاصة ولا سول الفيديو و دقق فاللحظة اللي تقالت فيها الهضرة.'")
                .doesNotContain("'landing.promiseCheck': 'حل الأدلة'")
                .doesNotContain("وبقا مربوط باللحظة")
                .doesNotContain("اللحظة اللي تقالات فيها");
    }

    @Test
    void adminPagesUseTheLatestMobileAssets() throws IOException {
        for (String page : ADMIN_PAGES) {
            assertThat(html(page))
                    .as("mobile assets in %s", page)
                    .contains("/css/dist.css?v=20260916-22")
                    .contains("/js/i18n.js?v=20260915-1");
        }
    }

    @Test
    void adminDashboardSurfacesOpenReaderReports() throws IOException {
        assertThat(html("admin.html"))
                .contains("id=\"adminAssessmentReportsAlert\"")
                .contains("href=\"/admin/programmes?focus=reports\"")
                .contains("admin.readerReportsAlertTitle")
                .contains("/js/admin.js?v=20260915-1");
        assertThat(html("js/admin.js"))
                .contains("/api/admin/programmes/assessment-reports")
                .contains("renderAssessmentReportsAlert");
        assertThat(html("js/programme-admin.js"))
                .contains("focusReaderReports")
                .contains("programme-report-flag")
                .contains("programme-promise-row.has-reader-reports")
                .contains("expandedPromises")
                .contains("expandedAssessmentDetails")
                .contains("admin.focusedReviewActive")
                .contains("dismissAssessmentReportConfirm")
                .contains("assessmentReportDismissed")
                .contains("/assessment-reports/${reportId}/dismiss")
                .contains("window.setTimeout(pollActiveWork, 3000)")
                .contains("updateProgrammeMediaPanel(programmeId)")
                .contains("data-programme-id")
                .doesNotContain("window.setTimeout(load, 3000)");
        assertThat(html("js/catalog-ui.js"))
                .contains("category.dataset.reportCategory = ''")
                .contains("FACTUAL_OR_LEGAL_ERROR")
                .contains("OUTDATED_OR_MISSING_SOURCE")
                .contains("UNCLEAR_REASONING")
                .contains("assessmentId: dialog.dataset.assessmentId || null")
                .contains("category: dialog.querySelector('[data-report-category]').value");
    }

    @Test
    void suggestedEpisodesCanBeSelectedAndSentToTheBatchImporter() throws IOException {
        assertThat(html("admin-suggestions.html"))
                .contains("id=\"selectAllSuggestions\"")
                .contains("id=\"selectedSuggestionCount\"")
                .contains("id=\"addSelectedSuggestions\"");
        assertThat(html("js/admin.js"))
                .contains("const maxSuggestionSelection = 20")
                .contains("suggestion.moderationStatus !== 'REVIEW_REQUIRED'")
                .contains("parameters.append('youtubeUrl', youtubeUrl)")
                .contains("getAll('youtubeUrl')")
                .contains("selectedSuggestionIds.clear()")
                .contains("addToImporter(selectedUrls)");
        assertThat(html("js/i18n.js"))
                .contains("'admin.addSelectedToImporter': 'Add selected to importer'")
                .contains("'admin.addSelectedToImporter': 'Ajouter la sélection'")
                .contains("'admin.addSelectedToImporter': 'زيد المختارين'");
    }

    @Test
    void narrowAdminHeadersPutLanguageChoicesBesideTheNavigation() throws IOException {
        assertThat(html("css/dist.css"))
                .contains("@media (max-width:360px)")
                .contains(".admin-page .public-header .header-actions{display:contents}")
                .contains(".admin-page .public-header .header-actions>.site-language-options{grid-area:2/1");
    }

    @Test
    void partyPageCombinesTheBriefingWithTheFullProgramme() throws IOException {
        String partyPage = html("party.html");
        assertThat(partyPage)
                .contains("id=\"partyProgrammeTab\"")
                .contains("id=\"partyChatTab\"")
                .contains("id=\"partyEpisodesTab\"")
                .contains("class=\"programme-overview\"")
                .contains("id=\"programmeMedia\" class=\"programme-media\"")
                .contains("class=\"video-js vjs-big-play-centered\"")
                .contains("/webjars/video.js/8.23.8/dist/video-js.min.css")
                .contains("/css/dist.css?v=20260916-22")
                .contains("/js/videojs-config.js?v=20260911-1")
                .contains("/webjars/video.js/8.23.8/dist/video.min.js")
                .contains("/js/i18n.js?v=20260915-1")
                .contains("/js/party.js?v=20260914-1")
                .containsOnlyOnce("data-i18n=\"programme.kicker\"")
                .doesNotContain("data-i18n=\"programme.mediaPowered\"")
                .doesNotContain("id=\"partyBriefingTab\"")
                .doesNotContain("id=\"programmeMediaAudio\"")
                .doesNotContain("id=\"programmeMediaTranscript\"")
                .doesNotContain("programme.mediaTitle")
                .doesNotContain("programme.mediaTranscript")
                .doesNotContain("data-programme-skip")
                .doesNotContain("id=\"partyPriorities\"");
        assertThat(partyPage.indexOf("id=\"programmeMedia\"")).isBetween(
                partyPage.indexOf("id=\"partyProgramme\""),
                partyPage.indexOf("id=\"partyPromises\""));
        assertThat(partyPage.indexOf("/js/videojs-config.js"))
                .isLessThan(partyPage.indexOf("/webjars/video.js/8.23.8/dist/video.min.js"));
        assertThat(html("js/videojs-config.js"))
                .contains("window.VIDEOJS_NO_DYNAMIC_STYLE = true");
        assertThat(html("js/i18n.js"))
                .contains("'programme.mediaDisclosure': 'خلاصة بالذكاء الاصطناعي، الخطأ وارد.'");
        assertThat(html("js/party.js"))
                .doesNotContain("['briefing'")
                .doesNotContain("#programmeMediaAudio")
                .doesNotContain("#programmeMediaTranscript");
        assertThat(html("css/dist.css"))
                .contains("https://storage.googleapis.com/fhemni-public-assets-mohamed-playground/fonts/videojs.46d5222f8568.woff");
    }

    private String html(String page) throws IOException {
        return new ClassPathResource("static/" + page).getContentAsString(UTF_8);
    }

    private void assertCanonicalPrimaryNavigation(String page) throws IOException {
        String pageHtml = html(page);
        int start = pageHtml.indexOf("<nav class=\"primary-nav\"");
        int end = pageHtml.indexOf("</nav>", start);
        assertThat(start).as("primary navigation start in %s", page).isGreaterThanOrEqualTo(0);
        assertThat(end).as("primary navigation end in %s", page).isGreaterThan(start);
        String navigation = pageHtml.substring(start, end);
        assertThat(navigation)
                .as("primary navigation in %s", page)
                .contains("href=\"/elections/2026\"")
                .contains("href=\"/videos\"")
                .contains("href=\"/parties\"")
                .contains("href=\"/priorities\"")
                .doesNotContain("common.howItWorks");
        assertThat(navigation.indexOf("href=\"/elections/2026\""))
                .as("election results lead the primary navigation in %s", page)
                .isLessThan(navigation.indexOf("href=\"/videos\""));
    }
}
