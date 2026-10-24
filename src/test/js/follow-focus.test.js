const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const flush=()=>new Promise(resolve=>setImmediate(resolve));
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};

// Removing a focused subtree returns focus to body, as in a browser. Disabled
// controls cannot receive focus; focusin lets renderers observe user navigation.
function fixture(kind,request){
    const listeners={};
    const document={addEventListener(type,fn){(listeners[type]??=[]).push(fn);},dispatch(type,event={}){for(const fn of listeners[type]||[])fn(event);}};
    function element(tag){
        let disabled=false;
        const node={tag,children:[],parentNode:null,ownerDocument:document,value:'',attributes:{},
            get disabled(){return disabled;},set disabled(value){disabled=!!value;if(disabled&&document.activeElement===this)document.activeElement=document.body;},
            get isConnected(){return this===document.body||!!this.parentNode?.isConnected;},
            contains(other){return this===other||this.children.some(child=>child.contains(other));},
            append(...children){for(const child of children){child.parentNode=this;this.children.push(child);}},
            replaceChildren(...children){if(this.children.some(child=>child.contains(document.activeElement)))document.activeElement=document.body;for(const child of this.children)child.parentNode=null;this.children=[];this.append(...children);},
            setAttribute(name,value){this.attributes[name]=String(value);},getAttribute(name){return this.attributes[name]??null;},
            querySelectorAll(selector){return walk(this).slice(1).filter(child=>selector.startsWith('[')?child.getAttribute(selector.slice(1,-1).split('=')[0])===selector.slice(1,-1).split('=')[1]?.replaceAll('"',''):child.tag===selector);},
            querySelector(selector){return this.querySelectorAll(selector)[0]||null;},
            focus(){if(this.disabled||!this.isConnected)return;document.activeElement=this;document.dispatch('focusin',{target:this});}
        };return node;
    }
    document.createElement=element;document.createTextNode=text=>Object.assign(element('#text'),{textContent:text});
    document.body=element('body');document.activeElement=document.body;
    const host=element('section'),outside=element('button');document.body.append(host,outside);document.getElementById=()=>host;
    let locale='en';
    const window={document,addEventListener(){},FhemniFollowing:{...require('../../main/resources/static/js/following.js'),request},FhemniGovernmentModel:require('../../main/resources/static/js/government-model.js'),FhemniI18n:{locale:()=>locale},FhemniAuth:{refreshSession:async()=>({authenticated:true}),loginPage:()=>'/login'},FhemniUI:{announceStatus(node,text){node.textContent=text;}},location:{pathname:'/government',search:''}};
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/'+kind+'.js'),'utf8'),{window,document});
    if(kind==='follow-controls')window.FhemniFollowControls.mount(host,{type:'REGION',id:'r',correctionType:'MEASURE',correctionId:'m'});
    else document.dispatch('DOMContentLoaded');
    return {document,host,outside,find:tag=>walk(host).find(node=>node.tag===tag),locale(value){locale=value;document.dispatch('fhemni:localechange');}};
}
function walk(node){return [node,...node.children.flatMap(walk)];}
const digest={enabled:false,capable:true,locale:'en'};

for(const outcome of ['success','failure'])test('follow restores keyboard focus after '+outcome,async()=>{
    const pending=deferred(),ui=fixture('follow-controls',async(p,method)=>method?pending.promise:{following:false});await flush();
    const button=ui.find('button');button.focus();const action=button.onclick();
    if(outcome==='success')pending.resolve({following:true});else pending.reject(Error('offline'));
    await action;assert.ok(ui.document.activeElement===ui.find('button'),'focus returns to the current follow button');assert.notEqual(ui.document.activeElement,button);
});

test('follow locale redraw preserves the focused correction field and its draft',async()=>{
    const ui=fixture('follow-controls',async()=>({following:false}));await flush();
    const text=ui.find('textarea');text.value='Unsent correction';text.oninput();text.focus();ui.locale('fr');
    assert.ok(ui.document.activeElement===ui.find('textarea'),'focus remains on the correction field');assert.equal(ui.find('textarea').value,'Unsent correction');
});

test('pending follow restores the current localized control without stealing outside focus',async()=>{
    for(const moveOutside of [false,true]){
        const pending=deferred(),ui=fixture('follow-controls',async(p,method)=>method?pending.promise:{following:false});await flush();
        ui.find('button').focus();const action=ui.find('button').onclick();ui.locale('fr');
        if(moveOutside)ui.outside.focus();pending.resolve({following:true});await action;
        assert.ok(ui.document.activeElement===(moveOutside?ui.outside:ui.find('button')),'completion preserves the user’s current focus destination');
    }
});

