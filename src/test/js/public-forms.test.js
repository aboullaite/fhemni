const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const staticRoot = path.resolve(__dirname, '../../main/resources/static');

test('public form fields have a shared visible surface independent of page nesting', () => {
    const css = fs.readFileSync(path.join(staticRoot, 'css/public-shell.css'), 'utf8');
    const rule = css.match(/body\[data-public-shell\] main :where\(([^{}]+)\)\s*\{([^}]+)\}/);
    assert.ok(rule, 'main itself may be public-stack; field styling must not require another nested stack');
    assert.match(rule[1], /select/);
    assert.match(rule[1], /textarea/);
    for (const type of ['checkbox', 'radio', 'range', 'hidden', 'file', 'submit', 'button']) {
        assert.ok(rule[1].includes(`:not([type=${type}])`), `${type} is not a text field`);
    }
    assert.match(rule[2], /border:\s*1px solid var\(--line\)/);
    assert.match(rule[2], /background:\s*var\(--white\)/);
    assert.match(rule[2], /min-width:\s*0/);
    assert.match(rule[2], /font:\s*inherit/);
});

test('ministers clear action uses the shared secondary button', () => {
    const html = fs.readFileSync(path.join(staticRoot, 'government-ministers.html'), 'utf8');
    const clear = html.match(/<button\b[^>]*id="ministersClear"[^>]*>/)[0];
    assert.match(clear, /class="[^"]*secondary-button/);
});
