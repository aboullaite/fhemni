const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
test('authentication publishes only opaque change signals and invalidates cached sessions from other windows',async()=>{
 const signals=[],listeners={};let calls=0;
 const window={localStorage:{setItem:(...args)=>signals.push(args)},addEventListener:(key,fn)=>listeners[key]=fn,
  location:{assign(){}},setTimeout:()=>1,clearTimeout(){}};
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/auth.js','utf8'),{window,document:{addEventListener(){}},
  fetch:async()=>{calls++;return {ok:true,json:async()=>({authenticated:true,user:{id:'private-owner'},csrfHeader:'csrf',csrfToken:'secret'})};},AbortController,URLSearchParams,Headers});
 await window.FhemniAuth.session();
 assert.equal(signals[0]?.[0],'fhemni:auth-change');
 assert.doesNotMatch(JSON.stringify(signals),/private-owner|secret/);
 await window.FhemniAuth.refreshSession();assert.equal(signals.length,1,'same owner does not rebroadcast');
 listeners.storage({key:'fhemni:auth-change'});await window.FhemniAuth.session();assert.equal(calls,3);
 await window.FhemniAuth.signOut();assert.equal(signals.length,2);
});
