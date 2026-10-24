(function(root,factory){const api=factory(root,typeof module==='object'?require('./government-model.js'):root.FhemniGovernmentModel,typeof module==='object'?require('./government-charts.js'):root.FhemniGovernmentCharts,typeof module==='object'?require('./government-visuals.js'):root.FhemniGovernmentVisuals);if(typeof module==='object'&&module.exports)module.exports=api;else{root.FhemniCommitment=api;document.addEventListener('DOMContentLoaded',api.init);}})(typeof window==='object'?window:globalThis,function(root,model,charts,visuals){
    function detailLoadCopy(state,locale='ar'){return model.COPY[locale][state==='notFound'?'notFound':state==='loading'?'loading':'error'];}
    function canonicalCampaignPath(view){return view?.origin==='CAMPAIGN'?'/promises/'+encodeURIComponent(view.slug.replace(/^campaign-/,'')):null;}
    function observationPageFor(rows,id){const index=rows.findIndex(row=>row.id===id);return index<0?1:Math.floor(index/10)+1;}
    const DETAIL_COPY={
        ar:{unassessed:'ما زال ما نشرناش معطيات موثقة باش نقيّمو تنفيذ هاد الالتزام.',history:'سجل التحديثات',noHistory:'ما كاين حتى تحديث منشور بعد.'},
        fr:{unassessed:'Nous n’avons pas encore publié de données documentées permettant d’évaluer la réalisation de cet engagement.',history:'Historique des mises à jour',noHistory:'Aucune mise à jour publiée pour le moment.'},
        en:{unassessed:'We have not yet published documented data to assess delivery of this commitment.',history:'Update history',noHistory:'No updates have been published yet.'}
    };
    function renderCommitment(view,locale='ar',doc=globalThis.document,{observationPage=1,revisionPage=1,linkedCampaigns={}}={}){
        const copy=model.COPY[locale],detail=DETAIL_COPY[locale],local=value=>model.localized(value,locale);
        const el=(tag,text,className)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;if(className)node.className=className;return node;};
        for(const [id,key]of Object.entries({commitmentBack:'back',originalTitle:'original',evidenceTitle:'sources',observationsTitle:'observations',revisionsTitle:'history'}))doc.getElementById(id).textContent=copy[key];
        doc.getElementById('revisionsTitle').textContent=detail.history;
        const title=doc.getElementById('commitmentTitle'),titleLanguage=model.titleLanguage(view,locale);title.textContent=local(view.title);title.setAttribute('lang',titleLanguage);title.setAttribute('dir',titleLanguage==='ar'?'rtl':'auto');
        const languageNote=doc.getElementById('commitmentLanguage');languageNote.textContent=copy.originalArabic;languageNote.hidden=titleLanguage!=='ar'||locale==='ar';
        const ambition=doc.getElementById('commitmentAmbition');ambition.replaceChildren();ambition.hidden=!visuals.baselineComparison(view);
        if(!ambition.hidden){const chart=el('div'),v=visuals.COPY[locale];ambition.append(el('h2',v.ambitions),el('p',v.ambitionNote,'government-meta'));if(view.programmeContext?.sourceStatus==='MIRROR_UNAUTHENTICATED')ambition.append(el('p',v.sourceNote,'government-source-status'));visuals.renderBaseline(chart,view,{locale,document:doc,showTitle:false});ambition.append(chart);const source=el('a',v.details);source.href='#commitmentSources';source.onclick=()=>{doc.getElementById('commitmentSources').open=true;};ambition.append(source);}
        const context=view.programmeContext;
        function evidenceLinks(ids){const group=el('span',null,'government-evidence-links');for(const [index,id]of (ids||[]).entries()){const source=view.evidence?.find(item=>item.id===id),link=el('a',source?.locator||`${copy.evidenceLink} ${index+1}`);link.href='#evidence-'+id;link.onclick=()=>{doc.getElementById('commitmentSources').open=true;doc.getElementById('evidence-'+id)?.focus?.();};group.append(link);}return group;}
        const original=doc.getElementById('commitmentOriginal');original.textContent=view.originalText||copy.unknown;original.setAttribute('lang',view.originalLanguage||'ar');original.setAttribute('dir','auto');
        const normalized=value=>String(value||'').normalize('NFC').trim().replace(/\s+/g,' ');
        const excerptInSources=!!context&&(view.evidence||[]).some(source=>normalized(source.passage)===normalized(view.originalText));
        doc.getElementById('commitmentOriginalSection').hidden=!normalized(view.originalText)||excerptInSources||Object.values(view.title||{}).some(value=>normalized(value)===normalized(view.originalText));
        const assessment=doc.getElementById('commitmentAssessment'),badge=el(view.deliveryAssessment?'p':'h2',copy[model.assessmentState(view)]||copy.UNASSESSED,'government-status');badge.setAttribute('data-state',model.assessmentState(view));assessment.replaceChildren(...(view.deliveryAssessment?[el('h2',copy.assessment),badge]:[badge]));
        if(!view.deliveryAssessment)assessment.append(el('p',view.observations?.length?copy.deliveryNote:detail.unassessed,'government-meta'));
        if(view.deliveryAssessment){assessment.append(el('p',copy[view.deliveryAssessment.evidenceConfidence]||copy.unknown),el('p',local(view.deliveryAssessment.explanation)),el('p',`${copy.reviewed}: ${model.date(view.deliveryAssessment.reviewedAt,locale)}`,'government-meta'),evidenceLinks(view.deliveryAssessment.evidenceIds));}
        const facts=doc.getElementById('commitmentFacts');facts.replaceChildren();
        for(const [key,value]of [['author',local(view.author)||copy.unknown],['responsible',local(view.responsibleBody)||copy.unknown],['scope',local(view.geographicScope?.label)||copy.unknown],['deadline',view.deadline?`${model.date(view.deadline,locale)} · ${copy[model.deriveDeadline(view.deadline,view.deliveryAssessment)]}`:context?.deadlineText||copy.UNKNOWN]])if(value!=null)facts.append(el('dt',copy[key]),el('dd',value));
        const links=doc.getElementById('commitmentLinks');links.replaceChildren();links.hidden=!view.links?.length;
        if(view.links?.length)links.append(el('h2',copy.links));
        for(const relationship of view.links||[]){const campaign=linkedCampaigns[relationship.campaignPromiseId],article=el('article',null,'public-panel public-stack'),link=el('a',campaign?local(campaign.title):copy.readProgramme);link.href='/government/commitments/'+encodeURIComponent(relationship.campaignPromiseId);article.append(el('h3',copy[relationship.relation]||copy.UNRESOLVED),el('p',local(relationship.explanation)),link);
            if(campaign){link.href=canonicalCampaignPath(campaign);article.append(el('p',`${copy.author}: ${local(campaign.author)}`));const quote=el('blockquote',campaign.originalText);quote.setAttribute('lang',campaign.originalLanguage||'ar');quote.setAttribute('dir','auto');article.append(quote);}
            const provenance=el('details');provenance.append(el('summary',copy.programmeVersion),el('p',relationship.programmeVersionId),evidenceLinks(relationship.evidenceIds));article.append(provenance);links.append(article);
        }
        const comparison=doc.getElementById('commitmentChart');comparison.replaceChildren();comparison.hidden=!view.target;
        if(view.target){comparison.append(el('h2',copy.comparison));const chart=el('div');comparison.append(chart);charts.renderChart(chart,{...view.target,scope:view.geographicScope},view.observations||[],locale,doc);}
        const sources=doc.getElementById('commitmentEvidence');sources.replaceChildren();
        if(!view.evidence?.length)sources.append(el('p',copy.unknown));
        for(const source of view.evidence||[]){const item=el('article',null,'government-evidence');item.id='evidence-'+source.id;item.tabIndex=-1;
            const url=model.sourceUrl(source.url);if(url){const a=el('a',source.locator?`${copy.evidenceLink} · ${source.locator}`:copy.evidenceLink);a.href=url;a.rel='noopener noreferrer';item.append(a);}
            const passage=el('blockquote',source.passage);passage.setAttribute('lang',source.originalLanguage||'ar');passage.setAttribute('dir','auto');item.append(passage);
            item.append(el('p',`${copy.sourceDate}: ${model.date(source.publishedAt,locale)} · ${copy.retrieved}: ${model.date(source.retrievedAt,locale)}`,'government-meta'));sources.append(item);
        }
        const observations=doc.getElementById('commitmentObservations');observations.replaceChildren();
        doc.getElementById('commitmentObservationSection').hidden=!view.observations?.length;
        if(view.observations?.length){
            const wrapper=el('div',null,'public-table-scroll'),table=el('table',null,'public-table');table.append(el('caption',copy.observations));
            const head=el('thead'),header=el('tr');for(const label of [copy.period,copy.value,copy.unit,copy.scope,copy.sources]){const th=el('th',label);th.setAttribute('scope','col');header.append(th);}head.append(header);table.append(head);
            const body=el('tbody');
            for(const observation of view.observations.slice((observationPage-1)*10,observationPage*10)){
                const row=el('tr');row.id='observation-'+observation.id;row.tabIndex=-1;for(const value of [`${model.date(observation.periodStart,locale)} — ${model.date(observation.periodEnd,locale)}`,model.number(observation.value,locale),observation.unit,local(observation.scope?.label)])row.append(el('td',value));const sourceCell=el('td');
                sourceCell.append(evidenceLinks(observation.evidenceIds));row.append(sourceCell);body.append(row);
            }
            table.append(body);wrapper.append(table);observations.append(wrapper);
        }
        const history=doc.getElementById('commitmentHistory');history.replaceChildren();
        if(!view.revisions?.length)history.append(el('p',detail.noHistory,'government-meta'));
        for(const revision of (view.revisions||[]).slice((revisionPage-1)*10,revisionPage*10)){const item=el('p'),link=el('a',`${copy.publishedRevision} ${model.number(revision.version,locale)} · ${model.date(revision.publishedAt,locale)}`);link.href='/government/revisions/'+encodeURIComponent(revision.id);item.append(link);if(revision.explanation)item.append(el('span',' · '+local(revision.explanation)));history.append(item);}
    }
    function init(){
        const doc=root.document;if(!doc.getElementById('commitmentTitle'))return;
        const slug=root.location.pathname.split('/').filter(Boolean).at(-1);let view,observationPage=1,revisionPage=1,observationPager,revisionPager,loadState='loading',linkedCampaigns={};
        const lang=()=>root.FhemniI18n?.locale()||'ar';
        function render(){const copy=model.COPY[lang()];doc.getElementById('commitmentBack').textContent=copy.back;doc.getElementById('commitmentStatus').textContent=loadState==='ready'?'':detailLoadCopy(loadState,lang());doc.getElementById('commitmentRetry').hidden=loadState!=='error';doc.getElementById('commitmentRetry').textContent=copy.retry;if(!view)return;renderCommitment(view,lang(),doc,{observationPage,revisionPage,linkedCampaigns});root.FhemniFollowControls?.mount(doc.getElementById('commitmentFollow'),{type:'COMMITMENT',id:view.id,correctionType:'COMMITMENT',correctionId:view.id});
            for(const [name,rows,page] of [['observation',view.observations||[],observationPage],['revision',view.revisions||[],revisionPage]]){
                const host=doc.getElementById(name+'Pager');host.hidden=rows.length<=10;const options={page,pages:Math.max(1,Math.ceil(rows.length/10)),total:rows.length,locale:lang()};
                let pager=name==='observation'?observationPager:revisionPager;
                if(!pager){pager=root.FhemniUI.createPager({...options,onChange:next=>{if(name==='observation')observationPage=next;else revisionPage=next;render();}});host.append(pager);if(name==='observation')observationPager=pager;else revisionPager=pager;}else pager.update(options);
            }
        }
        async function load(){loadState='loading';render();
            try{view=await root.FhemniGovernmentAPI.fetchCommitment(slug);const campaignPath=canonicalCampaignPath(view);if(campaignPath){root.location.replace(campaignPath);return;}const observationId=new URL(root.location.href).searchParams.get('observation');observationPage=observationPageFor(view.observations||[],observationId);loadState='ready';render();doc.getElementById('commitmentContent').hidden=false;const target=doc.getElementById('observation-'+observationId);target?.focus();target?.scrollIntoView({block:'center'});
                await Promise.all((view.links||[]).map(async link=>{try{linkedCampaigns[link.campaignPromiseId]=await root.FhemniGovernmentAPI.fetchCommitment(link.campaignPromiseId);}catch{/* The original published promise remains reachable through its link. */}}));
                const focused=doc.activeElement,href=focused?.getAttribute?.('href');render();if(href&&!focused.isConnected)Array.from(doc.querySelectorAll('a[href]')).find(a=>a.getAttribute('href')===href)?.focus();
            }
            catch(error){loadState=error.status===404?'notFound':'error';render();}
        }
        doc.getElementById('commitmentRetry').onclick=load;doc.addEventListener('fhemni:localechange',render);load();
    }
    return {renderCommitment,detailLoadCopy,canonicalCampaignPath,observationPageFor,init};
});
