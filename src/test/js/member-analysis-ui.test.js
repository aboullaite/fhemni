const {test}=require('node:test'),assert=require('node:assert/strict');
const modulePath='../../main/resources/static/js/member-analysis-api.js';
test('effective policy fails closed and renders actual limits in every locale',()=>{
 const ui=require(modulePath);const policy={enabled:false,remaining:0,monthlyLimit:5,maxSeconds:1800,concurrency:1,resetsAt:'2026-11-01T00:00:00Z'};
 for(const locale of ['ar','fr','en']){assert.equal(ui.canSubmit(policy),false);assert.ok(ui.limitText(policy,locale).includes('5'));assert.ok(ui.limitText(policy,locale).includes('30'));assert.equal(ui.canSubmit({...policy,enabled:true,remaining:1}),true);assert.equal(ui.canSubmit({...policy,enabled:true,remaining:1,active:true}),false);}
 assert.equal(ui.canSubmit(null),false);assert.equal(ui.limitText(null,'en'),'');
});
test('one submission key survives uncertain retry and reload without double click',async()=>{
 const ui=require(modulePath),map=new Map(),storage={getItem:k=>map.get(k),setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)};let calls=0,resolve;
 const api={submitMemberAnalysis:input=>{calls++;return new Promise(r=>resolve=()=>r({id:input.idempotencyKey}));}};
 const draft=ui.submissionDraft(storage,()=> 'key-1234567890123456');const first=draft.send(api,{url:'https://youtu.be/abcdefghijk',language:'en'});const second=draft.send(api,{url:'https://youtu.be/abcdefghijk',language:'en'});assert.equal(calls,1);resolve();assert.deepEqual(await first,await second);
 const restored=ui.submissionDraft(storage,()=> 'new-key');assert.equal(restored.read().key,'key-1234567890123456');
});
test('result state preserves selected tab and page but never fabricates a transcript',()=>{
 const ui=require(modulePath);assert.deepEqual(ui.readState('?tab=claims&page=3'),{tab:'claims',page:3});assert.deepEqual(ui.readState('?tab=bad&page=-4'),{tab:'summary',page:1});
 assert.equal(ui.transcript({detailedSummary:'Not verbatim',chapters:[{summary:'Not a transcript'}]}),null);
 assert.equal(ui.playerUrl('abcdefghijk',83),'https://www.youtube-nocookie.com/embed/abcdefghijk?start=83');assert.equal(ui.playerUrl('bad/../id',0),null);
 for(const locale of ['ar','fr','en']){assert.ok(ui.COPY[locale].PRIVATE);assert.ok(ui.COPY[locale].PUBLISHED);assert.ok(ui.COPY[locale].transcriptMissing);assert.ok(!ui.COPY[locale].PROCESSING.includes('%'));}
});
test('private history is bounded to ten and report text stays literal',()=>{
 const ui=require(modulePath);const node=tag=>({tag,children:[],append(...n){this.children.push(...n)},replaceChildren(...n){this.children=n}}),doc={createElement:node},host=node('tbody');
 ui.renderHistory(host,Array.from({length:15},()=>({id:'id',title:'<script>bad()</script>',state:'READY',language:'en',createdAt:'2026-10-04T00:00:00Z'})),'en',doc,2);
 assert.equal(host.children.length,10);assert.equal(host.children[0].children[0].children[0].textContent,'<script>bad()</script>');assert.ok(host.children[0].children[0].children[0].href.endsWith('?page=2'));
});
test('submission drafts are isolated by authenticated account',async()=>{
 const ui=require(modulePath),map=new Map(),storage={getItem:k=>map.get(k),setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)};
 const a=ui.submissionDraft(storage,()=> 'a-key-123456789012','owner-a');await a.send({submitMemberAnalysis:async()=>({id:'a'})},{url:'private-a',language:'en'});
 const b=ui.submissionDraft(storage,()=> 'b-key-123456789012','owner-b');assert.equal(b.read(),null);
});
test('confirmed admission permits a deliberate fresh attempt while uncertain retry keeps its key',async()=>{
 const ui=require(modulePath),map=new Map(),storage={getItem:k=>map.get(k),setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)};let n=0,keys=[];const draft=ui.submissionDraft(storage,()=> 'key-number-'+(++n));const api={submitMemberAnalysis:async row=>{keys.push(row.idempotencyKey);return {id:'job'};}};const input={url:'url',language:'en'};
 await draft.send(api,input);draft.confirmed();await draft.send(api,input);assert.notEqual(keys[0],keys[1]);
});
test('retained history uses its loaded page and eligibility copy gives corrective guidance',()=>{
 const ui=require(modulePath);assert.equal(ui.historyPage({page:1},2),1);assert.equal(ui.historyPage(null,2),2);
 for(const lang of ['ar','fr','en'])for(const code of ['VIDEO_TOO_LONG','LIVE_UNSUPPORTED','DURATION_UNAVAILABLE','VIDEO_UNAVAILABLE','VERIFICATION_UNAVAILABLE'])assert.notEqual(ui.errorMessage({code,status:400},lang),ui.COPY[lang].error);
});
function resultHarness(request){
 const fs=require('node:fs'),vm=require('node:vm'),nodes=new Map(),doc={listeners:{},visibilityState:'visible',addEventListener(name,fn){this.listeners[name]=fn}};
 const node=tag=>({tag,children:[],attributes:{},value:'',append(...n){this.children.push(...n)},replaceChildren(...n){this.children=n},setAttribute(k,v){this.attributes[k]=String(v)},getAttribute(k){return this.attributes[k]||null},removeAttribute(k){delete this.attributes[k]},focus(){doc.activeElement=this}});
 doc.createElement=tag=>{const value=node(tag);Object.defineProperty(value,'id',{set(id){nodes.set(id,value)}});return value;};doc.getElementById=id=>{if(!nodes.has(id))nodes.set(id,node('div'));return nodes.get(id);};
 let user='owner-a',lang='en';const api={...require(modulePath),loadMemberJob:async()=>({id:'job',title:'Private title',state:'READY',videoId:'abcdefghijk',report:{summary:'Private summary',claims:[]}}),request:request|| (async path=>path.endsWith('/questions')?[]:{state:path.endsWith('/question-status')?'READY':'PRIVATE'})};
 const root={document:doc,FhemniMember:api,FhemniAuth:{refreshSession:async()=>({authenticated:!!user,user:{id:user}}),loginPage:p=>'/login?continue='+encodeURIComponent(p)},FhemniI18n:{locale:()=>lang,t:k=>k},FhemniUI:{mountTabs:()=>({select(){}}),announceStatus:(n,t)=>n.textContent=t},location:{pathname:'/account/analyses/job',search:'?tab=claims&page=2',href:'http://localhost/account/analyses/job?tab=claims&page=2'},history:{pushState(){}},listeners:{},addEventListener(n,f){this.listeners[n]=f},setTimeout:()=>1,clearTimeout(){},confirm:()=>true};
 vm.runInNewContext(fs.readFileSync(require('node:path').resolve(__dirname,'../../main/resources/static/js/member-analysis-result.js'),'utf8'),{window:root,URL,Promise});doc.listeners.DOMContentLoaded();return {doc,root,setUser:v=>user=v,setLocale:v=>lang=v};
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('private question draft survives locale and source-tab visits only for the same owner',async()=>{
 const h=resultHarness();await flush();const input=h.doc.getElementById('memberQuestion');input.value='My unsent question';h.setLocale('fr');h.doc.listeners['fhemni:localechange']();assert.equal(input.value,'My unsent question');
 h.doc.visibilityState='hidden';h.doc.listeners.visibilitychange();assert.equal(h.doc.getElementById('memberResultContent').hidden,true);h.doc.visibilityState='visible';h.doc.listeners.visibilitychange();await flush();assert.equal(input.value,'My unsent question');
 h.doc.visibilityState='hidden';h.doc.listeners.visibilitychange();h.setUser('owner-b');h.doc.visibilityState='visible';h.doc.listeners.visibilitychange();await flush();assert.equal(input.value,'');
 h.setUser(null);h.doc.listeners.visibilitychange();await flush();assert.equal(h.doc.getElementById('memberResultContent').hidden,true);assert.equal(h.doc.getElementById('memberSummary').children.length,0);
});
test('recovered failed and uncertain questions allow only explicit sends and retain localized drafts',async()=>{
 for(const recovery of ['FAILED','UNCERTAIN']){
  let posts=0;const h=resultHarness(async(path,method)=>{if(method==='POST'){posts++;return {question:'Draft',answer:'Answer'};}return path.endsWith('/questions')?[]:{state:path.endsWith('/question-status')?recovery:'PRIVATE'};});await flush();
  const get=id=>h.doc.getElementById(id),input=get('memberQuestion');input.value='Draft';
  assert.equal(posts,0);assert.equal(get('memberAsk').disabled,false);
  assert.match(get('memberQuestionStatus').textContent,/not.*automatically/i);assert.match(get('memberQuestionStatus').textContent,/new question/i);
  for(const locale of ['ar','fr','en']){h.setLocale(locale);h.doc.listeners['fhemni:localechange']();assert.equal(input.value,'Draft');assert.equal(get('memberQuestionStatus').textContent,h.root.FhemniMember.questionRecovery(recovery,locale).message);}
  assert.equal(input.getAttribute('aria-describedby'),'memberQuestionStatus');
  await get('memberQuestionForm').onsubmit({preventDefault(){}});assert.equal(posts,1);assert.equal(input.value,'');assert.equal(h.doc.activeElement,input);
 }
});
test('processing and unavailable status block duplicate questions without losing drafts',async()=>{
 let recovery='PROCESSING',posts=0;const h=resultHarness(async(path,method)=>{if(method==='POST')posts++;return path.endsWith('/questions')?[]:{state:path.endsWith('/question-status')?recovery:'PRIVATE'};});await flush();
 const get=id=>h.doc.getElementById(id);get('memberQuestion').value='Next question';assert.equal(get('memberAsk').disabled,true);assert.equal(get('memberQuestion').disabled,false);
 await get('memberQuestionForm').onsubmit({preventDefault(){}});assert.equal(posts,0);
 recovery='UNKNOWN';await get('memberQuestionRefresh').onclick();assert.equal(get('memberAsk').disabled,true);assert.equal(get('memberQuestionRefresh').hidden,false);assert.equal(get('memberQuestion').value,'Next question');
 recovery='FAILED';await get('memberQuestionRefresh').onclick();assert.equal(get('memberAsk').disabled,false);assert.equal(posts,0);
});
test('failed send reads recovery status without retry and clears private content on denied status',async()=>{
 let posts=0,statusReads=0,denied=false;const h=resultHarness(async(path,method)=>{if(method==='POST'){posts++;throw Error('lost response');}if(path.endsWith('/question-status')){statusReads++;if(denied)throw Object.assign(Error('denied'),{status:401});return {state:posts?'UNCERTAIN':'READY'};}return path.endsWith('/questions')?[]:{state:'PRIVATE'};});await flush();
 const get=id=>h.doc.getElementById(id);get('memberQuestion').value='Keep me';await get('memberQuestionForm').onsubmit({preventDefault(){}});
 assert.equal(posts,1);assert.equal(statusReads,2);assert.equal(get('memberQuestion').value,'Keep me');assert.match(get('memberQuestionStatus').textContent,/new question/i);
 denied=true;await get('memberQuestionRefresh').onclick();assert.equal(get('memberResultContent').hidden,true);assert.equal(get('memberQuestion').value,'');assert.equal(get('memberSummary').children.length,0);
 assert.equal(get('memberSignIn').hidden,false);
});
test('status-check completion restores focus and stale answer reads cannot restore another owner’s content',async()=>{
 let status='FAILED',resolveAnswers,questions=0;const h=resultHarness(async path=>{if(path.endsWith('/questions')){questions++;if(questions===2)return new Promise(resolve=>resolveAnswers=resolve);return [];}return {state:path.endsWith('/question-status')?status:'PRIVATE'};});await flush();
 const get=id=>h.doc.getElementById(id);get('memberQuestionRefresh').focus();await get('memberQuestionRefresh').onclick();assert.equal(h.doc.activeElement,get('memberQuestion'));
 status='ANSWERED';const check=get('memberQuestionRefresh').onclick();await flush();h.doc.visibilityState='hidden';h.doc.listeners.visibilitychange();h.setUser('owner-b');h.doc.visibilityState='visible';h.doc.listeners.visibilitychange();await flush();
 resolveAnswers([{question:'Owner A secret',answer:'Private answer'}]);await check;h.doc.listeners['fhemni:localechange']();assert.equal(get('memberAnswers').children.length,0);
});
test('a conversation fetch failure cannot hide confirmed recovery guidance',async()=>{
 const h=resultHarness(async path=>{if(path.endsWith('/questions'))throw Error('temporarily unavailable');return {state:path.endsWith('/question-status')?'FAILED':'PRIVATE'};});await flush();
 assert.match(h.doc.getElementById('memberQuestionStatus').textContent,/new question/i);assert.equal(h.doc.getElementById('memberAsk').disabled,false);
});
