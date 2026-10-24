(function(root,factory){
    const api=factory(root,typeof module==='object'&&module.exports?require('./government-cards.js'):root.FhemniGovernmentCards);
    if(typeof module==='object'&&module.exports)module.exports=api;
    else {root.FhemniHome=api;document.addEventListener('DOMContentLoaded',()=>api.init(document,root));}
})(typeof window==='object'?window:globalThis,function(root,cards){
    const COPY={
        ar:{pending:'تشكيل الحكومة قيد المتابعة',pendingText:'غادي نجمعو هنا المعطيات المؤكدة على الحكومة وبرنامجها، ونفصلوها على وعود الأحزاب.',formed:'الحكومة تشكّلات، والبرنامج قيد المتابعة',formedText:'التزامات البرنامج الحكومي غادي تبان من بعد النشر والمراجعة.',published:'البرنامج الحكومي متوفر',publishedText:'شوف الالتزامات اللي تراجعات والأدلة ديال التتبع.',unavailable:'تعذّر تحميل آخر معطيات التتبع',unavailableText:'عاود المحاولة، أو تصفّح برامج الأحزاب ونتائج الانتخابات.',loading:'كنحمّلو آخر المعطيات…',empty:'ما كايناش مراجعات منشورة دابا.',topicsError:'تعذّر تحميل المواضيع.',promisesError:'تعذّر تحميل مراجعات الوعود.',feasibility:'تقييم قابلية الإنجاز',POSSIBLE:'ممكن',HARD:'صعيب',NOT_ACHIEVABLE:'غير قابل للإنجاز',open:'شوف الوعد والمصادر'},
        fr:{pending:'Formation du gouvernement : suivi en préparation',pendingText:'Les informations confirmées sur le gouvernement et son programme seront présentées ici, séparément des promesses des partis.',formed:'Gouvernement formé, programme en attente',formedText:'Les engagements seront disponibles après publication et examen du programme.',published:'Programme gouvernemental disponible',publishedText:'Explorez les engagements examinés et les éléments du suivi.',unavailable:'Impossible de charger les dernières informations',unavailableText:'Réessayez ou consultez les programmes des partis et les résultats électoraux.',loading:'Chargement des dernières informations…',empty:'Aucune évaluation publiée pour le moment.',topicsError:'Impossible de charger les thèmes.',promisesError:'Impossible de charger les évaluations.',feasibility:'Évaluation de faisabilité',POSSIBLE:'Possible',HARD:'Difficile',NOT_ACHIEVABLE:'Non réalisable',open:'Voir la promesse et ses sources'},
        en:{pending:'Government formation: tracking in preparation',pendingText:'Confirmed information about the government and its programme will appear here, separately from party promises.',formed:'Government formed, programme pending',formedText:'Programme commitments will appear after publication and review.',published:'Government programme available',publishedText:'Explore reviewed commitments and the evidence behind their progress.',unavailable:'Latest tracking information could not be loaded',unavailableText:'Try again, or explore party programmes and election results.',loading:'Loading the latest information…',empty:'No published reviews yet.',topicsError:'Topics could not be loaded.',promisesError:'Promise reviews could not be loaded.',feasibility:'Feasibility assessment',POSSIBLE:'Possible',HARD:'Difficult',NOT_ACHIEVABLE:'Not achievable',open:'Read the promise and sources'}
    };
    const ASSESSMENT_COPY={
        ar:{feasibility:'قابلية الإنجاز فخمس سنين',INSUFFICIENT_DATA:'المعطيات ما كافياش',partyUnknown:'اسم الحزب غير متاح'},
        fr:{feasibility:'Faisabilité sur cinq ans',INSUFFICIENT_DATA:'Données insuffisantes',partyUnknown:'Parti indisponible'},
        en:{feasibility:'Five-year feasibility',INSUFFICIENT_DATA:'Insufficient data',partyUnknown:'Party unavailable'}
    };
    const TRACKING_COPY={ar:{programme:'شوف البرنامج الحكومي',campaign:'الأحزاب والبرامج',coverage:'الالتزامات المراجعة',updates:'آخر المستجدات',deadlines:'أقرب الآجال المعلنة',of:'من',unknown:'العدد الإجمالي ما تحددش بعد'},fr:{programme:'Voir le programme gouvernemental',campaign:'Partis et programmes',coverage:'Engagements examinés',updates:'Dernières mises à jour',deadlines:'Prochaines échéances annoncées',of:'sur',unknown:'Total pas encore établi'},en:{programme:'Read the government programme',campaign:'Parties and programmes',coverage:'Reviewed commitments',updates:'Latest updates',deadlines:'Upcoming announced deadlines',of:'of',unknown:'Total not yet established'}};
    const DISCOVERY_COPY={
        ar:{more:'جميع المواضيع',programmeEmpty:'مواد البرنامج غادي تبان هنا ملي تتوفر.',programmeError:'تعذّر تحميل مواد البرنامج.',research:'هاد المقتطفات من نسخة البرنامج اللي التحقق من مصدرها مازال جاري؛ ماشي دليل على الإنجاز.',unassessed:'الإنجاز ما تقيّمش بعد',details:'التفاصيل والتتبع',programme:'من البرنامج الحكومي'},
        fr:{more:'Tous les thèmes',programmeEmpty:'Les extraits apparaîtront ici dès leur disponibilité.',programmeError:'Impossible de charger les extraits du programme.',research:'Ces extraits proviennent d’une copie du programme dont la source reste à authentifier ; ils ne prouvent pas sa réalisation.',unassessed:'Réalisation non évaluée',details:'Détails et suivi',programme:'Dans le programme gouvernemental'},
        en:{more:'All topics',programmeEmpty:'Programme excerpts will appear here when available.',programmeError:'Programme excerpts could not be loaded.',research:'These excerpts come from a programme copy whose source is still being authenticated; they are not evidence of delivery.',unassessed:'Delivery not yet assessed',details:'Details and tracking',programme:'From the government programme'}
    };
    function renderHome({mandate,publications,topics=[],promises=[],parties=[],programme=[],programmeLoading=false,programmeFailed=false,deadlines=[],deadlinesFailed=false,loadState='loading',topicsLoading=false,promisesLoading=false,topicsFailed=false,promisesFailed=false,locale='ar'},doc=document){
        const copy={...(COPY[locale]||COPY.ar),...(ASSESSMENT_COPY[locale]||ASSESSMENT_COPY.ar)};
        const text=(id,value)=>{const node=doc.getElementById(id);if(node)node.textContent=value;};
        const element=(tag,value,className)=>{const node=doc.createElement(tag);if(value!=null)node.textContent=value;if(className)node.className=className;return node;};
        const localized=value=>value?.[locale]||value?.ar||value?.fr||value?.en||'';
        const discovery=DISCOVERY_COPY[locale]||DISCOVERY_COPY.ar;
        const excerpt=value=>value.length>160?value.slice(0,157).trimEnd()+'…':value;
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
        for(const [id,rows,key]of [['homeUpdates',loadState==='ready'?publications||mandate?.recentUpdates||[]:[],'updates'],['homeDeadlines',loadState==='ready'?deadlines:[],'deadlines']]){
            const section=doc.getElementById(id);if(!section)continue;section.replaceChildren();section.hidden=!rows.length&&!(key==='deadlines'&&deadlinesFailed);
            if(key==='updates'){
                if(!rows.length)continue;
                section.append(element('h2',cards.COPY[locale].publications),element('p',cards.COPY[locale].publicationNote,'programme-feature-collection-note'));
                const grid=element('div',null,'programme-feature-grid');cards.renderCards(grid,rows,{locale,document:doc,topics,mode:'updates'});section.append(grid);continue;
            }
            if(!section.hidden)section.append(element('h2',tracking[key]));
            if(key==='deadlines'&&deadlinesFailed)section.append(element('p',copy.unavailable));
            for(const row of rows.slice(0,3)){const item=element('article',null,'government-update'),a=element('a',localized(row.title));a.href=key==='deadlines'?'/government/commitments/'+encodeURIComponent(row.slug):row.subjectType==='COMMITMENT'?'/government/commitments/'+encodeURIComponent(row.subjectId):row.subjectType==='MEASURE'?'/government/measures/'+encodeURIComponent(row.subjectId):'/government';item.append(a);const timestamp=row.deadline||row.publishedAt;if(timestamp&&Number.isFinite(Date.parse(timestamp)))item.append(element('p',new Intl.DateTimeFormat(locale,{dateStyle:'medium',timeZone:'UTC'}).format(new Date(timestamp)),'government-meta'));section.append(item);}
        }
        const retry=doc.getElementById('homeRetry');if(retry)retry.hidden=loadState!=='unavailable';
        const topicList=doc.getElementById('homeTopics');topicList.replaceChildren();
        if(topicsLoading)topicList.append(element('p',copy.loading));
        else if(topicsFailed)topicList.append(element('p',copy.topicsError));
        const moreTopics=doc.getElementById('homeMoreTopics');moreTopics?.replaceChildren();
        const visibleTopics=topicsLoading||topicsFailed?[]:topics.filter(topic=>!topic.parentCode&&topic.selectable);
        visibleTopics.forEach((topic,index)=>{
            const a=element('a',null,'home-topic');
            a.append(element('span',localized(topic.name||topic.label)));
            const arrow=element('span','↗','home-topic-arrow');arrow.setAttribute('aria-hidden','true');a.append(arrow);
            a.href=`/government?tab=programme&origin=GOVERNMENT&topic=${encodeURIComponent(topic.code)}`;
            (index<6?topicList:moreTopics)?.append(a);
        });
        const disclosure=doc.getElementById('homeTopicsDisclosure');if(disclosure)disclosure.hidden=visibleTopics.length<=6;
        text('homeMoreTopicsLabel',discovery.more);
        const programmeCards=doc.getElementById('homeProgrammeCards');
        if(programmeCards){
            programmeCards.replaceChildren();
            if(programmeLoading)programmeCards.append(element('p',copy.loading));
            else if(programmeFailed)programmeCards.append(element('p',discovery.programmeError));
            else if(!programme.length)programmeCards.append(element('p',discovery.programmeEmpty));
            else cards.renderCards(programmeCards,programme,{locale,document:doc,topics});
        }
        const shuffle=doc.getElementById('homeShuffle');if(shuffle){shuffle.textContent=cards.COPY[locale].shuffle;shuffle.disabled=programmeLoading;}
        const note=doc.getElementById('homeProgrammeNote');if(note){note.hidden=programmeLoading||programmeFailed||!programme.slice(0,3).some(row=>row.programmeContext?.sourceStatus==='MIRROR_UNAUTHENTICATED');note.textContent=note.hidden?'':discovery.research;}
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
            if(summary)article.append(element('p',excerpt(summary)));
            const action=element('a',copy.open,'text-link');action.href=link.href;article.append(action);list.append(article);
        });
        for(const [key,failed,loading] of [['topics',topicsFailed,topicsLoading],['promises',promisesFailed,promisesLoading],['programme',programmeFailed,programmeLoading]]){
            const retry=doc.getElementById(`${key}Retry`);
            if(retry){retry.hidden=!failed;retry.disabled=loading;}
        }
        if(focusHref&&previousFocus?.isConnected===false){[...doc.querySelectorAll('main a')].find(link=>link.getAttribute('href')===focusHref)?.focus();}
    }
    function init(doc,win){
        let state={loadState:'loading',topicsLoading:true,promisesLoading:true,programmeLoading:true,topics:[],promises:[],parties:[],programme:[]};
        const render=()=>renderHome({...state,locale:win.FhemniI18n?.locale()||'ar'},doc);
        const refresh=win.FhemniGovernmentAPI.createLatestRequest(
            async()=>{const mandate=await win.FhemniGovernmentAPI.fetchMandate();const publications=await cards.loadUpdates(win.FhemniGovernmentAPI,mandate.recentUpdates||[]);return {mandate,publications};},
            (data,error)=>{state={...state,mandate:data?.mandate,publications:data?.publications,loadState:error?'unavailable':'ready'};render();});
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
        const loadProgramme=async()=>{if(state.programmeLoading&&state.programmeRequested)return;state.programmeRequested=true;state.programmeLoading=true;render();try{state.programme=await cards.loadProgramme(win.FhemniGovernmentAPI);state.programmeFailed=false;}catch{state.programmeFailed=true;}state.programmeLoading=false;render();};
        loadProgramme();
        load('/api/catalog/government/2026-2031/deadlines','deadlines');
        doc.getElementById('topicsRetry').addEventListener('click',()=>load('/api/catalog/policy-topics','topics',value=>value.topics));
        doc.getElementById('promisesRetry').addEventListener('click',()=>load('/api/catalog/promises?size=3','promises'));
        doc.getElementById('programmeRetry')?.addEventListener('click',loadProgramme);
        doc.getElementById('homeShuffle')?.addEventListener('click',loadProgramme);
    }
    return {renderHome,init,COPY};
});
