const {test}=require('node:test'),assert=require('node:assert/strict');
test('regional comparison requires matching level metric unit method period and unique scopes',()=>{
    const {comparisonRows}=require('../../main/resources/static/js/government-regions.js');
    const item=(code,level='REGION',unit='homes')=>({observation:{scope:{level,code},metric:'housing',unit,methodologyKey:'v1',periodStart:'2026-01-01',periodEnd:'2026-09-30',value:20,evidenceIds:['proof']}});
    assert.equal(comparisonRows([item('01'),item('02')]).length,2);
    for(const rows of [[item('01'),item('MA','NATIONAL')],[item('01'),item('02','REGION','people')],[item('01'),item('01')]])assert.deepEqual(comparisonRows(rows),[]);
    assert.deepEqual(comparisonRows([item('01'),{observation:{...item('02').observation,value:null}}]),[]);
});
test('regional links identify the exact observation, even beyond the first detail page',()=>{
    const {observationLink}=require('../../main/resources/static/js/government-regions.js');assert.equal(observationLink({commitmentSlug:'health',observation:{id:'row-21'}}),'/government/commitments/health?observation=row-21#observation-row-21');
    const {observationPageFor}=require('../../main/resources/static/js/commitment-detail.js');assert.equal(observationPageFor(Array.from({length:21},(_,i)=>({id:'row-'+(i+1)})),'row-21'),3);
});
