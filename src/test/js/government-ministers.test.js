const {test}=require('node:test'),assert=require('node:assert/strict');
let ministers={};try{ministers=require('../../main/resources/static/js/government-ministers.js');}catch(error){if(error.code!=='MODULE_NOT_FOUND')throw error;}
const member=(id,extra={})=>({id,name:{ar:'وزير '+id,fr:'Ministre '+id,en:'Minister '+id},role:{ar:'الصحة',fr:'Santé publique',en:'Public health'},partyCode:null,effectiveFrom:'2024-10-23',effectiveUntil:null,evidenceIds:[],...extra});
function document(){function node(tag){return {tag,children:[],attributes:{},textContent:'',append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children;this.textContent=''},setAttribute(k,v){this.attributes[k]=v},addEventListener(event,fn){this[event]=fn},focus(){this.focused=true}};}return {createElement:node};}
const walk=node=>[node,...node.children.flatMap(walk)],text=node=>walk(node).map(n=>n.textContent).join(' ');
test('search matches every translated name and role, ignoring case and accents',()=>{
    assert.equal(typeof ministers.selectMembers,'function');
    const rows=[member('one',{name:{ar:'نادية فتاح',fr:'Nadia Fettah',en:'Nadia Fettah'}}),member('two',{role:{fr:'Éducation',en:'Education'}})];
    assert.deepEqual(ministers.selectMembers(rows,{q:'  NADIA ',status:'current'},'2026-10-23').items.map(x=>x.id),['one']);
    assert.deepEqual(ministers.selectMembers(rows,{q:'education',status:'current'},'2026-10-23').items.map(x=>x.id),['two']);
    assert.deepEqual(ministers.selectMembers(rows,{q:'نادية',status:'current'},'2026-10-23').items.map(x=>x.id),['one']);
});
test('current and former filters use membership dates as of the snapshot and exclude future appointments',()=>{
    assert.equal(typeof ministers.selectMembers,'function');
    const rows=[member('current'),member('former',{effectiveUntil:'2024-10-22'}),member('future',{effectiveFrom:'2027-01-01'}),member('ending',{effectiveUntil:'2026-10-23'})];
    assert.deepEqual(ministers.selectMembers(rows,{status:'current'},'2026-10-23').items.map(x=>x.id),['current','ending']);
    assert.deepEqual(ministers.selectMembers(rows,{status:'former'},'2026-10-23').items.map(x=>x.id),['former']);
    assert.equal(ministers.selectMembers(rows,{status:'all'},'2026-10-23').total,4);
});
test('directory limits pages to ten and clamps bookmarks after filtering',()=>{
    assert.equal(typeof ministers.selectMembers,'function');
    const rows=Array.from({length:31},(_,i)=>member(String(i)));
    assert.equal(ministers.selectMembers(rows,{status:'current',page:1},'2026-10-23').items.length,10);
    const last=ministers.selectMembers(rows,{status:'current',page:99},'2026-10-23');
    assert.equal(last.page,4);assert.deepEqual(last.items.map(x=>x.id),['30']);
    assert.equal(ministers.selectMembers(rows,{q:'missing',page:99},'2026-10-23').page,1);
});
test('directory URLs preserve encoded searches and validate filters and pages',()=>{
    assert.equal(typeof ministers.readState,'function');
    assert.deepEqual(ministers.readState('/government/ministers?status=former&q=Nadia&page=2'),{q:'Nadia',status:'former',page:2});
    assert.deepEqual(ministers.readState('/government/ministers?status=bad&page=-1'),{q:'',status:'current',page:1});
    const url=ministers.stateUrl({q:'الصحة & Santé',status:'all',page:3});
    assert.deepEqual(ministers.readState(url),{q:'الصحة & Santé',status:'all',page:3});
});
test('cards render readable membership periods and unknown affiliations in every language',()=>{
    assert.equal(typeof ministers.renderMembers,'function');
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        ministers.renderMembers(host,[member('one'),member('old',{effectiveUntil:'2024-10-22',partyCode:'PI'})],{locale,asOf:'2026-10-23',document:doc});
        assert.equal(walk(host).filter(n=>n.tag==='article').length,2);
        assert.match(text(host),/2024/);assert.match(text(host),/PI/);
        assert.match(text(host),new RegExp({ar:'وزير one',fr:'Ministre one',en:'Minister one'}[locale]));
        assert.match(text(host),new RegExp(ministers.COPY[locale].partyUnknown));
        assert.doesNotMatch(text(host),/undefined|NaN/);assert.ok(!walk(host).some(n=>n.tag==='img'));
    }
});
test('evidence loads only on expansion, deduplicates ids and renders safe links and literal quotations',async()=>{
    assert.equal(typeof ministers.renderMembers,'function');
    const doc=document(),host=doc.createElement('section'),requests=[];
    ministers.renderMembers(host,[member('one',{evidenceIds:['source one','source one']})],{locale:'en',asOf:'2026-10-23',document:doc,request:async path=>{requests.push(path);return {id:'source one',url:'javascript:alert(1)',passage:'Literal <script>quote</script>',originalLanguage:'fr',locator:'Appointment decree',publishedAt:'2024-10-23',retrievedAt:'2026-10-23'};}});
    assert.equal(requests.length,0);const details=walk(host).find(n=>n.tag==='details');details.open=true;await details.toggle();
    assert.deepEqual(requests,['/api/catalog/government/2026-2031/sources/source%20one']);
    assert.match(text(host),/Literal <script>quote<\/script>/);assert.match(text(host),/Appointment decree/);
    assert.ok(!walk(host).some(n=>String(n.href).startsWith('javascript:')));
    assert.ok(walk(host).some(n=>n.tag==='blockquote'&&n.attributes.lang==='fr'&&n.attributes.dir==='auto'));
    await details.toggle();assert.equal(requests.length,1);
});
test('failed evidence can be retried without losing the minister card',async()=>{
    assert.equal(typeof ministers.renderMembers,'function');
    const doc=document(),host=doc.createElement('section');let attempts=0;
    ministers.renderMembers(host,[member('one',{evidenceIds:['source']})],{locale:'en',asOf:'2026-10-23',document:doc,request:async()=>{if(++attempts===1)throw new Error('offline');return {url:'https://example.org/decree',passage:'Appointment'};}});
    const details=walk(host).find(n=>n.tag==='details');details.open=true;await details.toggle();
    assert.match(text(host),/Minister one/);assert.match(text(host),/could not be loaded/);
    await walk(host).find(n=>n.tag==='button').onclick();assert.ok(walk(host).some(n=>n.href==='https://example.org/decree'));
});
test('research summaries are labelled prose in every locale while genuine source passages remain quotations',async()=>{
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        ministers.renderMembers(host,[member('one',{evidenceIds:['summary','quotation']})],{locale,document:doc,request:async path=>({url:'https://example.org/cabinet',passage:path.endsWith('/summary')?'Research summary — Publisher: Appointment recorded.':'Literal wording from the decree.',originalLanguage:path.endsWith('/summary')?'en':'fr'})});
        const details=walk(host).find(n=>n.tag==='details');details.open=true;await details.toggle();
        const notes=walk(host).filter(n=>n.tag==='p'&&n.textContent==='Research summary — Publisher: Appointment recorded.');
        assert.equal(notes.length,1);assert.equal(notes[0].attributes.lang,'en');assert.equal(notes[0].attributes.dir,'auto');
        assert.match(text(host),new RegExp({ar:'ماشي اقتباس',fr:'pas une citation',en:'not a source quotation'}[locale]));
        const quotes=walk(host).filter(n=>n.tag==='blockquote');assert.equal(quotes.length,1);assert.equal(quotes[0].textContent,'Literal wording from the decree.');
    }
});
test('optional preview 404 never hides a valid public directory and local preview is disclosed',async()=>{
    assert.equal(typeof ministers.loadDirectory,'function');
    const data=await ministers.loadDirectory(async path=>{if(path.endsWith('/preview'))throw {status:404};return {membership:[member('one')]};});
    assert.equal(data.mandate.membership.length,1);assert.equal(data.preview,null);
    const preview=await ministers.loadDirectory(async path=>path.endsWith('/preview')?{localPreview:true,snapshotDate:'2026-10-23'}:{membership:[]});
    assert.equal(preview.preview.localPreview,true);
    await assert.rejects(ministers.loadDirectory(async path=>{if(!path.endsWith('/preview'))throw new Error('offline');return null;}),/offline/);
});
test('preview metadata failure is reported instead of silently presenting a preview as published membership',async()=>{
    await assert.rejects(ministers.loadDirectory(async path=>{if(path.endsWith('/preview'))throw new Error('preview offline');return {membership:[member('one')]};}),/preview offline/);
});
