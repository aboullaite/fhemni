const {test}=require('node:test'),assert=require('node:assert/strict');
test('apply requires sourced operational availability, eligibility, steps and a safe official URL',()=>{
    const {canApply}=require('../../main/resources/static/js/measure.js');
    const value={operationalStatus:'AVAILABLE',eligibility:{en:'Documented criteria'},steps:{en:'Official steps'},officialActionUrl:'https://example.org/apply',evidence:[{id:'proof'}]};
    assert.equal(canApply(value),true);
    for(const operationalStatus of ['UNKNOWN','PENDING','SUSPENDED'])assert.equal(canApply({...value,operationalStatus}),false);
    for(const change of [{eligibility:null},{steps:null},{evidence:[]},{officialActionUrl:'javascript:bad()'}])assert.equal(canApply({...value,...change}),false);
});
test('missing practical fields remain unavailable in all locales',()=>{
    const {measureFacts}=require('../../main/resources/static/js/measure.js');
    for(const locale of ['ar','fr','en']){const rows=measureFacts({operationalStatus:'PENDING',evidence:[]},locale);assert.ok(rows.some(row=>row.key==='eligibility'&&row.missing));assert.ok(rows.every(row=>row.label&&row.value));}
});
test('retained measure pages disable pagination while busy or stale after errors',()=>{
    const {setPageState}=require('../../main/resources/static/js/measure.js');const button={disabled:false},host={setAttribute(k,v){this[k]=v},querySelectorAll:()=>[button]};setPageState(host,'loading');assert.equal(button.disabled,true);assert.equal(host['aria-busy'],'true');button.disabled=false;setPageState(host,'error');assert.equal(button.disabled,true);
});
