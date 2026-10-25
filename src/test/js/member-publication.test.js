const {test}=require('node:test'),assert=require('node:assert/strict');
test('editorial member report preview is literal text and never includes a private conversation',()=>{
    const {reportText}=require('../../main/resources/static/js/member-publication.js');const text=reportText({report:{title:'<img onerror=bad()>',summary:'Source summary'},conversation:['Private question'],providerInteractionId:'secret'});assert.ok(text.includes('<img onerror=bad()>'));assert.ok(!text.includes('Private question'));assert.ok(!text.includes('secret'));
});
test('switching publication requests clears prior actions even when opening fails',()=>{
    const {createSelection}=require('../../main/resources/static/js/member-publication.js');
    const state=createSelection(); const a=state.begin(); state.resolve(a,{request:{id:'A'}});
    assert.equal(state.value.request.id,'A'); const b=state.begin(); assert.equal(state.value,null);
    state.resolve(a,{request:{id:'A'}}); assert.equal(state.value,null);
    state.fail(b); assert.equal(state.value,null);
});

function documentFixture(){
    const doc={activeElement:null,listeners:{},nodes:new Map(),addEventListener(name,fn){this.listeners[name]=fn}};
    function node(tag){return {tag,ownerDocument:doc,children:[],attributes:{},textContent:'',value:'',append(...values){this.children.push(...values)},replaceChildren(...values){this.children=values},setAttribute(name,value){this.attributes[name]=String(value)},getAttribute(name){return this.attributes[name]??null},addEventListener(name,fn){this['on'+name]=fn},focus(){doc.activeElement=this},querySelectorAll(selector){return walk(this).slice(1).filter(n=>selector==='button'?n.tag==='button':selector==='a'?n.tag==='a':n.tag===selector)},querySelector(selector){return this.querySelectorAll(selector)[0]||null}};}
    doc.createElement=node;doc.getElementById=id=>{if(!doc.nodes.has(id))doc.nodes.set(id,node('div'));return doc.nodes.get(id)};return doc;
}
const walk=node=>[node,...node.children.flatMap(walk)];
test('report review exposes labelled source evidence without HTML, unsafe links or private job fields',()=>{
    const {renderReport}=require('../../main/resources/static/js/member-publication.js'),doc=documentFixture(),host=doc.createElement('section');
    renderReport(host,{language:'ar',videoUrl:'https://www.youtube.com/watch?v=abcdefghijk',conversation:['PRIVATE'],providerInteractionId:'SECRET',report:{title:'عنوان',summary:'<img onerror=bad()>',detailedSummary:'التفاصيل',claims:[{statement:'Claim text',speaker:'Speaker',startSeconds:12,verdict:'UNVERIFIABLE',explanation:'Needs evidence',sources:[{title:'Official evidence',url:'https://example.org/evidence',publishedDate:'2026-10-04'},{title:'Unsafe',url:'javascript:bad()'}]}]}},'en',doc);
    const nodes=walk(host);assert.ok(nodes.some(n=>n.tag==='h4'&&n.textContent==='Summary'));assert.ok(nodes.some(n=>n.textContent==='<img onerror=bad()>'));assert.ok(nodes.some(n=>n.tag==='a'&&n.href==='https://example.org/evidence'));assert.ok(nodes.some(n=>n.tag==='a'&&n.href==='https://www.youtube.com/watch?v=abcdefghijk'));assert.ok(nodes.some(n=>n.lang==='ar'&&n.dir==='auto'));assert.ok(!nodes.some(n=>n.href?.startsWith('javascript:')));assert.ok(!nodes.some(n=>['PRIVATE','SECRET'].includes(n.textContent)));assert.ok(!nodes.some(n=>n.tag==='pre'));
});
function publicationHarness(request){
    const fs=require('node:fs'),vm=require('node:vm'),doc=documentFixture();
    const root={document:doc,FhemniFollowing:{request},FhemniGovernmentModel:require('../../main/resources/static/js/government-model.js'),FhemniI18n:{locale:()=> 'en',t:key=>key==='label.UNVERIFIABLE'?'Unverifiable':key},FhemniUI:{...require('../../main/resources/static/js/public-ui.js'),createPager:options=>require('../../main/resources/static/js/public-ui.js').createPager({...options,document:doc})},confirm:()=>true};
    vm.runInNewContext(fs.readFileSync(require('node:path').resolve(__dirname,'../../main/resources/static/js/member-publication.js'),'utf8'),{window:root,document:doc,URL,queueMicrotask});doc.listeners.DOMContentLoaded();return {doc,root};
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('queue names each report and failed opening cannot leave an earlier report actionable',async()=>{
    const rows=[{id:'A',state:'REQUESTED',title:'Report A',videoUrl:'https://www.youtube.com/watch?v=aaaaaaaaaaa',requestedAt:'2026-10-04T10:00:00Z'},{id:'B',state:'REQUESTED',title:'Report B',videoUrl:'https://www.youtube.com/watch?v=bbbbbbbbbbb',requestedAt:'2026-10-04T10:00:00Z'}];
    const {doc}=publicationHarness(async path=>{if(path.endsWith('?page=1'))return {items:rows,totalItems:2};if(path.endsWith('/A'))return {request:rows[0],report:{report:{title:'Report A',summary:'Summary'}}};throw Error('offline');});await flush();
    const host=doc.getElementById('memberPublication');let buttons=host.querySelectorAll('button');assert.ok(buttons[0].getAttribute('aria-label')?.includes('Report A'));assert.ok(buttons[1].getAttribute('aria-label')?.includes('Report B'));assert.ok(walk(host).some(n=>n.href===rows[0].videoUrl));await buttons[0].onclick();await host.querySelectorAll('button')[1].onclick();const preview=doc.getElementById('memberPublicationPreview');assert.equal(preview.querySelectorAll('button').length,0);assert.ok(!walk(preview).some(n=>n.textContent==='Report A'));assert.match(doc.getElementById('memberPublicationStatus').textContent,/could not|failed/i);
});
test('review and publication announce completion and restore focus to the current preview heading',async()=>{
    const row={id:'A',state:'REQUESTED',title:'Report A',videoUrl:'https://www.youtube.com/watch?v=aaaaaaaaaaa',requestedAt:'2026-10-04T10:00:00Z'};
    const {doc}=publicationHarness(async(path,method)=>{if(method==='POST'){row.state=path.endsWith('/publish')?'PUBLISHED':'REVIEWED';if(row.state==='PUBLISHED')row.publicPath='/videos/reviewed-video';return {derivativeId:'derived'};}if(path.endsWith('?page=1'))return {items:[{...row}],totalItems:1};return {request:{...row},report:{language:'en',videoUrl:row.videoUrl,report:{title:'Report A',summary:'Summary'}}};});await flush();await doc.getElementById('memberPublication').querySelector('button').onclick();
    const preview=doc.getElementById('memberPublicationPreview'),note=preview.querySelector('textarea');note.value='Sources checked';note.oninput();await preview.querySelectorAll('button').find(n=>n.textContent==='Approve review').onclick();assert.equal(doc.activeElement,preview.querySelector('h3'));assert.match(doc.getElementById('memberPublicationStatus').textContent,/review.*approved/i);assert.ok(walk(preview).some(n=>n.textContent==='Reviewed'));
    await preview.querySelectorAll('button').find(n=>n.textContent==='Publish separate version').onclick();assert.equal(doc.activeElement,preview.querySelector('h3'));assert.match(doc.getElementById('memberPublicationStatus').textContent,/published/i);assert.ok(walk(preview).some(n=>n.href==='/videos/reviewed-video'));
});
test('preview uses the existing translated verdict labels',async()=>{
    const row={id:'A',state:'REQUESTED',title:'Report A',requestedAt:'2026-10-04T10:00:00Z'};
    const {doc}=publicationHarness(async path=>path.includes('?')?{items:[row],totalItems:1}:{request:row,report:{language:'en',report:{title:'Report A',summary:'Summary',claims:[{statement:'Claim',verdict:'UNVERIFIABLE',sources:[]}]}}});await flush();await doc.getElementById('memberPublication').querySelector('button').onclick();assert.ok(walk(doc.getElementById('memberPublicationPreview')).some(n=>n.textContent==='Assessment: Unverifiable'));
});
