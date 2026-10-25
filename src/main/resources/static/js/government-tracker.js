(function(root,factory){const api=factory(root,typeof module==='object'?require('./government-model.js'):root.FhemniGovernmentModel,typeof module==='object'?require('./government-visuals.js'):root.FhemniGovernmentVisuals,typeof module==='object'?require('./government-cards.js'):root.FhemniGovernmentCards);if(typeof module==='object'&&module.exports)module.exports=api;else{root.FhemniGovernmentTracker=api;document.addEventListener('DOMContentLoaded',api.init);}})(typeof window==='object'?window:globalThis,function(root,model,visuals,cards){
    function renderTracker(view,state,doc=globalThis.document){
        const locale=view.locale||'ar',copy=model.COPY[locale],local=value=>model.localized(value,locale);
        const el=(tag,text,className)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;if(className)node.className=className;return node;};
        for(const tab of ['overview','programme','delivery','regions'])doc.getElementById('tracker'+tab[0].toUpperCase()+tab.slice(1)).hidden=tab!==state.tab;
        for(const [id,key]of Object.entries({tabOverview:'overview',tabProgramme:'programme',tabDelivery:'delivery',tabRegions:'regions',trackerFilterTitle:'filters',originLabel:'origin',topicLabel:'topic',partyLabel:'party',trackerRetry:'retry',trackerSearchLabel:'search',trackerSearchSubmit:'searchSubmit',assessmentLabel:'assessment'}))doc.getElementById(id).textContent=copy[key];
        const regionalProgramme=state.tab==='regions'&&state.regionalView!=='measurements';
        doc.getElementById('trackerSearchForm').hidden=!['programme','delivery'].includes(state.tab)&&!regionalProgramme;
        doc.getElementById('trackerAssessmentField').hidden=state.tab==='regions'||state.tab==='programme'&&state.origin==='CAMPAIGN';
        doc.getElementById('trackerFilters').hidden=!['programme','delivery'].includes(state.tab)&&!regionalProgramme;
        doc.getElementById('trackerTopicMethod').textContent=copy.topicMethod;
        doc.getElementById('trackerOriginField').hidden=state.tab==='delivery'||state.tab==='regions';
        doc.getElementById('trackerPartyField').hidden=state.tab!=='programme'||state.origin!=='CAMPAIGN';
        doc.getElementById('trackerRegionField').hidden=state.tab!=='regions';
        doc.getElementById('trackerRegionalControls').hidden=state.tab!=='regions';
        doc.getElementById('trackerRetry').hidden=view.loadState!=='error';
        const preview=doc.getElementById('trackerPreview');preview.replaceChildren();preview.hidden=!view.preview?.localPreview;
        if(view.preview?.localPreview){
            const disclosure=el('details');disclosure.append(el('summary',copy.sources),el('p',copy.previewNote,'government-meta'));
            const links=el('div',null,'public-toolbar'),source=model.sourceUrl(view.preview.publicSourceUrl);if(source){const link=el('a',copy.officialSource,'text-link');link.href=source;links.append(link);}
            if(view.preview.pdfUrl==='/api/catalog/government/2026-2031/preview/pdf'){const link=el('a',copy.previewPdf,'text-link');link.href=view.preview.pdfUrl;links.append(link);}disclosure.append(links);preview.append(disclosure);
        }
        const overview=doc.getElementById('trackerOverview');overview.replaceChildren();
        const title=view.preview?.localPreview?copy.browseProgramme:view.mandate?.verifiedAt?(view.mandate.lifecycle==='PROGRAMME_PUBLISHED'?copy.published:view.mandate.lifecycle==='FORMED_PROGRAMME_PENDING'?copy.formed:copy.pending):copy.pending;
        const panel=el('section',null,'public-panel public-stack');panel.append(el('h2',view.loadState==='ready'?title:copy[view.loadState==='error'?'error':'loading']));
        if(view.loadState==='loading')panel.append(el('p',copy.loading));
        else if(view.loadState==='error')panel.append(el('p',copy.error));
        else if(view.preview?.localPreview||view.mandate?.programme)panel.append(el('p',copy.overviewIntro));
        else panel.append(el('p',view.mandate?.lifecycle==='FORMATION'?copy.pendingText:copy.programmePending));
        if(view.loadState==='ready'&&view.mandate?.verifiedAt&&!view.preview?.localPreview)panel.append(el('p',`${copy.verified}: ${model.date(view.mandate.verifiedAt,locale)}`,'government-meta'));
        const coverage=view.mandate?.reviewCoverage;
        if(view.loadState==='ready'&&coverage&&!view.preview?.localPreview)panel.append(el('p',`${copy.coverage}: ${model.number(coverage.reviewed,locale)} ${coverage.total==null?'· '+copy.unknownTotal:copy.of+' '+model.number(coverage.total,locale)}`));
        if(view.loadState==='ready'){const actions=el('div',null,'government-overview-actions');for(const [label,href]of [[copy.browseProgramme,'/government?tab=programme&origin=GOVERNMENT'],[copy.ministers,'/government/ministers']]){const link=el('a',label,'primary-button');link.href=href;actions.append(link);}panel.append(actions);}
        const campaign=el('a',copy.readProgramme,'text-link');campaign.href='/government?tab=programme&origin=CAMPAIGN';panel.append(campaign);overview.append(panel);
        if(view.loadState==='ready'&&view.mandate?.verifiedAt){const history=el('a',copy.history,'text-link');history.href='/government/history';panel.append(history);}
        const evidenceIds=[...new Set([...(view.mandate?.programme?.evidenceIds||[]),...(view.mandate?.membership||[]).flatMap(member=>member.evidenceIds||[])])];
        if(view.loadState==='ready'&&view.mandate?.verifiedAt&&evidenceIds.length){
            const disclosure=el('details'),body=el('div',null,'public-stack');disclosure.id='trackerSources';disclosure.append(el('summary',copy.sources),body);let loaded=false,pending=false;
            async function loadSources(){if(loaded||pending)return;pending=true;body.replaceChildren(el('p',copy.loading));try{
                const sources=await Promise.all(evidenceIds.map(id=>root.FhemniGovernmentAPI.request('/api/catalog/government/2026-2031/sources/'+encodeURIComponent(id))));body.replaceChildren();
                for(const source of sources){const article=el('article',null,'government-evidence'),url=model.sourceUrl(source.url);if(url){const a=el('a',source.locator?copy.evidenceLink+' · '+source.locator:copy.evidenceLink);a.href=url;a.rel='noopener noreferrer';article.append(a);}const researchNote=!!view.preview?.localPreview;if(researchNote)article.append(el('p',copy.sourceResearchNote,'government-meta'));const passage=el(researchNote?'p':'blockquote',source.passage);passage.setAttribute('lang',researchNote?'en':source.originalLanguage||'ar');passage.setAttribute('dir','auto');article.append(passage,el('p',`${copy.sourceDate}: ${model.date(source.publishedAt,locale)} · ${copy.retrieved}: ${model.date(source.retrievedAt,locale)}`,'government-meta'));body.append(article);}loaded=true;
            }catch{const retry=el('button',copy.retry);retry.type='button';retry.onclick=loadSources;body.replaceChildren(el('p',copy.error),retry);}finally{pending=false;}}
            disclosure.addEventListener('toggle',()=>{if(disclosure.open)return loadSources();});overview.append(disclosure);
        }
        if(view.loadState==='ready'){
            if(view.programmePicks?.length||view.picksFailed){
                const picks=el('section'),heading=el('div',null,'programme-feature-heading'),shuffle=el('button',cards.COPY[locale][view.picksFailed?'retry':'shuffle']),grid=el('div',null,'programme-feature-grid'),feedback=el('p',view.picksFailed?cards.COPY[locale].failed:'','programme-feature-collection-note');feedback.setAttribute('role','status');feedback.hidden=!view.picksFailed;grid.setAttribute('aria-live','polite');shuffle.type='button';shuffle.id='trackerRefreshPicks';
                shuffle.onclick=async()=>{if(shuffle.disabled)return;if(!view.onShuffle)return view.onFilter?.(state);shuffle.disabled=true;grid.setAttribute('aria-busy','true');try{view.programmePicks=await view.onShuffle();cards.renderCards(grid,view.programmePicks,{locale,document:doc,topics:view.topics||[]});feedback.hidden=true;feedback.textContent='';shuffle.textContent=cards.COPY[locale].shuffle;}catch{feedback.hidden=false;feedback.textContent=cards.COPY[locale].failed;}finally{shuffle.disabled=false;grid.setAttribute('aria-busy','false');}};
                heading.append(el('h2',cards.COPY[locale].picks),shuffle);picks.append(heading,feedback,grid);
                if(view.programmePicks?.length)cards.renderCards(grid,view.programmePicks,{locale,document:doc,topics:view.topics||[]});overview.append(picks);
            }
            const updates=el('section',null,'public-stack');updates.append(el('h2',copy.publications),el('p',copy.publicationsNote,'government-meta'));
            const publications=view.publications||view.mandate?.recentUpdates||[];
            if(!publications.length)updates.append(el('p',copy.noUpdates));
            else {const grid=el('div',null,'programme-feature-grid');cards.renderCards(grid,publications,{locale,document:doc,topics:view.topics||[],mode:'updates',limit:6});updates.append(grid);}
            overview.append(updates);
            if(root.FhemniMeasures){const measures=el('section',null,'public-stack'),copy=root.FhemniMeasures.COPY[locale],link=el('a',copy.title,'primary-button');link.href='/government/measures';measures.append(link,el('p',copy.intro));overview.append(measures);}
        }
        for(const tab of ['programme','delivery']){
            const content=doc.getElementById('tracker'+tab[0].toUpperCase()+tab.slice(1));content.replaceChildren();
            if(tab!==state.tab)continue;
            if(view.loadState!=='ready'){content.append(el('p',copy[view.loadState==='error'?'error':'loading']));continue;}
            if(tab==='programme'&&state.origin==='CAMPAIGN')content.append(el('p',copy.campaignNote,'government-note'));
            if(tab==='delivery')content.append(el('p',copy.deliveryNote,'government-note'));
            if(view.insights&&(tab==='delivery'||state.origin!=='CAMPAIGN')){const graphic=el('section',null,'government-visuals');visuals.renderProgramme(graphic,view.insights,{locale,document:doc,state,topics:view.topics||[],onFilter:view.onFilter});content.append(graphic);}
            else if(view.insightsFailed&&(tab==='delivery'||state.origin!=='CAMPAIGN')){const note=el('p',visuals.COPY[locale].unavailable,'government-meta'),retry=el('button',copy.retry);retry.type='button';retry.onclick=()=>view.onFilter?.(state);content.append(note,retry);}
            const rows=view.page?.items||[];
            const grid=el('div',null,'government-card-grid');
            if(!rows.length){content.append(el('p',tab==='delivery'?copy.deliveryEmpty:copy.empty));if(state.origin==='GOVERNMENT'&&!view.mandate?.programme&&!view.preview?.localPreview)content.append(el('p',copy.programmePending));}
            for(const row of rows.slice(0,10)){
                const card=el('article',null,'public-panel government-commitment-card');
                const heading=el('h2'),link=el('a',local(row.title));link.href=row.origin==='CAMPAIGN'?'/promises/'+encodeURIComponent(row.slug.replace(/^campaign-/,'')):'/government/commitments/'+encodeURIComponent(row.slug);heading.append(link);
                const titleLanguage=model.titleLanguage(row,locale);link.setAttribute('lang',titleLanguage);link.setAttribute('dir',titleLanguage==='ar'?'rtl':'auto');
                if(row.origin==='CAMPAIGN'){
                    const party=(view.parties||[]).find(party=>party.code===row.author?.en),author=el('p',null,'home-promise-author');
                    if(party?.symbolVerified&&/^\/assets\/[\w/.-]+$/.test(party.symbolAsset||'')){const image=el('img');image.src=party.symbolAsset;image.alt='';image.width=image.height=32;author.append(image);}
                    author.append(el('span',(locale==='ar'?party?.nameAr:party?.nameFr)||party?.nameAr||copy.campaign));card.append(author);
                }
                card.append(heading);
                if(titleLanguage==='ar'&&locale!=='ar')card.append(el('p',copy.originalArabic,'government-meta'));
                if(row.programmeContext){const context=row.programmeContext;if(context.printedPages?.length)card.append(el('p',`${copy.printedPages}: ${context.printedPages.join('، ')}`,'government-meta'));}
                if(row.origin==='GOVERNMENT'){
                    const badge=el('p',copy[model.assessmentState(row)]||copy.UNASSESSED,'government-status');badge.setAttribute('data-state',model.assessmentState(row));card.append(badge);
                    if(row.deliveryAssessment?.evidenceConfidence)card.append(el('p',copy[row.deliveryAssessment.evidenceConfidence],'government-meta'));
                    if(row.deadline)card.append(el('p',`${copy.deadline}: ${model.date(row.deadline,locale)} · ${copy[model.deriveDeadline(row.deadline,row.deliveryAssessment)]}`));
                    if(row.geographicScope)card.append(el('p',`${copy.scope}: ${local(row.geographicScope.label)}`,'government-meta'));
                }
                grid.append(card);
            }
            if(rows.length)content.append(grid);
        }
        const regions=doc.getElementById('trackerRegions');if(state.tab==='regions'&&view.loadState==='ready'&&root.FhemniRegions){if(regionalProgramme)root.FhemniRegions.renderProgramme(regions,view.page,{locale,document:doc,state,onFilter:view.onFilter});else root.FhemniRegions.render(regions,view.page,locale,doc);}else regions.replaceChildren(el('p',view.loadState==='ready'?copy.regionalEmpty:copy[view.loadState==='error'?'error':'loading']));
    }
    function createTrackerNavigation({load,render,history,url}){
        let version=0,controller;
        return {async go(next,push=false){const state=model.readTrackerState(next),current=++version;controller?.abort();controller=new AbortController();if(push)history.pushState({},'',model.trackerUrl(url(),state));render({loadState:'loading'},state);try{const view=await load(state,{signal:controller.signal});if(current===version)render({...view,loadState:'ready'},state);}catch(error){if(current===version&&error.name!=='AbortError')render({loadState:'error',error},state);}return state;}};
    }
    async function loadTrackerView(api,state,options){
        const filtered={...state,origin:state.tab==='delivery'?'GOVERNMENT':state.origin,party:state.tab==='delivery'?'':state.party,assessment:state.tab==='programme'&&state.origin==='CAMPAIGN'?'':state.assessment};
        const listTab=['programme','delivery'].includes(state.tab);
        const regionalPath=state.regionalView==='measurements'?'observations?'+new URLSearchParams({region:state.region||'',page:state.page}):'regional-programme?'+new URLSearchParams({region:state.region||'',topic:state.topic||'',q:state.q||'',page:state.page});
        const [mandate,page,preview,graphic]=await Promise.all([api.fetchMandate(options),state.tab==='regions'?api.request('/api/catalog/government/2026-2031/'+regionalPath,options):listTab?api.fetchCommitments(filtered,options):null,api.fetchPreview(options),listTab&&filtered.origin!=='CAMPAIGN'?api.fetchInsights(filtered,options).then(insights=>({insights})).catch(error=>{if(error.name==='AbortError')throw error;return {insightsFailed:true};}):{}]);
        let discovery={};
        if(state.tab==='overview'){
            const [picks,publications]=await Promise.all([cards.loadProgramme(api,options).then(programmePicks=>({programmePicks})).catch(error=>{if(error.name==='AbortError')throw error;return {picksFailed:true};}),cards.loadUpdates(api,mandate.recentUpdates||[],{...options,limit:6})]);
            discovery={...picks,publications};
        }
        return {mandate,page,preview,...graphic,...discovery};
    }
    async function loadFilterMetadata(request){const results=await Promise.allSettled([request('/api/catalog/parties'),request('/api/catalog/policy-topics')]);return {parties:results[0].status==='fulfilled'?results[0].value:[],topics:results[1].status==='fulfilled'?results[1].value.topics:[],failed:results.flatMap((result,index)=>result.status==='rejected'?[index?'topics':'parties']:[])};}
    function updatePagerBusy(host,tab,loadState,hasPager){host.hidden=!['programme','delivery','regions'].includes(tab)||!hasPager;host.setAttribute('aria-busy',String(loadState==='loading'));if(loadState==='loading')for(const button of host.querySelectorAll('button'))button.disabled=true;}
    function captureTrackerUi(doc,tabs,tab){const saved={sourcesOpen:!!doc.getElementById('trackerSources')?.open,chip:/^tracker(?:Chip-|TopicBar-|RegionBar-|RefreshPicks)/.test(doc.activeElement?.id||'')?doc.activeElement.id:null};tabs.select('tab'+tab[0].toUpperCase()+tab.slice(1));return saved;}
    function restoreTrackerUi(doc,saved){const sources=doc.getElementById('trackerSources');if(sources)sources.open=saved.sourcesOpen;if(saved.chip)(doc.getElementById(saved.chip)||doc.getElementById('trackerFilterTitle'))?.focus();}
    function createTopicFocus(){let pending=null;return {
        capture(doc){const id=doc.activeElement?.id;if(/^tracker(?:TopicBar-|RegionBar-)/.test(id||''))pending=id;else if(doc.activeElement&&doc.activeElement!==doc.body&&!['trackerFilterTitle','trackerRegion'].includes(id))pending=null;},
        restore(doc,loadState){if(!pending)return;const regional=pending.startsWith('trackerRegionBar-'),fallback=regional?'trackerRegion':'trackerFilterTitle';const target=loadState==='ready'?doc.getElementById(pending)||(!regional&&doc.getElementById('trackerChip-topic')):null;(target||doc.getElementById(fallback))?.focus();if(loadState!=='loading')pending=null;}
    };}
    function catalogState(state,origin,catalog){return {...state,origin,topic:catalog.some(row=>row.code===state.topic)?state.topic:'',party:'',assessment:'',page:1};}
    function init(){
        const doc=root.document;if(!doc.getElementById('trackerTabs'))return;
        let state=model.readTrackerState(root.location.href),view={loadState:'loading'},parties=[],topics=[],campaignTopics=[],programmeTopics=[],regions=[],pager,metadataFailed=[];const topicFocus=createTopicFocus();
        const locale=()=>root.FhemniI18n?.locale()||'ar';
        const tabs=root.FhemniUI.mountTabs(doc.getElementById('trackerTabs'),{selected:'tab'+state.tab[0].toUpperCase()+state.tab.slice(1),onChange:id=>{const tab=id.slice(3).toLowerCase(),next={...state,tab,page:1};go(['delivery','regions'].includes(tab)?catalogState(next,'GOVERNMENT',programmeTopics):next);}});
        function render(next,current){topicFocus.capture(doc);const focused=doc.activeElement,href=focused?.getAttribute?.('href'),savedUi=captureTrackerUi(doc,tabs,current.tab);view=next;state=current;topics=state.tab==='programme'&&state.origin==='CAMPAIGN'?campaignTopics:programmeTopics;renderTracker({...next,parties,topics,onFilter:go,onShuffle:async()=>{const picks=await cards.loadProgramme(root.FhemniGovernmentAPI);next.programmePicks=picks;next.picksFailed=false;return picks;},locale:locale()},state,doc);
            const paging=doc.getElementById('trackerPager');
            if(next.page){const pages=Math.max(1,Math.ceil(next.page.totalItems/10));if(state.page>pages){state={...state,page:pages};root.history.replaceState({},'',model.trackerUrl(root.location.href,state));nav.go(root.location.href,false);return;}const data={page:state.page,pages,total:next.page.totalItems,locale:locale()};if(!pager){pager=root.FhemniUI.createPager({...data,onChange:page=>go({...state,page})});paging.append(pager);}else pager.update(data);}
            updatePagerBusy(paging,state.tab,next.loadState,Boolean(pager));
            fillOptions();
            doc.getElementById('trackerOrigin').value=state.origin;doc.getElementById('trackerTopic').value=state.topic;doc.getElementById('trackerParty').value=state.party;
            fillRegions();doc.getElementById('trackerRegion').value=state.region;
            fillRegionalModes();doc.getElementById('trackerRegionalView').value=state.regionalView;
            doc.getElementById('trackerSearch').value=state.q;doc.getElementById('trackerAssessment').value=state.assessment;
            renderFilterState();
            const follow=doc.getElementById('trackerFollow'),target=root.FhemniFollowing?.contextTarget(state);follow.hidden=!target;if(target)root.FhemniFollowControls.mount(follow,{...target,label:target.type==='REGION'?regions.find(row=>row.code===target.id)?.label:null});
            restoreTrackerUi(doc,savedUi);
            topicFocus.restore(doc,next.loadState);
            if(href&&!focused.isConnected)Array.from(doc.querySelectorAll('a[href]')).find(a=>a.getAttribute('href')===href)?.focus();
            root.FhemniUI.announceStatus(doc.getElementById('trackerStatus'),next.loadState==='ready'?(next.page?root.FhemniUI.countPhrase(next.page.totalItems,'result',locale()):model.COPY[locale()][state.tab]):model.COPY[locale()][next.loadState==='error'?'error':'loading']);
        }
        const nav=createTrackerNavigation({load:(state,options)=>loadTrackerView(root.FhemniGovernmentAPI,state,options),render,history:root.history,url:()=>root.location.href});
        function go(next){nav.go(model.trackerUrl(root.location.href,next),true);}
        function renderFilterState(){const copy=model.COPY[locale()],host=doc.getElementById('trackerActiveFilters');host.replaceChildren();host.hidden=!['programme','delivery'].includes(state.tab)&&!(state.tab==='regions'&&state.regionalView!=='measurements');
            for(const key of state.tab==='regions'?['topic']:['topic','party'])if(state[key]){const row=key==='topic'?topics.find(row=>row.code===state[key]):parties.find(row=>row.code===state[key]),button=doc.createElement('button');button.id='trackerChip-'+key;button.type='button';button.className='government-filter-chip';button.textContent=`${copy[key]}: ${key==='topic'?topicLabel(row||{code:state[key]}):(locale()==='ar'?row?.nameAr:row?.nameFr)||state[key]} ×`;button.onclick=()=>go({...state,[key]:'',page:1});host.append(button);}
            if(state.q||state.assessment){for(const key of state.tab==='regions'?['q']:['q','assessment'])if(state[key]){const button=doc.createElement('button');button.id='trackerChip-'+key;button.type='button';button.className='government-filter-chip';button.textContent=(key==='q'?state.q:copy[state.assessment])+' ×';button.setAttribute('aria-label',copy.clear+': '+button.textContent);button.onclick=()=>go({...state,[key]:'',page:1});host.append(button);}}
            if(state.topic||state.party||state.q||state.assessment){const clear=doc.createElement('button');clear.id='trackerChip-clear';clear.type='button';clear.className='secondary-button';clear.textContent=copy.clear;clear.onclick=()=>go({...state,topic:'',party:'',q:'',assessment:'',page:1});host.append(clear);}
            const error=doc.getElementById('trackerFilterError');error.hidden=!metadataFailed.length;error.textContent=copy.filterError;const retry=doc.getElementById('trackerFilterRetry');retry.hidden=!metadataFailed.length;retry.textContent=copy.retry;
        }
        async function metadata(){const retry=doc.getElementById('trackerFilterRetry');retry.disabled=true;try{const [loaded,geo,catalog]=await Promise.all([loadFilterMetadata(root.FhemniGovernmentAPI.request),root.FhemniGovernmentAPI.request('/api/catalog/government/regions').then(rows=>({rows})).catch(()=>({failed:true})),root.FhemniGovernmentAPI.request('/api/catalog/government/2026-2031/programme-topics').then(rows=>({rows})).catch(()=>({failed:true}))]);if(!loaded.failed.includes('parties'))parties=loaded.parties;if(!loaded.failed.includes('topics'))campaignTopics=loaded.topics;if(!geo.failed)regions=geo.rows;if(!catalog.failed)programmeTopics=catalog.rows.map(row=>({...row,selectable:true}));metadataFailed=[...loaded.failed,...geo.failed?['regions']:[],...catalog.failed?['programmeTopics']:[]];fillRegions();render(view,state);}finally{retry.disabled=false;}}
        function fillRegionalModes(){const copy=root.FhemniRegions.PROGRAMME_COPY[locale()];doc.getElementById('regionalViewLabel').textContent=copy.mode;const select=doc.getElementById('trackerRegionalView');select.replaceChildren();for(const value of ['programme','measurements']){const option=doc.createElement('option');option.value=value;option.textContent=copy[value];select.append(option);}}
        function fillRegions(){const copy=root.FhemniRegions.COPY[locale()];doc.getElementById('regionLabel').textContent=copy.region;const select=doc.getElementById('trackerRegion');select.replaceChildren();const all=state.regionalView==='measurements'?copy.all:root.FhemniRegions.PROGRAMME_COPY[locale()].all;for(const row of [{code:'',label:all},...regions]){const option=doc.createElement('option');option.value=row.code;option.textContent=model.localized(row.label,locale());select.append(option);}}
        function topicLabel(row){return state.tab!=='programme'||state.origin!=='CAMPAIGN'?model.programmeTopicLabel(row,locale()):model.localized(row?.label,locale())||row?.code;}
        function fillOptions(){const copy=model.COPY[locale()];for(const [id,rows,getValue,getText]of [['trackerOrigin',[{code:'GOVERNMENT',label:copy.government},{code:'CAMPAIGN',label:copy.campaign}],row=>row.code,row=>row.label],['trackerTopic',[{code:'',label:copy.all},...topics.filter(row=>!row.parentCode&&row.selectable)],row=>row.code,topicLabel],['trackerParty',[{code:'',nameAr:copy.all,nameFr:copy.all},...parties],row=>row.code,row=>locale()==='ar'?row.nameAr:row.nameFr]]){const select=doc.getElementById(id);select.replaceChildren();for(const row of rows){const option=doc.createElement('option');option.value=getValue(row);option.textContent=getText(row);select.append(option);}}}
        function fillAssessments(){const select=doc.getElementById('trackerAssessment'),copy=model.COPY[locale()];select.replaceChildren();for(const value of ['','UNASSESSED','NO_EVIDENCE_FOUND','UNDERWAY','PARTLY_DELIVERED','DELIVERED','WITHDRAWN']){const option=doc.createElement('option');option.value=value;option.textContent=value?copy[value]:copy.all;select.append(option);}}
        doc.getElementById('trackerSearchForm').onsubmit=event=>{event.preventDefault();go({...state,q:doc.getElementById('trackerSearch').value.trim(),page:1});};
        doc.getElementById('trackerAssessment').onchange=event=>go({...state,assessment:event.target.value,page:1});
        doc.getElementById('trackerRegionalView').onchange=event=>go({...state,regionalView:event.target.value,page:1});
        doc.getElementById('trackerOrigin').onchange=event=>go(catalogState(state,event.target.value,event.target.value==='CAMPAIGN'?campaignTopics:programmeTopics));doc.getElementById('trackerTopic').onchange=event=>go({...state,topic:event.target.value,page:1});doc.getElementById('trackerParty').onchange=event=>go({...state,party:event.target.value,page:1});doc.getElementById('trackerRetry').onclick=()=>nav.go(root.location.href,false);
        root.addEventListener('popstate',()=>nav.go(root.location.href,false));doc.addEventListener('fhemni:localechange',()=>{fillOptions();fillRegions();fillAssessments();render(view,state);});
        if(root.matchMedia?.('(max-width: 599px)').matches)doc.getElementById('trackerFilters').open=false;
        doc.getElementById('trackerRegion').onchange=event=>go({...state,region:event.target.value,page:1});doc.getElementById('trackerFilterRetry').onclick=metadata;fillOptions();fillRegions();fillAssessments();nav.go(root.location.href,false);metadata();
    }
    return {renderTracker,createTrackerNavigation,loadTrackerView,loadFilterMetadata,updatePagerBusy,captureTrackerUi,restoreTrackerUi,createTopicFocus,catalogState,init};
});
