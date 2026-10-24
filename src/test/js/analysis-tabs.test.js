const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const staticRoot = path.resolve(__dirname, '../../main/resources/static');

// A small DOM fixture reads the real page so missing tab/panel relationships
// cannot be hidden by a hand-authored copy of its markup.
function harness(dir = 'ltr') {
    const nodes = [], stack = [];
    const doc = {documentElement: {dir}, listeners: {}, addEventListener(name, fn) {this.listeners[name] = fn;}};
    function matches(node, selector) {
        const descendant = selector.indexOf(' ');
        if (descendant !== -1) {
            if (!matches(node, selector.slice(descendant + 1))) return false;
            for (let parent = node.parent; parent; parent = parent.parent) {
                if (matches(parent, selector.slice(0, descendant))) return true;
            }
            return false;
        }
        const id = selector.match(/#([\w-]+)/);
        if (id && node.id !== id[1]) return false;
        for (const [, name] of selector.matchAll(/\.([\w-]+)/g)) if (!node.classList.contains(name)) return false;
        for (const [, name, value] of selector.matchAll(/\[([\w-]+)(?:="([^"]*)")?\]/g)) {
            if (value === undefined ? node.getAttribute(name) === null : node.getAttribute(name) !== value) return false;
        }
        const tag = selector.match(/^[a-z]+/);
        return !tag || node.tag === tag[0];
    }
    for (const token of fs.readFileSync(path.join(staticRoot, 'analysis.html'), 'utf8').matchAll(/<\/?([a-z][\w-]*)\b([^>]*?)>/gi)) {
        const [, tag, raw] = token;
        if (token[0].startsWith('</')) {if (stack.at(-1)?.tag === tag) stack.pop(); continue;}
        const attributes = {};
        for (const [, key, value] of raw.matchAll(/([\w-]+)(?:="([^"]*)")?/g)) attributes[key] = value ?? '';
        const classes = new Set((attributes.class || '').split(/\s+/));
        const node = {tag, attributes, id: attributes.id, parent: stack.at(-1), ownerDocument: doc,
            hidden: 'hidden' in attributes, value: '', listeners: {}, dataset: {},
            classList: {contains: name => classes.has(name), toggle(name, on) {if (on) classes.add(name); else classes.delete(name);}},
            setAttribute(key, value) {attributes[key] = String(value);}, getAttribute(key) {return attributes[key] ?? null;},
            addEventListener(key, fn) {(this.listeners[key] ||= []).push(fn);}, removeEventListener() {},
            dispatch(key, event = {}) {for (const fn of this.listeners[key] || []) fn(event);},
            focus() {doc.activeElement = this;},
            contains(other) {for (; other; other = other.parent) if (other === this) return true; return false;},
            querySelectorAll(selector) {return nodes.filter(n => n !== this && this.contains(n) && matches(n, selector));},
            querySelector(selector) {return this.querySelectorAll(selector)[0] || null;}
        };
        for (const [key, value] of Object.entries(attributes)) if (key.startsWith('data-')) node.dataset[key.slice(5)] = value;
        nodes.push(node);
        if (!['meta', 'link', 'img', 'input', 'br', 'hr', 'path', 'circle'].includes(tag)) stack.push(node);
    }
    doc.querySelectorAll = selector => nodes.filter(node => matches(node, selector));
    doc.querySelector = selector => doc.querySelectorAll(selector)[0] || null;
    doc.getElementById = id => nodes.find(node => node.id === id) || null;
    const window = {FhemniUI: require(path.join(staticRoot, 'js/public-ui.js')), addEventListener() {}};
    const context = vm.createContext({document: doc, window, Set, URLSearchParams});
    vm.runInContext(fs.readFileSync(path.join(staticRoot, 'js/analysis.js'), 'utf8'), context);
    vm.runInContext('bindEvents()', context);
    const tabs = doc.querySelectorAll('[data-tab]');
    return {doc, tabs, run: source => vm.runInContext(source, context),
        key(tab, key) {let prevented = false; tab.dispatch('keydown', {key, preventDefault() {prevented = true;}}); return prevented;}};
}

test('analysis tabs connect each section to its label and expose only one tab stop', () => {
    const {doc, tabs} = harness();
    assert.equal(tabs.length, 3);
    for (const [index, tab] of tabs.entries()) {
        assert.ok(tab.id, 'tabs need stable accessible identities');
        const panel = doc.getElementById(tab.getAttribute('aria-controls'));
        assert.ok(panel, 'each tab controls a real section');
        assert.equal(panel.getAttribute('aria-labelledby'), tab.id);
        assert.equal(panel.hidden, index !== 0);
        assert.equal(tab.getAttribute('tabindex'), index === 0 ? '0' : '-1');
    }
});

test('analysis tab arrows follow reading direction and Home/End wrap selection without entering the panel', () => {
    for (const dir of ['ltr', 'rtl']) {
        const h = harness(dir), [overview, claims, ask] = h.tabs;
        overview.focus();
        assert.equal(h.key(overview, dir === 'rtl' ? 'ArrowLeft' : 'ArrowRight'), true);
        assert.equal(h.doc.activeElement, claims);
        assert.equal(h.doc.getElementById('claimsTab').hidden, false);
        h.key(claims, 'End'); assert.equal(h.doc.activeElement, ask);
        h.key(ask, dir === 'rtl' ? 'ArrowLeft' : 'ArrowRight'); assert.equal(h.doc.activeElement, overview);
        h.key(overview, dir === 'rtl' ? 'ArrowRight' : 'ArrowLeft'); assert.equal(h.doc.activeElement, ask);
        h.key(ask, 'Home'); assert.equal(h.doc.activeElement, overview);
        assert.equal(h.key(overview, 'Tab'), false);
    }
});

test('programmatic section switches preserve external focus and recover focus from a hidden panel', () => {
    const h = harness(), input = h.doc.getElementById('questionInput');
    const external = h.doc.querySelector('[data-site-language]');
    external.focus(); h.run("activateTab('ask')");
    assert.equal(h.doc.activeElement, external);
    assert.equal(h.doc.getElementById('askTab').hidden, false);
    assert.equal(h.doc.getElementById('askTab').classList.contains('hidden'), false);
    input.value = 'Keep my draft'; input.focus(); h.run("activateTab('overview')");
    assert.equal(h.doc.activeElement, h.tabs[0]);
    assert.equal(input.value, 'Keep my draft');
    h.tabs[2].dispatch('click');
    assert.equal(h.doc.getElementById('askTab').hidden, false);
    assert.notEqual(h.doc.activeElement, input);
});

test('question has a persistent localized label and answer modes announce their selected state', () => {
    const h = harness(), input = h.doc.getElementById('questionInput');
    const label = h.doc.querySelector(`label[for="${input.id}"]`);
    assert.ok(label, 'question input has a visible label independent of its placeholder');
    assert.ok(label.getAttribute('data-i18n'));
    assert.equal(label.hidden, false);
    const [video, check] = h.doc.querySelectorAll('.mode-option');
    check.dispatch('click');
    assert.equal(check.getAttribute('aria-pressed'), 'true');
    assert.equal(video.getAttribute('aria-pressed'), 'false');
    assert.notEqual(check.getAttribute('role'), 'tab');
    assert.equal(h.run('state.questionMode'), 'CHECK');
    video.dispatch('click');
    assert.equal(video.getAttribute('aria-pressed'), 'true');
    assert.equal(h.run('state.questionMode'), 'VIDEO');
});
