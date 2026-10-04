(function(root,factory){
    const api=factory(root);
    if(typeof module==='object'&&module.exports)module.exports=api;
    else {root.FhemniHome=api;document.addEventListener('DOMContentLoaded',()=>api.init(document,root));}
})(typeof window==='object'?window:globalThis,function(root){
    const COPY={
        ar:{pending:'تشكيل الحكومة قيد المتابعة',pendingText:'غادي نجمعو هنا المعطيات المؤكدة على الحكومة وبرنامجها، ونفصلوها على وعود الأحزاب.',formed:'الحكومة تشكّلات، والبرنامج قيد المتابعة',formedText:'التزامات البرنامج الحكومي غادي تبان من بعد النشر والمراجعة.',published:'البرنامج الحكومي متوفر',publishedText:'شوف الالتزامات اللي تراجعات والأدلة ديال التتبع.',unavailable:'تعذّر تحميل آخر معطيات التتبع',unavailableText:'عاود المحاولة، أو تصفّح برامج الأحزاب ونتائج الانتخابات.',loading:'كنحمّلو آخر المعطيات…',verified:'آخر تحقق',empty:'ما كايناش مراجعات منشورة دابا.',topicsError:'تعذّر تحميل المواضيع.',promisesError:'تعذّر تحميل مراجعات الوعود.',feasibility:'تقييم قابلية الإنجاز',POSSIBLE:'ممكن',HARD:'صعيب',NOT_ACHIEVABLE:'غير قابل للإنجاز',open:'شوف الوعد والمصادر'},
        fr:{pending:'Formation du gouvernement : suivi en préparation',pendingText:'Les informations confirmées sur le gouvernement et son programme seront présentées ici, séparément des promesses des partis.',formed:'Gouvernement formé, programme en attente',formedText:'Les engagements seront disponibles après publication et examen du programme.',published:'Programme gouvernemental disponible',publishedText:'Explorez les engagements examinés et les éléments du suivi.',unavailable:'Impossible de charger les dernières informations',unavailableText:'Réessayez ou consultez les programmes des partis et les résultats électoraux.',loading:'Chargement des dernières informations…',verified:'Dernière vérification',empty:'Aucune évaluation publiée pour le moment.',topicsError:'Impossible de charger les thèmes.',promisesError:'Impossible de charger les évaluations.',feasibility:'Évaluation de faisabilité',POSSIBLE:'Possible',HARD:'Difficile',NOT_ACHIEVABLE:'Non réalisable',open:'Voir la promesse et ses sources'},
        en:{pending:'Government formation: tracking in preparation',pendingText:'Confirmed information about the government and its programme will appear here, separately from party promises.',formed:'Government formed, programme pending',formedText:'Programme commitments will appear after publication and review.',published:'Government programme available',publishedText:'Explore reviewed commitments and the evidence behind their progress.',unavailable:'Latest tracking information could not be loaded',unavailableText:'Try again, or explore party programmes and election results.',loading:'Loading the latest information…',verified:'Last verified',empty:'No published reviews yet.',topicsError:'Topics could not be loaded.',promisesError:'Promise reviews could not be loaded.',feasibility:'Feasibility assessment',POSSIBLE:'Possible',HARD:'Difficult',NOT_ACHIEVABLE:'Not achievable',open:'Read the promise and sources'}
    };
    const ASSESSMENT_COPY={
        ar:{feasibility:'قابلية الإنجاز فخمس سنين',INSUFFICIENT_DATA:'المعطيات ما كافياش',partyUnknown:'اسم الحزب غير متاح'},
        fr:{feasibility:'Faisabilité sur cinq ans',INSUFFICIENT_DATA:'Données insuffisantes',partyUnknown:'Parti indisponible'},
        en:{feasibility:'Five-year feasibility',INSUFFICIENT_DATA:'Insufficient data',partyUnknown:'Party unavailable'}
    };
    const TRACKING_COPY={ar:{programme:'شوف البرنامج الحكومي',campaign:'الأحزاب والبرامج',coverage:'الالتزامات المراجعة',updates:'آخر المستجدات',deadlines:'أقرب الآجال المعلنة',of:'من',unknown:'العدد الإجمالي ما تحددش بعد'},fr:{programme:'Voir le programme gouvernemental',campaign:'Partis et programmes',coverage:'Engagements examinés',updates:'Dernières mises à jour',deadlines:'Prochaines échéances annoncées',of:'sur',unknown:'Total pas encore établi'},en:{programme:'Read the government programme',campaign:'Parties and programmes',coverage:'Reviewed commitments',updates:'Latest updates',deadlines:'Upcoming announced deadlines',of:'of',unknown:'Total not yet established'}};
    function renderHome({mandate,topics=[],promises=[],parties=[],deadlines=[],deadlinesFailed=false,loadState='loading',topicsLoading=false,promisesLoading=false,topicsFailed=false,promisesFailed=false,locale='ar'},doc=document){
        const copy={...(COPY[locale]||COPY.ar),...(ASSESSMENT_COPY[locale]||ASSESSMENT_COPY.ar)};
        const text=(id,value)=>{const node=doc.getElementById(id);if(node)node.textContent=value;};
        const element=(tag,value,className)=>{const node=doc.createElement(tag);if(value!=null)node.textContent=value;if(className)node.className=className;return node;};
        const localized=value=>value?.[locale]||value?.ar||value?.fr||value?.en||'';
        const previousFocus=doc.activeElement,focusHref=previousFocus?.getAttribute?.('href');
        const tracking=TRACKING_COPY[locale]||TRACKING_COPY.ar;
        let state='pending';
        if(loadState==='loading')state='loading';
        else if(loadState==='unavailable')state='unavailable';
        else if(mandate?.verifiedAt && Number.isFinite(Date.parse(mandate.verifiedAt))){
            if(mandate.lifecycle==='FORMED_PROGRAMME_PENDING')state='formed';
            if(mandate.lifecycle==='PROGRAMME_PUBLISHED'&&mandate.programme)state='published';
        }
        text('mandateStatus',copy[state]);text('mandateExplanation',copy[state+'Text']||'');
        if(state==='published'&&!mandate?.reviewCoverage?.reviewed)text('mandateExplanation',copy.empty);
        const programmeLink=doc.getElementById('homeProgrammeLink');
        if(programmeLink){programmeLink.href=state==='published'?'/government?tab=programme&origin=GOVERNMENT':'/parties';programmeLink.textContent=tracking[state==='published'?'programme':'campaign'];}
        const coverage=doc.getElementById('mandateCoverage');if(coverage){coverage.hidden=state!=='published'||!mandate?.reviewCoverage;const value=mandate?.reviewCoverage;coverage.textContent=!coverage.hidden?`${tracking.coverage}: ${value.reviewed} ${value.total==null?'· '+tracking.unknown:tracking.of+' '+value.total}`:'';}
        for(const [id,rows,key]of [['homeUpdates',loadState==='ready'?mandate?.recentUpdates||[]:[],'updates'],['homeDeadlines',loadState==='ready'?deadlines:[],'deadlines']]){
            const section=doc.getElementById(id);if(!section)continue;section.replaceChildren();section.hidden=!rows.length&&!(key==='deadlines'&&deadlinesFailed);
            if(!section.hidden)section.append(element('h2',tracking[key]));
            if(key==='deadlines'&&deadlinesFailed)section.append(element('p',copy.unavailable));
            for(const row of rows.slice(0,3)){const item=element('article',null,'government-update'),a=element('a',localized(row.title));a.href=key==='deadlines'?'/government/commitments/'+encodeURIComponent(row.slug):row.subjectType==='COMMITMENT'?'/government/commitments/'+encodeURIComponent(row.subjectId):'/government';item.append(a);const timestamp=row.deadline||row.publishedAt;if(timestamp&&Number.isFinite(Date.parse(timestamp)))item.append(element('p',new Intl.DateTimeFormat(locale,{dateStyle:'medium',timeZone:'UTC'}).format(new Date(timestamp)),'government-meta'));section.append(item);}
        }
        const verified=doc.getElementById('mandateVerified');
        const verifiedDate=loadState==='ready'&&mandate?.verifiedAt&&Number.isFinite(Date.parse(mandate.verifiedAt))?new Date(mandate.verifiedAt):null;
        if(verified){verified.hidden=!verifiedDate;verified.textContent=verifiedDate?`${copy.verified}: ${new Intl.DateTimeFormat(locale,{dateStyle:'medium',timeZone:'UTC'}).format(verifiedDate)}`:'';}
        const retry=doc.getElementById('homeRetry');if(retry)retry.hidden=loadState!=='unavailable';
        const topicList=doc.getElementById('homeTopics');topicList.replaceChildren();
        if(topicsLoading)topicList.append(element('p',copy.loading));
        else if(topicsFailed)topicList.append(element('p',copy.topicsError));
        else topics.filter(topic=>!topic.parentCode&&topic.selectable).forEach(topic=>{const a=element('a',localized(topic.name||topic.label),'home-topic');a.href=`/government?tab=programme&origin=CAMPAIGN&topic=${encodeURIComponent(topic.code)}`;topicList.append(a);});
        const list=doc.getElementById('featuredPromises');list.replaceChildren();
        if(promisesLoading)list.append(element('p',copy.loading));
        else if(promisesFailed)list.append(element('p',copy.promisesError));
        else if(!promises.length)list.append(element('p',copy.empty));
        else promises.slice(0,3).forEach(promise=>{
            const article=element('article',null,'home-promise');
            const party=parties.find(p=>p.code===promise.partyCode);
            const partyName=(locale==='ar'?party?.nameAr:party?.nameFr)||party?.nameAr||party?.nameFr||copy.partyUnknown;
            const author=element('span',null,'home-promise-author');
            if(party?.symbolVerified&&typeof party.symbolAsset==='string'&&/^\/assets\/[\w/.-]+$/.test(party.symbolAsset)){
                const logo=element('img');logo.src=party.symbolAsset;logo.alt='';logo.width=32;logo.height=32;author.append(logo);
            }
            author.append(element('span',partyName));article.append(author);
            const title=element('h3');const link=element('a',localized(promise.title));link.href=`/promises/${encodeURIComponent(promise.slug)}`;title.append(link);article.append(title);
            const verdict=root.FhemniI18n?.t(`promise.verdict.${promise.verdict}`)||copy[promise.verdict]||copy.INSUFFICIENT_DATA;
            article.append(element('p',`${copy.feasibility}: ${verdict}`,'home-promise-assessment'));
            const rawSummary=localized(promise.assessmentSummary);
            const summary=root.FhemniCatalog?root.FhemniCatalog.assessmentSummary(rawSummary,promise.verdict):rawSummary;
            if(summary)article.append(element('p',summary));
            const action=element('a',copy.open,'text-link');action.href=link.href;article.append(action);list.append(article);
        });
        for(const key of ['topics','promises']){
            const retry=doc.getElementById(`${key}Retry`);
            if(retry){retry.hidden=key==='topics'?!topicsFailed:!promisesFailed;retry.disabled=key==='topics'?topicsLoading:promisesLoading;}
        }
        if(focusHref&&previousFocus?.isConnected===false){[...doc.querySelectorAll('main a')].find(link=>link.getAttribute('href')===focusHref)?.focus();}
    }
    function init(doc,win){
        let state={loadState:'loading',topicsLoading:true,promisesLoading:true,topics:[],promises:[],parties:[]};
        const render=()=>renderHome({...state,locale:win.FhemniI18n?.locale()||'ar'},doc);
        const refresh=win.FhemniGovernmentAPI.createLatestRequest(
            ()=>win.FhemniGovernmentAPI.fetchMandate(),
            (data,error)=>{state={...state,mandate:data,loadState:error?'unavailable':'ready'};render();});
        doc.getElementById('homeRetry').addEventListener('click',()=>{state.loadState='loading';render();refresh();});
        doc.addEventListener('fhemni:localechange',render);
        render();refresh();
        const load=async(path,key,transform=value=>value)=>{
            state[key+'Loading']=true;render();
            try{state[key]=transform(await win.FhemniGovernmentAPI.request(path));state[key+'Failed']=false;}
            catch{state[key+'Failed']=true;}
            state[key+'Loading']=false;
            render();
        };
        load('/api/catalog/policy-topics','topics',value=>value.topics);
        load('/api/catalog/promises?size=3','promises');
        load('/api/catalog/parties','parties');
        load('/api/catalog/government/2026-2031/deadlines','deadlines');
        doc.getElementById('topicsRetry').addEventListener('click',()=>load('/api/catalog/policy-topics','topics',value=>value.topics));
        doc.getElementById('promisesRetry').addEventListener('click',()=>load('/api/catalog/promises?size=3','promises'));
    }
    return {renderHome,init,COPY};
});
