const {test}=require('node:test'),assert=require('node:assert/strict');
const history=require('../../main/resources/static/js/government-history.js');
function document(){const nodes=new Map();function node(tag){return {tag,children:[],textContent:'',attributes:{},append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children;this.textContent=''},setAttribute(k,v){this.attributes[k]=v},addEventListener(k,fn){this[k]=fn},focus(){this.focused=true}};}return {createElement:node,getElementById(id){if(!nodes.has(id))nodes.set(id,node('div'));return nodes.get(id)}};}
const walk=n=>[n,...n.children.flatMap(walk)],text=n=>walk(n).map(n=>n.textContent).join(' ');
const revision=(subjectType,snapshot)=>({id:'11111111-1111-4111-8111-111111111111',subjectType,slug:'health',version:2,publishedAt:'2026-10-02T00:00:00Z',explanation:{ar:'توضيح',fr:'Explication',en:'Publication explanation'},snapshot,evidence:[]});
test('history routes validate subject selection and bound page requests',()=>{
    assert.deepEqual(history.readHistoryState('/government/history'),{type:'MANDATE',slug:'2026-2031',page:1,revision:null});
    assert.deepEqual(history.readHistoryState('/government/history?type=MEASURE&slug=health&page=2'),{type:'MEASURE',slug:'health',page:2,revision:null});
    assert.equal(history.readHistoryState('/government/history?type=PRIVATE&slug=bad%2Fpath&page=1.2').type,'MANDATE');
    assert.equal(history.readHistoryState('/government/history?page=100001').page,1);
    assert.equal(history.historyApiPath(history.readHistoryState('/government/history?type=LINK&slug=health&page=3')),'/api/catalog/government/2026-2031/history?type=LINK&slug=health&page=3');
});
test('history shows at most ten linked published snapshots and localized explanations',()=>{
    for(const locale of ['ar','fr','en']){const doc=document(),host=doc.createElement('section');history.renderHistory(host,{items:Array.from({length:12},(_,i)=>({id:'revision-'+i,version:i+1,publishedAt:'2026-10-02',explanation:{ar:'توضيح',fr:'Explication',en:'Explanation'}}))},locale,doc);
    assert.equal(walk(host).filter(n=>n.tag==='a').length,10);assert.ok(walk(host).some(n=>n.href==='/government/revisions/revision-0'));assert.ok(text(host).includes({ar:'توضيح',fr:'Explication',en:'Explanation'}[locale]));}
});
test('commitment snapshots preserve original wording, assessment and safe evidence without private fields',()=>{
    const doc=document(),host=doc.createElement('section'),data=revision('COMMITMENT',{title:{en:'Old health promise'},originalText:'Literal <script>wording</script>',originalLanguage:'ar-MA',author:{en:'Party author'},responsibleBody:{en:'Health ministry'},geographicScope:{label:{en:'National'}},deliveryAssessment:{state:'UNDERWAY',evidenceConfidence:'CORROBORATED',explanation:{en:'Earlier assessment'},reviewedAt:'2026-09-01'},target:{metric:'clinics',unit:'clinics',baseline:0,targetValue:30},deadline:'2027-01-01',links:[{relation:'MODIFIED',explanation:{en:'Earlier relationship'},programmeVersionId:'private-programme-id'}],editorId:'private-editor',observations:[]});
    data.evidence=[{id:'source',url:'javascript:alert(1)',passage:'Literal <img> quote',originalLanguage:'ar',locator:'page 3'},{id:'safe',url:'https://example.org/report',passage:'Source quote'}];
    history.renderRevision(host,data,'en',doc);const output=text(host);
    assert.match(output,/previously published snapshot/i);assert.match(output,/not the current/i);assert.match(output,/Old health promise/);assert.match(output,/Literal <script>wording<\/script>/);assert.match(output,/Earlier assessment/);assert.match(output,/Earlier relationship/);assert.match(output,/Health ministry/);assert.match(output,/30/);assert.doesNotMatch(output,/private-editor|private-programme-id|undefined|NaN/);
    assert.ok(walk(host).some(n=>n.href==='/government/commitments/health'));assert.ok(walk(host).some(n=>n.href==='https://example.org/report'));assert.ok(!walk(host).some(n=>String(n.href).startsWith('javascript:')));assert.ok(walk(host).some(n=>n.tag==='blockquote'&&n.attributes.dir==='auto'));
});
test('mandate snapshots label membership roles, dates, coverage and programme version',()=>{
    const doc=document(),host=doc.createElement('section');history.renderRevision(host,revision('MANDATE',{label:{en:'Government'},lifecycle:'PROGRAMME_PUBLISHED',verifiedAt:'2026-10-01',membership:[{name:{en:'Minister'},role:{en:'Education'},partyCode:'PI',effectiveFrom:'2026-09-01',effectiveUntil:'2026-10-01'}],programme:{version:3,title:{en:'Programme title'},publishedAt:'2026-09-15'},reviewCoverage:{reviewed:8,total:20,complete:false}}),'en',doc);
    assert.match(text(host),/Education/);assert.match(text(host),/Programme version/);assert.match(text(host),/Programme title/);assert.match(text(host),/20/);assert.match(text(host),/2026/);
});
test('measure and relationship snapshots render readable archived facts in all languages',()=>{
    for(const locale of ['ar','fr','en']){const doc=document(),host=doc.createElement('section');history.renderRevision(host,revision('MEASURE',{title:{en:'Grant'},operationalStatus:'AVAILABLE',eligibility:{en:'Residents'},steps:{en:'Apply online'},announcement:'2026-09-01',effectiveDate:'2026-10-01',officialActionUrl:'https://example.org/apply',scope:{label:{en:'Region'}}}),locale,doc);assert.match(text(host),/Residents/);assert.match(text(host),/Apply online/);assert.ok(walk(host).some(n=>n.href==='/government/measures/health'));assert.doesNotMatch(text(host),/undefined|NaN/);
    history.renderRevision(host,revision('LINK',{title:{en:'Promise relationship'},relationship:{relation:'ADOPTED',explanation:{en:'The programme adopted it'},campaignPromiseId:'campaign-id',governmentCommitmentId:'commitment-id'}}),locale,doc);assert.match(text(host),/The programme adopted it/);assert.doesNotMatch(text(host),/campaign-id|commitment-id/);}
});
test('long archived lists expose later entries through native pagination buttons',()=>{
    const doc=document(),host=doc.createElement('section');history.renderRevision(host,revision('MANDATE',{label:{en:'Government'},membership:Array.from({length:12},(_,i)=>({name:{en:'Member '+i},role:{en:'Role'}}))}),'en',doc);assert.match(text(host),/Member 8/);assert.doesNotMatch(text(host),/Member 9/);const next=walk(host).find(n=>n.tag==='button'&&n.textContent==='Next');assert.ok(next);next.click();assert.match(text(host),/Member 9/);assert.match(text(host),/Member 11/);assert.doesNotMatch(text(host),/Member 8/);
});
test('archived ministers share directory cards and logos without changing historical membership',async()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        const data=revision('MANDATE',{label:{en:'Government'},membership:[{name:{en:'Archived minister'},role:{en:'Archived role'},partyCode:'PI',effectiveFrom:'2026-09-01',effectiveUntil:'2026-10-10',evidenceIds:['archived-source']},{name:{en:'Unknown affiliation'},partyCode:null}]});
        data.evidence=[{id:'archived-source',url:'https://example.org/archive',passage:'Archived evidence'}];
        history.renderRevision(host,data,locale,doc,{parties:[{code:'PI',nameAr:'حزب الاستقلال',nameFr:'Istiqlal',symbolAsset:'/assets/parties/pi-display.png'}]});
        const cards=walk(host).filter(n=>n.className==='public-panel minister-card');
        assert.equal(cards.length,2);
        assert.ok(walk(host).some(n=>n.className==='ministers-directory'));
        assert.ok(walk(cards[0]).some(n=>n.tag==='h4'&&n.textContent==='Archived minister'));
        assert.ok(walk(cards[0]).some(n=>n.tag==='img'&&n.src==='/assets/parties/pi-display.png'));
        assert.match(text(cards[0]),new RegExp({ar:'الأعضاء الحاليون',fr:'Membres actuels',en:'Current members'}[locale]),'status must use publication date, not today');
        assert.match(text(cards[1]),new RegExp({ar:'الانتماء الحزبي غير موثق',fr:'Affiliation politique non documentée',en:'Party affiliation not documented'}[locale]));
        const sources=walk(cards[0]).find(n=>n.tag==='details');sources.open=true;await sources.toggle();
        assert.match(text(sources),/Archived evidence/);
    }
});
test('revision navigation loads party presentation metadata without fetching current membership',async()=>{
    const requests=[],renders=[],data=revision('MANDATE',{membership:[]});
    const nav=history.createNavigation({request:async path=>{requests.push(path);return path==='/api/catalog/parties'?[{code:'PI'}]:data;},render:value=>renders.push(value)});
    await nav.go('/government/revisions/example');
    assert.deepEqual(requests,['/api/catalog/government/revisions/example','/api/catalog/parties']);
    assert.deepEqual(renders.at(-1).parties,[{code:'PI'}]);
});
test('late history responses cannot replace a newer navigation result and back navigation never pushes state',async()=>{
    const pending=[],renders=[],pushes=[];const nav=history.createNavigation({request:path=>new Promise((resolve,reject)=>pending.push({path,resolve,reject})),render:value=>renders.push(value),push:url=>pushes.push(url)});
    const old=nav.go('/government/history?page=1',true),current=nav.go('/government/history?page=2',false);pending[1].resolve({items:[],page:2,totalItems:20});await current;pending[0].resolve({items:[{id:'old'}],page:1,totalItems:20});await old;assert.equal(renders.at(-1).data.page,2);assert.equal(pushes.length,1);
});
test('not-found history and network failure have distinct states and no stale snapshot',async()=>{
    for(const status of [404,500]){const renders=[],nav=history.createNavigation({request:async()=>{throw {status}},render:value=>renders.push(value)});await nav.go('/government/revisions/11111111-1111-4111-8111-111111111111');assert.equal(renders.at(-1).loadState,status===404?'missing':'error');assert.equal(renders.at(-1).data,null);}
});
test('out-of-range bookmarks load the final available page and replace the URL',async()=>{
    const paths=[],renders=[],replacements=[];const nav=history.createNavigation({request:async path=>{paths.push(path);return path.endsWith('page=99')?{items:[],page:99,totalItems:12}:{items:[{id:'last'}],page:2,totalItems:12}},render:value=>renders.push(value),replace:url=>replacements.push(url)});
    await nav.go('/government/history?type=MEASURE&slug=grant&page=99');assert.equal(paths.length,2);assert.match(paths[1],/page=2$/);assert.deepEqual(replacements,['/government/history?type=MEASURE&slug=grant&page=2']);assert.equal(renders.at(-1).data.items[0].id,'last');assert.equal(renders.at(-1).state.page,2);
});
