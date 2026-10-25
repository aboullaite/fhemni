const {test}=require('node:test'),assert=require('node:assert/strict');
let links;try{links=require('../../main/resources/static/js/promise-government-links.js');}catch(error){if(error.code!=='MODULE_NOT_FOUND')throw error;links={};}
function document(){const doc={activeElement:null};doc.createElement=tag=>({tag,children:[],attributes:{},textContent:'',append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children;this.textContent=''},setAttribute(k,v){this.attributes[k]=String(v)},addEventListener(k,fn){this[k]=fn},focus(){doc.activeElement=this},querySelectorAll(selector){return walk(this).filter(n=>selector==='details'?n.tag==='details':selector==='[id]'?!!n.id:false)}});return doc;}
const walk=node=>[node,...node.children.flatMap(walk)],text=node=>walk(node).map(n=>n.textContent).join(' ');
const relationship=(overrides={})=>({campaignPromiseId:'campaign-id',governmentCommitmentId:'government-id',relation:'ADOPTED',programmeVersionId:'programme-id',explanation:{ar:'تفسير مراجع',fr:'Explication examinée',en:'Reviewed explanation'},evidenceIds:['source'],...overrides});
const fixture=(rows=[relationship()])=>({id:'campaign-id',origin:'CAMPAIGN',links:rows,evidence:[{id:'source',url:'https://example.org/programme',passage:'Literal <script>passage</script>',locator:'page 4',originalLanguage:'ar-MA',publishedAt:'2026-10-01T00:00:00Z',retrievedAt:'2026-10-02T00:00:00Z'},{id:'unrelated',url:'https://example.org/unrelated',passage:'Must not appear'}]});
test('reviewed relationships remain distinct from feasibility and use exact safe evidence',()=>{
 assert.equal(typeof links.renderRelationships,'function');
 for(const locale of ['ar','fr','en']){const doc=document(),host=doc.createElement('section');links.renderRelationships(host,fixture(),{locale,doc,programme:{id:'programme-id',version:2,title:{en:'Published programme'}}});
  const output=text(host);assert.match(output,/Published programme/);assert.match(output,/Literal <script>passage<\/script>/);assert.doesNotMatch(output,/Must not appear|undefined/);assert.ok(walk(host).some(n=>n.href==='/government/commitments/government-id'));assert.ok(walk(host).some(n=>n.href==='https://example.org/programme'));assert.ok(walk(host).some(n=>n.tag==='blockquote'&&n.attributes.lang==='ar-MA'&&n.attributes.dir==='auto'));
 }
 const doc=document(),host=doc.createElement('section'),data=fixture();data.evidence[0].url='javascript:alert(1)';links.renderRelationships(host,data,{locale:'en',doc});assert.ok(!walk(host).some(n=>String(n.href).startsWith('javascript:')));
});
test('earlier and unknown programme versions are never presented as current',()=>{
 assert.equal(typeof links.renderRelationships,'function');const doc=document(),host=doc.createElement('section');
 links.renderRelationships(host,fixture(),{locale:'en',doc,programme:{id:'different',version:3,title:{en:'Current title'}}});assert.match(text(host),/Earlier programme version/);assert.doesNotMatch(text(host),/Current title/);assert.match(text(host),/programme-id/);
 links.renderRelationships(host,fixture(),{locale:'en',doc});assert.match(text(host),/Reviewed programme version/);assert.doesNotMatch(text(host),/Earlier|Current programme/);
});
test('no review is not treated as not identified and null targets never create links',()=>{
 assert.equal(typeof links.renderRelationships,'function');const doc=document(),host=doc.createElement('section');links.renderRelationships(host,fixture([]),{locale:'en',doc});assert.match(text(host),/No reviewed relationship/);assert.doesNotMatch(text(host),/Not identified/);
 links.renderRelationships(host,fixture([relationship({governmentCommitmentId:null,relation:'NOT_IDENTIFIED'})]),{locale:'en',doc});assert.match(text(host),/Not identified in the reviewed programme/);assert.equal(walk(host).filter(n=>n.href?.startsWith('/government/commitments/')).length,0);
});
test('relationship list pages at ten without altering feasibility content',()=>{
 assert.equal(typeof links.mount,'function');const doc=document(),host=doc.createElement('section'),rows=Array.from({length:12},(_,i)=>relationship({explanation:{en:'Relationship '+i}}));const page=links.mount(host,{doc,locale:()=> 'en',api:{},slug:'health'});page.render({state:'ready',data:fixture(rows)});assert.match(text(host),/Relationship 9/);assert.doesNotMatch(text(host),/Relationship 10/);walk(host).find(n=>n.tag==='button'&&n.textContent==='Next').click();assert.match(text(host),/Relationship 10/);assert.doesNotMatch(text(host),/Relationship 9/);
});
test('campaign lookup distinguishes unreviewed from retryable failure while optional metadata cannot hide reviews',async()=>{
 assert.equal(typeof links.mount,'function');const doc=document(),host=doc.createElement('section');let failure=404;
 const page=links.mount(host,{doc,locale:()=> 'en',slug:'health care',api:{fetchCommitment:async slug=>{assert.equal(slug,'campaign-health care');if(failure)throw {status:failure};return fixture();},fetchMandate:async()=>{throw Error('metadata unavailable')}}});
 await page.load();assert.match(text(host),/No reviewed relationship/);failure=500;await page.load();assert.match(text(host),/could not be loaded/);assert.ok(walk(host).some(n=>n.tag==='button'&&!n.hidden&&n.textContent==='Try again'));failure=null;await page.load();assert.match(text(host),/Reviewed explanation/);assert.match(text(host),/Reviewed programme version/);
});
test('locale changes retain source disclosure, keyboard focus and selected relationship page',()=>{
 const doc=document(),host=doc.createElement('section');let locale='en';const page=links.mount(host,{doc,locale:()=>locale,slug:'health',api:{}});page.render({state:'ready',data:fixture(Array.from({length:12},(_,i)=>relationship({explanation:{en:'Entry '+i,fr:'Entrée '+i}})))});
 walk(host).find(n=>n.textContent==='Next').click();const details=walk(host).find(n=>n.tag==='details');details.open=true;walk(details).find(n=>n.tag==='summary').focus();locale='fr';page.render();assert.match(text(host),/Entrée 10/);assert.doesNotMatch(text(host),/Entrée 9/);assert.equal(walk(host).find(n=>n.tag==='details').open,true);assert.equal(doc.activeElement.textContent,'Version et sources');
});
test('slow optional programme metadata does not delay reviews or overwrite a later retry',async()=>{
 const doc=document(),host=doc.createElement('section'),pending=[];let request=0;const page=links.mount(host,{doc,locale:()=> 'en',slug:'health',api:{fetchCommitment:async()=>fixture([relationship({explanation:{en:++request===1?'First review':'New review'}})]),fetchMandate:()=>new Promise(resolve=>pending.push(resolve))}});
 const first=page.load();await new Promise(resolve=>setImmediate(resolve));assert.match(text(host),/First review/);const second=page.load();await new Promise(resolve=>setImmediate(resolve));pending[1]({programme:{id:'programme-id',title:{en:'New metadata'},version:2}});await second;pending[0]({programme:{id:'programme-id',title:{en:'Old metadata'},version:1}});await first;assert.match(text(host),/New review/);assert.match(text(host),/New metadata/);assert.doesNotMatch(text(host),/Old metadata/);
});
test('retry returns keyboard focus to the panel after its hidden button loses focus',async()=>{
 const doc=document(),host=doc.createElement('section');let resolve;const page=links.mount(host,{doc,locale:()=> 'en',slug:'health',api:{fetchCommitment:()=>new Promise(done=>resolve=done),fetchMandate:async()=>({programme:null})}});page.render({state:'error'});walk(host).find(n=>n.tag==='button').focus();const retry=page.load();doc.activeElement=null;resolve(fixture());await retry;assert.equal(doc.activeElement?.id,'promiseGovernmentTitle');
});
