const {test}=require('node:test'),assert=require('node:assert/strict');
let visuals={};try{visuals=require('../../main/resources/static/js/government-visuals.js');}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}
function document(){const nodes=new Map();const make=tag=>({tag,children:[],attributes:{},style:{},textContent:'',append(...xs){this.children.push(...xs)},replaceChildren(...xs){this.children=xs;this.textContent=''},setAttribute(k,v){this.attributes[k]=String(v)},focus(){},addEventListener(k,v){this[k]=v}});return {createElement:make,getElementById(id){if(!nodes.has(id))nodes.set(id,make('section'));return nodes.get(id)}};}
const walk=n=>[n,...n.children.flatMap(walk)],text=n=>walk(n).map(x=>x.textContent).join(' ');
test('delivery ring keeps proposals outside its results and leaves their discovery to the milestone card',()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        const budgetEntries=Array.from({length:8},(_,i)=>({slug:'budget-'+i,title:{ar:'وعد '+i,fr:'Engagement '+i,en:'Commitment '+i},summary:{ar:'600 درهم مقترحة',fr:'600 DH proposés',en:'DH 600 proposed'}}));
        visuals.renderDeliverySummary(host,{totalEntries:595,deliveryCounts:{UNASSESSED:595},budgetEntries},{locale,document:doc});
        assert.ok(!walk(host).some(n=>n.tag==='details'));
        assert.ok(!walk(host).some(n=>n.href==='/government/commitments/budget-0'));
        assert.equal(walk(host).find(n=>n.className==='government-delivery-ring').style.background,'conic-gradient(#dce3de 0% 100%)');
        assert.doesNotMatch(text(host),/0%|NaN|undefined/);
        visuals.renderDeliverySummary(host,{totalEntries:595,deliveryCounts:{UNASSESSED:595},budgetEntries:[]},{locale,document:doc});
        assert.equal(walk(host).filter(n=>n.tag==='details').length,0);
    }
});
test('delivery ring keeps unknown evidence separate and links every status to its filtered list',()=>{
    const doc=document(),host=doc.createElement('section');
    visuals.renderDeliverySummary(host,{totalEntries:10,deliveryCounts:{DELIVERED:3,UNDERWAY:2,PARTLY_DELIVERED:1,NO_EVIDENCE_FOUND:1,WITHDRAWN:1,UNASSESSED:2}},{locale:'en',document:doc});
    assert.match(text(host),/30%/);
    assert.equal(walk(host).filter(n=>n.tag==='a').length,6);
    assert.ok(walk(host).some(n=>n.href==='/government?tab=programme&origin=GOVERNMENT&assessment=UNASSESSED'));
    assert.match(walk(host).find(n=>n.className==='government-delivery-ring').style.background,/conic-gradient/);
});
test('unassessed, empty and unavailable delivery totals never claim zero percent delivery',()=>{
    for(const locale of ['ar','fr','en'])for(const insights of [null,{totalEntries:0,deliveryCounts:{}},{totalEntries:595,deliveryCounts:{UNASSESSED:595}},{totalEntries:595}]){
        const doc=document(),host=doc.createElement('section');visuals.renderDeliverySummary(host,insights,{locale,document:doc});
        assert.doesNotMatch(text(host),/0%|NaN|undefined/);
        if(insights?.deliveryCounts?.UNASSESSED)assert.match(text(host),/595/);
    }
});
const row=(slug='assistance',extra={})=>({slug,title:{ar:'الدعم',fr:'الدعم',en:'الدعم'},topicCodes:['HEALTH'],programmeContext:{sourceStatus:'MIRROR_UNAUTHENTICATED',printedPages:[55],targetText:'value: 1000 · unit: MAD per month · operator: equal · baseline: {"value":500,"unit":"MAD per month","source":"programme-reported"}',deadlineText:null},...extra});
test('unclassified entries are not disguised as a policy topic',()=>{
    for(const locale of ['ar','fr','en']){const doc=document(),host=doc.createElement('section');visuals.renderProgramme(host,{topics:[{code:'OTHER',count:210}],entries:[]},{locale,document:doc,topics:[{code:'OTHER',label:{ar:'مواضيع أخرى',en:'Other topics',fr:'Autres thèmes'}}]});
        assert.ok(text(host).includes({ar:'ما تصنفش بعد',fr:'Pas encore classé',en:'Not yet classified'}[locale]));assert.match(text(host),/210/);}
});
test('baseline comparison preserves programme provenance, zero and strict inequality without inventing progress',()=>{
    assert.equal(typeof visuals.baselineComparison,'function');
    assert.deepEqual(visuals.baselineComparison(row()),{baseline:500,target:1000,unit:'MAD per month',operator:'=',period:null});
    const r=row();r.programmeContext.targetText='value: 1 · unit: days · operator: less-than · baseline: {"value":0,"unit":"days","source":"programme-reported"}';
    assert.equal(visuals.baselineComparison(r).baseline,0);assert.equal(visuals.baselineComparison(r).operator,'<');
    for(const bad of ['value: 50 · unit: percent · operator: equal · baseline: {"value":38,"unit":"hours","source":"programme-reported"}', 'value: 100 · unit: people · operator: equal', 'value: 100 · value: 200 · unit: people', 'value: Infinity · unit: people · operator: equal · baseline: {"value":3,"unit":"people","source":"programme-reported"}']){r.programmeContext.targetText=bad;assert.equal(visuals.baselineComparison(r),null);}
});
test('stated-date timeline retains literal precision and leaves recurring/yearless entries out of the dated series',()=>{
    assert.equal(typeof visuals.programmeDates,'function');
    const rows=['2031','ابتداء من السنة المالية 2027','2027–2031','كل ثلاثة أشهر',null].map((deadlineText,i)=>row('r'+i,{programmeContext:{deadlineText}}));
    const result=visuals.programmeDates(rows);
    assert.equal(result.dated.length,3);assert.equal(result.withoutYear,2);
    assert.equal(result.dated[0].literal,'ابتداء من السنة المالية 2027');
    assert.deepEqual(result.dated[1].years,[2027,2031]);assert.equal(result.dated[0].deadline,undefined);
});
test('interactive dates expand exact year groups without assigning periods or undated entries to a year',()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        const entries=[...Array.from({length:12},(_,i)=>row('year-'+i,{programmeContext:{deadlineText:'Starting in 2027',sourceStatus:'MIRROR_UNAUTHENTICATED',printedPages:[7]}})),row('period',{programmeContext:{deadlineText:'2027–2031'}}),row('unknown',{programmeContext:{deadlineText:'Every three months'}})];
        visuals.renderProgramme(host,{entries},{locale,document:doc,state:{tab:'programme'}});
        const year=walk(host).find(n=>n.id==='programme-dates-year-2027');
        assert.ok(year,'single-year timeline marker');
        const panel=walk(host).find(n=>n.id==='programme-dates-panel');
        assert.equal(panel.hidden,true);
        year.onclick();assert.equal(panel.hidden,false);assert.equal(year.attributes['aria-expanded'],'true');
        assert.equal(walk(panel).filter(n=>n.tag==='li').length,10);
        assert.ok(!walk(panel).some(n=>n.href?.endsWith('/period')));
        assert.match(text(panel),/Starting in 2027/);
        assert.ok(!walk(panel).some(n=>n.className==='government-source-status'));
        walk(panel).find(n=>n.tag==='button'&&n.textContent===visuals.COPY[locale].next).onclick();
        assert.equal(walk(panel).filter(n=>n.tag==='li').length,2);
        const period=walk(host).find(n=>n.id==='programme-dates-multi-2027-2031');period.onclick();
        assert.equal(year.attributes['aria-expanded'],'false');
        assert.equal(walk(panel).filter(n=>n.tag==='li').length,1);
        assert.match(text(panel),/2027–2031/);
        assert.ok(!walk(host).some(n=>n.id==='programme-dates-year-2031'),'period endpoints do not become individual-year events');
        const undated=walk(host).find(n=>n.id==='programme-dates-undated');undated.onclick();
        assert.match(text(panel),/Every three months/);
        undated.onclick();assert.equal(panel.hidden,true);assert.equal(undated.attributes['aria-expanded'],'false');
    }
});
test('compact timeline keeps guidance discoverable, concise counts accessible, and closes back to its marker',()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        visuals.renderProgramme(host,{entries:[row('year',{programmeContext:{deadlineText:'2027'}}),row('period',{programmeContext:{deadlineText:'2027–2031'}}),row('unknown',{programmeContext:{deadlineText:null}})]},{locale,document:doc});
        const guide=walk(host).find(n=>n.className==='government-date-guide');assert.ok(guide);
        assert.equal(guide.tag,'details');assert.ok(text(guide).includes(visuals.COPY[locale].datesNote));
        const marker=walk(host).find(n=>n.id==='programme-dates-year-2027');
        const count=marker.children.find(n=>n.className==='government-date-marker-count');
        assert.equal(count.textContent,'1');assert.equal(count.attributes['aria-hidden'],'true');
        assert.equal(marker.attributes['aria-label'],undefined,'do not override language-tagged content and literal timing');
        assert.match(marker.children.find(n=>n.className==='sr-only').textContent,/: 1/);
        const undated=walk(host).find(n=>n.className==='government-date-undated');
        assert.ok(walk(undated).some(n=>n.id==='programme-dates-undated'));
        let focused=false;marker.focus=()=>{focused=true};marker.onclick();
        const panel=walk(host).find(n=>n.id==='programme-dates-panel');assert.equal(panel.hidden,false);
        walk(panel).find(n=>n.id==='programme-dates-close').onclick();
        assert.equal(panel.hidden,true);assert.equal(marker.attributes['aria-expanded'],'false');assert.equal(focused,true);
    }
});
test('timeline previews actual entries and opens their details immediately beside the selected date',()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        visuals.renderProgramme(host,{entries:[row('later',{title:{en:'Later item',fr:'Later item',ar:'Later item'},programmeContext:{deadlineText:'2031'}}),row('earlier',{title:{en:'Care access',fr:'Care access',ar:'Care access'},programmeContext:{deadlineText:'Starting in 2027'}}),row('period',{title:{en:'Hospital network',fr:'Hospital network',ar:'Hospital network'},programmeContext:{deadlineText:'2027 / 2031'}})]},{locale,document:doc});
        const marker=walk(host).find(n=>n.id==='programme-dates-year-2027');
        assert.match(text(marker),/Care access/);
        assert.match(text(marker),/Starting in 2027/);
        marker.onclick();
        const selected=walk(host).find(n=>n.children.includes(marker));
        assert.ok(walk(selected).some(n=>n.id==='programme-dates-panel'&&!n.hidden),'details stay with the selected date, not below the entire timeline');
        const period=walk(host).find(n=>n.id==='programme-dates-multi-2027-2031');
        assert.match(text(period),/Hospital network/);period.onclick();
        assert.ok(!walk(selected).some(n=>n.id==='programme-dates-panel'));
        assert.equal(walk(host).filter(n=>n.id==='programme-dates-panel').length,1);
        const dates=walk(host).filter(n=>n.className==='government-date-marker').map(n=>n.id);
        assert.deepEqual(dates,['programme-dates-year-2027','programme-dates-multi-2027-2031','programme-dates-year-2031']);
    }
});
test('date disclosures show readable target figures, not serialized extraction metadata',()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section'),r=row();r.programmeContext.deadlineText='ابتداء من 2027';
        visuals.renderProgramme(host,{entries:[r]},{locale,document:doc});
        walk(host).find(n=>n.id==='programme-dates-year-2027').onclick();
        const panel=walk(host).find(n=>n.id==='programme-dates-panel');
        assert.match(text(panel),/1[,.\s]?000/);assert.match(text(panel),/500/);
        assert.doesNotMatch(text(panel),/value:|operator:|baseline:|programme-reported/);
        const timing=walk(panel).find(n=>n.className==='government-date-literal');assert.equal(timing.attributes.lang,'ar');
    }
});
test('standalone date targets retain inequalities and reject ambiguous extracted values',()=>{
    for(const targetText of ['value: 12 · unit: billion MAD per year · operator: >=','value: 12 · value: 20 · unit: billion MAD per year · operator: >=']){
        const doc=document(),host=doc.createElement('section');
        visuals.renderProgramme(host,{entries:[row('scalar',{originalText:'Original source passage',programmeContext:{deadlineText:'2027',targetText}})]},{locale:'en',document:doc});
        walk(host).find(n=>n.id==='programme-dates-year-2027').onclick();const panel=walk(host).find(n=>n.id==='programme-dates-panel');
        if(targetText.includes('value: 20')){assert.match(text(panel),/Original source passage/);assert.doesNotMatch(text(panel),/≥/);}
        else assert.match(text(panel),/≥ 12.*billion MAD per year/);
        assert.doesNotMatch(text(panel),/value:|operator:/);
    }
});
test('topic bars use complete summary counts, keep filters and reset page on activation in every locale',()=>{
    assert.equal(typeof visuals.renderProgramme,'function');
    for(const locale of ['ar','fr','en']){const doc=document(),host=doc.createElement('section'),changes=[];
        visuals.renderProgramme(host,{totalEntries:79,topics:[{code:'HEALTH',count:79}],entries:[row()]},{locale,document:doc,state:{tab:'delivery',q:'care',page:7,topic:''},topics:[{code:'HEALTH',label:{ar:'الصحة',fr:'Santé',en:'Health'}}],onFilter:state=>changes.push(state)});
        const button=walk(host).find(n=>n.tag==='button'&&n.attributes['aria-pressed']==='false');assert.ok(button);button.onclick();
        assert.deepEqual(changes,[{tab:'delivery',q:'care',page:1,topic:'HEALTH'}]);assert.match(text(host),/79/);
        assert.doesNotMatch(text(host),/NaN|undefined|0%/);assert.ok(walk(host).some(n=>n.href==='/government/commitments/assistance'));
        assert.match(text(host),new RegExp(visuals.COPY[locale].ambitionNote));
    }
});
test('baseline renderer uses text and labelled bars, preserves operators, and never creates completion meters',()=>{
    assert.equal(typeof visuals.renderBaseline,'function');
    for(const locale of ['ar','fr','en']){const doc=document(),host=doc.createElement('section'),r=row();r.programmeContext.targetText=r.programmeContext.targetText.replace('equal','>=');
        visuals.renderBaseline(host,r,{locale,document:doc});assert.match(text(host),/≥/);assert.match(text(host),/500/);assert.match(text(host),/1[,.\s]?000/);
        assert.ok(!walk(host).some(n=>n.tag==='progress'||n.attributes.role==='progressbar'));assert.match(text(host),new RegExp(visuals.COPY[locale].baseline));
    }
});
test('timelines paginate ten at a time and cabinet dates refer to membership with navigable names',()=>{
    assert.equal(typeof visuals.renderCabinet,'function');
    const doc=document(),host=doc.createElement('section'),members=Array.from({length:14},(_,i)=>({id:String(i),name:{ar:'عضو '+i,en:'Member '+i},effectiveFrom:'2026-10-16',effectiveUntil:i===0?'2026-10-20':null,evidenceIds:['source']}));
    visuals.renderCabinet(host,members,{locale:'en',document:doc,asOf:'2026-10-23'});
    assert.equal(walk(host).filter(n=>n.tag==='li').length,10);
    const next=walk(host).find(n=>n.tag==='button'&&n.textContent===visuals.COPY.en.next);next.onclick();
    assert.equal(walk(host).filter(n=>n.tag==='li').length,5);
    assert.ok(walk(host).some(n=>n.href?.startsWith('/government/ministers?status=all&q=')));assert.match(text(host),/Joined government/);
    assert.doesNotMatch(text(host),/Became minister/);
});
test('tracker loads matching insights with effective delivery filters, and isolates a chart failure from results',async()=>{
    const tracker=require('../../main/resources/static/js/government-tracker.js');assert.equal(typeof tracker.loadTrackerView,'function');
    const calls=[],api={fetchMandate:async()=>({}),fetchPreview:async()=>null,fetchCommitments:async state=>{calls.push(['rows',state]);return {items:[],totalItems:79};},fetchInsights:async state=>{calls.push(['insights',state]);return {totalEntries:79,topics:[{code:'HEALTH',count:79}],entries:[]};}};
    const state={tab:'delivery',origin:'CAMPAIGN',party:'PAM',topic:'HEALTH',q:'care',assessment:'DELIVERED',page:8};
    const view=await tracker.loadTrackerView(api,state,{});assert.equal(view.insights.totalEntries,79);
    assert.equal(calls[0][1].assessment,'DELIVERED');assert.equal(calls[1][1].assessment,'');assert.equal(calls[0][1].origin,'GOVERNMENT');assert.equal(calls[0][1].party,'');
    api.fetchInsights=async()=>{throw new Error('offline')};const partial=await tracker.loadTrackerView(api,state,{});assert.equal(partial.page.totalItems,79);assert.equal(partial.insightsFailed,true);
});
test('actual tracker and detail renderers mount graphics, with the baseline warning adjacent in each locale',()=>{
    const tracker=require('../../main/resources/static/js/government-tracker.js'),detail=require('../../main/resources/static/js/commitment-detail.js');
    for(const locale of ['ar','fr','en']){const doc=document(),r={...row(),origin:'GOVERNMENT',evidence:[],observations:[],revisions:[]};
        tracker.renderTracker({locale,loadState:'ready',mandate:{membership:[],recentUpdates:[]},page:{items:[r],totalItems:1},insights:{totalEntries:1,topics:[{code:'HEALTH',count:1}],entries:[r]}},{tab:'programme',origin:'GOVERNMENT'},doc);
        assert.match(text(doc.getElementById('trackerProgramme')),new RegExp(visuals.COPY[locale].ambitions));
        detail.renderCommitment(r,locale,doc);assert.match(text(doc.getElementById('commitmentAmbition')),new RegExp(visuals.COPY[locale].ambitionNote));assert.match(text(doc.getElementById('commitmentAmbition')),/500/);
    }
});
test('keyboard focus on a chart filter survives tracker rerender',()=>{
    const {captureTrackerUi,restoreTrackerUi}=require('../../main/resources/static/js/government-tracker.js');
    let focused=false;const doc={activeElement:{id:'trackerTopicBar-HEALTH'},getElementById:id=>id==='trackerTopicBar-HEALTH'?{focus(){focused=true}}:null};
    const saved=captureTrackerUi(doc,{select(){}},'delivery');restoreTrackerUi(doc,saved);assert.equal(focused,true);
});
test('campaign promises never acquire government programme graphics or trigger their data request',async()=>{
    const tracker=require('../../main/resources/static/js/government-tracker.js'),doc=document();let requested=false;
    const api={fetchMandate:async()=>({}),fetchPreview:async()=>null,fetchCommitments:async()=>({items:[],totalItems:0}),fetchInsights:async()=>{requested=true;return {}}};
    await tracker.loadTrackerView(api,{tab:'programme',origin:'CAMPAIGN'},{});assert.equal(requested,false);
    tracker.renderTracker({locale:'en',loadState:'ready',mandate:{membership:[],recentUpdates:[]},page:{items:[],totalItems:0},insights:{topics:[{code:'HEALTH',count:79}],entries:[row()]}},{tab:'programme',origin:'CAMPAIGN'},doc);
    assert.ok(!text(doc.getElementById('trackerProgramme')).includes(visuals.COPY.en.topics));
});
test('topic focus intent persists through loading, without stealing focus after the reader moves elsewhere',()=>{
    const {createTopicFocus}=require('../../main/resources/static/js/government-tracker.js');assert.equal(typeof createTopicFocus,'function');
    const nodes={},doc={getElementById:id=>nodes[id],activeElement:null};
    for(const id of ['trackerTopicBar-HEALTH','trackerFilterTitle','trackerSearch'])nodes[id]={id,focus(){doc.activeElement=this}};
    const focus=createTopicFocus();doc.activeElement=nodes['trackerTopicBar-HEALTH'];focus.capture(doc);focus.restore(doc,'loading');assert.equal(doc.activeElement.id,'trackerFilterTitle');
    focus.capture(doc);focus.restore(doc,'ready');assert.equal(doc.activeElement.id,'trackerTopicBar-HEALTH');
    focus.capture(doc);focus.restore(doc,'loading');doc.activeElement=nodes.trackerSearch;focus.capture(doc);focus.restore(doc,'ready');assert.equal(doc.activeElement.id,'trackerSearch');
    doc.activeElement=nodes['trackerTopicBar-HEALTH'];focus.capture(doc);focus.restore(doc,'loading');const link={tag:'a'};doc.activeElement=link;focus.capture(doc);focus.restore(doc,'ready');assert.equal(doc.activeElement,link);
});
