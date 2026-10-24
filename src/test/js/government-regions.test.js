const {test}=require('node:test'),assert=require('node:assert/strict');
test('regional programme links are distinct from measured delivery, bounded and filterable in every locale',()=>{
    const {renderProgramme}=require('../../main/resources/static/js/government-regions.js');
    const node=tag=>({tag,children:[],attributes:{},style:{},append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children},setAttribute(key,value){this.attributes[key]=value}});
    const walk=n=>[n,...n.children.flatMap(walk)],text=n=>walk(n).map(n=>n.textContent||'').join(' ');
    for(const locale of ['ar','fr','en']){const host=node('section'),changes=[];
        renderProgramme(host,{linkedEntries:12,programmeEntries:595,regionCounts:[{region:{code:'oriental',label:{ar:'الشرق',fr:'Oriental',en:'Oriental'}},count:12}],items:Array.from({length:12},(_,i)=>({commitment:{slug:'entry-'+i,title:{ar:'السكك',fr:'السكك',en:'السكك'},programmeContext:{sourceStatus:'MIRROR_UNAUTHENTICATED'}},places:[{regionCode:'oriental',place:{ar:'وجدة',fr:'Oujda',en:'Oujda'},printedPage:109,sourceExcerpt:'نص المصدر'}]}))},{locale,document:{createElement:node},state:{tab:'regions',page:3},onFilter:s=>changes.push(s)});
        const grid=host.children.find(n=>n.className==='government-card-grid');assert.ok(grid);assert.equal(grid.children.length,10);
        assert.equal(walk(host).filter(n=>n.tag==='article').length,10);assert.ok(!text(host).includes('undefined'));assert.ok(!text(host).includes('0%'));
        assert.ok(walk(host).some(n=>n.href==='/government/commitments/entry-0'));
        const filter=walk(host).find(n=>n.id==='trackerRegionBar-oriental');filter.onclick();assert.equal(changes[0].page,1);assert.equal(changes[0].region,'oriental');
        assert.ok(text(host).includes(require('../../main/resources/static/js/government-model.js').COPY[locale].sourceUnverified));
    }
});
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
