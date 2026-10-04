const {test}=require('node:test'),assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const path=require('node:path');
function document(){
    const nodes=new Map();function node(tag){return {tag,children:[],hidden:false,textContent:'',attributes:{},append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children;this.textContent=''},setAttribute(key,val){this.attributes[key]=val},addEventListener(){}};}
    return {createElement:node,getElementById(id){if(!nodes.has(id))nodes.set(id,node('div'));return nodes.get(id)},nodes};
}
const text=node=>[node.textContent,...node.children.map(text)].join(' ');
test('tracker renders formation and source wording without invented people scores or unsafe HTML in every locale',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js');
    for(const locale of ['ar','fr','en']){
        const doc=document();renderTracker({locale,loadState:'ready',mandate:{lifecycle:'FORMATION',verifiedAt:null,membership:[],recentUpdates:[]},page:{items:[{id:'1',slug:'example',origin:'GOVERNMENT',title:{ar:'<img src=x onerror=bad()>',fr:'Title',en:'Title'},deliveryAssessment:null}],page:1,pageSize:10,totalItems:1}}, {tab:'programme',origin:'GOVERNMENT'},doc);
        assert.equal(doc.getElementById('trackerOverview').hidden,true);assert.equal(doc.getElementById('trackerProgramme').hidden,false);
        const output=[...doc.nodes.values()].map(text).join(' ');assert.ok(!output.includes('NaN'));assert.ok(!output.includes('undefined'));
        assert.ok(text(doc.getElementById('trackerProgramme')).includes(locale==='ar'?'<img src=x onerror=bad()>':'Title'));
        assert.ok(!output.includes('0%'));
    }
});
test('latest tracker load cannot overwrite newer navigation and browser back does not push state',async()=>{
    const {createTrackerNavigation}=require('../../main/resources/static/js/government-tracker.js');
    const completions=[],renders=[],pushes=[];
    const nav=createTrackerNavigation({load:state=>new Promise(resolve=>completions.push({state,resolve})),render:(view,state)=>renders.push([view,state]),history:{pushState:(a,b,url)=>pushes.push(url)},url:()=> 'http://localhost/government'});
    const old=nav.go('/government?tab=programme&page=1',true),latest=nav.go('/government?tab=delivery&page=2',false);
    completions[1].resolve({value:'latest'});await latest;completions[0].resolve({value:'stale'});await old;
    assert.equal(renders.at(-1)[0].value,'latest');assert.equal(renders.at(-1)[1].tab,'delivery');assert.equal(pushes.length,1);
});
test('detail preserves source text, unknown baseline and bounded observation tables',()=>{
    const {renderCommitment}=require('../../main/resources/static/js/commitment-detail.js');
    const doc=document();renderCommitment({title:{en:'Sourced promise'},originalText:'Literal <source> text',originalLanguage:'ar',evidence:[],observations:[],revisions:[],deliveryAssessment:null},'en',doc);
    assert.match(text(doc.getElementById('commitmentOriginal')),/Literal <source> text/);
    assert.match(text(doc.getElementById('commitmentAssessment')),/not.*assessed/i);
});
test('reviewed relationships and dated assessment evidence remain visible; unknown comparison does not hide a target',()=>{
    const {renderCommitment}=require('../../main/resources/static/js/commitment-detail.js');
    const doc=document();renderCommitment({title:{en:'Commitment'},author:{en:'Published programme'},originalText:'Text',originalLanguage:'ar-MA',target:{metric:'homes',unit:'homes',targetValue:400,baseline:100,baselineDate:'2026-01-01'},observations:[],evidence:[{id:'proof',url:'https://example.org',passage:'Original proof'}],links:[{campaignPromiseId:'promise',relation:'MODIFIED',programmeVersionId:'version',explanation:{en:'The programme changes the original target.'}}],deliveryAssessment:{state:'NO_EVIDENCE_FOUND',evidenceConfidence:'CONTESTED',reviewedAt:'2026-10-01T00:00:00Z',evidenceIds:['proof'],explanation:{en:'Search was inconclusive'}}},'en',doc);
    assert.match(text(doc.getElementById('commitmentAssessment')),/2026/);
    assert.match(text(doc.getElementById('commitmentLinks')),/changes the original target/);
    assert.match(text(doc.getElementById('commitmentChart')),/400.*homes/);
    assert.match(text(doc.getElementById('commitmentChart')),/100/);
    assert.match(text(doc.getElementById('commitmentFacts')),/Published programme/);
});
test('missing records are distinguished from temporary failures in every locale',()=>{
    const {detailLoadCopy}=require('../../main/resources/static/js/commitment-detail.js');
    for(const locale of ['ar','fr','en']){assert.notEqual(detailLoadCopy('notFound',locale),detailLoadCopy('error',locale));assert.ok(detailLoadCopy('error',locale).length);}
});
test('metadata failures preserve the other filter and provide an explicit retry state',async()=>{
    const {loadFilterMetadata}=require('../../main/resources/static/js/government-tracker.js');
    const result=await loadFilterMetadata(async path=>{if(path.endsWith('/parties'))return [{code:'PI'}];throw new Error('offline');});
    assert.deepEqual(result.parties,[{code:'PI'}]);assert.deepEqual(result.topics,[]);assert.deepEqual(result.failed,['topics']);
});
test('pagination stays mounted during navigation and reports its busy state',()=>{
    const {updatePagerBusy}=require('../../main/resources/static/js/government-tracker.js');
    const button={disabled:false},host={hidden:true,attributes:{},setAttribute(k,v){this.attributes[k]=v},querySelectorAll(){return [button]}};
    updatePagerBusy(host,'programme','loading',true);assert.equal(host.hidden,false);assert.equal(button.disabled,true);assert.equal(host.attributes['aria-busy'],'true');
    updatePagerBusy(host,'overview','ready',true);assert.equal(host.hidden,true);
});
test('campaign commitments return to the original promise page, never a government delivery assessment',()=>{
    const {canonicalCampaignPath}=require('../../main/resources/static/js/commitment-detail.js');
    assert.equal(canonicalCampaignPath({origin:'CAMPAIGN',slug:'campaign-health-access'}),'/promises/health-access');
    assert.equal(canonicalCampaignPath({origin:'GOVERNMENT',slug:'health-access'}),null);
});
test('loading and failed requests do not assert that the government is still forming',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js');
    for(const loadState of ['loading','error']){const doc=document();renderTracker({locale:'en',loadState},{tab:'overview',origin:'GOVERNMENT'},doc);assert.doesNotMatch(text(doc.getElementById('trackerOverview')),/Government formation/);}
});
test('render orchestration selects tabs before removing their content and retains source disclosure state',()=>{
    const {captureTrackerUi,restoreTrackerUi}=require('../../main/resources/static/js/government-tracker.js');const order=[];
    const source={open:true},control={focus(){order.push('focus')}},doc={activeElement:{id:'trackerChip-topic'},getElementById:id=>id==='trackerSources'?source:id==='trackerFilterTitle'?control:null};
    const saved=captureTrackerUi(doc,{select:id=>order.push(id)},'programme');source.open=false;restoreTrackerUi(doc,saved);
    assert.deepEqual(order,['tabProgramme','focus']);assert.equal(source.open,true);
});
