(function(root,factory){const api=factory(root);if(typeof module==='object'&&module.exports)module.exports=api;else{root.FhemniGovernmentAdmin=api;document.addEventListener('DOMContentLoaded',api.init);}})(typeof window==='object'?window:globalThis,function(root){
    const COPY={
        ar:{title:'مراجعة المعطيات الحكومية',private:'المسودات خاصة. راجع النصوص والترجمات والمصادر قبل النشر.',source:'المصدر والنص الأصلي',saveSource:'حفظ المصدر',save:'حفظ مسودة خاصة',review:'تأكيد المراجعة',publish:'نشر المراجعة',stale:'تعذّر الحفظ. إلا تبدّلت المراجعة، حدّث اللائحة وحمّل النسخة الجديدة؛ النص ديالك باقي محفوظ هنا.',reason:'سبب المراجعة',new:'مسودة جديدة',queue:'المراجعات',refresh:'تحديث',edit:'نسخة للتصحيح',add:'إضافة',remove:'حذف',include:'إدراج',saved:'تحفظات المسودة الخاصة.',published:'تنشرات المراجعة.',reviewed:'تأكدات المراجعة. النشر خطوة منفصلة.',confirm:'تأكدتي من النصوص والمصادر والترجمات الثلاثة؟ النشر غادي يبان للعموم.',cancel:'رجوع',type:'نوع السجل',slug:'الرابط المختصر',MANDATE:'الحكومة',COMMITMENT:'التزام',preview:'معاينة المسودة',DRAFT:'مسودة',REVIEWED:'مراجَعة',PUBLISHED:'منشورة',SUPERSEDED:'نسخة سابقة'},
        fr:{title:'Révision des données gouvernementales',private:'Les brouillons sont privés. Vérifiez les textes, traductions et sources avant publication.',source:'Source et passage original',saveSource:'Enregistrer la source',save:'Enregistrer le brouillon privé',review:'Valider la révision',publish:'Publier la révision',stale:'Échec. Si la révision a changé, actualisez et rechargez-la ; votre saisie reste ici.',reason:'Motif de la révision',new:'Nouveau brouillon',queue:'Révisions',refresh:'Actualiser',edit:'Copier pour corriger',add:'Ajouter',remove:'Retirer',include:'Inclure',saved:'Brouillon privé enregistré.',published:'Révision publiée.',reviewed:'Révision validée. La publication est une étape distincte.',confirm:'Textes, sources et trois traductions vérifiés ? La publication sera publique.',cancel:'Retour',type:'Type de fiche',slug:'Adresse courte',MANDATE:'Mandat',COMMITMENT:'Engagement',preview:'Aperçu du brouillon',DRAFT:'Brouillon',REVIEWED:'Révisé',PUBLISHED:'Publié',SUPERSEDED:'Version précédente'},
        en:{title:'Government editorial desk',private:'Drafts are private. Check wording, translations and sources before publishing.',source:'Source and original passage',saveSource:'Save source',save:'Save private draft',review:'Confirm review',publish:'Publish revision',stale:'Could not save. If the revision changed, refresh and load it again; your input is retained here.',reason:'Revision reason',new:'New draft',queue:'Revisions',refresh:'Refresh',edit:'Copy to correct',add:'Add',remove:'Remove',include:'Include',saved:'Private draft saved.',published:'Revision published.',reviewed:'Review confirmed. Publication is a separate step.',confirm:'Have you checked the wording, sources and all three translations? Publishing makes this public.',cancel:'Back',type:'Record type',slug:'Short address',MANDATE:'Mandate',COMMITMENT:'Commitment',preview:'Draft preview',DRAFT:'Draft',REVIEWED:'Reviewed',PUBLISHED:'Published',SUPERSEDED:'Previous version'}
    };
    const labels={label:['التسمية','Libellé','Label'],title:['العنوان','Titre','Title'],name:['الاسم','Nom','Name'],role:['المهمة','Rôle','Role'],partyCode:['رمز الحزب','Code du parti','Party code'],effectiveFrom:['بداية المهمة','Début du mandat','Effective from'],effectiveUntil:['نهاية المهمة','Fin du mandat','Effective until'],evidenceIds:['مراجع الأدلة (UUID بفواصل)','Références des preuves (UUID séparés par virgules)','Evidence references (comma-separated UUIDs)'],membership:['أعضاء الحكومة','Composition','Membership'],programme:['البرنامج','Programme','Programme'],version:['النسخة','Version','Version'],publishedAt:['تاريخ النشر','Date de publication','Published on'],verifiedAt:['آخر تحقق','Dernière vérification','Verified at'],reviewCoverage:['تغطية المراجعة','Couverture de révision','Review coverage'],reviewed:['عدد الالتزامات المراجعة','Engagements révisés','Reviewed commitments'],total:['العدد الإجمالي المعروف','Total connu','Known total'],complete:['المراجعة كاملة','Révision complète','Review complete'],lifecycle:['مرحلة التشكيل','Étape de formation','Formation stage'],originalText:['النص الأصلي','Texte original','Original text'],originalLanguage:['لغة المصدر','Langue source','Source language'],author:['صاحب الالتزام','Auteur','Author'],topicCodes:['رموز المواضيع بفواصل','Codes des thèmes séparés par virgules','Comma-separated topic codes'],geographicScope:['النطاق الجغرافي','Périmètre géographique','Geographic scope'],responsibleBody:['الجهة المسؤولة','Organisme responsable','Responsible body'],target:['الهدف','Objectif','Target'],deadline:['الأجل الموثق','Échéance documentée','Sourced deadline'],metric:['المؤشر','Indicateur','Metric'],unit:['الوحدة','Unité','Unit'],baseline:['القيمة المرجعية','Valeur de référence','Baseline'],baselineDate:['تاريخ المرجع','Date de référence','Baseline date'],targetValue:['القيمة المستهدفة','Valeur cible','Target value'],targetDate:['تاريخ الهدف','Date cible','Target date'],methodologyKey:['مرجع المنهجية','Référence méthodologique','Methodology key'],deliveryAssessment:['تقييم الإنجاز','Évaluation de réalisation','Delivery assessment'],state:['الحالة','État','State'],evidenceConfidence:['قوة الأدلة','Confiance des preuves','Evidence confidence'],explanation:['التفسير','Explication','Explanation'],reviewedAt:['تاريخ المراجعة','Date de révision','Reviewed at'],links:['روابط وعود الأحزاب','Liens aux promesses des partis','Campaign promise links'],campaignPromiseId:['مرجع وعد الحزب','Référence de la promesse','Campaign promise ID'],governmentCommitmentId:['مرجع الالتزام الحكومي','Référence de l’engagement','Government commitment ID'],relation:['العلاقة','Relation','Relation'],programmeVersionId:['مرجع نسخة البرنامج','Référence de la version','Programme version ID'],evidence:['المصادر المحفوظة','Sources enregistrées','Saved evidence'],observations:['القياسات المرصودة','Observations','Observations'],value:['القيمة','Valeur','Value'],periodStart:['بداية الفترة','Début de période','Period start'],periodEnd:['نهاية الفترة','Fin de période','Period end'],scope:['النطاق','Périmètre','Scope'],level:['المستوى','Niveau','Level'],code:['الرمز','Code','Code'],url:['رابط المصدر','Lien source','Source URL'],passage:['النص الداعم','Passage justificatif','Supporting passage'],locator:['الصفحة أو الموضع','Page ou emplacement','Page or locator'],retrievedAt:['تاريخ الاطلاع','Date de consultation','Retrieved at'],id:['المعرف الثابت','Identifiant stable','Stable identifier']};
    const translated={ar:'العربية',fr:'Français',en:'English'};
    Object.assign(labels,{
        FORMATION:['تشكيل الحكومة','Formation du gouvernement','Government formation'],FORMED_PROGRAMME_PENDING:['حكومة مشكلة، البرنامج منتظر','Gouvernement formé, programme attendu','Government formed, programme pending'],PROGRAMME_PUBLISHED:['البرنامج منشور','Programme publié','Programme published'],
        NATIONAL:['وطني','National','National'],REGION:['جهة','Région','Region'],PROVINCE:['عمالة أو إقليم','Préfecture ou province','Prefecture or province'],COMMUNE:['جماعة','Commune','Commune'],
        NO_EVIDENCE_FOUND:['ما تلقاش دليل بعد البحث','Aucune preuve trouvée après recherche','No evidence found after review'],UNDERWAY:['في طور الإنجاز','En cours','Underway'],PARTLY_DELIVERED:['منجز جزئياً','Partiellement réalisé','Partly delivered'],DELIVERED:['منجز','Réalisé','Delivered'],WITHDRAWN:['مسحوب','Retiré','Withdrawn'],
        GOVERNMENT_REPORTED:['تصريح حكومي','Déclaration gouvernementale','Government reported'],CORROBORATED:['مؤيد بأدلة مستقلة','Corroboré','Corroborated'],CONTESTED:['الأدلة متعارضة','Preuves contestées','Contested evidence'],
        ADOPTED:['متبنى','Adopté','Adopted'],MODIFIED:['معدل','Modifié','Modified'],NOT_IDENTIFIED:['غير محدد بعد مراجعة كاملة','Non identifié après révision complète','Not identified after complete review'],UNRESOLVED:['غير محسوم','Non résolu','Unresolved'],
        loading:['كنحملو المراجعات…','Chargement des révisions…','Loading revisions…'],empty:['ما كاينة حتى مراجعة بعد.','Aucune révision pour le moment.','No revisions yet.'],replace:['تعويض النص غير المحفوظ؟','Remplacer la saisie non enregistrée ?','Replace unsaved input?'],savedRevision:['المراجعة المحفوظة','Révision enregistrée','Saved revision'],yes:['نعم','Oui','Yes'],no:['لا','Non','No']
    });
    const local={ar:'area',fr:'area',en:'area'};
    const scope={level:['NATIONAL','REGION','PROVINCE','COMMUNE'],code:'text',label:local};
    const source={id:'uuid',url:'url',passage:'area',locator:'text?',originalLanguage:['ar','fr','en'],publishedAt:'time?',retrievedAt:'time'};
    const mandate={label:local,lifecycle:['FORMATION','FORMED_PROGRAMME_PENDING','PROGRAMME_PUBLISHED'],verifiedAt:'time',membership:[{id:'uuid',name:local,role:local,partyCode:'text?',effectiveFrom:'date',effectiveUntil:'date?',evidenceIds:'refs'}],programme:{optional:true,fields:{id:'uuid',version:'number',title:local,publishedAt:'date',evidenceIds:'refs'}},reviewCoverage:{optional:true,fields:{reviewed:'number',total:'number?',complete:'boolean'}}};
    const commitment={id:'uuid',title:local,originalText:'area',originalLanguage:['ar','fr','en'],author:local,topicCodes:'refs',geographicScope:scope,responsibleBody:{optional:true,fields:local},target:{optional:true,fields:{metric:'text',unit:'text',baseline:'number?',baselineDate:'date?',targetValue:'number',targetDate:'date?',methodologyKey:'text'}},deadline:'date?',deliveryAssessment:{optional:true,fields:{state:['NO_EVIDENCE_FOUND','UNDERWAY','PARTLY_DELIVERED','DELIVERED','WITHDRAWN'],evidenceConfidence:['GOVERNMENT_REPORTED','CORROBORATED','CONTESTED'],explanation:local,evidenceIds:'refs',reviewedAt:'time'}},links:[{campaignPromiseId:'text',governmentCommitmentId:'text?',relation:['ADOPTED','MODIFIED','NOT_IDENTIFIED','UNRESOLVED'],programmeVersionId:'text',explanation:local,evidenceIds:'refs'}],evidence:'evidence',observations:[{id:'uuid',metric:'text',unit:'text',value:'number',periodStart:'date',periodEnd:'date',scope,methodologyKey:'text',evidenceIds:'refs',reviewedAt:'time'}]};
    function prepareDraft(type,slug,payload,reason){if(!reason?.trim())throw Error('A revision reason is required.');return {type,slug,reason:reason.trim(),payload:type==='MANDATE'?{...payload,id:'2026-2031',recentUpdates:[]}:{...payload,slug,origin:'GOVERNMENT',campaignPromiseId:null,revisions:[]}};}
    function previewLines(value,locale,path=''){
        const rows=[],index=['ar','fr','en'].indexOf(locale);
        for(const [key,item] of Object.entries(value||{})){
            if(item===null||['archivedSha256','revisions','recentUpdates'].includes(key))continue;
            const name=labels[key]?.[index]||translated[key]||key,label=path?path+' / '+name:name;
            if(Array.isArray(item))item.forEach((entry,n)=>{if(typeof entry==='object')rows.push(...previewLines(entry,locale,label+' '+(n+1)));else rows.push({label,text:String(entry)});});
            else if(typeof item==='object')rows.push(...previewLines(item,locale,label));
            else rows.push({label,text:labels[String(item)]?.[index]||(typeof item==='boolean'?labels[item?'yes':'no'][index]:String(item))});
        }
        return rows;
    }
    function init(){
        const doc=root.document;if(!doc.getElementById('governmentDesk'))return;
        let locale=['ar','fr','en'].includes(doc.documentElement.lang)?doc.documentElement.lang:'ar',sources=[],revisions=[],page=1,editor,sourceEditor;
        let activeType='MANDATE',dirty=false,queueState='loading';const cachedDrafts=new Map(),sourcePickers=[];
        const t=key=>COPY[locale][key]||labels[key]?.[['ar','fr','en'].indexOf(locale)]||translated[key]||key;
        const el=(tag,text)=>{const node=doc.createElement(tag);if(text!==undefined)node.textContent=text;return node;};
        const status=text=>root.FhemniUI.announceStatus(doc.getElementById('deskStatus'),text);
        async function api(path,body){const options=body===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)};const res=await root.fetch('/api/admin/government/2026-2031'+path,await root.FhemniAuth.withCsrf(options));if(!res.ok){const data=await res.json().catch(()=>({}));throw Error(data.detail||t('stale'));}return res.json();}
        function field(key,schema,value){
            const group=el('div');group.className='government-editor-field';
            if(schema?.optional){const label=el('label'),check=el('input');check.type='checkbox';check.checked=value!=null;label.append(check,el('span',t('include')+' · '+t(key)));const nested=object(schema.fields,value||{}),box=el('fieldset');box.append(el('legend',t(key)),nested.node);box.hidden=box.disabled=!check.checked;check.onchange=()=>{box.hidden=box.disabled=!check.checked;};group.append(label,box);return {node:group,get:()=>check.checked?nested.get():null};}
            if(Array.isArray(schema)&&typeof schema[0]==='object'){
                const set=el('fieldset'),legend=el('legend',t(key)),list=el('div'),add=el('button',t('add'));add.type='button';const items=[];
                const append=value=>{const item=object(schema[0],value),remove=el('button',t('remove'));remove.type='button';remove.onclick=()=>{items.splice(items.indexOf(item),1);item.node.remove();add.focus();};item.node.append(remove);items.push(item);list.append(item.node);};(value||[]).forEach(append);add.onclick=()=>{append({});items.at(-1).node.querySelector('input,textarea,select')?.focus();};set.append(legend,list,add);group.append(set);return {node:group,get:()=>items.map(item=>item.get())};
            }
            if(typeof schema==='object'&&!Array.isArray(schema)){const set=el('fieldset');set.append(el('legend',t(key)));const fields=object(schema,value||{});set.append(fields.node);group.append(set);return {node:group,get:fields.get};}
            const label=el('label');label.append(el('span',t(key)+(String(schema).startsWith('time')?' (UTC)':'')));let input;
            if(schema==='evidence'||key==='evidenceIds'){
                input=el('select');input.multiple=true;
                let choices=[];
                const update=(selected=value||[])=>{
                    choices=[...sources];for(const item of selected)if(!choices.some(row=>row.id===(item.id||item)))choices.push(typeof item==='object'?item:{id:item,url:item,passage:''});
                    input.replaceChildren();
                    for(const saved of choices){const option=el('option',saved.url+' · '+(saved.locator||'')+' · '+saved.passage.slice(0,100));option.value=saved.id;option.selected=selected.some(source=>(source.id||source)===saved.id);input.append(option);}
                };update();sourcePickers.push(()=>update([...input.selectedOptions].map(option=>choices.find(row=>row.id===option.value))));
                label.append(input);group.append(label);return {node:group,get:()=>[...input.selectedOptions].map(option=>key==='evidenceIds'?option.value:choices.find(source=>source.id===option.value))};
            }
            if(Array.isArray(schema)){input=el('select');schema.forEach(choice=>{const option=el('option',t(choice));option.value=choice;input.append(option);});input.value=value||schema[0];}
            else {const kind=schema.replace('?','');input=el(kind==='area'?'textarea':'input');if(kind!=='area')input.type=kind==='boolean'?'checkbox':kind==='date'?'date':kind==='time'?'datetime-local':kind==='number'?'number':kind==='url'?'url':'text';if(kind==='number')input.step='any';if(kind==='boolean')input.checked=!!value;else input.value=kind==='uuid'?(value||root.crypto.randomUUID()):kind==='refs'?(value||[]).join(', '):kind==='time'&&value?value.slice(0,19):(value??'');if(kind==='uuid')input.readOnly=true;if(['url','uuid','date','time'].includes(kind))input.dir='ltr';input.required=!schema.endsWith('?')&&!['boolean','refs'].includes(kind);}
            if(String(schema).startsWith('time'))input.step='1';
            if(['ar','fr','en'].includes(key)){input.lang=key;input.dir=key==='ar'?'rtl':'ltr';}
            label.append(input);group.append(label);
            return {node:group,get:()=>{if(schema==='boolean')return input.checked;const val=input.value.trim();if(String(schema).endsWith('?')&&!val)return null;if(String(schema).startsWith('number'))return val?Number(val):null;if(String(schema).startsWith('time'))return val?new Date(val+'Z').toISOString():null;if(schema==='refs')return val?val.split(',').map(item=>item.trim()).filter(Boolean):[];return val;}};
        }
        function object(schema,value){const node=el('div');node.className='government-editor-grid';const fields=Object.entries(schema).map(([key,type])=>[key,field(key,type,value[key])]);fields.forEach(([,field])=>node.append(field.node));return {node,get:()=>Object.fromEntries(fields.map(([key,field])=>[key,field.get()]))};}
        function mount(value={}){const type=doc.getElementById('subjectType').value;activeType=type;sourcePickers.length=0;editor=object(type==='MANDATE'?mandate:commitment,value);doc.getElementById('draftFields').replaceChildren(editor.node);}
        let pager;
        function renderQueue(){
            const list=doc.getElementById('revisionList');list.replaceChildren();
            const paged=root.FhemniUI.paginate(revisions,page);page=paged.page;
            if(queueState==='loading'||!revisions.length)list.append(el('p',t(queueState==='loading'?'loading':'empty')));
            for(const revision of paged.items){
                const card=el('article');card.className='public-panel public-stack';
                const heading=el('h3',revision.slug+' · '+revision.revisionNumber);heading.id='revision-'+revision.id;heading.tabIndex=-1;
                card.append(heading,el('p',t(revision.status)),el('p',revision.reason));
                const preview=el('details');preview.append(el('summary',t('preview')));
                const summary=el('dl');for(const row of previewLines(JSON.parse(revision.payload),locale))summary.append(el('dt',row.label),el('dd',row.text));preview.append(summary);card.append(preview);
                const actions=el('div');actions.className='public-toolbar';
                const edit=el('button',t('edit'));edit.onclick=()=>{
                    if(dirty&&!root.confirm(t('replace')))return;
                    doc.getElementById('subjectType').value=revision.subjectType;
                    doc.getElementById('subjectSlug').value=revision.slug;mount(JSON.parse(revision.payload));
                    doc.getElementById('revisionReason').value='';dirty=false;doc.getElementById('subjectSlug').focus();
                };actions.append(edit);
                for(const [state,action]of [['DRAFT','review'],['REVIEWED','publish']])if(revision.status===state){
                    const button=el('button',t(action));button.onclick=async()=>{
                        if(action==='publish'&&!root.confirm(t('confirm')))return;
                        button.disabled=true;
                        try{await api('/drafts/'+revision.id+'/'+action,{expectedVersion:revision.rowVersion});await refresh();doc.getElementById('revision-'+revision.id)?.focus();status(t(action==='publish'?'published':'reviewed'));}
                        catch(error){status(t('stale')+' '+error.message);}finally{button.disabled=false;}
                    };actions.append(button);
                }
                card.append(actions);list.append(card);
            }
            if(!pager){pager=root.FhemniUI.createPager({...paged,locale,onChange:value=>{page=value;renderQueue();}});doc.getElementById('revisionPager').append(pager);}
            else pager.update({...paged,locale});
        }
        async function refresh(){[sources,revisions]=await Promise.all([api('/evidence'),api('/drafts')]);sourcePickers.forEach(update=>update());queueState='ready';renderQueue();}
        function copy(){for(const[id,key]of Object.entries({deskTitle:'title',deskNotice:'private',evidenceTitle:'source',saveEvidence:'saveSource',editorTitle:'new',newRecord:'new',typeLabel:'type',slugLabel:'slug',reasonLabel:'reason',saveDraft:'save',queueTitle:'queue',refreshDrafts:'refresh'}))doc.getElementById(id).textContent=t(key);for(const option of doc.getElementById('subjectType').options)option.textContent=t(option.value);}
        doc.getElementById('draftForm').addEventListener('input',()=>{dirty=true;});
        doc.getElementById('subjectType').onchange=()=>{
            cachedDrafts.set(activeType,{value:editor.get(),slug:doc.getElementById('subjectSlug').value,reason:doc.getElementById('revisionReason').value,dirty});
            const type=doc.getElementById('subjectType').value,saved=cachedDrafts.get(type);
            doc.getElementById('subjectSlug').value=saved?.slug||(type==='MANDATE'?'2026-2031':'');doc.getElementById('revisionReason').value=saved?.reason||'';dirty=saved?.dirty||false;mount(saved?.value);
        };
        doc.getElementById('newRecord').onclick=()=>{if(dirty&&!root.confirm(t('replace')))return;doc.getElementById('revisionReason').value='';doc.getElementById('subjectSlug').value=activeType==='MANDATE'?'2026-2031':'';mount();dirty=false;};
        doc.getElementById('draftForm').onsubmit=async event=>{event.preventDefault();const button=doc.getElementById('saveDraft');button.disabled=true;try{
            const saved=await api('/drafts',prepareDraft(doc.getElementById('subjectType').value,doc.getElementById('subjectSlug').value,editor.get(),doc.getElementById('revisionReason').value));dirty=false;page=1;await refresh();status(t('saved'));
            const link=el('a',t('savedRevision')+' '+saved.revisionNumber);link.href='#revision-'+saved.id;doc.getElementById('deskStatus').append(link);
        }catch(error){status(t('stale')+' '+error.message);}finally{button.disabled=false;}};
        doc.getElementById('evidenceForm').onsubmit=async event=>{event.preventDefault();const button=doc.getElementById('saveEvidence');button.disabled=true;try{const value=sourceEditor.get();await api('/evidence',{evidence:{...value,archivedSha256:null},archive:null});const previous=editor.get();await refresh();mount(previous);sourceEditor=object(source,{retrievedAt:new Date().toISOString()});doc.getElementById('evidenceFields').replaceChildren(sourceEditor.node);status(t('saved'));}catch(error){status(error.message);}finally{button.disabled=false;}};
        doc.getElementById('refreshDrafts').onclick=()=>refresh().catch(error=>status(error.message));
        doc.addEventListener('fhemni:localechange',()=>{const previous=editor.get(),savedSource=sourceEditor.get();locale=doc.documentElement.lang;copy();mount(previous);sourceEditor=object(source,savedSource);doc.getElementById('evidenceFields').replaceChildren(sourceEditor.node);renderQueue();});
        copy();sourceEditor=object(source,{retrievedAt:new Date().toISOString()});doc.getElementById('evidenceFields').append(sourceEditor.node);mount();renderQueue();refresh().catch(error=>{queueState='failed';doc.getElementById('revisionList').replaceChildren(el('p',t('stale')));status(error.message);});
    }
    return {COPY,prepareDraft,previewLines,init};
});
