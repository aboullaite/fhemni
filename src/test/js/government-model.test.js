const {test}=require('node:test'),assert=require('node:assert/strict');
const file='../../main/resources/static/js/government-model.js';
const scope={level:'REGION',code:'R01'};
const target={metric:'beds',unit:'beds',targetValue:200,baseline:null,methodologyKey:'hospital-bed-v1',scope};
const row={metric:'beds',unit:'beds',value:0,periodStart:'2026-01-01',periodEnd:'2026-09-30',methodologyKey:'hospital-bed-v1',scope,evidenceIds:['source']};
test('only compatible metric unit scope and methodology observations enter a comparison',()=>{
    const {comparableObservations}=require(file);
    for(const changed of [{unit:'people'},{metric:'staff'},{scope:{level:'NATIONAL',code:'MA'}},{methodologyKey:'v2'},{value:null},{evidenceIds:[]}])assert.equal(comparableObservations(target,[{...row,...changed}]).available,false);
    assert.equal(comparableObservations(target,[]).available,false);
    const valid=comparableObservations(target,[row]);assert.equal(valid.available,true);assert.equal(valid.rows[0].value,0);assert.equal(valid.progress,null);
    assert.equal(comparableObservations({...target,scope:null},[row]).available,false);
});
test('deadline and assessment are independent; absence is not failure and delivery does not prove timeliness',()=>{
    const {deriveDeadline,assessmentState}=require(file);
    assert.equal(deriveDeadline(null,null,'2026-10-04'),'UNKNOWN');
    assert.equal(deriveDeadline('2026-02-30',null,'2026-10-04'),'UNKNOWN');
    assert.equal(deriveDeadline('2026-10-04',null,'2026-10-04'),'UPCOMING');
    assert.equal(deriveDeadline('2026-10-03',null,'2026-10-04'),'PASSED');
    assert.equal(deriveDeadline('2026-10-03',{state:'DELIVERED'},'2026-10-04'),'CLOSED');
    assert.equal(assessmentState({deliveryAssessment:null}),'UNASSESSED');
});
test('tracker URL validates all filters and preserves a real selected tab and server page',()=>{
    const {readTrackerState,trackerUrl}=require(file);
    assert.deepEqual(readTrackerState('/government?tab=programme&origin=CAMPAIGN&topic=HEALTH&page=2'),{tab:'programme',origin:'CAMPAIGN',topic:'HEALTH',party:'',page:2});
    const safe=readTrackerState('/government?tab=hack&origin=HACK&topic=<script>&page=-2');
    assert.equal(safe.tab,'overview');assert.equal(safe.origin,'GOVERNMENT');assert.equal(safe.topic,'');assert.equal(safe.page,1);
    assert.equal(readTrackerState('/government?tab=programme&origin=GOVERNMENT&party=RNI').party,'','inapplicable hidden party filters are removed');
    assert.match(trackerUrl('http://localhost/government?lang=fr',safe),/lang=fr/);
});
