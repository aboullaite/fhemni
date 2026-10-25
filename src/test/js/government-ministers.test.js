const {test}=require('node:test'),assert=require('node:assert/strict');
let ministers={};try{ministers=require('../../main/resources/static/js/government-ministers.js');}catch(error){if(error.code!=='MODULE_NOT_FOUND')throw error;}
const member=(id,extra={})=>({id,name:{ar:'وزير '+id,fr:'Ministre '+id,en:'Minister '+id},role:{ar:'الصحة',fr:'Santé publique',en:'Public health'},partyCode:null,effectiveFrom:'2024-10-23',effectiveUntil:null,evidenceIds:[],...extra});
const parties=[{code:'PI',nameAr:'حزب الاستقلال',nameFr:'Parti de l’Istiqlal',symbolAsset:'/assets/parties/pi-display.png'}];
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
test('directory uses complete three-column pages and clamps bookmarks after filtering',()=>{
    assert.equal(typeof ministers.selectMembers,'function');
    const rows=Array.from({length:31},(_,i)=>member(String(i)));
    assert.equal(ministers.selectMembers(rows,{status:'current',page:1},'2026-10-23').items.length,9);
    const last=ministers.selectMembers(rows,{status:'current',page:99},'2026-10-23');
    assert.equal(last.page,4);assert.deepEqual(last.items.map(x=>x.id),['27','28','29','30']);
    const allPages=[1,2,3,4].flatMap(page=>ministers.selectMembers(rows,{status:'current',page},'2026-10-23').items.map(x=>x.id));
    assert.deepEqual(allPages,rows.map(x=>x.id),'pagination must neither skip nor duplicate ministers');
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
        ministers.renderMembers(host,[member('one'),member('old',{effectiveUntil:'2024-10-22',partyCode:'PI'})],{locale,asOf:'2026-10-23',document:doc,parties});
        assert.equal(walk(host).filter(n=>n.tag==='article').length,2);
        assert.match(text(host),/2024/);assert.match(text(host),new RegExp(locale==='ar'?'حزب الاستقلال':'Parti de l’Istiqlal'));
        assert.match(text(host),new RegExp({ar:'وزير one',fr:'Ministre one',en:'Minister one'}[locale]));
        assert.match(text(host),new RegExp(ministers.COPY[locale].partyUnknown));
        assert.doesNotMatch(text(host),/undefined|NaN/);
        const logos=walk(host).filter(n=>n.tag==='img');assert.equal(logos.length,1);assert.equal(logos[0].src,'/assets/parties/pi-display.png');assert.equal(logos[0].alt,'');
        const link=walk(host).find(n=>n.href==='/parties/PI');assert.ok(link);assert.match(text(link),new RegExp(locale==='ar'?'حزب الاستقلال':'Parti de l’Istiqlal'));
        assert.equal(link.attributes.dir,locale==='ar'?'rtl':'ltr');
    }
});
test('only confirmed independent members are labelled unaffiliated, with no party logo or link',()=>{
    for(const [locale,label,unknown]of [['ar','لا منتمي','الانتماء الحزبي غير موثق'],['fr','Sans affiliation partisane','Affiliation politique non documentée'],['en','Unaffiliated','Party affiliation not documented']]){
        const doc=document(),host=doc.createElement('section');
        ministers.renderMembers(host,[member('independent',{partyCode:'INDEPENDENT'}),member('unknown'),member('uncatalogued',{partyCode:'NEW'})],{locale,document:doc,parties});
        const cards=host.children;
        assert.match(text(cards[0]),new RegExp(label));assert.doesNotMatch(text(cards[0]),/INDEPENDENT/);
        assert.match(text(cards[1]),new RegExp(unknown));assert.doesNotMatch(text(cards[1]),new RegExp(label));
        assert.match(text(cards[2]),/NEW/);assert.doesNotMatch(text(cards[2]),new RegExp(label));
        assert.ok(!walk(host).some(n=>n.tag==='img'));
        assert.ok(!walk(cards[0]).some(n=>n.tag==='a'));assert.ok(!walk(cards[1]).some(n=>n.tag==='a'));
    }
});
test('party names fall back to the available official language and unsafe logo paths are never loaded',()=>{
    const doc=document(),host=doc.createElement('section');
    for(const symbolAsset of ['https://example.org/logo.png','//example.org/logo.png','/assets/parties/../private.png','/assets/parties/%2e%2e/private.png','/assets/parties/pi-display.png?redirect=1','/assets/brand/logo.png']){
        ministers.renderMembers(host,[member('one',{partyCode:'PI'})],{locale:'ar',document:doc,parties:[{...parties[0],nameAr:null,symbolAsset}]});
        assert.match(text(host),/Parti de l’Istiqlal/);assert.ok(!walk(host).some(n=>n.tag==='img'));
    }
});
test('party decoration preserves the nine-card rendering limit',()=>{
    const doc=document(),host=doc.createElement('section');
    ministers.renderMembers(host,Array.from({length:15},(_,i)=>member(String(i),{partyCode:'PI'})),{document:doc,parties});
    assert.equal(host.children.length,9);assert.equal(walk(host).filter(n=>n.tag==='img').length,9);
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
test('Arabic Fhemni affiliation notes render as labelled prose rather than source quotations',async()=>{
    const passage='ملاحظة توثيقية من فهّمني: الانتماء الحزبي موثق فالمصدر المرتبط.';
    for(const locale of ['ar','fr','en']){
        const doc=document(),host=doc.createElement('section');
        ministers.renderMembers(host,[member('one',{evidenceIds:['affiliation']})],{locale,document:doc,request:async()=>({url:'https://example.org/affiliation',passage,originalLanguage:'ar'})});
        const details=walk(host).find(n=>n.tag==='details');details.open=true;await details.toggle();
        assert.ok(!walk(host).some(n=>n.tag==='blockquote'));
        const note=walk(host).find(n=>n.tag==='p'&&n.textContent===passage);assert.ok(note);assert.equal(note.attributes.lang,'ar');assert.equal(note.attributes.dir,'auto');
        assert.match(text(host),new RegExp({ar:'ماشي اقتباس',fr:'pas une citation',en:'not a source quotation'}[locale]));
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
test('directory loads the election party catalogue alongside membership',async()=>{
    const requested=[];
    const data=await ministers.loadDirectory(async path=>{requested.push(path);if(path==='/api/catalog/parties')return parties;if(path.endsWith('/preview'))throw {status:404};return {membership:[member('one',{partyCode:'PI'})]};});
    assert.deepEqual(data.parties,parties);assert.ok(requested.includes('/api/catalog/parties'));
});
test('an unavailable party catalogue keeps the roster readable with its recorded party code',async()=>{
    const data=await ministers.loadDirectory(async path=>{if(path==='/api/catalog/parties')throw new Error('catalogue offline');if(path.endsWith('/preview'))throw {status:404};return {membership:[member('one',{partyCode:'PI'})]};});
    assert.deepEqual(data.parties,[]);
    const doc=document(),host=doc.createElement('section');ministers.renderMembers(host,data.mandate.membership,{locale:'en',document:doc,parties:data.parties});
    assert.match(text(host),/Minister one/);assert.match(text(host),/PI/);assert.ok(!walk(host).some(n=>n.tag==='img'));
});
test('page initialization passes the loaded catalogue to cards and keeps it when the language changes',async()=>{
    const fs=require('node:fs'),vm=require('node:vm'),doc=document(),nodes={};let locale='ar';
    doc.getElementById=id=>nodes[id]||(nodes[id]=doc.createElement('div'));
    doc.addEventListener=(event,fn)=>{doc[event]=fn;};
    doc.getElementById('ministersStatusFilter').options=['current','former','all'].map(value=>({value}));
    const window={document:doc,location:{href:'http://localhost/government/ministers'},history:{replaceState(){},pushState(){}},addEventListener(){},FhemniI18n:{locale:()=>locale},
        FhemniUI:require('../../main/resources/static/js/public-ui.js'),FhemniGovernmentModel:require('../../main/resources/static/js/government-model.js'),FhemniGovernmentVisuals:require('../../main/resources/static/js/government-visuals.js'),
        FhemniGovernmentAPI:{request:async path=>{if(path==='/api/catalog/parties')return parties;if(path.endsWith('/preview'))throw {status:404};return {verifiedAt:'2026-10-23',membership:[member('one',{partyCode:'PI'})]};}}};
    vm.runInNewContext(fs.readFileSync(require.resolve('../../main/resources/static/js/government-ministers.js'),'utf8'),{window,URL,URLSearchParams});
    window.FhemniGovernmentMinisters.init();await new Promise(resolve=>setImmediate(resolve));
    assert.equal(nodes.ministersFilters.disabled,false);assert.match(text(nodes.ministersContent),/حزب الاستقلال/);
    assert.ok(walk(nodes.ministersContent).some(n=>n.src==='/assets/parties/pi-display.png'));
    locale='en';doc['fhemni:localechange']();assert.match(text(nodes.ministersContent),/Parti de l’Istiqlal/);
});
