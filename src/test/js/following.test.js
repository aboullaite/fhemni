const {test}=require('node:test'),assert=require('node:assert/strict');
test('following tab and both pages survive URL round trips',()=>{
 const {readFollowState,followStateUrl}=require('../../main/resources/static/js/following.js');
 const state={selected:'followItemsTab',page:3,entryPage:2};assert.deepEqual(readFollowState(followStateUrl(state)),state);
 assert.deepEqual(readFollowState('?tab=bad&page=-1&followingPage=oops'),{selected:'followUpdatesTab',page:1,entryPage:1});
});
test('follow targets and material update links are strictly scoped',()=>{
    const {targetPath,updatePath}=require('../../main/resources/static/js/following.js');
    assert.equal(targetPath({type:'TOPIC',id:'HEALTH'}),'/api/account/follows/TOPIC/HEALTH');
    assert.throws(()=>targetPath({type:'USER',id:'other'}));
    assert.equal(updatePath({subjectType:'MEASURE',subjectId:'id'}),'/government/measures/id');
});
test('contextual tracker follows use only the active scope',()=>{
    const {contextTarget}=require('../../main/resources/static/js/following.js');
    assert.deepEqual(contextTarget({tab:'regions',region:'r',topic:'HEALTH'}),{type:'REGION',id:'r'});
    assert.deepEqual(contextTarget({tab:'delivery',topic:'HEALTH'}),{type:'TOPIC',id:'HEALTH'});
    assert.equal(contextTarget({tab:'overview',topic:'HEALTH'}),null);
});
test('private feed text is not interpreted as HTML and tables never exceed ten records',()=>{
    const {renderUpdates}=require('../../main/resources/static/js/following.js');
    const node=tag=>({tag,children:[],textContent:'',append(...v){this.children.push(...v)},replaceChildren(...v){this.children=v}}),doc={createElement:node},host=node('div');
    renderUpdates(host,Array.from({length:12},()=>({title:{en:'<script>bad()</script>'},subjectType:'COMMITMENT',subjectId:'x',publishedAt:'2026-10-04T10:00:00Z'})),'en',doc);
    assert.equal(host.children.length,10);assert.equal(host.children[0].children[0].textContent,'<script>bad()</script>');assert.equal(host.children[0].children[0].tag,'a');
});
test('correction review retains untrusted text literally and never offers automatic publication',()=>{
    const {renderCorrections}=require('../../main/resources/static/js/government-corrections.js');const node=tag=>({tag,children:[],append(...n){this.children.push(...n)},replaceChildren(...n){this.children=n}}),doc={createElement:node},host=node('div');renderCorrections(host,[{id:'id',status:'OPEN',text:'<script>bad()</script>',evidenceUrl:'javascript:bad()'}],'en',doc,()=>{});const walk=n=>[n,...n.children.flatMap(walk)];assert.ok(walk(host).some(n=>n.textContent==='<script>bad()</script>'));assert.ok(!walk(host).some(n=>n.href==='javascript:bad()'));assert.ok(!walk(host).some(n=>n.textContent==='Publish'));
});
test('changing locale retains a private correction draft and names a regional follow explicitly',async()=>{
    const fs=require('node:fs'),vm=require('node:vm');let locale='en';const doc={listeners:{},addEventListener(key,fn){this.listeners[key]=fn}};function element(tag){return {tag,children:[],ownerDocument:doc,isConnected:true,value:'',setAttribute(){},append(...n){this.children.push(...n)},replaceChildren(...n){this.children=n},focus(){}};}doc.createElement=element;
    let sessionCalls=0,resolveSubmission,resolveFollow;const window={FhemniFollowing:{...require('../../main/resources/static/js/following.js'),request:async(path,method)=>method==='POST'?new Promise(resolve=>resolveSubmission=resolve):method==='PUT'?new Promise(resolve=>resolveFollow=resolve):({following:false})},FhemniGovernmentModel:require('../../main/resources/static/js/government-model.js'),FhemniI18n:{locale:()=>locale},FhemniAuth:{session:()=>Promise.reject(Error('stale')),refreshSession:async()=>{sessionCalls++;return {authenticated:true}}},FhemniUI:{announceStatus(){}},location:{pathname:'/government/measures/x',search:''}};
    vm.runInNewContext(fs.readFileSync(require('node:path').resolve(__dirname,'../../main/resources/static/js/follow-controls.js'),'utf8'),{window});const host=element('section');window.FhemniFollowControls.mount(host,{type:'REGION',id:'region',label:{en:'Test region',fr:'Région test'},correctionType:'MEASURE',correctionId:'id'});await new Promise(resolve=>setImmediate(resolve));const walk=n=>[n,...n.children.flatMap(walk)];
    assert.ok(walk(host).some(n=>n.tag==='button'&&n.textContent.includes('Test region')));
    const input=walk(host).find(n=>n.tag==='input'),text=walk(host).find(n=>n.tag==='textarea');input.value='https://example.org/proof';input.oninput();text.value='Unsent review';text.oninput();locale='fr';doc.listeners['fhemni:localechange']();assert.ok(walk(host).some(n=>n.value==='Unsent review'));assert.ok(walk(host).some(n=>n.value==='https://example.org/proof'));
    assert.equal(sessionCalls,1);const pending=walk(host).find(n=>n.tag==='form').onsubmit({preventDefault(){}});locale='en';doc.listeners['fhemni:localechange']();assert.equal(walk(host).find(n=>n.type==='submit').disabled,true);resolveSubmission({});await pending;assert.equal(walk(host).find(n=>n.tag==='textarea').value,'');assert.equal(walk(host).find(n=>n.type==='submit').disabled,false);
    const pendingFollow=walk(host).find(n=>n.tag==='button'&&n.type==='button').onclick();locale='fr';doc.listeners['fhemni:localechange']();resolveFollow({following:true});await pendingFollow;const current=walk(host).find(n=>n.tag==='button'&&n.type==='button');assert.equal(current.disabled,false);assert.match(current.textContent,/Ne plus suivre/);
});
test('optional catalog failure keeps private follows and feed available',async()=>{
    const {loadAccountView}=require('../../main/resources/static/js/following.js');const result=await loadAccountView(async path=>path.includes('/updates')?{items:[{id:'event'}]}:[{id:'follow'}],async()=>{throw Error('offline')},1);assert.equal(result.updates.items[0].id,'event');assert.equal(result.entries[0].id,'follow');assert.equal(result.metadataFailed,true);
});
test('normal private refresh keeps mounted controls busy, while anonymous state hides them',()=>{
    const {setPrivateBusy}=require('../../main/resources/static/js/following.js');const button={},host={hidden:true,setAttribute(k,v){this[k]=v},querySelectorAll:()=>[button]};setPrivateBusy(host,'loading',true);assert.equal(host.hidden,false);assert.equal(button.disabled,true);setPrivateBusy(host,'anonymous',false);assert.equal(host.hidden,true);
});
test('review notes survive redraws and pending correction actions remain disabled',()=>{
    const {renderCorrections}=require('../../main/resources/static/js/government-corrections.js');const node=tag=>({tag,children:[],append(...n){this.children.push(...n)},replaceChildren(...n){this.children=n}}),doc={createElement:node},host=node('div'),drafts=new Map([['id','Unfinished note']]);renderCorrections(host,[{id:'id',status:'OPEN',text:'Source'}],'fr',doc,()=>{},drafts,new Set(['id']));const walk=n=>[n,...n.children.flatMap(walk)];assert.equal(walk(host).find(n=>n.tag==='textarea').value,'Unfinished note');assert.ok(walk(host).filter(n=>n.tag==='button').every(n=>n.disabled));
});
test('failed correction review is announced after the final redraw',async()=>{
    const fs=require('fs'),vm=require('vm'),nodes=new Map(),listeners={};function node(){return {children:[],textContent:'',value:'',append(...v){this.children.push(...v)},replaceChildren(...v){this.children=v},setAttribute(){},focus(){},querySelectorAll(){return []}};}const document={createElement:node,getElementById:id=>{if(!nodes.has(id))nodes.set(id,node());return nodes.get(id)},addEventListener:(k,v)=>listeners[k]=v};
    const window={document,FhemniI18n:{locale:()=> 'en'},FhemniGovernmentModel:require('../../main/resources/static/js/government-model.js'),FhemniFollowing:{...require('../../main/resources/static/js/following.js'),request:async(path,method)=>{if(method==='POST')throw Error('offline');return {items:[{id:'x',status:'OPEN',text:'Check'}],totalItems:1}}},FhemniUI:{createPager:()=>({...node(),update(){}})}};
    vm.runInNewContext(fs.readFileSync(require('path').resolve(__dirname,'../../main/resources/static/js/government-corrections.js'),'utf8'),{window,document});listeners.DOMContentLoaded();await new Promise(r=>setImmediate(r));const walk=n=>[n,...n.children.flatMap(walk)],host=nodes.get('governmentCorrections');const note=walk(host).find(n=>n.maxLength===4000);note.value='Keep note';note.oninput();walk(host).find(n=>n.type==='button').onclick();await new Promise(r=>setImmediate(r));assert.equal(nodes.get('correctionsStatus').textContent,window.FhemniGovernmentModel.COPY.en.error);assert.equal(walk(host).find(n=>n.maxLength===4000).value,'Keep note');
});
