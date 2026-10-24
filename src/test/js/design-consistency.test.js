const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static');
const css=fs.readFileSync(path.join(root,'css/public-shell.css'),'utf8');
// These are stylesheet contracts, not a substitute for rendered-browser checks.
const rules=[...css.replace(/\/\*[\s\S]*?\*\//g,'').matchAll(/([^{}]+)\{([^{}]*)\}/g)];
function rule(selector){return rules.filter(match=>match[1].trim().split(/,\s*(?![^()]*\))/).includes(selector)).map(match=>match[2]).join(';');}
test('public headings and comparison content have a readable hierarchy',()=>{
    assert.match(rule(':where(body[data-public-shell] main) h2'),/font-size:\s*24px/);
    assert.match(rule(':where(body[data-public-shell] main) h3'),/font-size:\s*20px/);
    assert.match(rule('body[data-public-shell] .programme-heading h2'),/font-size:\s*clamp\(24px,\s*2\.5vw,\s*30px\)/);
    assert.match(rule('body[data-public-shell] .priority-comparison-promise > strong'),/font-size:\s*16px/);
    assert.match(rule('body[data-public-shell] .priority-comparison-promise > strong'),/-webkit-line-clamp:\s*unset/);
});
test('vote tracks accommodate the shared minimum target without clipping focus',()=>{
    assert.match(rule('body[data-public-shell] .suggestion-voting'),/grid-template-columns:\s*44px minmax\(44px,auto\) 44px/);
    assert.match(rule('body[data-public-shell] .suggestion-voting'),/overflow:\s*visible/);
    assert.match(rule('body[data-public-shell] .vote-button'),/height:\s*44px/);
});
test('composite fields, disclosures and legal reading measure retain their layout contracts',()=>{
    assert.ok(css.includes('select:not(.filter-control select)'));
    assert.match(rule('body[data-public-shell] main summary'),/min-height:\s*44px/);
    assert.match(rule('body[data-public-shell] main summary'),/cursor:\s*pointer/);
    assert.match(rule('body[data-public-shell] .legal-document'),/max-width:\s*80ch/);
});
test('selected map regions and Arabic source text retain focus and language-specific styling',()=>{
    assert.match(rule('body[data-public-shell] .election-map-region:focus-visible path'),/stroke:\s*var\(--orange\)/);
    assert.match(rule('body[data-public-shell] main :lang(ar)'),/font-family:\s*"Tajawal"/);
    assert.match(rule('body[data-public-shell] main :lang(ary)'),/font-family:\s*"Tajawal"/);
    assert.match(rule('body[data-public-shell] .back-link:dir(rtl) .back-link-icon'),/scaleX\(-1\)/);
    for(const file of ['party.html','person.html','promise.html'])assert.match(fs.readFileSync(path.join(root,file),'utf8'),/<span class="back-link-icon" aria-hidden="true">←<\/span>/);
});
test('member action templates use visible shared button styles',()=>{
    for(const [file,ids]of [['member-analysis.html',['memberResultRetry','memberAsk','memberPublicationRequest']],['my-analyses.html',['memberHistoryRetry']]]){
        const html=fs.readFileSync(path.join(root,file),'utf8');
        for(const id of ids){const tag=html.match(new RegExp('<button\\b[^>]*id="'+id+'"[^>]*>'));assert.ok(tag,id);assert.match(tag[0],/class="[^"]*(?:primary|secondary)-button/);}
    }
});
test('methodology explanations reuse the shared panel surface',()=>{
    const html=fs.readFileSync(path.join(root,'methodology.html'),'utf8');
    assert.equal((html.match(/<article class="public-panel"/g)||[]).length,4);
    assert.doesNotMatch(html,/class="card border/);
});
