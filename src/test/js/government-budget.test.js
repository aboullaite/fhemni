const {test}=require('node:test'),assert=require('node:assert/strict');
const {renderCommitment}=require('../../main/resources/static/js/commitment-detail.js');
const {renderCards}=require('../../main/resources/static/js/government-cards.js');
const {renderTracker}=require('../../main/resources/static/js/government-tracker.js');
const make=tag=>({tag,children:[],attributes:{},style:{},textContent:'',append(...xs){this.children.push(...xs)},replaceChildren(...xs){this.children=xs;this.textContent=''},setAttribute(k,v){this.attributes[k]=String(v)}});
const walk=n=>[n,...n.children.flatMap(walk)],text=n=>walk(n).map(x=>x.textContent).join(' ');
const document=()=>{const nodes=new Map();return {createElement:make,getElementById(id){if(!nodes.has(id))nodes.set(id,make('div'));return nodes.get(id)}}};
const row={slug:'programme-p063-primary-care-1600',origin:'GOVERNMENT',title:{ar:'الصحة',fr:'Santé',en:'Health'},deliveryAssessment:null,budgetProposals:[{kind:'PROPOSED_FUNDING',reviewedOn:'2026-10-25',summary:{ar:'1.8 مليار درهم مقترحة',fr:'1,8 milliard de DH proposé',en:'DH 1.8 billion proposed'},detail:{ar:'ماشي مراكز تأهلات',fr:'Pas des centres déjà rénovés',en:'Not centres already renovated'},source:{title:{ar:'مذكرة التقديم',fr:'Note de présentation',en:'Presentation note'},url:'https://medias24.com/example.pdf',printedPages:[51],pdfPages:[61],publishedOn:'2026-10-23',sha256:'8'.repeat(64)}}]};
test('budget proposals appear consistently in detail and both card layouts without claiming delivery',()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document();renderCommitment(row,locale,doc);
        const budget=doc.getElementById('commitmentBudget');assert.match(text(budget),/1[.,]8/);
        assert.ok(text(budget).includes(row.budgetProposals[0].detail[locale]));
        assert.ok(walk(budget).some(n=>n.href==='https://medias24.com/example.pdf#page=61'));
        assert.match(text(budget),/51/);assert.match(text(budget),/61/);assert.match(text(budget),/2026/);
        assert.ok(walk(doc.getElementById('commitmentAssessment')).some(n=>n.attributes['data-state']==='UNASSESSED'));
        const host=make('div');renderCards(host,[row],{locale,document:doc});assert.match(text(host),/1[.,]8/);
        renderTracker({locale,loadState:'ready',page:{items:[row]}},{tab:'programme',origin:'GOVERNMENT'},doc);
        assert.match(text(doc.getElementById('trackerProgramme')),/1[.,]8/);
        renderCommitment({...row,budgetProposals:[]},locale,doc);
        assert.equal(doc.getElementById('commitmentBudget').hidden,true);
        assert.equal(text(doc.getElementById('commitmentBudget')),'');
    }
});
test('budget entry point reveals distinct proposal cards and can collapse them in every locale',()=>{
    const {renderMilestone}=require('../../main/resources/static/js/government-budget.js');
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=make('section'),entries=Array.from({length:8},(_,i)=>({slug:'entry-'+i,title:{ar:'عنوان '+i,fr:'Titre '+i,en:'Title '+i},summary:{ar:'تمويل مقترح',fr:'Financement proposé',en:'Proposed funding'}}));
        renderMilestone(host,{budgetEntries:[...entries,entries[0]]},{locale,document:doc,expandable:true});
        const button=walk(host).find(n=>n.tag==='button'),grid=walk(host).find(n=>n.id==='budget-proposals');
        assert.ok(button);assert.equal(grid.hidden,true);assert.equal(grid.children.length,8);
        assert.equal(button.attributes['aria-controls'],grid.id);assert.equal(button.attributes['aria-expanded'],'false');
        button.onclick();assert.equal(grid.hidden,false);assert.equal(button.attributes['aria-expanded'],'true');
        assert.ok(walk(grid).some(n=>n.href==='/government/commitments/entry-7'));
        button.onclick();assert.equal(grid.hidden,true);
        renderMilestone(host,{budgetEntries:entries},{locale,document:doc});
        assert.ok(walk(host).some(n=>n.href==='/government?tab=delivery&origin=GOVERNMENT#budget-proposals'));
        assert.ok(!walk(host).some(n=>n.tag==='button'));
        renderMilestone(host,{budgetEntries:[]},{locale,document:doc});assert.equal(host.hidden,true);assert.equal(host.children.length,0);
    }
});
test('proposal sources cannot create executable links',()=>{
    const doc=document(),proposal=row.budgetProposals[0];
    renderCommitment({...row,budgetProposals:[{...proposal,source:{...proposal.source,url:'javascript:alert(1)'}}]},'en',doc);
    assert.ok(!walk(doc.getElementById('commitmentBudget')).some(n=>n.href?.startsWith('javascript:')));
});