test('expired follow session transfers owned focus to the sign-in link',async()=>{
    const pending=deferred(),ui=fixture('follow-controls',async(p,method)=>method?pending.promise:{following:false});await flush();
    ui.find('button').focus();const action=ui.find('button').onclick();pending.reject({status:401});await action;
    assert.ok(ui.document.activeElement===ui.find('a'),'focus moves to the available sign-in link');
});

for(const outcome of ['success','failure'])test('digest restores the save control after '+outcome+' across locale redraws',async()=>{
    const pending=deferred(),ui=fixture('digest-settings',async(p,method)=>method?pending.promise:digest);await flush();
    ui.find('input').checked=true;ui.find('select').value='fr';ui.find('select').onchange();ui.find('button').focus();
    const action=ui.find('form').onsubmit({preventDefault(){}});ui.locale('fr');
    assert.equal(ui.find('button').disabled,true);
    if(outcome==='success')pending.resolve({...digest,enabled:true,locale:'fr'});else pending.reject(Error('offline'));
    await action;assert.ok(ui.document.activeElement===ui.find('button'),'focus returns to the current save button');assert.equal(ui.find('button').disabled,false);
    assert.equal(ui.find('input').checked,true);assert.equal(ui.find('select').value,'fr');
});

test('digest locale redraw preserves the focused language selection',async()=>{
    const ui=fixture('digest-settings',async()=>digest);await flush();ui.find('select').focus();ui.locale('ar');
    assert.ok(ui.document.activeElement===ui.find('select'),'focus remains on the language selection');
});

test('digest completion does not steal focus moved outside during saving',async()=>{
    const pending=deferred(),ui=fixture('digest-settings',async(p,method)=>method?pending.promise:digest);await flush();
    ui.find('button').focus();const action=ui.find('form').onsubmit({preventDefault(){}});ui.outside.focus();ui.locale('fr');pending.resolve(digest);await action;
    assert.ok(ui.document.activeElement===ui.outside,'outside focus remains unchanged');
});

test('digest anonymous transition hides the form without stealing focus',async()=>{
    const pending=deferred(),ui=fixture('digest-settings',async(p,method)=>method?pending.promise:digest);await flush();
    ui.find('button').focus();const action=ui.find('form').onsubmit({preventDefault(){}});ui.outside.focus();pending.reject({status:401});await action;
    assert.equal(ui.host.hidden,true);assert.equal(ui.find('form'),undefined);assert.ok(ui.document.activeElement===ui.outside,'outside focus remains unchanged');
});

test('follow completion preserves navigation to a correction field',async()=>{
    const pending=deferred(),ui=fixture('follow-controls',async(p,method)=>method?pending.promise:{following:false});await flush();
    ui.find('button').focus();const action=ui.find('button').onclick();ui.find('textarea').focus();pending.resolve({following:true});await action;
    assert.ok(ui.document.activeElement===ui.find('textarea'),'follow completion retains the newly focused field');
});

test('digest save focus intent is cancelled after focus moves away and back to body',async()=>{
    const pending=deferred(),ui=fixture('digest-settings',async(p,method)=>method?pending.promise:digest);await flush();
    ui.find('button').focus();const action=ui.find('form').onsubmit({preventDefault(){}});ui.outside.focus();ui.document.body.focus();pending.resolve(digest);await action;
    assert.ok(ui.document.activeElement===ui.document.body,'stale save focus is not restored');
});

for(const outcome of ['success','failure'])test('correction submission restores focus after a pending locale redraw and '+outcome,async()=>{
    const pending=deferred(),ui=fixture('follow-controls',async(p,method)=>method==='POST'?pending.promise:{following:false});await flush();
    const send=walk(ui.host).find(node=>node.type==='submit');send.focus();const action=ui.find('form').onsubmit({preventDefault(){}});ui.locale('fr');
    if(outcome==='success')pending.resolve({});else pending.reject(Error('offline'));await action;
    assert.ok(ui.document.activeElement===walk(ui.host).find(node=>node.type==='submit'),'focus returns to the available correction submit button');
    const message=require('../../main/resources/static/js/following.js').COPY.fr[outcome==='success'?'sent':'error'];
    assert.equal(ui.host.querySelector('[role="status"]').textContent,message,'the current visible status announces the result in the current language');
});

test('digest actions use the shared primary and secondary button styles',async()=>{
    const ready=fixture('digest-settings',async()=>digest);await flush();assert.equal(ready.find('button').className,'primary-button');
    const failed=fixture('digest-settings',async()=>{throw Error('offline');});await flush();assert.equal(failed.find('button').className,'secondary-button');
});
