(function(root,factory){
    const common=typeof module==='object'&&module.exports;
    const api=factory(common?require('./government-model.js'):root.FhemniGovernmentModel,common?require('./public-ui.js'):root.FhemniUI);
    if(common)module.exports=api;
    else{root.FhemniPromiseGovernment=api;root.document.addEventListener('DOMContentLoaded',()=>{
        const host=root.document.getElementById('promiseGovernmentLinks');if(!host)return;
        const slug=decodeURIComponent(root.location.pathname.split('/').filter(Boolean).at(-1)||'');
        const page=api.mount(host,{doc:root.document,locale:()=>root.FhemniI18n?.locale()||'ar',slug,api:root.FhemniGovernmentAPI});
        root.document.addEventListener('fhemni:localechange',()=>page.render());page.load();
    });}
})(typeof window==='object'?window:globalThis,function(model,ui){
    const COPY={
        ar:{title:'من الوعد الانتخابي للبرنامج الحكومي',note:'هاد العلاقة مراجعة بوحدها. ما كتبدّلش تقييم قابلية إنجاز الوعد اللي فوق، وما كتثبتش إنجاز الحكومة.',unreviewed:'ما كايناش بعد علاقة مراجعة ومنشورة لهاد الوعد. هادشي ما كيعنيش أنه غايب من البرنامج الحكومي.',current:'نسخة البرنامج الحالية',earlier:'نسخة سابقة من البرنامج',version:'نسخة البرنامج المراجعة',provenance:'النسخة والمصادر',missingSource:'المصدر المشار ليه ما متاحش دابا.',error:'تعذّر تحميل مراجعة العلاقة.',loading:'كنحمّلو مراجعة العلاقة…'},
        fr:{title:'De la promesse au programme gouvernemental',note:'Ce lien fait l’objet d’un examen distinct. Il ne remplace pas l’évaluation de faisabilité ci-dessus et ne prouve pas une réalisation gouvernementale.',unreviewed:'Aucun lien examiné n’est encore publié pour cette promesse. Cela ne signifie pas qu’elle est absente du programme gouvernemental.',current:'Version actuelle du programme',earlier:'Version antérieure du programme',version:'Version du programme examinée',provenance:'Version et sources',missingSource:'La source citée est actuellement indisponible.',error:'Le lien examiné n’a pas pu être chargé.',loading:'Chargement du lien examiné…'},
        en:{title:'From campaign promise to government programme',note:'This relationship is reviewed separately. It does not replace the feasibility assessment above or establish government delivery.',unreviewed:'No reviewed relationship has been published for this promise yet. This does not mean it is absent from the government programme.',current:'Current programme version',earlier:'Earlier programme version',version:'Reviewed programme version',provenance:'Version and sources',missingSource:'The cited source is currently unavailable.',error:'The reviewed relationship could not be loaded.',loading:'Loading the reviewed relationship…'}
    };
    function localized(value,locale){return value?.[locale]||value?.ar||value?.fr||value?.en||'';}
    function renderRelationships(host,data,{locale='ar',doc,programme=null,page=1}={}){
        const copy=COPY[locale],shared=model.COPY[locale],el=(tag,value,className)=>{const node=doc.createElement(tag);if(value!=null)node.textContent=value;if(className)node.className=className;return node;};
        const opened=new Set([...host.querySelectorAll('details')].filter(node=>node.open).map(node=>node.id));
        const focused=doc.activeElement?.id;
        host.replaceChildren();const rows=ui.paginate(data?.links||[],page);
        if(!rows.total)host.append(el('p',copy.unreviewed));
        rows.items.forEach((relationship,index)=>{
            const key=(rows.page-1)*10+index,article=el('article',null,'government-update public-stack');
            article.append(el('h3',shared[relationship.relation]||shared.UNRESOLVED));
            const explanation=el('p',localized(relationship.explanation,locale));explanation.dir='auto';article.append(explanation);
            const same=programme?.id&&programme.id===relationship.programmeVersionId;
            const versionLabel=same?copy.current:programme?.id?copy.earlier:copy.version;
            const version=el('p',versionLabel,'government-meta');
            if(same){const name=el('bdi',' · '+localized(programme.title,locale)+' · '+programme.version);name.dir='auto';version.append(name);}article.append(version);
            if(relationship.governmentCommitmentId){const link=el('a',shared.open,'text-link');link.href='/government/commitments/'+encodeURIComponent(relationship.governmentCommitmentId);link.id='promiseGovernmentTarget-'+key;article.append(link);}
            const details=el('details',null,'government-evidence');details.id='promiseGovernmentEvidence-'+key;details.open=opened.has(details.id);
            const summary=el('summary',copy.provenance);summary.id=details.id+'-summary';details.append(summary);
            const provenance=el('p',shared.programmeVersion+': '),identifier=el('bdi',relationship.programmeVersionId||shared.unknown);identifier.dir='ltr';provenance.append(identifier);details.append(provenance);
            for(const [sourceIndex,id]of (relationship.evidenceIds||[]).entries()){
                const source=data.evidence?.find(item=>item.id===id);if(!source){details.append(el('p',copy.missingSource));continue;}
                const evidence=el('div',null,'government-evidence'),url=model.sourceUrl(source.url);
                if(url){const link=el('a',source.locator?shared.evidenceLink+' · '+source.locator:shared.evidenceLink,'government-evidence-link');link.href=url;link.rel='noopener noreferrer';link.id=details.id+'-source-'+sourceIndex;evidence.append(link);}
                const quote=el('blockquote',source.passage);quote.setAttribute('dir','auto');if(/^[a-z]{2,3}(?:-[A-Za-z0-9]+)*$/.test(source.originalLanguage||''))quote.setAttribute('lang',source.originalLanguage);evidence.append(quote);
                evidence.append(el('p',`${shared.sourceDate}: ${model.date(source.publishedAt,locale)} · ${shared.retrieved}: ${model.date(source.retrievedAt,locale)}`,'government-meta'));details.append(evidence);
            }
            article.append(details);host.append(article);
        });
        if(focused)for(const node of host.querySelectorAll('[id]'))if(node.id===focused)node.focus();
        return rows;
    }
    function mount(host,{doc,locale,slug,api}){
        let view={state:'loading',data:null,programme:null},page=1,version=0,pager;
        const el=tag=>doc.createElement(tag),heading=el('h2'),note=el('p'),status=el('p'),rows=el('div'),pagerHost=el('div'),retry=el('button');
        heading.id='promiseGovernmentTitle';host.setAttribute('aria-labelledby',heading.id);note.className='government-note';rows.className='public-stack';status.setAttribute('role','status');status.setAttribute('aria-live','polite');retry.type='button';host.replaceChildren(heading,note,status,rows,pagerHost,retry);
        function render(next){if(next)view={...view,...next};const lang=locale(),copy=COPY[lang];heading.textContent=copy.title;note.textContent=copy.note;
            status.textContent=view.state==='loading'?copy.loading:view.state==='error'?copy.error:'';status.hidden=view.state==='ready';retry.textContent=model.COPY[lang].retry;retry.hidden=view.state!=='error';rows.setAttribute('aria-busy',String(view.state==='loading'));
            if(view.state!=='ready'){rows.replaceChildren();pagerHost.hidden=true;return;}
            const result=renderRelationships(rows,view.data,{locale:lang,doc,programme:view.programme,page});page=result.page;
            const config={...result,locale:lang};
            if(!pager){pager=ui.createPager({...config,document:doc,onChange:nextPage=>{page=nextPage;render();}});pagerHost.append(pager);}else pager.update(config);
            pagerHost.hidden=result.pages<=1;
        }
        async function load(){const current=++version,restoreFocus=doc.activeElement===retry;render({state:'loading',programme:null});
            const finish=next=>{if(current!==version)return;render(next);if(restoreFocus){if(retry.hidden){heading.tabIndex=-1;heading.focus();}else retry.focus();}};
            await Promise.allSettled([
                api.fetchCommitment('campaign-'+slug).then(data=>finish({state:'ready',data}),error=>finish({state:error.status===404?'ready':'error',data:null})),
                api.fetchMandate().then(mandate=>{if(current===version)render({programme:mandate.programme||null});},()=>{})
            ]);
        }
        retry.onclick=load;render();return {load,render};
    }
    return {COPY,renderRelationships,mount};
});
