const {test}=require('node:test');
const assert=require('node:assert/strict');
const path=require('node:path');
const modulePath=path.resolve(__dirname,'../../main/resources/static/js/government-admin.js');
test('editor produces typed private drafts and never silently publishes',()=>{
    const {prepareDraft}=require(modulePath);
    const payload={id:'stable-id',slug:'health',title:{ar:'أ',fr:'B',en:'C'}};
    const draft=prepareDraft('COMMITMENT','health',payload,'Source correction');
    assert.equal(draft.type,'COMMITMENT');assert.equal(draft.payload.id,'stable-id');
    assert.equal(draft.payload.origin,'GOVERNMENT');assert.equal(draft.reason,'Source correction');
    assert.ok(!('status' in draft));assert.deepEqual(draft.payload.revisions,[]);
    assert.throws(()=>prepareDraft('MANDATE','2026-2031',{},' '),/reason/i);
});
test('measure drafts use the practical schema without campaign or delivery fields',()=>{
    const {prepareDraft}=require(modulePath);const draft=prepareDraft('MEASURE','measure',{id:'id',operationalStatus:'PENDING'},'Source review');
    assert.equal(draft.payload.slug,'measure');assert.ok(!('origin' in draft.payload));assert.ok(!('revisions' in draft.payload));
});
test('all three locales describe separate save review and publish actions',()=>{
    const {COPY}=require(modulePath);
    for(const lang of ['ar','fr','en']) {
        for(const key of ['save','review','publish','stale','private','source','reason'])assert.ok(COPY[lang][key]?.length);
        assert.notEqual(COPY[lang].review,COPY[lang].publish);
    }
});
test('editor pagination uses the shared callback and locale changes update the mounted desk',async()=>{
    const fs=require('node:fs'),vm=require('node:vm');
    const nodes=new Map();
    function element(tag='div'){
        const node={tag,children:[],value:'',listeners:{},hidden:false,checked:false,textContent:'',append(...children){this.children.push(...children)},replaceChildren(...children){this.children=children},setAttribute(){},addEventListener(name,fn){this.listeners[name]=fn},focus(){},remove(){},querySelector(){return null}};
        Object.defineProperty(node,'options',{get:()=>node.children});Object.defineProperty(node,'selectedOptions',{get:()=>node.children.filter(child=>child.selected)});return node;
    }
    const doc={documentElement:{lang:'en'},listeners:{},createElement:element,addEventListener(name,fn){this.listeners[name]=fn},getElementById(id){if(!nodes.has(id))nodes.set(id,element());return nodes.get(id)}};
    doc.getElementById('subjectType').value='MANDATE';
    const rows=Array.from({length:12},(_,index)=>({id:String(index),slug:'revision-'+index,revisionNumber:1,status:'DRAFT',payload:'{}',reason:'Test revision'}));
    const ui=require('../../main/resources/static/js/public-ui.js');
    const window={document:doc,crypto:require('node:crypto').webcrypto,addEventListener(){},FhemniAuth:{withCsrf:async value=>value},FhemniUI:{...ui,createPager:options=>ui.createPager({...options,document:doc})},fetch:async url=>({ok:true,json:async()=>url.endsWith('/evidence')?[]:rows})};
    vm.runInNewContext(fs.readFileSync(modulePath,'utf8'),{window,document:doc,console,Date});window.FhemniGovernmentAdmin.init();
    await new Promise(resolve=>setImmediate(resolve));
    const walk=node=>[node,...node.children.flatMap(walk)];
    assert.ok(walk(doc.getElementById('evidenceFields')).filter(node=>node.type==='datetime-local').every(node=>node.step==='1'));
    const optional=walk(doc.getElementById('draftFields')).filter(node=>node.children[0]?.tag==='label'&&node.children[0].children.some(child=>child.type==='checkbox')&&node.children.length===2);
    assert.ok(optional.length>=2);assert.ok(optional.every(node=>node.children[1].tag==='fieldset'&&node.children[1].disabled));
    assert.equal(doc.getElementById('revisionList').children.length,10);
    const next=doc.getElementById('revisionPager').children[0].children[2];next.listeners.click();
    assert.equal(doc.getElementById('revisionList').children.length,2);
    const unfinished=walk(doc.getElementById('draftFields')).find(node=>node.tag==='textarea');unfinished.value='Unfinished review';
    doc.getElementById('subjectType').value='COMMITMENT';doc.getElementById('subjectType').onchange();
    doc.getElementById('subjectType').value='MANDATE';doc.getElementById('subjectType').onchange();
    assert.ok(walk(doc.getElementById('draftFields')).some(node=>node.value==='Unfinished review'));
    assert.equal(typeof doc.listeners['fhemni:localechange'],'function');
    doc.documentElement.lang='fr';doc.listeners['fhemni:localechange']();
    assert.equal(doc.getElementById('deskTitle').textContent,'Révision des données gouvernementales');
});
test('editor preview retains original text and uses human labels instead of only JSON',()=>{
    const {previewLines}=require(modulePath);
    const rows=previewLines({title:{ar:'العنوان',fr:'Titre',en:'Title'},originalText:'<script>source</script>',evidence:[{url:'https://example.org',passage:'Original passage'}]},'en');
    assert.ok(rows.some(row=>row.label.includes('Original text')&&row.text==='<script>source</script>'));
    assert.ok(rows.some(row=>row.text==='Original passage'));
});
