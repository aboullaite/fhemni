const {test}=require('node:test'),assert=require('node:assert/strict');
test('digest settings never promise email delivery when capability is unavailable',()=>{
    const {digestState,COPY}=require('../../main/resources/static/js/digest-settings.js');
    assert.deepEqual(digestState({enabled:false,capable:false,unavailableReason:'UNAVAILABLE'}),{checked:false,disabled:true,message:'unavailable'});
    assert.equal(digestState({enabled:true,capable:false}).disabled,false,'revocation always remains available');
    for(const locale of ['ar','fr','en'])assert.ok(COPY[locale].unavailable&&COPY[locale].consent);
});
