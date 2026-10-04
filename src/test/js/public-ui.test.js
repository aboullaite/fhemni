const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
function ui(){
    const file=path.resolve(__dirname,'../../main/resources/static/js/public-ui.js');
    assert.ok(fs.existsSync(file),'shared public controls exist');
    return require(file);
}
test('pagination never exceeds ten and clamps invalid or empty pages',()=>{
    const {paginate}=ui(); const values=Array.from({length:23},(_,i)=>i);
    assert.deepEqual(paginate(values,1),{items:[0,1,2,3,4,5,6,7,8,9],page:1,pages:3,total:23});
    assert.deepEqual(paginate(values,99).items,[20,21,22]);
    for(const page of [0,-1,NaN,Infinity,'foo',1.5]) assert.equal(paginate(values,page).page,1);
    assert.deepEqual(paginate([],3),{items:[],page:1,pages:1,total:0});
});
test('Arabic counts use all plural categories; other languages retain their own nouns',()=>{
    const {countPhrase}=ui();
    const expected=['لا وعود','وعد واحد','وعدان','3 وعود','11 وعداً','100 وعد'];
    [0,1,2,3,11,100].forEach((value,i)=>assert.equal(countPhrase(value,'promise','ar'),expected[i]));
    assert.equal(countPhrase(2,'promise','ar',{oblique:true}),'وعدين');
    assert.equal(countPhrase(2,'promise','en'),'2 promises');
    assert.equal(countPhrase(2,'promise','fr'),'2 promesses');
});
test('repeat announcements clear and restore but cannot overwrite a newer status',()=>{
    const {announceStatus}=ui(); const node={textContent:'Ready'};const callbacks=[];
    announceStatus(node,'Ready',fn=>callbacks.push(fn)); assert.equal(node.textContent,'');
    announceStatus(node,'Changed',fn=>callbacks.push(fn));callbacks[0]();
    assert.equal(node.textContent,'Changed');
    announceStatus(node,'Changed',fn=>callbacks.push(fn)); callbacks[1](); assert.equal(node.textContent,'Changed');
});
function node(id) {return {id,attributes:{},listeners:{},hidden:false,
    getAttribute(key){return this.attributes[key]},setAttribute(key,value){this.attributes[key]=value},
    addEventListener(key,fn){this.listeners[key]=fn},removeEventListener(key){delete this.listeners[key]},
    focus(){this.focused=true;}};}
test('tabs hide inactive panels, handle RTL keyboard navigation, and preserve elements during locale changes',()=>{
    const {mountTabs}=ui(); const tabs=['one','two','three'].map(node),panels=tabs.map(t=>node(t.id+'Panel'));
    tabs.forEach((tab,i)=>tab.attributes['aria-controls']=panels[i].id);
    const doc={documentElement:{dir:'rtl'},getElementById:id=>panels.find(p=>p.id===id)};
    const root={ownerDocument:doc,querySelectorAll:()=>tabs};const changes=[];
    const mounted=mountTabs(root,{selected:'one',onChange:id=>changes.push(id)});
    assert.equal(panels[0].hidden,false);assert.equal(panels[1].hidden,true);
    tabs[0].listeners.keydown({key:'ArrowLeft',preventDefault(){}});
    assert.equal(panels[1].hidden,false);assert.equal(tabs[1].focused,true);assert.deepEqual(changes,['two']);
    doc.documentElement.dir='ltr'; tabs[1].listeners.keydown({key:'ArrowRight',preventDefault(){}});
    assert.equal(tabs[2].getAttribute('aria-selected'),'true');
    mounted.select('one'); assert.equal(panels[0].hidden,false);
    const inside={};doc.activeElement=inside;panels[0].contains=value=>value===inside;
    mounted.select('two');assert.equal(tabs[1].focused,true);
    assert.equal(panels[0].hidden,true);
    mounted.destroy();assert.equal(tabs[0].listeners.click,undefined);
});

test('pager disables boundaries and reports requested pages without changing table data',()=>{
    const {createPager}=ui();
    const doc={createElement(tag){return {...node(tag),children:[],append(...children){this.children.push(...children)}};}};
    const changes=[];
    const pager=createPager({page:1,pages:3,total:23,locale:'fr',onChange:page=>changes.push(page),document:doc});
    const [previous,status,next]=pager.children;
    assert.equal(previous.disabled,true);assert.equal(next.disabled,false);
    previous.listeners.click();next.listeners.click();assert.deepEqual(changes,[2]);
    assert.match(status.textContent,/1 \/ 3/);assert.match(status.textContent,/23 résultats/);
    assert.match(status.textContent,/1–10 sur 23/);
    assert.equal(typeof pager.update,'function');
    pager.update({page:2,pages:3,total:23,locale:'fr'});
    assert.equal(pager.children[1],status,'retain the live region');
    assert.match(status.textContent,/11–20 sur 23/);
    previous.listeners.click();assert.deepEqual(changes,[2,1]);
    const empty=createPager({page:1,pages:1,total:0,onChange(){throw Error('empty page navigation')},document:doc});
    empty.children[2].listeners.click();assert.equal(empty.children[2].disabled,true);
});
