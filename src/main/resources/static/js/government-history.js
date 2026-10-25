(function(root,factory){
    const api=factory(root,typeof module==='object'?require('./government-model.js'):root.FhemniGovernmentModel,typeof module==='object'?require('./public-ui.js'):root.FhemniUI,typeof module==='object'?require('./measure.js'):root.FhemniMeasures,typeof module==='object'?require('./government-ministers.js'):root.FhemniGovernmentMinisters);
    if(typeof module==='object'&&module.exports)module.exports=api;else{root.FhemniGovernmentHistory=api;document.addEventListener('DOMContentLoaded',api.init);}
})(typeof window==='object'?window:globalThis,function(root,model,ui,measures,ministers){
    const COPY={
        ar:{title:'سجل النسخ المنشورة',intro:'شوف شنو كان منشور، وفاش تنشر، وعلاش تبدّل.',archived:'هادي نسخة سبق نشرها، ماشي العرض الحالي. المعطيات والخطوات اللي فيها ممكن تكون تبدّلات.',current:'شوف العرض الحالي',open:'قرا النسخة المنشورة',explanation:'توضيح النشر',empty:'ما كاينة حتى نسخة منشورة لهاد السجل.',missing:'هاد النسخة ما كايناش ضمن النسخ المنشورة.',publishedAt:'تاريخ النشر',snapshot:'المعطيات وقت النشر',allHistory:'جميع النسخ المنشورة لهاد السجل',role:'المهمة',from:'بداية المهمة',until:'نهاية المهمة',lifecycle:'مرحلة الحكومة',complete:'المراجعة كاملة',incomplete:'المراجعة غير مكتملة',metric:'المؤشر',targetDate:'تاريخ الهدف',baselineDate:'تاريخ المرجع',programmeRecorded:'العلاقة مربوطة بنسخة البرنامج المراجعة.',originalPromise:'شوف الوعد الأصلي',linkedCommitment:'شوف الالتزام الحكومي',recordedProcess:'رابط المسطرة وقت النشر',MANDATE:'الحكومة',COMMITMENT:'الالتزام',MEASURE:'التدبير',LINK:'العلاقة بين الوعد والبرنامج'},
        fr:{title:'Historique des publications',intro:'Consultez les versions publiées, leurs dates et les explications des changements.',archived:'Ceci est une version précédemment publiée, pas la version actuelle. Les données et démarches peuvent avoir changé.',current:'Voir la version actuelle',open:'Lire la version publiée',explanation:'Explication de la publication',empty:'Aucune version publiée pour cette fiche.',missing:'Cette version ne figure pas parmi les publications.',publishedAt:'Publié le',snapshot:'Données au moment de la publication',allHistory:'Toutes les versions de cette fiche',role:'Fonction',from:'Début de fonction',until:'Fin de fonction',lifecycle:'Étape du gouvernement',complete:'Examen complet',incomplete:'Examen incomplet',metric:'Indicateur',targetDate:'Date de l’objectif',baselineDate:'Date de référence',programmeRecorded:'Lien établi avec la version du programme examinée.',originalPromise:'Voir la promesse originale',linkedCommitment:'Voir l’engagement gouvernemental',recordedProcess:'Lien vers la démarche lors de la publication',MANDATE:'Gouvernement',COMMITMENT:'Engagement',MEASURE:'Mesure',LINK:'Lien entre promesse et programme'},
        en:{title:'Published history',intro:'Read what was published, when it was published and why it changed.',archived:'This is a previously published snapshot, not the current view. Its information and official steps may have changed.',current:'View the current record',open:'Read published snapshot',explanation:'Publication explanation',empty:'No published snapshots for this record.',missing:'This snapshot is not among the published records.',publishedAt:'Published',snapshot:'Information at publication',allHistory:'All published versions of this record',role:'Role',from:'Role started',until:'Role ended',lifecycle:'Government stage',complete:'Review complete',incomplete:'Review incomplete',metric:'Metric',targetDate:'Target date',baselineDate:'Baseline date',programmeRecorded:'Relationship recorded against the reviewed programme version.',originalPromise:'View the original promise',linkedCommitment:'View the government commitment',recordedProcess:'Official process linked at publication',MANDATE:'Government',COMMITMENT:'Commitment',MEASURE:'Measure',LINK:'Promise and programme relationship'}
    };
    function readHistoryState(value){
        const url=new URL(value,'http://localhost'),query=url.searchParams,rawType=query.get('type'),type=['MANDATE','COMMITMENT','MEASURE','LINK'].includes(rawType)?rawType:'MANDATE';
        const rawSlug=query.get('slug'),slug=type==='MANDATE'?'2026-2031':/^[a-zA-Z0-9_-]{1,160}$/.test(rawSlug||'')?rawSlug:'';
        const requested=Number(query.get('page')),page=Number.isInteger(requested)&&requested>0&&requested<=100000?requested:1;
        const revision=url.pathname.startsWith('/government/revisions/')?url.pathname.split('/')[3]:null;
        return {type,slug,page,revision};
    }
    function historyPath(type,slug){return '/government/history?'+new URLSearchParams({type,slug});}
    function historyApiPath(state){return state.revision?'/api/catalog/government/revisions/'+encodeURIComponent(state.revision):'/api/catalog/government/2026-2031/history?'+new URLSearchParams({type:state.type,slug:state.slug,page:state.page});}
    function currentPath(type,slug,snapshot={}){
        if(type==='COMMITMENT')return snapshot.origin==='CAMPAIGN'?'/promises/'+encodeURIComponent(String(slug).replace(/^campaign-/,'')):'/government/commitments/'+encodeURIComponent(slug);
        if(type==='MEASURE')return '/government/measures/'+encodeURIComponent(slug);
        if(type==='LINK'&&snapshot.relationship?.governmentCommitmentId)return '/government/commitments/'+encodeURIComponent(snapshot.relationship.governmentCommitmentId);
        return '/government';
    }
    function elements(doc){return (tag,text,className)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;if(className)node.className=className;return node;};}
    function renderHistory(host,page,locale='ar',doc=globalThis.document){
        const el=elements(doc),copy=COPY[locale],common=model.COPY[locale];host.replaceChildren();
        if(!page.items?.length)host.append(el('p',copy.empty));
        for(const row of (page.items||[]).slice(0,10)){
            const article=el('article',null,'public-panel public-stack'),heading=el('h2'),link=el('a',`${copy.open} · ${common.publishedRevision} ${model.number(row.version,locale)}`);
            link.href='/government/revisions/'+encodeURIComponent(row.id);heading.append(link);article.append(heading,el('p',`${copy.publishedAt}: ${model.date(row.publishedAt,locale)}`,'government-meta'),el('p',model.localized(row.explanation,locale)||common.unknown));host.append(article);
        }
    }
    function renderRevision(host,revision,locale='ar',doc=globalThis.document,{parties=[]}={}){
        const el=elements(doc),copy=COPY[locale],common=model.COPY[locale],view=revision.snapshot||{},local=value=>model.localized(value,locale)||common.unknown,date=value=>model.date(value,locale),number=value=>model.number(value,locale);
        host.replaceChildren();host.append(el('p',copy.archived,'government-note'));
        const current=el('a',copy.current,'text-link');current.href=currentPath(revision.subjectType,revision.slug,view);host.append(current);
        const heading=el('h2',local(view.title||view.label)||copy.snapshot);heading.tabIndex=-1;host.append(heading);
        const metadata=el('dl',null,'government-facts');host.append(metadata);
        function fact(target,label,value){target.append(el('dt',label),el('dd',value==null||value===''?common.unknown:value));}
        fact(metadata,common.publishedRevision,number(revision.version));fact(metadata,copy.publishedAt,date(revision.publishedAt));fact(metadata,copy.explanation,local(revision.explanation));
        function section(title){const node=el('section',null,'public-stack');node.append(el('h3',title));host.append(node);return node;}
        function facts(target,rows){const dl=el('dl',null,'government-facts');for(const [label,value]of rows)fact(dl,label,value);target.append(dl);}
        function bounded(title,rows,renderRow){
            if(!rows?.length)return;const group=section(title),content=el('div',null,'public-stack');group.append(content);let page=1;
            const draw=()=>{content.replaceChildren();for(const row of rows.slice((page-1)*10,page*10))content.append(renderRow(row));};draw();
            if(rows.length>10){const pager=ui.createPager({page,pages:Math.ceil(rows.length/10),total:rows.length,locale,document:doc,onChange:next=>{page=next;draw();pager.update({page,pages:Math.ceil(rows.length/10),total:rows.length,locale});}});group.append(pager);}
        }
        function quote(text,language){const node=el('blockquote',text||common.unknown);node.setAttribute('dir','auto');if(language)node.setAttribute('lang',language);return node;}
        function relationship(row){const article=el('article',null,'public-panel public-stack');article.append(el('h4',common[row.relation]||common.UNRESOLVED),el('p',local(row.explanation)));
            if(row.programmeVersionId)article.append(el('p',copy.programmeRecorded,'government-meta'));
            for(const [id,label]of [[row.campaignPromiseId,copy.originalPromise],[row.governmentCommitmentId,copy.linkedCommitment]])if(id){const link=el('a',label);link.href='/government/commitments/'+encodeURIComponent(id);article.append(link);}return article;}
        if(revision.subjectType==='MANDATE'){
            facts(host,[[copy.lifecycle,common[{FORMATION:'pending',FORMED_PROGRAMME_PENDING:'formed',PROGRAMME_PUBLISHED:'published'}[view.lifecycle]]||common.unknown],[common.verified,date(view.verifiedAt)]]);
            if(view.reviewCoverage){const coverage=view.reviewCoverage;facts(host,[[common.coverage,`${number(coverage.reviewed)} ${coverage.total==null?common.unknownTotal:common.of+' '+number(coverage.total)} · ${coverage.complete?copy.complete:copy.incomplete}`]]);}
            if(view.programme){const programme=section(common.programmeVersion);facts(programme,[[common.programmeVersion,number(view.programme.version)],[common.programme,local(view.programme.title)],[copy.publishedAt,date(view.programme.publishedAt)]]);}
            if(view.membership?.length){
                const group=section(common.membership),content=el('div',null,'ministers-directory'),rows=view.membership,pageSize=ministers.PAGE_SIZE;
                const evidence=new Map([...(revision.evidence||[]),...(view.evidence||[])].map(source=>[source.id,source]));
                // Archive cards use the same presentation, but never substitute today's membership or evidence.
                const request=async path=>{const source=evidence.get(decodeURIComponent(path.split('/').pop()));if(!source)throw new Error('Source absent from archived revision');return source;};
                const asOf=String(revision.publishedAt||view.verifiedAt||'').slice(0,10);
                let page=1;group.append(content);
                const draw=()=>ministers.renderMembers(content,rows.slice((page-1)*pageSize,page*pageSize),{locale,asOf,parties,headingTag:'h4',document:doc,request});
                draw();
                if(rows.length>pageSize){const options={pages:Math.ceil(rows.length/pageSize),total:rows.length,pageSize,locale};const pager=ui.createPager({...options,page,document:doc,onChange:next=>{page=next;draw();pager.update({...options,page});}});group.append(pager);}
            }
        }else if(revision.subjectType==='COMMITMENT'){
            section(common.original).append(quote(view.originalText,view.originalLanguage));
            facts(host,[[common.origin,view.origin==='CAMPAIGN'?common.campaign:common.government],[common.author,local(view.author)],[common.responsible,local(view.responsibleBody)],[common.scope,local(view.geographicScope?.label)],[common.deadline,date(view.deadline)]]);
            const assessment=section(common.assessment),value=view.deliveryAssessment;assessment.append(el('p',common[value?.state]||common.UNASSESSED));
            if(value)facts(assessment,[[common.confidence,common[value.evidenceConfidence]||common.unknown],[copy.explanation,local(value.explanation)],[common.reviewed,date(value.reviewedAt)]]);
            if(view.target){const target=view.target;facts(section(common.comparison),[[copy.metric,target.metric],[common.unit,target.unit],[common.baseline,number(target.baseline)],[copy.baselineDate,date(target.baselineDate)],[common.target,number(target.targetValue)],[copy.targetDate,date(target.targetDate)]]);}
            bounded(common.links,view.links,relationship);
            bounded(common.observations,view.observations,row=>{const article=el('article',null,'public-panel');facts(article,[[copy.metric,row.metric],[common.value,number(row.value)],[common.unit,row.unit],[common.period,`${date(row.periodStart)} — ${date(row.periodEnd)}`],[common.scope,local(row.scope?.label)],[common.reviewed,date(row.reviewedAt)]]);return article;});
        }else if(revision.subjectType==='MEASURE'){
            facts(host,measures.measureFacts(view,locale).map(row=>[row.label,row.value]));const url=model.sourceUrl(view.officialActionUrl);if(url){const link=el('a',copy.recordedProcess);link.href=url;link.rel='noopener noreferrer';host.append(link);}
        }else if(revision.subjectType==='LINK')section(common.links).append(relationship(view.relationship||{}));
        const sources=[...new Map([...(revision.evidence||[]),...(view.evidence||[])].map(source=>[source.id||source.url,source])).values()];
        if(!sources.length)section(common.sources).append(el('p',common.unknown));
        bounded(common.sources,sources,source=>{const article=el('article',null,'government-evidence'),url=model.sourceUrl(source.url);if(url){const link=el('a',source.locator?`${common.evidenceLink} · ${source.locator}`:common.evidenceLink);link.href=url;link.rel='noopener noreferrer';article.append(link);}else if(source.locator)article.append(el('p',source.locator));article.append(quote(source.passage,source.originalLanguage),el('p',`${common.sourceDate}: ${date(source.publishedAt)} · ${common.retrieved}: ${date(source.retrievedAt)}`,'government-meta'));return article;});
        const all=el('a',copy.allHistory,'text-link');all.href=historyPath(revision.subjectType,revision.slug);host.append(all);
    }
    function createNavigation({request,render,push=()=>{},replace=()=>{}}){
        let sequence=0;
        return {async go(url,shouldPush=false){const ticket=++sequence,state=readHistoryState(url);if(shouldPush)push(url);render({state,data:null,loadState:'loading'});
            try{let data=await request(historyApiPath(state));if(ticket!==sequence)return;
                if(!state.revision){const last=Math.max(1,Math.ceil(data.totalItems/10));if(state.page>last){state.page=last;const normalized=new URL(url,'http://localhost');normalized.searchParams.set('page',last);replace(normalized.pathname+normalized.search);data=await request(historyApiPath(state));}}
                let parties=[];
                if(state.revision&&data.subjectType==='MANDATE'){
                    try{const catalog=await request('/api/catalog/parties');if(Array.isArray(catalog))parties=catalog;}catch{/* Historical facts remain readable without party artwork. */}
                }
                if(ticket===sequence)render({state,data,parties,loadState:'ready'});}
            catch(error){if(ticket===sequence)render({state,data:null,loadState:error.status===404?'missing':'error'});}
        }};
    }
    function init(){
        const doc=root.document,content=doc.getElementById('governmentHistoryContent');if(!content)return;let view={state:readHistoryState(root.location.href),data:null,loadState:'loading'},pager;
        const locale=()=>root.FhemniI18n?.locale()||'ar',pagerHost=doc.getElementById('governmentHistoryPager');
        function render(next){if(next)view=next;const lang=locale(),copy=COPY[lang],common=model.COPY[lang],status=doc.getElementById('governmentHistoryStatus'),title=doc.getElementById('governmentHistoryTitle');
            title.textContent=copy.title;doc.title=copy.title+' — Fhemni';doc.getElementById('governmentHistoryIntro').textContent=copy.intro;const back=doc.getElementById('governmentHistoryBack');back.textContent=common.back;
            const retry=doc.getElementById('governmentHistoryRetry');retry.textContent=common.retry;retry.hidden=view.loadState!=='error';status.textContent=view.loadState==='ready'?'':view.loadState==='missing'?copy.missing:common[view.loadState==='loading'?'loading':'error'];
            content.setAttribute('aria-busy',String(view.loadState==='loading'));pagerHost.setAttribute('aria-busy',String(view.loadState==='loading'));for(const button of pagerHost.querySelectorAll('button'))button.disabled=view.loadState!=='ready';
            if(view.loadState!=='ready'){content.replaceChildren();return;}
            pagerHost.hidden=!!view.state.revision;
            if(view.state.revision){renderRevision(content,view.data,lang,doc,{parties:view.parties});return;}
            const subject=doc.createElement('p');subject.className='government-meta';subject.textContent=copy[view.state.type];renderHistory(content,view.data,lang,doc);content.prepend(subject);
            const options={page:view.data.page,pages:Math.max(1,Math.ceil(view.data.totalItems/10)),total:view.data.totalItems,locale:lang};
            if(!pager){pager=ui.createPager({...options,document:doc,onChange:page=>{const url=new URL(root.location.href);url.searchParams.set('page',page);nav.go(url.pathname+url.search,true);}});pagerHost.append(pager);}else pager.update(options);
            ui.announceStatus(status,ui.countPhrase(view.data.totalItems,'result',lang));
        }
        const nav=createNavigation({request:path=>root.FhemniGovernmentAPI.request(path),render,push:url=>root.history.pushState({},'',url),replace:url=>root.history.replaceState({},'',url)});
        doc.getElementById('governmentHistoryRetry').onclick=()=>nav.go(root.location.href);doc.addEventListener('fhemni:localechange',()=>render());root.addEventListener('popstate',()=>nav.go(root.location.href));nav.go(root.location.href);
    }
    return {COPY,readHistoryState,historyPath,historyApiPath,currentPath,renderHistory,renderRevision,createNavigation,init};
});
