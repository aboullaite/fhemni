const {test}=require('node:test'),assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const path=require('node:path');
function document(){
    const nodes=new Map();function node(tag){return {tag,children:[],hidden:false,textContent:'',attributes:{},append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children;this.textContent=''},setAttribute(key,val){this.attributes[key]=val},addEventListener(name,handler){this[name]=handler}};}
    return {createElement:node,getElementById(id){if(!nodes.has(id))nodes.set(id,node('div'));return nodes.get(id)},nodes};
}
const text=node=>[node.textContent,...node.children.map(text)].join(' ');
test('overview delivery summary always loads government-wide counts regardless of retained filters',async()=>{
    const {loadTrackerView}=require('../../main/resources/static/js/government-tracker.js'),calls=[];
    const api={fetchMandate:async()=>({recentUpdates:[]}),fetchPreview:async()=>null,fetchInsights:async state=>{calls.push(state);return {totalEntries:0,entries:[],deliveryCounts:{}};}};
    const view=await loadTrackerView(api,{tab:'overview',origin:'CAMPAIGN',topic:'HEALTH',assessment:'DELIVERED'},{});
    assert.ok(calls.some(state=>state.origin==='GOVERNMENT'&&!state.topic&&!state.assessment&&!state.party&&!state.q));
    assert.deepEqual(view.deliveryInsights.deliveryCounts,{});
});
test('commitment detail hides repeated source labels and empty measurements without hiding sources or assessment',()=>{
    const {renderCommitment}=require('../../main/resources/static/js/commitment-detail.js');
    for(const locale of ['ar','fr','en']){
        const doc=document();
        renderCommitment({title:{ar:'المساعد الأسري',en:'Family assistant'},originalText:'  المساعد الأسري  ',observations:[],revisions:[],deliveryAssessment:null,evidence:[{id:'pdf',url:'https://example.org/programme.pdf'}]},locale,doc);
        assert.equal(doc.getElementById('commitmentOriginalSection').hidden,true);
        assert.equal(doc.getElementById('commitmentObservationSection').hidden,true);
        assert.match(text(doc.getElementById('commitmentAssessment')),/ما تقيّمش|non encore évaluée|not yet assessed/);
        assert.equal(doc.getElementById('commitmentEvidence').children[0].children[0].href,'https://example.org/programme.pdf');
        assert.ok(text(doc.getElementById('commitmentHistory')).trim());
        renderCommitment({title:{ar:'مهننة الرعاية الأسرية'},originalText:'المساعد الأسري',programmeContext:{kind:'institutional'},evidence:[{id:'pdf',url:'https://example.org/programme.pdf',passage:'المساعد الأسري'}]},locale,doc);
        assert.equal(doc.getElementById('commitmentOriginalSection').hidden,true);
        assert.match(text(doc.getElementById('commitmentEvidence')),/المساعد الأسري/);
        renderCommitment({title:{ar:'المساعد الأسري'},originalText:'تكوين وتأهيل العاملين فالمساعدة الأسرية',observations:[{id:'obs',value:10,unit:'people'}],deliveryAssessment:{state:'UNDERWAY',explanation:{en:'Evidence reviewed'}}},locale,doc);
        assert.equal(doc.getElementById('commitmentOriginalSection').hidden,false);
        assert.equal(doc.getElementById('commitmentObservationSection').hidden,false);
        assert.match(text(doc.getElementById('commitmentObservations')),/10.*people/);
        assert.doesNotMatch(text(doc.getElementById('commitmentAssessment')),/ما تقيّمش|non encore évaluée|not yet assessed/);
    }
});
test('government overview uses the same rich programme cards and a separate publication feed',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js'),doc=document();
    renderTracker({locale:'en',loadState:'ready',mandate:{recentUpdates:[]},programmePicks:[{slug:'pick',title:{en:'Programme pick'},originalText:'A programme excerpt',programmeContext:{deadlineText:'2027'}}],publications:[{subjectType:'COMMITMENT',subjectId:'news',title:{en:'Published record'},publishedAt:'2026-10-23T00:00:00Z'}]},{tab:'overview'},doc);
    assert.match(text(doc.getElementById('trackerOverview')),/A programme excerpt/);
    assert.match(text(doc.getElementById('trackerOverview')),/Published record/);
    assert.match(text(doc.getElementById('trackerOverview')),/Show other picks/);
});
test('overview loads discovery independently, so failed picks do not hide the government',async()=>{
    const {loadTrackerView}=require('../../main/resources/static/js/government-tracker.js');
    const api={fetchMandate:async()=>({recentUpdates:[]}),fetchPreview:async()=>null,fetchInsights:async()=>{throw Error('offline')}};
    const view=await loadTrackerView(api,{tab:'overview'},{});assert.equal(view.picksFailed,true);assert.ok(view.mandate);
});
test('shuffling programme picks updates only their grid and keeps the trigger mounted',async()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js'),doc=document(),walk=n=>[n,...n.children.flatMap(walk)];
    const view={locale:'en',loadState:'ready',programmePicks:[{slug:'old',title:{en:'Old pick'}}],onShuffle:async()=>[{slug:'new',title:{en:'New pick'}}]};
    renderTracker(view,{tab:'overview'},doc);const host=doc.getElementById('trackerOverview'),button=walk(host).find(n=>n.id==='trackerRefreshPicks');
    await button.onclick();assert.ok(walk(host).includes(button));assert.match(text(host),/New pick/);assert.doesNotMatch(text(host),/Old pick/);
    view.onShuffle=async()=>{throw Error('offline')};await button.onclick();assert.match(text(host),/could not be loaded/);assert.match(text(host),/New pick/);assert.equal(button.disabled,false);
});
test('switching programme catalogs clears unsupported campaign topics but keeps shared subjects',()=>{
    const {catalogState}=require('../../main/resources/static/js/government-tracker.js');
    const catalog=[{code:'WATER'},{code:'HEALTH'}];
    assert.equal(catalogState({topic:'WATER_ENERGY_ENVIRONMENT',party:'PI',page:2},'GOVERNMENT',catalog).topic,'');
    assert.equal(catalogState({topic:'HEALTH',page:2},'GOVERNMENT',catalog).topic,'HEALTH');
    assert.equal(catalogState({topic:'HEALTH',page:2},'GOVERNMENT',catalog).page,1);
});
test('regional bar focus survives loading and returns without stealing unrelated control focus',()=>{
    const {createTopicFocus}=require('../../main/resources/static/js/government-tracker.js'),focus=createTopicFocus(),doc=document();
    doc.body={};const region=doc.getElementById('trackerRegion'),bar=doc.getElementById('trackerRegionBar-oriental');bar.id='trackerRegionBar-oriental';
    region.focus=()=>{doc.activeElement=region};region.id='trackerRegion';bar.focus=()=>{doc.activeElement=bar};doc.activeElement=bar;
    focus.capture(doc);focus.restore(doc,'loading');assert.equal(doc.activeElement,region);
    focus.capture(doc);focus.restore(doc,'ready');assert.equal(doc.activeElement,bar);
    focus.capture(doc);const unrelated={};doc.activeElement=unrelated;focus.capture(doc);focus.restore(doc,'ready');assert.equal(doc.activeElement,unrelated);
});
test('regions default to programme references and expose measurements only when explicitly selected',async()=>{
    const model=require('../../main/resources/static/js/government-model.js'),{loadTrackerView}=require('../../main/resources/static/js/government-tracker.js'),calls=[];
    const api={fetchMandate:async()=>({}),fetchPreview:async()=>null,request:async path=>{calls.push(path);return {items:[]}}};
    const state=model.readTrackerState('/government?tab=regions&topic=WATER&q=dam');assert.equal(state.regionalView,'programme');
    await loadTrackerView(api,state,{});assert.match(calls.pop(),/regional-programme\?.*topic=WATER.*q=dam/);
    await loadTrackerView(api,{...state,regionalView:'measurements'},{});assert.match(calls.pop(),/observations\?/);
    assert.equal(model.readTrackerState(model.trackerUrl('/government',{...state,regionalView:'measurements'})).regionalView,'measurements');
});
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
test('practical-measure updates open the published measure rather than the tracker root',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js');const doc=document();renderTracker({locale:'en',loadState:'ready',mandate:{recentUpdates:[{subjectType:'MEASURE',subjectId:'measure-id',title:{en:'New access'}}]}},{tab:'overview',origin:'GOVERNMENT'},doc);
    const walk=node=>[node,...node.children.flatMap(walk)];assert.ok(walk(doc.getElementById('trackerOverview')).some(node=>node.href==='/government/measures/measure-id'));
});
test('preview metadata is a collapsed source disclosure without a large heading or entry counter',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js'),doc=document();
    renderTracker({locale:'en',loadState:'ready',preview:{localPreview:true,entryCount:595,publicSourceUrl:'https://www.chambredesrepresentants.ma/ar/node/467708',pdfUrl:'/api/catalog/government/2026-2031/preview/pdf'},mandate:{lifecycle:'FORMATION',recentUpdates:[]}}, {tab:'overview',origin:'GOVERNMENT'},doc);
    assert.doesNotMatch(text(doc.getElementById('trackerPreview')),/local research preview|595/i);
    const disclosure=doc.getElementById('trackerPreview').children.find(node=>node.tag==='details');assert.ok(disclosure);assert.ok(!disclosure.open);
    assert.doesNotMatch(text(doc.getElementById('trackerPreview')),/not.*matched.*official/i);
    assert.match(text(doc.getElementById('trackerPreview')),/not unique promises or evidence of delivery/);
    assert.doesNotMatch(text(doc.getElementById('trackerOverview')),/Government formation/);
    const walk=node=>[node,...node.children.flatMap(walk)],links=walk(doc.getElementById('trackerOverview'));
    assert.ok(links.some(node=>node.href==='/government?tab=programme&origin=GOVERNMENT'));
    assert.ok(links.some(node=>node.href==='/government/ministers'));
    assert.match(text(doc.getElementById('trackerOverview')),/publications.*delivery/i);
});
test('Arabic programme entries keep their language and source status in translated interfaces, with ten cards maximum',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js');
    for(const locale of ['en','fr','ar']){const doc=document();renderTracker({locale,loadState:'ready',page:{items:Array.from({length:11},(_,i)=>({slug:'entry-'+i,origin:'GOVERNMENT',title:{ar:'الصحة',fr:'الصحة',en:'الصحة'},programmeContext:{sourceStatus:'MIRROR_UNAUTHENTICATED',printedPages:[12]},deliveryAssessment:null}))}},{tab:'programme',origin:'GOVERNMENT'},doc);
        const grid=doc.getElementById('trackerProgramme').children.find(node=>node.className==='government-card-grid');assert.ok(grid,'cards share a responsive grid without wrapping the charts');
        const cards=grid.children.filter(node=>node.tag==='article');assert.equal(cards.length,10);
        const heading=cards[0].children.find(node=>node.tag==='h2');assert.equal(heading.children[0].attributes.lang,'ar');
        assert.ok(text(cards[0]).includes(require('../../main/resources/static/js/government-model.js').COPY[locale].UNASSESSED));
        assert.match(text(cards[0]),/12/);
        assert.ok(!cards[0].children.some(node=>node.className==='government-source-status'));
    }
});
test('programme and delivery cards use the same bounded grid in all locales',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js');
    for(const tab of ['programme','delivery'])for(const locale of ['ar','fr','en']){
        const doc=document();renderTracker({locale,loadState:'ready',page:{items:Array.from({length:12},(_,i)=>({slug:'entry-'+i,origin:'GOVERNMENT',title:{ar:'الصحة',fr:'Santé',en:'Health'}}))}},{tab,origin:'GOVERNMENT'},doc);
        const panel=doc.getElementById(tab==='programme'?'trackerProgramme':'trackerDelivery'),grid=panel.children.find(node=>node.className==='government-card-grid');
        assert.ok(grid);assert.equal(grid.children.length,10);assert.ok(grid.children.every(node=>node.tag==='article'));
    }
});
test('detail omits internal research context while retaining original text, sources and unassessed delivery',()=>{
    const {renderCommitment}=require('../../main/resources/static/js/commitment-detail.js');
    for(const locale of ['ar','fr','en']){
        const doc=document(),url='https://www.chambredesrepresentants.ma/ar/node/467708';
        renderCommitment({title:{ar:'الصحة'},originalText:'المصدر الأصلي',programmeContext:{sourceStatus:'MIRROR_UNAUTHENTICATED',printedPages:[34,46],scopeText:'Establish regulated family-assistant and care occupations',caveats:['Needs manual review'],summaryEn:'Internal extraction note'},evidence:[{id:'programme',url,passage:'نص البرنامج',originalLanguage:'ar'}],target:null,deliveryAssessment:null},locale,doc);
        assert.equal(doc.nodes.has('commitmentContext'),false);
        assert.equal(doc.getElementById('commitmentOriginal').textContent,'المصدر الأصلي');
        const walk=node=>[node,...node.children.flatMap(walk)];
        assert.ok(walk(doc.getElementById('commitmentEvidence')).some(node=>node.href===url));
        assert.ok(walk(doc.getElementById('commitmentAssessment')).some(node=>node.attributes['data-state']==='UNASSESSED'));
        assert.doesNotMatch([...doc.nodes.values()].map(text).join(' '),/Establish regulated|Needs manual review|Internal extraction note/);
    }
});
test('tracker source disclosure no longer includes local test account controls in any locale',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js');
    for(const locale of ['ar','fr','en']){const doc=document();renderTracker({locale,loadState:'ready',preview:{localPreview:true,sessionUrl:'/api/catalog/government/2026-2031/preview/session'}},{tab:'overview',origin:'GOVERNMENT'},doc);
    const walk=node=>[node,...node.children.flatMap(walk)];assert.ok(!walk(doc.getElementById('trackerPreview')).some(node=>node.tag==='button'));}
});
test('preview overview leaves membership browsing to the paginated directory and never labels import time as verification',()=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js'),doc=document();
    renderTracker({locale:'en',loadState:'ready',preview:{localPreview:true},mandate:{verifiedAt:'2026-10-23T12:00:00Z',membership:Array.from({length:32},(_,i)=>({name:{en:'Member '+i},role:{en:'Portfolio'}}))}},{tab:'overview',origin:'GOVERNMENT'},doc);
    const output=text(doc.getElementById('trackerOverview'));assert.doesNotMatch(output,/Member 0|Member 31|Last verified/);
});
test('removing research context preserves literal deadline without inventing a delivery chart',()=>{
    const {renderCommitment}=require('../../main/resources/static/js/commitment-detail.js'),doc=document();
    renderCommitment({title:{en:'Target'},target:null,deadline:null,programmeContext:{targetText:'value: 45 · unit: days per pathway · operator: equal · baseline: {"value":10,"unit":"days"}',deadlineText:'By the end of the mandate'}},'en',doc);
    assert.match(text(doc.getElementById('commitmentFacts')),/By the end of the mandate/);
    assert.doesNotMatch([...doc.nodes.values()].map(text).join(' '),/value: 45|\{"value"/);
    assert.equal(doc.getElementById('commitmentChart').hidden,true);
});
test('preview source metadata is labelled as research notes rather than presented as source quotations',async t=>{
    const {renderTracker}=require('../../main/resources/static/js/government-tracker.js'),doc=document(),saved=globalThis.FhemniGovernmentAPI;t.after(()=>{globalThis.FhemniGovernmentAPI=saved});
    globalThis.FhemniGovernmentAPI={request:async()=>({passage:'Research summary — Cabinet source',url:'https://example.org/cabinet',originalLanguage:'fr'})};
    renderTracker({locale:'en',loadState:'ready',preview:{localPreview:true},mandate:{verifiedAt:'2026-10-23T00:00:00Z',programme:{evidenceIds:['research-source']}}},{tab:'overview',origin:'GOVERNMENT'},doc);
    const walk=node=>[node,...node.children.flatMap(walk)],disclosure=walk(doc.getElementById('trackerOverview')).find(node=>node.id==='trackerSources');disclosure.open=true;await disclosure.toggle();
    const nodes=walk(disclosure);assert.ok(nodes.some(node=>node.tag==='p'&&node.textContent==='Research summary — Cabinet source'&&node.attributes.lang==='en'));assert.ok(!nodes.some(node=>node.tag==='blockquote'));
});
