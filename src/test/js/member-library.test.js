const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const api=require('../../main/resources/static/js/member-analysis-api.js');
function harness(session={authenticated:false},policy={enabled:true,remaining:10,monthlyLimit:10,chatMonthlyLimit:20,chatRemaining:20,chatEnabled:true,maxSeconds:1800,retentionDays:30,resetsAt:'2026-11-01T00:00:00Z'},rows=[]){
 const nodes=new Map(),doc={listeners:{},visibilityState:'visible',querySelectorAll:()=>[],addEventListener(k,v){this.listeners[k]=v;}};
 const node=tag=>({tag,children:[],value:'',hidden:false,attributes:{},append(...n){this.children.push(...n);},replaceChildren(...n){this.children=n;},setAttribute(k,v){this.attributes[k]=v;},querySelectorAll:()=>[],focus(){doc.activeElement=this;}});
 doc.createElement=node;doc.getElementById=id=>{if(!nodes.has(id))nodes.set(id,node('div'));return nodes.get(id);};
 let calls=0;const storage=new Map(),root={document:doc,FhemniMember:{...api,request:async()=>{if(policy instanceof Error)throw policy;return policy;},loadMemberHistory:async page=>{calls++;return {items:rows,page,totalItems:rows.length};}},FhemniAuth:{refreshSession:async()=>session,loginPage:route=>'/login?continue='+route},FhemniI18n:{locale:()=> 'en'},FhemniUI:{createPager:()=>Object.assign(node('nav'),{update(){}})},sessionStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},crypto:{randomUUID:()=> 'unique-key-123456789'},location:{search:'',pathname:'/analyse',assign(){}},history:{pushState(){},replaceState(){}},listeners:{},addEventListener(k,v){this.listeners[k]=v;},setTimeout:()=>1,clearTimeout(){}};
 vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/member-library.js'),'utf8'),{window:root,URLSearchParams,Promise});doc.listeners.DOMContentLoaded();
 return {doc,root,setSession(value){session=value;},calls:()=>calls,get:id=>doc.getElementById(id)};
}
const flush=()=>new Promise(r=>setImmediate(r));
test('visitor sees a selling pitch and return-to-library login without fetching private history',async()=>{
 const h=harness();await flush();assert.equal(h.get('memberPitch').hidden,false);assert.equal(h.get('memberWorkspace').hidden,true);assert.equal(h.get('memberPitchLogin').href,'/login?continue=/analyse');assert.equal(h.calls(),0);
});
test('member sees library and real allowances; logout clears private content',async()=>{
 const h=harness({authenticated:true,user:{id:'owner'}},undefined,[{id:'one',videoId:'abcdefghijk',title:'Private title',state:'READY',expiresAt:'2099-01-01T00:00:00Z'}]);await flush();
 assert.equal(h.get('memberPitch').hidden,true);assert.equal(h.get('memberWorkspace').hidden,false);assert.equal(h.get('memberSubmitButton').disabled,false);assert.equal(h.get('memberLibraryRows').children.length,1);
 h.doc.visibilityState='hidden';h.doc.listeners.visibilitychange();assert.equal(h.get('memberLibraryRows').children.length,0);
 h.setSession({authenticated:false});h.doc.visibilityState='visible';h.doc.listeners.visibilitychange();await flush();assert.equal(h.get('memberWorkspace').hidden,true);
});
test('disabled or exhausted processing still shows saved videos while blocking new submissions',async()=>{
 for(const policy of [{enabled:false,remaining:10},{enabled:true,remaining:0}]){
  const h=harness({authenticated:true,user:{id:'owner'}},policy,[{id:'one',title:'Saved',state:'READY'}]);await flush();assert.equal(h.get('memberSubmitButton').disabled,true);assert.equal(h.get('memberLibraryRows').children.length,1);
 }
});
test('library rendering limits ten literal-text cards and includes expiry in every locale',()=>{
 const node=tag=>({tag,children:[],append(...n){this.children.push(...n)},replaceChildren(...n){this.children=n}}),doc={createElement:node};
 for(const locale of ['ar','fr','en']){const host=node('div');api.renderLibrary(host,Array.from({length:12},()=>({id:'safe',videoId:'abcdefghijk',title:'<script>bad</script>',state:'READY',expiresAt:'2099-01-01T00:00:00Z'})),locale,doc,1);assert.equal(host.children.length,10);const text=JSON.stringify(host);assert.match(text,/<script>bad<\/script>/);assert.match(text,/2099/);assert.match(text,/i.ytimg.com/);}
});
test('account changes discard the old library and load only the new account',async()=>{
 const h=harness({authenticated:true,user:{id:'one'}},undefined,[{id:'private-one',title:'First owner',state:'READY'}]);await flush();
 h.get('memberUrl').value='https://youtu.be/abcdefghijk';h.setSession({authenticated:true,user:{id:'two'}});h.root.listeners.pageshow({persisted:true});
 assert.equal(h.get('memberWorkspace').hidden,true);assert.equal(h.get('memberLibraryRows').children.length,0);await flush();assert.equal(h.get('memberUrl').value,'');
});
test('a submission completing after a hidden-tab refresh does not freeze the form',async()=>{
 const h=harness({authenticated:true,user:{id:'one'}});await flush();let finish;
 h.root.FhemniMember.submitMemberAnalysis=()=>new Promise(resolve=>finish=resolve);
 h.get('memberUrl').value='https://youtu.be/abcdefghijk';h.get('memberLanguage').value='en';
 const pending=h.get('memberSubmitForm').onsubmit({preventDefault(){}});
 h.doc.visibilityState='hidden';h.doc.listeners.visibilitychange();h.doc.visibilityState='visible';h.doc.listeners.visibilitychange();await flush();
 finish({id:'created'});await pending;assert.equal(h.get('memberSubmitButton').disabled,false);
});
