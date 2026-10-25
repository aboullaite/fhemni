const {test}=require('node:test'),assert=require('node:assert/strict');
let cards={};try{cards=require('../../main/resources/static/js/government-cards.js');}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}
const make=tag=>({tag,children:[],attributes:{},style:{},textContent:'',append(...xs){this.children.push(...xs)},replaceChildren(...xs){this.children=xs;this.textContent=''},setAttribute(k,v){this.attributes[k]=String(v)}});
const walk=n=>[n,...n.children.flatMap(walk)],text=n=>walk(n).map(x=>x.textContent).join(' ');
test('programme picks are stable for a seed, unique and drawn beyond the first page without changing input',()=>{
    assert.equal(typeof cards.selectProgramme,'function');
    const rows=Array.from({length:40},(_,i)=>({slug:'entry-'+i,title:{en:'Entry '+i},topicCodes:['TOPIC-'+i%4]})),before=JSON.stringify(rows);
    const a=cards.selectProgramme([...rows,rows[0]],{seed:42});
    assert.equal(a.length,3);assert.equal(new Set(a.map(r=>r.slug)).size,3);
    assert.deepEqual(a,cards.selectProgramme(rows,{seed:42}));assert.notDeepEqual(a,cards.selectProgramme(rows,{seed:2026}));
    assert.ok(a.some(r=>Number(r.slug.split('-')[1])>=10));assert.equal(JSON.stringify(rows),before);
});
test('programme discovery hydrates only its picks and preserves source context if a detail fails',async()=>{
    assert.equal(typeof cards.loadProgramme,'function');
    const rows=Array.from({length:20},(_,i)=>({slug:'p'+i,title:{en:'Item '+i},programmeContext:{sourceStatus:'MIRROR_UNAUTHENTICATED'}})),requests=[];
    const api={fetchInsights:async state=>{assert.deepEqual(state,{origin:'GOVERNMENT'});return {entries:rows}},fetchCommitment:async slug=>{requests.push(slug);if(requests.length===1)throw Error('offline');return {...rows.find(r=>r.slug===slug),originalText:'Source passage'}}};
    const selected=await cards.loadProgramme(api,{seed:42});assert.equal(requests.length,3);assert.equal(selected.length,3);
    assert.equal(selected.filter(r=>r.originalText).length,2);assert.ok(selected.every(r=>r.programmeContext.sourceStatus==='MIRROR_UNAUTHENTICATED'));
});
test('recent publications remain chronological and keep their own title/date when enriched',async()=>{
    assert.equal(typeof cards.loadUpdates,'function');
    const rows=[{subjectType:'COMMITMENT',subjectId:'old',title:{en:'Old publication'},publishedAt:'2026-10-20T00:00:00Z'},{subjectType:'COMMITMENT',subjectId:'new',title:{en:'New publication'},publishedAt:'2026-10-23T00:00:00Z'}];
    const result=await cards.loadUpdates({fetchCommitment:async slug=>({slug,title:{en:'Current title'},originalText:'Context'})},rows);
    assert.equal(result[0].title.en,'New publication');assert.equal(result[0].entry.originalText,'Context');assert.equal(rows[0].subjectId,'old');
});
test('shared cards show source targets as targets, never measured delivery, and preserve safe links and Arabic language',()=>{
    assert.equal(typeof cards.renderCards,'function');
    for(const locale of ['ar','fr','en']){
        const host=make('div');cards.renderCards(host,[{slug:'capacity',title:{ar:'طاقة الميناء'},originalText:'90 مليون طن',originalLanguage:'ar',programmeContext:{sourceStatus:'MIRROR_UNAUTHENTICATED',deadlineText:'2027–2031',targetText:'value: 90 · unit: people · operator: equal · baseline: {"value":50,"unit":"people","source":"programme-reported"}'}}],{locale,document:{createElement:make}});
        assert.equal(host.children.length,1);assert.match(text(host),/2027–2031/);assert.match(text(host),/90/);assert.match(text(host),/50/);
        assert.ok(walk(host).some(n=>n.href==='/government/commitments/capacity'&&n.attributes.lang==='ar'));
        assert.ok(!walk(host).some(n=>n.tag==='progress'||n.attributes.role==='progressbar'));
        assert.ok(!walk(host).some(n=>n.className==='programme-feature-note'));
        assert.doesNotMatch(text(host),/value:|baseline:|undefined|NaN/);
    }
});
