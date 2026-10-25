const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../..');

async function shell() {
    const file = path.join(root, 'scripts/public-shell.mjs');
    assert.ok(fs.existsSync(file), 'shared shell generator exists');
    return import(file);
}

test('party tab selection leaves keyboard focus on the tab instead of stealing it into chat', () => {
    const source=fs.readFileSync(path.join(root,'src/main/resources/static/js/party.js'),'utf8');
    const select=source.slice(source.indexOf('    function selectTab('),source.indexOf('    function setupProgrammeMediaPlayer('));
    let chatFocus=0;
    const views=['programme','ask','episodes'].map(name=>[name,element(),element(),true]);
    const sandbox={programmeViews:()=>views,viewFromUrl:()=> 'programme',
        document:{querySelector:()=>({focus(){chatFocus++;}})},
        window:{requestAnimationFrame:fn=>fn()}};
    vm.runInNewContext(select+';selectTab("ask",false);',sandbox);
    assert.equal(views[1][2].hidden,false);
    assert.equal(views[1][1].tabIndex,0);
    assert.equal(chatFocus,0);
});

test('shared legacy-page rules preserve touch targets, focus clearance and long labels', () => {
    const css=fs.readFileSync(path.join(root,'src/main/resources/static/css/public-shell.css'),'utf8');
    assert.match(css,/\.catalog-view-tabs a[^{}]*\.party-compare-action[^{}]*\.election-history-link[^{}]*\{[^}]*min-height:\s*44px/s);
    assert.match(css,/\.party-tabs[^{}]*\.history-tabs[^{}]*\{[^}]*padding:\s*6px[^}]*scroll-padding:\s*6px/s);
    assert.match(css,/\.priority-share-heading\s*\{[^}]*padding-inline-end:\s*48px/s);
    assert.match(css,/\.party-badge\s*>\s*span\s*\{[^}]*white-space:\s*normal[^}]*overflow-wrap:\s*anywhere/s);
});

test('all navigation remains real links without JavaScript; elections has a separate disclosure', async () => {
    const {renderPublicShell} = await shell();
    const {header, footer} = renderPublicShell({pagePath:'/elections/2026', area:'elections'});
    for (const href of ['/', '/government', '/analyse', '/elections/2026', '/elections/history', '/parties']) {
        assert.ok(header.includes(`href="${href}"`), href);
    }
    assert.match(header, /<button[^>]+aria-controls="shellElectionLinks"[^>]+aria-expanded="false"/);
    assert.match(header, /<button[^>]+class="shell-election-trigger"[^>]*aria-label="أقسام الانتخابات"/);
    assert.match(header, /<a href="\/elections\/2026"[^>]*data-i18n="shell.elections">الانتخابات<\/a>/);
    assert.doesNotMatch(header.match(/<div class="shell-election-heading"[\s\S]*?<\/div>/)[0],/⌄/);
    assert.match(header, /id="shellElectionLinks"/);
    assert.match(header, /href="\/elections\/2026#electionGraphRepresentatives"/);
    assert.match(footer, /data-legal-links/);
    assert.match(footer, /href="\/videos"/);
    assert.doesNotMatch(header, /href="\/videos"/);
    assert.doesNotMatch(header, /href="\/priorities"/);
});

test('generation changes only bounded shell regions and is idempotent', async () => {
    const {replaceShell,renderPublicShell} = await shell();
    const sample = '<body>before<header class="site-header public-header">old</header><main>KEEP &amp; EXACT</main><footer>old</footer><script src="/js/safe.js"></script></body>';
    const result = replaceShell(sample, renderPublicShell({pagePath:'/'}));
    assert.ok(result.includes('<main>KEEP &amp; EXACT</main>'));
    assert.ok(result.includes('<script src="/js/safe.js"></script></body>'));
    assert.equal(replaceShell(result, renderPublicShell({pagePath:'/'})), result);
    assert.throws(() => replaceShell('<main>missing</main>', renderPublicShell({pagePath:'/'})), /shell/i);
});

function element(attributes={}) {
    return {attributes, hidden:false, listeners:{}, focusCount:0,
        getAttribute(key){return this.attributes[key] ?? null;},
        setAttribute(key,value){this.attributes[key]=value;},
        addEventListener(type,fn){this.listeners[type]=fn;},
        focus(){this.focusCount++;}};
}
test('disclosures initialize once and Escape restores focus to their own trigger', () => {
    const file=path.join(root, 'src/main/resources/static/js/site-shell.js');
    assert.ok(fs.existsSync(file), 'shared disclosure enhancer exists');
    const {initSiteShell}=require(file);
    const toggle=element({'aria-controls':'links','aria-expanded':'false'});
    const panel=element();
    const doc={querySelectorAll:()=>[toggle],getElementById:()=>panel};
    initSiteShell(doc); initSiteShell(doc);
    assert.equal(panel.hidden,true);
    toggle.listeners.click();
    assert.equal(toggle.getAttribute('aria-expanded'),'true');
    assert.equal(panel.hidden,false);
    panel.listeners.keydown({key:'Escape',preventDefault(){},stopPropagation(){}});
    assert.equal(toggle.getAttribute('aria-expanded'),'false');
    assert.equal(toggle.focusCount,1);
});

