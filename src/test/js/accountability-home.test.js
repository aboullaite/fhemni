const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
function load(){
    const sandbox={module:{exports:{}},require:id=>require(path.resolve(__dirname,'../../main/resources/static/js',id)),window:{},document:{addEventListener(){}}};
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/landing.js'),'utf8'),sandbox);
    assert.equal(typeof sandbox.module.exports.renderHome,'function','home renderer exists');
    return sandbox.module.exports;
}
function doc(){
    const ids=new Map();
    function node(tag){return {tag,children:[],textContent:'',hidden:false,attributes:{},append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children;this.textContent=''},setAttribute(key,value){this.attributes[key]=value}};}
    return {createElement:node,getElementById(id){if(!ids.has(id))ids.set(id,node('div'));return ids.get(id)},ids};
}
const text=node=>[node.textContent,...node.children.map(text)].join(' ');
test('home uses rich publication cards without disguising catalogue dates as delivery',()=>{
    const {renderHome}=load(),document=doc();
    renderHome({locale:'en',loadState:'ready',publications:[{title:{en:'A published entry'},subjectType:'COMMITMENT',subjectId:'entry',publishedAt:'2026-10-23T00:00:00Z',entry:{slug:'entry',originalText:'A source excerpt',programmeContext:{deadlineText:'2027'}}}]},document);
    const output=text(document.getElementById('homeUpdates'));
    assert.match(output,/A source excerpt/);assert.match(output,/2027/);assert.match(output,/Catalogue updates/);
});
test('home offers six topic previews and keeps every remaining topic in the disclosure',()=>{
    const {renderHome}=load(),document=doc();
    const topics=Array.from({length:29},(_,i)=>({code:'topic-'+i,label:{en:'Topic '+i},selectable:true}));
    renderHome({locale:'en',topics},document);
    assert.equal(document.getElementById('homeTopics').children.length,6);
    assert.equal(document.getElementById('homeMoreTopics').children.length,23);
    assert.equal(document.getElementById('homeTopicsDisclosure').hidden,false);
    const links=[...document.getElementById('homeTopics').children,...document.getElementById('homeMoreTopics').children];
    assert.equal(new Set(links.map(x=>x.href)).size,29);
    assert.ok(links.every(x=>x.href.includes('origin=GOVERNMENT')));
    renderHome({locale:'en',topics:topics.slice(0,2)},document);
    assert.equal(document.getElementById('homeTopicsDisclosure').hidden,true);
});
test('programme snippets are bounded, linked and keep unverified source and unassessed delivery honest',()=>{
    const {renderHome}=load();
    for(const locale of ['ar','fr','en']){
        const document=doc();
        renderHome({locale,programme:Array.from({length:10},(_,i)=>({slug:'entry-'+i,title:{en:'Programme '+i},originalText:'x'.repeat(500),programmeContext:{sourceStatus:'MIRROR_UNAUTHENTICATED'}}))},document);
        const cards=document.getElementById('homeProgrammeCards').children;
        assert.equal(cards.length,3);
        const walk=n=>[n,...n.children.flatMap(walk)];
        assert.ok(walk(cards[0]).some(n=>n.href==='/government/commitments/entry-0'));
        assert.ok(!text(cards[0]).includes('x'.repeat(200)));
        assert.ok(text(document.getElementById('homeProgrammeNote')).trim().length>0);
        assert.ok(!text(cards[0]).includes('Delivered'));
        renderHome({locale,programmeFailed:true},document);
        assert.equal(document.getElementById('programmeRetry').hidden,false);
        assert.ok(!text(document.getElementById('homeProgrammeCards')).includes('Programme 0'));
    }
});
test('untranslated programme titles retain their source language in a French interface',()=>{
    const {renderHome}=load(),document=doc();
    renderHome({locale:'fr',programme:[{slug:'p',title:{ar:'طبيب الأسرة',fr:'طبيب الأسرة'},originalLanguage:'ar'}]},document);
    const walk=n=>[n,...n.children.flatMap(walk)];
    const title=walk(document.getElementById('homeProgrammeCards')).find(n=>n.tag==='h3').children[0];
    assert.equal(title.attributes.dir,'auto');
    assert.equal(title.attributes.lang,'ar');
});
test('published home links to the reviewed programme and shows only sourced updates and deadlines',()=>{
    const {renderHome}=load(),document=doc();
    renderHome({locale:'en',loadState:'ready',mandate:{lifecycle:'PROGRAMME_PUBLISHED',verifiedAt:'2026-10-01T00:00:00Z',programme:{id:'programme'},reviewCoverage:{reviewed:4,total:10,complete:false},recentUpdates:[{subjectType:'COMMITMENT',subjectId:'commitment',title:{en:'Reviewed update'},publishedAt:'2026-10-01T00:00:00Z'}]},deadlines:[{slug:'source-deadline',title:{en:'Sourced target'},deadline:'2027-01-01'}]},document);
    assert.equal(document.getElementById('homeProgrammeLink').href,'/government?tab=programme&origin=GOVERNMENT');
    assert.match(text(document.getElementById('mandateCoverage')),/4.*10/);
    assert.match(text(document.getElementById('homeUpdates')),/Reviewed update/);
    assert.match(text(document.getElementById('homeDeadlines')),/Sourced target/);
});
test('home omits the verification timestamp while retaining the verified programme state',()=>{
    for(const locale of ['ar','fr','en']){
        const {renderHome}=load(),document=doc();
        renderHome({locale,loadState:'ready',mandate:{lifecycle:'PROGRAMME_PUBLISHED',verifiedAt:'2026-10-23T00:00:00Z',programme:{id:'programme'}}},document);
        const output=[...document.ids.values()].map(text).join(' ');
        assert.doesNotMatch(output,/آخر تحقق|Dernière vérification|Last verified/);
        assert.equal(document.getElementById('homeProgrammeLink').href,'/government?tab=programme&origin=GOVERNMENT');
    }
});
test('formation and unavailable homes do not render invented membership, dates or delivery scores',()=>{
    const {renderHome}=load();
    for(const locale of ['ar','fr','en']) for(const loadState of ['ready','unavailable']){
        const document=doc();renderHome({locale,loadState,mandate:{lifecycle:'FORMATION',verifiedAt:null,membership:[{name:'UNVERIFIED PERSON'}]},topics:[],promises:[]},document);
        const output=[...document.ids.values()].map(text).join(' ');
        assert.ok(!output.includes('UNVERIFIED PERSON'));assert.ok(!output.includes('2031-01-01'));
        assert.ok(!output.includes('NaN'));assert.ok(!output.includes('undefined'));
        assert.ok(text(document.getElementById('mandateStatus')).trim().length>0);
        if(loadState==='unavailable')assert.equal(document.getElementById('homeRetry').hidden,false);
    }
});
test('published promises remain campaign feasibility, never government delivery',()=>{
    const {renderHome}=load();const document=doc();
    renderHome({locale:'en',loadState:'ready',mandate:null,topics:[],promises:[{slug:'test',partyCode:'P',title:{en:'<script>unsafe</script>'},verdict:'HARD',assessmentSummary:{en:'A feasibility review'}}]},document);
    const output=text(document.getElementById('featuredPromises'));
    assert.match(output,/feasibility/i);assert.match(output,/difficult/i);assert.ok(!output.includes('Delivered'));
    assert.match(output,/<script>unsafe<\/script>/,'source wording remains text, not HTML');
});
test('unverified programme metadata cannot override a pending home state',()=>{
    const {renderHome}=load();const document=doc();
    renderHome({locale:'en',loadState:'ready',mandate:{lifecycle:'PROGRAMME_PUBLISHED',verifiedAt:null,programme:{publishedAt:'UNVERIFIED DATE'}},topics:[],promises:[]},document);
    assert.ok(!text(document.getElementById('mandateStatus')).includes('UNVERIFIED DATE'));
});

