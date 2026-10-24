(function(root,factory){
    const commonJS=typeof module==='object'&&module.exports;
    const api=factory(root,commonJS?require('./government-model.js'):root.FhemniGovernmentModel,commonJS?require('./public-ui.js'):root.FhemniUI,commonJS?require('./government-visuals.js'):root.FhemniGovernmentVisuals);
    if(commonJS)module.exports=api;else{root.FhemniGovernmentMinisters=api;root.document.addEventListener('DOMContentLoaded',api.init);}
})(typeof window==='object'?window:globalThis,function(root,model,ui,visuals){
    const BASE='/api/catalog/government/2026-2031';
    const RESEARCH_NOTE={ar:'ملاحظة بحثية — ماشي اقتباس من المصدر',fr:'Note de recherche — pas une citation de la source',en:'Research note — not a source quotation'};
    const COPY={
        ar:{title:'شكون فالحكومة؟',intro:'تعرّف على أعضاء الحكومة، المسؤوليات ديالهم وفترة العضوية الموثقة، مع المصادر.',search:'قلب بالاسم أو المسؤولية',status:'العضوية',current:'الأعضاء الحاليون',former:'الأعضاء السابقون',all:'جميع الأعضاء',future:'تعيين مرتقب',role:'المسؤولية',from:'بداية العضوية',until:'نهاية العضوية',ongoing:'مستمرة حسب السجل',partyUnknown:'الانتماء الحزبي غير موثق',periodNote:'التواريخ كتخص العضوية فالحكومة، وماشي بالضرورة بداية نفس الحقيبة الوزارية.',originalNote:'بعض الأسماء والمسؤوليات كتظهر بلغة المصدر إذا ما توفرتش الترجمة.',asOf:'التشكيلة حسب معطيات',preview:'معاينة محلية — تشكيلة حقيقية موثقة، وماشي إعلان على حكومة 2026–2031.',empty:'ما كاين حتى عضو مطابق لهاد البحث.',clear:'مسح البحث',noSources:'ما كاينش مصدر مرتبط بهاد السجل.',unavailable:'ما كايناش تشكيلة منشورة دابا.'},
        fr:{title:'Qui est au gouvernement ?',intro:'Découvrez les membres du gouvernement, leurs fonctions et leurs périodes de participation documentées, avec les sources.',search:'Rechercher un nom ou une fonction',status:'Participation',current:'Membres actuels',former:'Anciens membres',all:'Tous les membres',future:'Nomination à venir',role:'Fonction',from:'Début de participation',until:'Fin de participation',ongoing:'En cours selon le registre',partyUnknown:'Affiliation politique non documentée',periodNote:'Ces dates concernent la participation au gouvernement, pas nécessairement le début du portefeuille actuel.',originalNote:'Certains noms et fonctions restent dans la langue de la source lorsqu’une traduction n’est pas disponible.',asOf:'Composition au',preview:'Aperçu local — composition réelle documentée, sans annoncer un gouvernement 2026–2031.',empty:'Aucun membre ne correspond à cette recherche.',clear:'Effacer la recherche',noSources:'Aucune source liée à cette fiche.',unavailable:'Aucune composition publiée pour le moment.'},
        en:{title:'Who is in government?',intro:'Explore government members, their roles and documented membership periods, with the supporting sources.',search:'Search by name or role',status:'Membership',current:'Current members',former:'Former members',all:'All members',future:'Upcoming appointment',role:'Role',from:'Membership started',until:'Membership ended',ongoing:'Ongoing in this record',partyUnknown:'Party affiliation not documented',periodNote:'These dates describe government membership, not necessarily the start of the current ministerial portfolio.',originalNote:'Some names and roles retain the source language when a translation is unavailable.',asOf:'Membership as of',preview:'Local preview — documented real membership, not an announcement of a 2026–2031 government.',empty:'No members match this search.',clear:'Clear search',noSources:'No source is linked to this record.',unavailable:'No membership has been published yet.'}
    };
    function normalize(value){return String(value||'').normalize('NFD').replace(/\p{M}/gu,'').toLocaleLowerCase().trim();}
    function membershipStatus(row,asOf){
        if(row.effectiveFrom&&row.effectiveFrom>asOf)return 'future';
        return row.effectiveUntil&&row.effectiveUntil<asOf?'former':'current';
    }
    function selectMembers(rows=[],state={},asOf=new Date().toISOString().slice(0,10)){
        const query=normalize(state.q),status=state.status||'current';
        const matches=rows.filter(row=>(status==='all'||membershipStatus(row,asOf)===status)&&(!query||normalize([...Object.values(row.name||{}),...Object.values(row.role||{})].join(' ')).includes(query)));
        return ui.paginate(matches,state.page||1);
    }
    function readState(url){
        const query=new URL(url,'http://localhost').searchParams,page=Number(query.get('page')),status=query.get('status');
        return {q:(query.get('q')||'').slice(0,200),status:['current','former','all'].includes(status)?status:'current',page:Number.isInteger(page)&&page>0&&page<=100000?page:1};
    }
    function stateUrl(state){const query=new URLSearchParams();if(state.q)query.set('q',state.q);if(state.status!=='current')query.set('status',state.status);if(state.page>1)query.set('page',state.page);return '/government/ministers'+(query.size?'?'+query:'');}
    async function loadDirectory(request){
        const [mandate,preview]=await Promise.allSettled([request(BASE),request(BASE+'/preview')]);
        if(mandate.status==='rejected')throw mandate.reason;
        if(preview.status==='rejected'&&preview.reason?.status!==404)throw preview.reason;
        return {mandate:mandate.value,preview:preview.status==='fulfilled'&&preview.value?.localPreview?preview.value:null};
    }
    function renderMembers(host,rows,{locale='ar',asOf=new Date().toISOString().slice(0,10),document:doc=root.document,request=path=>root.FhemniGovernmentAPI.request(path)}={}){
        const copy=COPY[locale],common=model.COPY[locale];
        const el=(tag,text,className)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;if(className)node.className=className;return node;};
        host.replaceChildren();
        for(const row of rows.slice(0,10)){
            const article=el('article',null,'public-panel minister-card'),heading=el('h2',model.localized(row.name,locale)||common.unknown),status=membershipStatus(row,asOf);
            heading.setAttribute('dir','auto');article.append(el('p',copy[status],'minister-status'),heading);
            const role=el('p',model.localized(row.role,locale)||common.unknown,'minister-role');role.setAttribute('dir','auto');article.append(role);
            const facts=el('dl',null,'minister-facts');
            function fact(label,value){const dd=el('dd',value);dd.setAttribute('dir','auto');facts.append(el('dt',label),dd);}
            fact(common.party,row.partyCode||copy.partyUnknown);fact(copy.from,model.date(row.effectiveFrom,locale));fact(copy.until,row.effectiveUntil?model.date(row.effectiveUntil,locale):copy.ongoing);article.append(facts);
            const ids=[...new Set(row.evidenceIds||[])];
            if(ids.length){
                const details=el('details',null,'minister-sources'),body=el('div',null,'minister-evidence');details.append(el('summary',common.sources),body);let loaded=false,pending=false;
                async function loadSources(){
                    if(loaded||pending)return;pending=true;body.setAttribute('aria-busy','true');body.replaceChildren(el('p',common.loading));
                    try{
                        const sources=await Promise.all(ids.map(id=>request(BASE+'/sources/'+encodeURIComponent(id))));body.replaceChildren();
                        for(const source of sources){
                            const section=el('section',null,'government-evidence'),url=model.sourceUrl(source.url);
                            if(url){const link=el('a',source.locator?common.evidenceLink+' · '+source.locator:common.evidenceLink);link.href=url;link.rel='noopener noreferrer';section.append(link);}else if(source.locator)section.append(el('p',source.locator));
                            const researchNote=source.passage?.startsWith('Research summary —');
                            if(researchNote)section.append(el('p',RESEARCH_NOTE[locale],'government-meta'));
                            const passage=el(researchNote?'p':'blockquote',source.passage||common.unknown);passage.setAttribute('dir','auto');if(source.originalLanguage)passage.setAttribute('lang',source.originalLanguage);
                            section.append(passage,el('p',`${common.sourceDate}: ${model.date(source.publishedAt,locale)} · ${common.retrieved}: ${model.date(source.retrievedAt,locale)}`,'government-meta'));body.append(section);
                        }loaded=true;
                    }catch{const retry=el('button',common.retry);retry.type='button';retry.onclick=loadSources;body.replaceChildren(el('p',common.error),retry);}
                    finally{pending=false;body.setAttribute('aria-busy','false');}
                }
                details.addEventListener('toggle',()=>{if(details.open)return loadSources();});article.append(details);
            }else article.append(el('p',copy.noSources,'government-meta'));
            host.append(article);
        }
    }
    function init(){
        const doc=root.document,content=doc.getElementById('ministersContent');if(!content)return;
        const get=id=>doc.getElementById(id),locale=()=>root.FhemniI18n?.locale()||'ar';
        let state=readState(root.location.href),data=null,loadState='loading',version=0,pager;
        function render(){
            const lang=locale(),copy=COPY[lang],common=model.COPY[lang];doc.title=copy.title+' — Fhemni';
            for(const [id,key]of Object.entries({ministersTitle:'title',ministersIntro:'intro',ministersSearchLabel:'search',ministersStatusLabel:'status',ministersPeriodNote:'periodNote',ministersOriginalNote:'originalNote',ministersClear:'clear'}))get(id).textContent=copy[key];
            get('ministersBack').textContent=common.back;get('ministersRetry').textContent=common.retry;get('ministersRetry').hidden=loadState!=='error';
            get('ministersFiltersLegend').textContent=common.filters;
            for(const option of get('ministersStatusFilter').options)option.textContent=copy[option.value];
            get('ministersSearch').value=state.q;get('ministersStatusFilter').value=state.status;
            get('ministersFilters').disabled=loadState!=='ready';content.setAttribute('aria-busy',String(loadState==='loading'));
            const status=get('ministersStatus'),pagerHost=get('ministersPager'),preview=get('ministersPreview'),snapshot=get('ministersSnapshot');
            preview.hidden=!data?.preview;preview.textContent=data?.preview?copy.preview:'';snapshot.textContent='';
            if(loadState!=='ready'){status.textContent=common[loadState==='error'?'error':'loading'];content.replaceChildren();get('ministersChanges').replaceChildren();pagerHost.hidden=true;return;}
            const asOf=data.preview?.snapshotDate||data.mandate.verifiedAt?.slice(0,10)||new Date().toISOString().slice(0,10),rows=data.mandate.membership||[],page=selectMembers(rows,state,asOf);
            if(state.page!==page.page){state.page=page.page;root.history.replaceState({},'',stateUrl(state));}
            snapshot.textContent=copy.asOf+': '+model.date(asOf,lang);
            visuals.renderCabinet(get('ministersChanges'),rows,{locale:lang,document:doc,asOf});
            renderMembers(content,page.items,{locale:lang,asOf,document:doc});
            ui.announceStatus(status,page.total?ui.countPhrase(page.total,'result',lang):rows.length?copy.empty:copy.unavailable);
            const options={page:page.page,pages:page.pages,total:page.total,locale:lang};
            if(!pager){pager=ui.createPager({...options,document:doc,onChange:page=>{state.page=page;root.history.pushState({},'',stateUrl(state));render();get('ministersTitle').focus();}});pagerHost.append(pager);}else pager.update(options);
            pagerHost.hidden=page.pages<=1;
        }
        async function load(){const ticket=++version;loadState='loading';data=null;render();try{const result=await loadDirectory(path=>root.FhemniGovernmentAPI.request(path));if(ticket!==version)return;data=result;loadState='ready';}catch{if(ticket!==version)return;loadState='error';}render();}
        get('ministersSearch').addEventListener('input',()=>{state.q=get('ministersSearch').value.slice(0,200);state.page=1;root.history.replaceState({},'',stateUrl(state));render();});
        get('ministersStatusFilter').addEventListener('change',()=>{state.status=get('ministersStatusFilter').value;state.page=1;root.history.pushState({},'',stateUrl(state));render();});
        get('ministersClear').addEventListener('click',()=>{state.q='';state.page=1;root.history.replaceState({},'',stateUrl(state));render();get('ministersSearch').focus();});
        get('ministersRetry').addEventListener('click',load);doc.addEventListener('fhemni:localechange',render);root.addEventListener('popstate',()=>{state=readState(root.location.href);render();});load();
    }
    return {COPY,selectMembers,readState,stateUrl,loadDirectory,renderMembers,init};
});
