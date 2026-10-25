const {test}=require('node:test'),assert=require('node:assert/strict');
const api=require('../../main/resources/static/js/government-api.js');
test('commitment requests transmit search and assessment with existing filters',async t=>{
    let requested;t.mock.method(globalThis,'fetch',async path=>{requested=path;return {ok:true,json:async()=>({items:[]})};});
    await api.fetchCommitments({origin:'GOVERNMENT',topic:'HEALTH',q:'الصحة',assessment:'UNASSESSED',page:2});
    const url=new URL(requested,'http://localhost');assert.equal(url.searchParams.get('q'),'الصحة');assert.equal(url.searchParams.get('assessment'),'UNASSESSED');assert.equal(url.searchParams.get('topic'),'HEALTH');assert.equal(url.searchParams.get('page'),'2');
});
test('preview is optional on a normal deployment but network failures remain distinguishable',async t=>{
    t.mock.method(globalThis,'fetch',async()=>({ok:false,status:404}));assert.equal(await api.fetchPreview(),null);
    globalThis.fetch=async()=>({ok:false,status:503});await assert.rejects(api.fetchPreview(),error=>error.status===503);
});