test('real party names and verified logos are reused rather than code badges',()=>{
    const {renderHome}=load();const document=doc();
    renderHome({locale:'ar',loadState:'ready',topics:[],parties:[{code:'P',nameAr:'اسم الحزب',nameFr:'Le parti',symbolAsset:'/assets/parties/p.png',symbolVerified:true}],promises:[{slug:'test',partyCode:'P',title:{ar:'وعد'},verdict:'HARD'}]},document);
    const card=document.getElementById('featuredPromises').children[0];
    assert.match(text(card),/اسم الحزب/);
    const walk=node=>[node,...node.children.flatMap(walk)];
    assert.equal(walk(card).find(node=>node.tag==='img')?.src,'/assets/parties/p.png');
});
test('pending fetches are loading rather than empty; homepage uses top-level selectable topics',()=>{
    const {renderHome}=load();const document=doc();
    renderHome({locale:'en',promisesLoading:true,topicsLoading:true},document);
    assert.match(text(document.getElementById('featuredPromises')),/Loading/);
    assert.match(text(document.getElementById('homeTopics')),/Loading/);
    renderHome({locale:'en',topics:[{code:'health',label:{en:'Health'},selectable:true,parentCode:null},{code:'schools',label:{en:'Schools'},parentCode:'education',selectable:true},{code:'other',label:{en:'Other'},selectable:false,parentCode:null}]},document);
    assert.equal(document.getElementById('homeTopics').children.length,1);
    assert.match(text(document.getElementById('homeTopics')),/Health/);
});
test('every feasibility verdict is meaningful in all locales, including insufficient evidence',()=>{
    const {renderHome}=load();
    for(const locale of ['ar','fr','en'])for(const verdict of ['POSSIBLE','HARD','NOT_ACHIEVABLE','INSUFFICIENT_DATA']){
        const document=doc();renderHome({locale,promises:[{slug:'p',title:{en:'Promise'},verdict}]},document);
        assert.ok(!text(document.getElementById('featuredPromises')).includes('—'));
    }
});
test('latest request wins and failures are reported instead of inventing a mandate',async()=>{
    const {createLatestRequest}=require('../../main/resources/static/js/government-api.js');
    const waiting=[];const applied=[];
    const refresh=createLatestRequest(()=>new Promise((resolve,reject)=>waiting.push({resolve,reject})),(value,error)=>applied.push({value,error}));
    const old=refresh(),newer=refresh();waiting[1].resolve({id:'current'});await newer;
    waiting[0].resolve({id:'stale'});await old;
    assert.equal(applied.length,1);assert.equal(applied[0].value.id,'current');
    const failed=refresh();waiting[2].reject(new Error('offline'));await failed;
    assert.equal(applied[1].value,null);assert.equal(applied[1].error.message,'offline');
});
