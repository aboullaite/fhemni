(function(root,factory){const common=typeof module==='object'&&module.exports;const api=factory(common?require('./government-model.js'):root.FhemniGovernmentModel,common?require('./government-visuals.js'):root.FhemniGovernmentVisuals);if(common)module.exports=api;else root.FhemniGovernmentCards=api;})(typeof window==='object'?window:globalThis,function(model,visuals){
    const COPY={
        ar:{picks:'من البرنامج الحكومي',shuffle:'شوف اختيارات أخرى',open:'شوف التفاصيل',publication:'نشر ففهّمني',publications:'آخر المنشورات',publicationNote:'مستجدات الفهرس، ماشي إعلان عن إنجاز حكومي.',timing:'التوقيت المذكور',target:'هدف فالبرنامج، ماشي إنجاز',empty:'المواد ما متوفراش دابا.',failed:'تعذّر تحميل الاختيارات.',retry:'عاود المحاولة'},
        fr:{picks:'À découvrir dans le programme',shuffle:'D’autres idées à explorer',open:'Voir les détails',publication:'Publié sur Fhemni',publications:'Publications récentes',publicationNote:'Mises à jour du catalogue, pas des annonces de réalisations.',timing:'Calendrier annoncé',target:'Objectif du programme, pas une réalisation',empty:'Aucune entrée disponible pour le moment.',failed:'Impossible de charger la sélection.',retry:'Réessayer'},
        en:{picks:'Explore the programme',shuffle:'Show other picks',open:'Explore the details',publication:'Published on Fhemni',publications:'Recent publications',publicationNote:'Catalogue updates, not announcements of government delivery.',timing:'Stated timing',target:'Programme target, not delivered progress',empty:'No programme entries available yet.',failed:'Programme picks could not be loaded.',retry:'Try again'}
    };
    function selectProgramme(rows,{seed=Math.random()*4294967296,limit=3}={}){
        const unique=[...new Map(rows.filter(row=>row?.slug).map(row=>[row.slug,row])).values()];let value=Number(seed)>>>0;
        const random=()=>{value=(Math.imul(1664525,value)+1013904223)>>>0;return value/4294967296;};
        for(let i=unique.length-1;i>0;i--){const j=Math.floor(random()*(i+1));[unique[i],unique[j]]=[unique[j],unique[i]];}
        const selected=[],seen=new Set();for(const row of unique){const topic=row.topicCodes?.[0]||row.programmeContext?.kind||row.slug;if(!seen.has(topic)){selected.push(row);seen.add(topic);}if(selected.length===limit)break;}
        for(const row of unique){if(selected.length>=limit)break;if(!selected.includes(row))selected.push(row);}return selected;
    }
    async function loadProgramme(api,{seed,limit=3,signal}={}){
        const insights=await api.fetchInsights({origin:'GOVERNMENT'},{signal});
        return Promise.all(selectProgramme(insights.entries||[],{seed,limit}).map(async row=>{try{return await api.fetchCommitment(row.slug,{signal});}catch(error){if(error.name==='AbortError')throw error;return row;}}));
    }
    function recent(rows,limit=3){return [...rows].sort((a,b)=>(Date.parse(b.publishedAt)||0)-(Date.parse(a.publishedAt)||0)).slice(0,limit);}
    async function loadUpdates(api,rows,{limit=3,signal}={}){
        return Promise.all(recent(rows,limit).map(async update=>{if(!['COMMITMENT','LINK'].includes(update.subjectType))return update;try{return {...update,entry:await api.fetchCommitment(update.subjectId,{signal})};}catch(error){if(error.name==='AbortError')throw error;return update;}}));
    }
    function renderCards(host,rows,{locale='ar',document:doc=globalThis.document,topics=[],mode='programme',limit=3}={}){
        const copy=COPY[locale],words=model.COPY[locale],local=value=>model.localized(value,locale),el=(tag,text,cls)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=String(text);if(cls)node.className=cls;return node;};host.replaceChildren();
        for(const [index,record]of (mode==='updates'?recent(rows,limit):rows.slice(0,limit)).entries()){
            const update=mode==='updates'?record:null,row=update?.entry||record,context=row.programmeContext;
            const card=el('article',null,'programme-feature-card tone-'+index%3),top=el('div',null,'programme-feature-top');
            const topic=topics.find(t=>(row.topicCodes||[]).includes(t.code)&&t.code!=='OTHER');
            top.append(el('span',topic?model.programmeTopicLabel({...topic,label:topic.label||topic.name},locale):update?copy.publication:copy.picks,'programme-feature-topic'));
            const mark=el('span','↗','programme-feature-mark');mark.setAttribute('aria-hidden','true');top.append(mark);card.append(top);
            const href=update?(row.slug?'/government/commitments/'+encodeURIComponent(row.slug):['COMMITMENT','LINK'].includes(update.subjectType)?'/government/commitments/'+encodeURIComponent(update.subjectId):update.subjectType==='MEASURE'?'/government/measures/'+encodeURIComponent(update.subjectId):'/government'):'/government/commitments/'+encodeURIComponent(row.slug);
            const heading=el('h3'),link=el('a',local(update?.title||row.title));link.href=href;link.setAttribute('dir','auto');if(/[\u0600-\u06ff]/.test(link.textContent))link.setAttribute('lang','ar');heading.append(link);card.append(heading);
            const summary=local(update?.summary)||(locale==='en'&&context?.summaryEn)||row.originalText||'';
            if(summary&&summary!==link.textContent){const excerpt=el('p',summary.length>180?summary.slice(0,177).trimEnd()+'…':summary,'programme-feature-excerpt');excerpt.setAttribute('dir','auto');if(/[\u0600-\u06ff]/.test(excerpt.textContent))excerpt.setAttribute('lang','ar');card.append(excerpt);}
            if(visuals.baselineComparison(row)){const metric=el('div',null,'programme-feature-metric'),chart=el('div');metric.append(el('p',copy.target,'programme-feature-caption'));visuals.renderBaseline(chart,row,{locale,document:doc,showTitle:false});metric.append(chart);card.append(metric);}
            if(context?.deadlineText){const timing=el('div',null,'programme-feature-timing'),literal=el('span',context.deadlineText);literal.setAttribute('dir','auto');if(/[\u0600-\u06ff]/.test(literal.textContent))literal.setAttribute('lang','ar');timing.append(el('span',copy.timing),literal);card.append(timing);}
            const footer=el('div',null,'programme-feature-footer');
            if(update&&Number.isFinite(Date.parse(update.publishedAt))){const time=el('time',copy.publication+' · '+model.date(update.publishedAt,locale));time.dateTime=update.publishedAt;footer.append(time);}
            else if(!update)footer.append(el('span',words[model.assessmentState(row)]||words.UNASSESSED));
            const action=el('a',copy.open,'text-link');action.href=href;footer.append(action);card.append(footer);host.append(card);
        }
    }
    return {COPY,selectProgramme,loadProgramme,loadUpdates,renderCards};
});