test('desktop navigation is exposed; resizing reconciles hidden controls and focus', () => {
    const {initSiteShell}=require(path.join(root,'src/main/resources/static/js/site-shell.js'));
    const toggle=element({'aria-controls':'shellPrimaryNav','aria-expanded':'false'});
    const panel=element(); const first=element(); panel.querySelector=()=>first;
    const media={matches:true,addEventListener(type,fn){this.changed=fn;}};
    const doc={querySelectorAll:()=>[toggle],getElementById:()=>panel,defaultView:{matchMedia:()=>media},activeElement:null};
    panel.contains=node=>node===first;
    initSiteShell(doc);
    assert.equal(panel.hidden,false); assert.equal(toggle.hidden,true);
    panel.listeners.keydown({key:'Escape',preventDefault(){},stopPropagation(){}});
    assert.equal(panel.hidden,false,'Escape must not hide desktop navigation');
    media.matches=false; doc.activeElement=first; media.changed();
    assert.equal(panel.hidden,true); assert.equal(toggle.hidden,false); assert.equal(toggle.focusCount,1);
    toggle.listeners.click(); media.matches=true; doc.activeElement=toggle; media.changed();
    assert.equal(panel.hidden,false); assert.equal(first.focusCount,1);
});

function electionDisclosure(hover=true){
    const toggle=element({'aria-controls':'shellElectionLinks','aria-expanded':'false'}),panel=element(),group=element(),link=element(),fallback=element();
    let pending;const media={matches:hover,addEventListener(type,fn){this.changed=fn;}};
    const doc={querySelectorAll:()=>[toggle],getElementById:()=>panel,activeElement:null,listeners:{},addEventListener(type,fn){this.listeners[type]=fn;},defaultView:{matchMedia:()=>media,setTimeout(fn){pending=fn;return 1;},clearTimeout(){pending=null;}}};
    toggle.closest=()=>group;group.querySelector=()=>fallback;group.contains=node=>[toggle,panel,link].includes(node);panel.contains=node=>node===link;
    require(path.join(root,'src/main/resources/static/js/site-shell.js')).initSiteShell(doc);
    return {toggle,panel,group,link,fallback,doc,media,flush(){const fn=pending;pending=null;fn?.();}};
}
test('desktop elections opens on hover and stays open while crossing into its submenu',()=>{
    const {group,panel,toggle,flush}=electionDisclosure();
    group.listeners.pointerenter?.({pointerType:'mouse'});
    assert.equal(panel.hidden,false);assert.equal(toggle.getAttribute('aria-expanded'),'true');
    group.listeners.pointerleave?.({pointerType:'mouse'});group.listeners.pointerenter?.({pointerType:'mouse'});flush();
    assert.equal(panel.hidden,false);
    group.listeners.pointerleave?.({pointerType:'mouse'});flush();assert.equal(panel.hidden,true);
});
test('touch and narrow screens retain explicit tap disclosure rather than sticky hover',()=>{
    for(const [hover,pointerType]of [[false,'mouse'],[true,'touch']]){
        const {group,panel,toggle,fallback}=electionDisclosure(hover);group.listeners.pointerenter?.({pointerType});
        assert.equal(fallback.hidden,false,'the Elections label remains a navigable link');assert.equal(toggle.hidden,false);
        assert.equal(panel.hidden,true);toggle.listeners.click();assert.equal(panel.hidden,false);
        toggle.listeners.click();assert.equal(panel.hidden,true);
    }
});
test('hover submenu respects keyboard focus and closes on Escape or leaving the group',()=>{
    const {group,panel,toggle,doc,link,flush}=electionDisclosure();
    toggle.listeners.click();doc.activeElement=link;group.listeners.pointerleave?.({pointerType:'mouse'});flush();assert.equal(panel.hidden,false);
    panel.listeners.keydown({key:'Escape',preventDefault(){},stopPropagation(){}});assert.equal(panel.hidden,true);assert.equal(toggle.focusCount,1);
    group.listeners.pointerenter?.({pointerType:'mouse'});group.listeners.focusout?.({relatedTarget:{}});assert.equal(panel.hidden,true);
});
test('elections closes on outside press and capability changes without leaving focus hidden',()=>{
    const {group,panel,toggle,doc,media,link}=electionDisclosure();
    group.listeners.pointerenter?.({pointerType:'mouse'});doc.listeners.pointerdown?.({target:{}});assert.equal(panel.hidden,true);
    toggle.listeners.click();doc.activeElement=link;media.matches=false;media.changed?.();
    assert.equal(panel.hidden,true);assert.equal(toggle.focusCount,1);
});

test('representative bookmark selects graphs and focuses the loaded representative section', () => {
    const nodes=new Map();
    const doc={addEventListener(){},querySelectorAll(){return []},getElementById(id){
        if(!nodes.has(id)) nodes.set(id,{...element(),hidden:true,querySelectorAll(){return []},scrollIntoView(){this.scrolled=true;}});
        return nodes.get(id);
    }};
    const script=fs.readFileSync(path.join(root,'src/main/resources/static/js/election-results.js'),'utf8')
        .replace("document.addEventListener('DOMContentLoaded', init);", 'globalThis.testTabs=bindTabs; globalThis.loaded=()=>showState("content");');
    // Use the controller's actual tab binding, not a duplicate routing implementation.
    const events={};
    const sandbox={document:doc,window:{addEventListener(key,fn){events[key]=fn;},location:{hash:'#electionGraphRepresentatives'},history:{replaceState(){throw Error('bookmark overwritten')}},queueMicrotask(fn){fn()},FhemniElectionRegionFilters:{SEAT_TYPES:{},normalizeState(){return{}},createDeferredAction(){return{}}}}};
    vm.runInNewContext(script,sandbox);
    sandbox.testTabs(); sandbox.loaded();
    assert.equal(nodes.get('electionGraphsPanel').hidden,false);
    assert.equal(nodes.get('electionGraphRepresentatives').focusCount,1);
    assert.equal(nodes.get('electionGraphRepresentatives').scrolled,true);
    assert.equal(typeof events.hashchange,'function','same-page archive links must update the active tab');
    sandbox.window.location.hash='#electionGraphBallots'; events.hashchange();
    assert.equal(nodes.get('electionGraphBallots').focusCount,1);
});
