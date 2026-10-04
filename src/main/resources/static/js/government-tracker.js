(function(root,factory){const api=factory(root,typeof module==='object'?require('./government-model.js'):root.FhemniGovernmentModel);if(typeof module==='object'&&module.exports)module.exports=api;else{root.FhemniGovernmentTracker=api;document.addEventListener('DOMContentLoaded',api.init);}})(typeof window==='object'?window:globalThis,function(root,model){
    function renderTracker(view,state,doc=globalThis.document){
        const locale=view.locale||'ar',copy=model.COPY[locale],local=value=>model.localized(value,locale);
        const el=(tag,text,className)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;if(className)node.className=className;return node;};
        for(const tab of ['overview','programme','delivery','regions'])doc.getElementById('tracker'+tab[0].toUpperCase()+tab.slice(1)).hidden=tab!==state.tab;
        for(const [id,key]of Object.entries({tabOverview:'overview',tabProgramme:'programme',tabDelivery:'delivery',tabRegions:'regions',trackerFilterTitle:'filters',originLabel:'origin',topicLabel:'topic',partyLabel:'party',trackerRetry:'retry'}))doc.getElementById(id).textContent=copy[key];
        doc.getElementById('trackerFilters').hidden=!['programme','delivery'].includes(state.tab);
        doc.getElementById('trackerOriginField').hidden=state.tab==='delivery';
        doc.getElementById('trackerPartyField').hidden=state.tab==='delivery'||state.origin!=='CAMPAIGN';
        doc.getElementById('trackerRegionField').hidden=state.tab!=='regions';
        doc.getElementById('trackerRetry').hidden=view.loadState!=='error';
        const overview=doc.getElementById('trackerOverview');overview.replaceChildren();
        const title=view.mandate?.verifiedAt?(view.mandate.lifecycle==='PROGRAMME_PUBLISHED'?copy.published:view.mandate.lifecycle==='FORMED_PROGRAMME_PENDING'?copy.formed:copy.pending):copy.pending;
        const panel=el('section',null,'public-panel public-stack');panel.append(el('h2',view.loadState==='ready'?title:copy[view.loadState==='error'?'error':'loading']));
        if(view.loadState==='loading')panel.append(el('p',copy.loading));
        else if(view.loadState==='error')panel.append(el('p',copy.error));
        else if(!view.mandate?.programme)panel.append(el('p',view.mandate?.lifecycle==='FORMATION'?copy.pendingText:copy.programmePending));
        if(view.loadState==='ready'&&view.mandate?.verifiedAt)panel.append(el('p',`${copy.verified}: ${model.date(view.mandate.verifiedAt,locale)}`,'government-meta'));
        const coverage=view.mandate?.reviewCoverage;
        if(view.loadState==='ready'&&coverage)panel.append(el('p',`${copy.coverage}: ${model.number(coverage.reviewed,locale)} ${coverage.total==null?'· '+copy.unknownTotal:copy.of+' '+model.number(coverage.total,locale)}`));
        const campaign=el('a',copy.readProgramme,'text-link');campaign.href='/government?tab=programme&origin=CAMPAIGN';panel.append(campaign);overview.append(panel);
        const evidenceIds=[...new Set([...(view.mandate?.programme?.evidenceIds||[]),...(view.mandate?.membership||[]).flatMap(member=>member.evidenceIds||[])])];
        if(view.loadState==='ready'&&view.mandate?.verifiedAt&&evidenceIds.length){
            const disclosure=el('details'),body=el('div',null,'public-stack');disclosure.id='trackerSources';disclosure.append(el('summary',copy.sources),body);let loaded=false,pending=false;
            async function loadSources(){if(loaded||pending)return;pending=true;body.replaceChildren(el('p',copy.loading));try{
                const sources=await Promise.all(evidenceIds.map(id=>root.FhemniGovernmentAPI.request('/api/catalog/government/2026-2031/sources/'+encodeURIComponent(id))));body.replaceChildren();
                for(const source of sources){const article=el('article',null,'government-evidence'),url=model.sourceUrl(source.url);if(url){const a=el('a',source.locator?copy.evidenceLink+' · '+source.locator:copy.evidenceLink);a.href=url;a.rel='noopener noreferrer';article.append(a);}const passage=el('blockquote',source.passage);passage.setAttribute('lang',source.originalLanguage||'ar');passage.setAttribute('dir',source.originalLanguage==='ar'?'rtl':'ltr');article.append(passage,el('p',`${copy.sourceDate}: ${model.date(source.publishedAt,locale)} · ${copy.retrieved}: ${model.date(source.retrievedAt,locale)}`,'government-meta'));body.append(article);}loaded=true;
            }catch{const retry=el('button',copy.retry);retry.type='button';retry.onclick=loadSources;body.replaceChildren(el('p',copy.error),retry);}finally{pending=false;}}
            disclosure.addEventListener('toggle',()=>{if(disclosure.open)loadSources();});overview.append(disclosure);
        }
        if(view.loadState==='ready'&&view.mandate?.verifiedAt&&view.mandate.membership?.length){const section=el('section',null,'public-stack');section.append(el('h2',copy.membership));for(const member of view.mandate.membership){const item=el('p');item.append(el('strong',local(member.name)),el('span',` · ${local(member.role)} · ${model.date(member.effectiveFrom,locale)}${member.effectiveUntil?' — '+model.date(member.effectiveUntil,locale):''}`));section.append(item);}overview.append(section);}
        if(view.loadState==='ready'){
            const updates=el('section',null,'public-stack');updates.append(el('h2',copy.updates));
            if(!view.mandate?.recentUpdates?.length)updates.append(el('p',copy.noUpdates));
            for(const update of (view.mandate?.recentUpdates||[]).slice(0,10)){
                const item=el('article',null,'government-update'),link=el('a',local(update.title));
                link.href=update.subjectType==='COMMITMENT'?'/government/commitments/'+encodeURIComponent(update.subjectId):update.subjectType==='MEASURE'?'/government/measures/'+encodeURIComponent(update.subjectId):'/government';item.append(link,el('p',model.date(update.publishedAt,locale),'government-meta'));updates.append(item);
            }
            overview.append(updates);
            if(root.FhemniMeasures){const measures=el('section',null,'public-stack'),copy=root.FhemniMeasures.COPY[locale],link=el('a',copy.title,'primary-button');link.href='/government/measures';measures.append(link,el('p',copy.intro));overview.append(measures);}
        }
        for(const tab of ['programme','delivery']){
            const content=doc.getElementById('tracker'+tab[0].toUpperCase()+tab.slice(1));content.replaceChildren();
            if(tab!==state.tab)continue;
            if(view.loadState!=='ready'){content.append(el('p',copy[view.loadState==='error'?'error':'loading']));continue;}
            if(tab==='programme'&&state.origin==='CAMPAIGN')content.append(el('p',copy.campaignNote,'government-note'));
            const rows=view.page?.items||[];
            if(!rows.length){content.append(el('p',copy.empty));if(state.origin==='GOVERNMENT'&&!view.mandate?.programme)content.append(el('p',copy.programmePending));}
            for(const row of rows.slice(0,10)){
                const card=el('article',null,'public-panel government-commitment-card');
                const heading=el('h2'),link=el('a',local(row.title));link.href=row.origin==='CAMPAIGN'?'/promises/'+encodeURIComponent(row.slug.replace(/^campaign-/,'')):'/government/commitments/'+encodeURIComponent(row.slug);heading.append(link);
                if(row.origin==='CAMPAIGN'){
                    const party=(view.parties||[]).find(party=>party.code===row.author?.en),author=el('p',null,'home-promise-author');
                    if(party?.symbolVerified&&/^\/assets\/[\w/.-]+$/.test(party.symbolAsset||'')){const image=el('img');image.src=party.symbolAsset;image.alt='';image.width=image.height=32;author.append(image);}
                    author.append(el('span',(locale==='ar'?party?.nameAr:party?.nameFr)||party?.nameAr||copy.campaign));card.append(author);
                }
                card.append(heading);
                if(row.origin==='GOVERNMENT'){
                    const badge=el('p',copy[model.assessmentState(row)]||copy.UNASSESSED,'government-status');badge.setAttribute('data-state',model.assessmentState(row));card.append(badge);
                    if(row.deliveryAssessment?.evidenceConfidence)card.append(el('p',copy[row.deliveryAssessment.evidenceConfidence],'government-meta'));
                    if(row.deadline)card.append(el('p',`${copy.deadline}: ${model.date(row.deadline,locale)} · ${copy[model.deriveDeadline(row.deadline,row.deliveryAssessment)]}`));
                    if(row.geographicScope)card.append(el('p',`${copy.scope}: ${local(row.geographicScope.label)}`,'government-meta'));
                }
                content.append(card);
            }
        }
        const regions=doc.getElementById('trackerRegions');if(state.tab==='regions'&&view.loadState==='ready'&&root.FhemniRegions)root.FhemniRegions.render(regions,view.page,locale,doc);else regions.replaceChildren(el('p',view.loadState==='ready'?copy.regionalEmpty:copy[view.loadState==='error'?'error':'loading']));
    }
    function createTrackerNavigation({load,render,history,url}){
        let version=0,controller;
        return {async go(next,push=false){const state=model.readTrackerState(next),current=++version;controller?.abort();controller=new AbortController();if(push)history.pushState({},'',model.trackerUrl(url(),state));render({loadState:'loading'},state);try{const view=await load(state,{signal:controller.signal});if(current===version)render({...view,loadState:'ready'},state);}catch(error){if(current===version&&error.name!=='AbortError')render({loadState:'error',error},state);}return state;}};
    }
    async function loadFilterMetadata(request){const results=await Promise.allSettled([request('/api/catalog/parties'),request('/api/catalog/policy-topics')]);return {parties:results[0].status==='fulfilled'?results[0].value:[],topics:results[1].status==='fulfilled'?results[1].value.topics:[],failed:results.flatMap((result,index)=>result.status==='rejected'?[index?'topics':'parties']:[])};}
    function updatePagerBusy(host,tab,loadState,hasPager){host.hidden=!['programme','delivery','regions'].includes(tab)||!hasPager;host.setAttribute('aria-busy',String(loadState==='loading'));if(loadState==='loading')for(const button of host.querySelectorAll('button'))button.disabled=true;}
    function captureTrackerUi(doc,tabs,tab){const saved={sourcesOpen:!!doc.getElementById('trackerSources')?.open,chip:doc.activeElement?.id?.startsWith('trackerChip-')?doc.activeElement.id:null};tabs.select('tab'+tab[0].toUpperCase()+tab.slice(1));return saved;}
    function restoreTrackerUi(doc,saved){const sources=doc.getElementById('trackerSources');if(sources)sources.open=saved.sourcesOpen;if(saved.chip)(doc.getElementById(saved.chip)||doc.getElementById('trackerFilterTitle'))?.focus();}
    function init(){
        const doc=root.document;if(!doc.getElementById('trackerTabs'))return;
        let state=model.readTrackerState(root.location.href),view={loadState:'loading'},parties=[],topics=[],regions=[],pager,metadataFailed=[];
        const locale=()=>root.FhemniI18n?.locale()||'ar';
        const tabs=root.FhemniUI.mountTabs(doc.getElementById('trackerTabs'),{selected:'tab'+state.tab[0].toUpperCase()+state.tab.slice(1),onChange:id=>go({...state,tab:id.slice(3).toLowerCase(),page:1})});
        function render(next,current){const focused=doc.activeElement,href=focused?.getAttribute?.('href'),savedUi=captureTrackerUi(doc,tabs,current.tab);view=next;state=current;renderTracker({...next,parties,locale:locale()},state,doc);
            const paging=doc.getElementById('trackerPager');
            if(next.page){const pages=Math.max(1,Math.ceil(next.page.totalItems/10));if(state.page>pages){state={...state,page:pages};root.history.replaceState({},'',model.trackerUrl(root.location.href,state));nav.go(root.location.href,false);return;}const data={page:state.page,pages,total:next.page.totalItems,locale:locale()};if(!pager){pager=root.FhemniUI.createPager({...data,onChange:page=>go({...state,page})});paging.append(pager);}else pager.update(data);}
            updatePagerBusy(paging,state.tab,next.loadState,Boolean(pager));
            doc.getElementById('trackerOrigin').value=state.origin;doc.getElementById('trackerTopic').value=state.topic;doc.getElementById('trackerParty').value=state.party;
            doc.getElementById('trackerRegion').value=state.region;
            renderFilterState();
            const follow=doc.getElementById('trackerFollow'),target=root.FhemniFollowing?.contextTarget(state);follow.hidden=!target;if(target)root.FhemniFollowControls.mount(follow,{...target,label:target.type==='REGION'?regions.find(row=>row.code===target.id)?.label:null});
            restoreTrackerUi(doc,savedUi);
            if(href&&!focused.isConnected)Array.from(doc.querySelectorAll('a[href]')).find(a=>a.getAttribute('href')===href)?.focus();
            root.FhemniUI.announceStatus(doc.getElementById('trackerStatus'),next.loadState==='ready'?(next.page?root.FhemniUI.countPhrase(next.page.totalItems,'result',locale()):model.COPY[locale()][state.tab]):model.COPY[locale()][next.loadState==='error'?'error':'loading']);
        }
        const nav=createTrackerNavigation({load:async(state,options)=>{const [mandate,page]=await Promise.all([root.FhemniGovernmentAPI.fetchMandate(options),state.tab==='regions'?root.FhemniGovernmentAPI.request('/api/catalog/government/2026-2031/observations?'+new URLSearchParams({region:state.region,page:state.page}),options):['programme','delivery'].includes(state.tab)?root.FhemniGovernmentAPI.fetchCommitments({...state,origin:state.tab==='delivery'?'GOVERNMENT':state.origin,party:state.tab==='delivery'?'':state.party},options):Promise.resolve(null)]);return {mandate,page};},render,history:root.history,url:()=>root.location.href});
        function go(next){nav.go(model.trackerUrl(root.location.href,next),true);}
        function renderFilterState(){const copy=model.COPY[locale()],host=doc.getElementById('trackerActiveFilters');host.replaceChildren();host.hidden=!['programme','delivery'].includes(state.tab);
            for(const key of ['topic','party'])if(state[key]){const row=key==='topic'?topics.find(row=>row.code===state[key]):parties.find(row=>row.code===state[key]),button=doc.createElement('button');button.id='trackerChip-'+key;button.type='button';button.className='government-filter-chip';button.textContent=`${copy[key]}: ${key==='topic'?model.localized(row?.label,locale())||state[key]:(locale()==='ar'?row?.nameAr:row?.nameFr)||state[key]} ×`;button.onclick=()=>go({...state,[key]:'',page:1});host.append(button);}
            if(state.topic||state.party){const clear=doc.createElement('button');clear.id='trackerChip-clear';clear.type='button';clear.textContent=copy.clear;clear.onclick=()=>go({...state,topic:'',party:'',page:1});host.append(clear);}
            const error=doc.getElementById('trackerFilterError');error.hidden=!metadataFailed.length;error.textContent=copy.filterError;const retry=doc.getElementById('trackerFilterRetry');retry.hidden=!metadataFailed.length;retry.textContent=copy.retry;
        }
        async function metadata(){const retry=doc.getElementById('trackerFilterRetry');retry.disabled=true;try{const [loaded,geo]=await Promise.all([loadFilterMetadata(root.FhemniGovernmentAPI.request),root.FhemniGovernmentAPI.request('/api/catalog/government/regions').then(rows=>({rows})).catch(()=>({failed:true}))]);if(!loaded.failed.includes('parties'))parties=loaded.parties;if(!loaded.failed.includes('topics'))topics=loaded.topics;if(!geo.failed)regions=geo.rows;metadataFailed=[...loaded.failed,...geo.failed?['regions']:[]];fillOptions();fillRegions();render(view,state);}finally{retry.disabled=false;}}
        function fillRegions(){const copy=root.FhemniRegions.COPY[locale()];doc.getElementById('regionLabel').textContent=copy.region;const select=doc.getElementById('trackerRegion');select.replaceChildren();for(const row of [{code:'',label:copy.all},...regions]){const option=doc.createElement('option');option.value=row.code;option.textContent=model.localized(row.label,locale());select.append(option);}}
        function fillOptions(){const copy=model.COPY[locale()];for(const [id,rows,getValue,getText]of [['trackerOrigin',[{code:'GOVERNMENT',label:copy.government},{code:'CAMPAIGN',label:copy.campaign}],row=>row.code,row=>row.label],['trackerTopic',[{code:'',label:copy.all},...topics.filter(row=>!row.parentCode&&row.selectable)],row=>row.code,row=>model.localized(row.label,locale())],['trackerParty',[{code:'',nameAr:copy.all,nameFr:copy.all},...parties],row=>row.code,row=>locale()==='ar'?row.nameAr:row.nameFr]]){const select=doc.getElementById(id);select.replaceChildren();for(const row of rows){const option=doc.createElement('option');option.value=getValue(row);option.textContent=getText(row);select.append(option);}}}
        doc.getElementById('trackerOrigin').onchange=event=>go({...state,origin:event.target.value,party:'',page:1});doc.getElementById('trackerTopic').onchange=event=>go({...state,topic:event.target.value,page:1});doc.getElementById('trackerParty').onchange=event=>go({...state,party:event.target.value,page:1});doc.getElementById('trackerRetry').onclick=()=>nav.go(root.location.href,false);
        root.addEventListener('popstate',()=>nav.go(root.location.href,false));doc.addEventListener('fhemni:localechange',()=>{fillOptions();fillRegions();render(view,state);});
        if(root.matchMedia?.('(max-width: 599px)').matches)doc.getElementById('trackerFilters').open=false;
        doc.getElementById('trackerRegion').onchange=event=>go({...state,region:event.target.value,page:1});doc.getElementById('trackerFilterRetry').onclick=metadata;fillOptions();fillRegions();nav.go(root.location.href,false);metadata();
    }
    return {renderTracker,createTrackerNavigation,loadFilterMetadata,updatePagerBusy,captureTrackerUi,restoreTrackerUi,init};
});
