(function(root,factory){
    const common=typeof module==='object'&&module.exports;
    const api=factory(common?require('./government-model.js'):root.FhemniGovernmentModel);
    if(common)module.exports=api;else root.FhemniGovernmentVisuals=api;
})(typeof window==='object'?window:globalThis,function(model){
    const COPY={
        ar:{topics:'البرنامج حسب المواضيع',entries:'مدخل فالبرنامج',topicsNote:'تصنيف فهّمني للتصفح، ماشي ترتيب للأولويات. المدخل يقدر يكون فكثر من موضوع؛ الأعداد ما كتجمعش وعود مستقلة.',more:'جميع المواضيع',less:'مواضيع أقل',ambitions:'منين كينطلق البرنامج وفين باغي يوصل؟',ambitionNote:'مقارنة الأرقام المذكورة فالبرنامج، ماشي قياس للإنجاز. كل رسم عندو الوحدة والمقياس ديالو.',baseline:'نقطة الانطلاق حسب البرنامج',target:'الهدف المعلن',details:'التفاصيل والمصدر',dates:'التواريخ المذكورة فالبرنامج',datesNote:'كنحتافظو بالتاريخ كما جا فالنص: بداية، فترة أو أفق؛ ماشي كلها آجال إنجاز.',withoutYear:'مداخل بلا سنة محددة فحقل الأجل',noDates:'ما كايناش سنة صريحة فهاد الاختيار.',changes:'تغييرات تشكيلة الحكومة',membershipNote:'التواريخ كتخص العضوية فالحكومة، وماشي بالضرورة بداية نفس الحقيبة.',joined:'بداية العضوية فالحكومة',left:'نهاية العضوية فالحكومة',asOf:'حسب السجل إلى',previous:'السابق',next:'التالي',page:'الصفحة',of:'من',unavailable:'الرسوم ما تحملوش. النتائج باقية متاحة.',retry:'عاود المحاولة',original:'العنوان بلغة المصدر العربية',unit:'الوحدة',noComparisons:'ما كايناش نقطة انطلاق وهدف بنفس الوحدة فهاد الاختيار.'},
        fr:{topics:'Le programme par thème',entries:'entrées du programme',topicsNote:'Classement éditorial de Fhemni pour explorer, pas un classement des priorités. Une entrée peut relever de plusieurs thèmes ; les nombres ne représentent pas des promesses distinctes.',more:'Tous les thèmes',less:'Moins de thèmes',ambitions:'Du point de départ à la cible',ambitionNote:'Chiffres déclarés dans le programme, pas une mesure de réalisation. Chaque graphique utilise sa propre unité et son échelle.',baseline:'Point de départ déclaré',target:'Cible annoncée',details:'Détails et source',dates:'Dates indiquées dans le programme',datesNote:'Débuts, périodes et horizons sont conservés tels que publiés ; ce ne sont pas tous des délais de réalisation.',withoutYear:'entrées sans année explicite dans le champ échéance',noDates:'Aucune année explicite dans cette sélection.',changes:'Changements au gouvernement',membershipNote:'Dates de participation au gouvernement, pas nécessairement de prise du portefeuille actuel.',joined:'Entrée au gouvernement',left:'Fin de participation',asOf:'Selon le registre au',previous:'Précédent',next:'Suivant',page:'Page',of:'sur',unavailable:'Les graphiques n’ont pas pu être chargés. Les résultats restent disponibles.',retry:'Réessayer',original:'Titre dans la langue source : arabe',unit:'Unité',noComparisons:'Aucun point de départ et cible de même unité dans cette sélection.'},
        en:{topics:'Explore the programme by topic',entries:'programme entries',topicsNote:'Fhemni’s editorial browsing tags, not a ranking of priorities. An entry may have several topics; counts are not additive distinct promises.',more:'All topics',less:'Fewer topics',ambitions:'From stated baseline to target',ambitionNote:'Figures reported in the programme, not measured delivery. Each chart has its own unit and scale.',baseline:'Programme-reported baseline',target:'Announced target',details:'Details and source',dates:'Dates stated in the programme',datesNote:'Starts, periods and horizons retain their published wording; these are not all completion deadlines.',withoutYear:'entries without an explicit year in the deadline field',noDates:'No explicit year in this selection.',changes:'Government membership changes',membershipNote:'Membership dates, not necessarily the start of the current ministerial portfolio.',joined:'Joined government',left:'Membership ended',asOf:'According to the record as of',previous:'Previous',next:'Next',page:'Page',of:'of',unavailable:'Charts could not be loaded. Results remain available.',retry:'Retry',original:'Title in the source language: Arabic',unit:'Unit',noComparisons:'No same-unit baseline and target in this selection.'}
    };
    const UNITS={
        'MAD per month':{ar:'درهم فالشهر',fr:'MAD par mois'},
        'billion MAD per year':{ar:'مليار درهم فالسنة',fr:'milliards MAD par an'},
        'MAD net-income exemption threshold':{ar:'درهم — حد إعفاء الدخل الصافي',fr:'MAD — seuil d’exonération du revenu net'},
        'percent of deceased spouse main pension':{ar:'% من المعاش الرئيسي للزوج المتوفى',fr:'% de la pension principale du conjoint décédé'},
        'percent of AMO expenditure':{ar:'% من نفقات التأمين الإجباري عن المرض',fr:'% des dépenses de l’AMO'},
        'school-transport beneficiaries':{ar:'مستفيد من النقل المدرسي',fr:'bénéficiaires du transport scolaire'},
        'percent of seeds used':{ar:'% من البذور المستعملة',fr:'% des semences utilisées'},
        'days offshore waiting':{ar:'أيام انتظار السفن خارج الميناء',fr:'jours d’attente au large'},
        'days at berth per vessel':{ar:'أيام بقاء كل سفينة فالرصيف',fr:'jours à quai par navire'},
        'tonnes annual port capacity':{ar:'طن — الطاقة السنوية للميناء',fr:'tonnes de capacité portuaire annuelle'}
    };
    const TIMELINE_COPY={
        ar:{hint:'اختار سنة أو فترة باش تشوف المداخل المرتبطة بها.',years:'سنوات مذكورة',periods:'فترات وتواريخ متعددة',undated:'بلا سنة صريحة',count:'عدد المداخل',expand:'المعطيات والمصدر',literal:'التوقيت كما جا فالنص',target:'معطيات الهدف المستخرجة',pages:'صفحات المصدر',noTiming:'ما كاينش توقيت محدد فحقل الأجل.'},
        fr:{hint:'Choisissez une année ou une période pour explorer les entrées associées.',years:'Années mentionnées',periods:'Périodes et dates multiples',undated:'Sans année explicite',count:'Nombre d’entrées',expand:'Données et source',literal:'Calendrier tel que publié',target:'Données extraites de la cible',pages:'Pages de la source',noTiming:'Aucun calendrier renseigné dans le champ échéance.'},
        en:{hint:'Choose a year or period to explore its entries.',years:'Years mentioned',periods:'Periods and multiple dates',undated:'No explicit year',count:'Number of entries',expand:'Data and source',literal:'Timing as published',target:'Extracted target data',pages:'Source pages',noTiming:'No timing stated in the deadline field.'}
    };
    const TIMELINE_UI={
        ar:{title:'تواريخ البرنامج',help:'كيفاش نقراها؟',close:'سد التفاصيل',undated:'تصفح المداخل اللي ما تذكرتش فيها سنة محددة'},
        fr:{title:'Le calendrier du programme',help:'Comment le lire ?',close:'Fermer les détails',undated:'Explorer les entrées sans année précisée'},
        en:{title:'The programme timeline',help:'How to read it',close:'Close details',undated:'Explore entries without a stated year'}
    };
    // Only explicit, same-unit pairs from the literal extraction qualify. No unit conversion or delivery inference.
    function baselineComparison(row){
        const literal=row?.programmeContext?.targetText;if(typeof literal!=='string')return null;
        const parts=literal.split(/ · (?=(?:value|unit|operator|periodText|baseline): )/),fields={};
        for(const part of parts){const match=part.match(/^(value|unit|operator|periodText|baseline): ([\s\S]+)$/);if(!match||Object.hasOwn(fields,match[1]))return null;fields[match[1]]=match[2];}
        const operators={equal:'=','>=':'≥','<=':'≤','less-than':'<','greater-than':'>',around:'≈'};
        if(!Object.hasOwn(operators,fields.operator)||!fields.unit?.trim()||!/^\d+(?:\.\d+)?$/.test(fields.value||''))return null;
        let baseline;try{baseline=JSON.parse(fields.baseline);}catch{return null;}
        const target=Number(fields.value);
        if(baseline?.source!=='programme-reported'||baseline.unit!==fields.unit||typeof baseline.value!=='number'||!Number.isFinite(baseline.value)||baseline.value<0||!Number.isFinite(target)||target<0)return null;
        return {baseline:baseline.value,target,unit:fields.unit,operator:operators[fields.operator],period:fields.periodText||null};
    }
    function programmeDates(entries=[]){
        const dated=[];let withoutYear=0;
        for(const row of entries){const literal=row.programmeContext?.deadlineText;const years=[...new Set((literal?.match(/\b(?:19|20|21)\d{2}\b/g)||[]).map(Number))].sort((a,b)=>a-b);
            if(years.length)dated.push({...row,literal,years});else withoutYear++;}
        dated.sort((a,b)=>a.years[0]-b.years[0]||a.slug.localeCompare(b.slug));return {dated,withoutYear};
    }
    function elements(doc){return (tag,text,cls)=>{const n=doc.createElement(tag);if(text!=null)n.textContent=String(text);if(cls)n.className=cls;return n;};}
    function linkToEntry(el,row,locale){const a=el('a',model.localized(row.title,locale));a.href='/government/commitments/'+encodeURIComponent(row.slug);a.setAttribute('dir','auto');if(/[\u0600-\u06ff]/.test(a.textContent))a.setAttribute('lang','ar');return a;}
    function renderBaseline(host,row,{locale='ar',document:doc=globalThis.document,showTitle=true}={}){
        const pair=baselineComparison(row);host.replaceChildren();host.hidden=!pair;if(!pair)return false;
        const copy=COPY[locale],el=elements(doc);host.className='government-baseline';
        if(showTitle){const title=el('h3');title.append(linkToEntry(el,row,locale));host.append(title);}
        const unit=UNITS[pair.unit]?.[locale]||pair.unit;const unitText=el('p',unit,'government-meta');unitText.setAttribute('dir','auto');host.append(unitText);
        const scale=Math.max(pair.baseline,pair.target,1);
        for(const [label,value,kind,operator]of [[copy.baseline,pair.baseline,'baseline',''],[copy.target,pair.target,'target',pair.operator==='='?'':pair.operator+' ']]){
            const line=el('div',null,'government-value-row'),number=el('bdi',operator+model.number(value,locale),'government-value');number.setAttribute('dir','ltr');
            const labels=el('div',null,'government-value-label');labels.append(el('span',label),number);
            const track=el('div',null,'government-bar-track'),bar=el('span',null,'government-bar-fill '+kind);bar.style.width=(value/scale*100)+'%';track.setAttribute('aria-hidden','true');track.append(bar);line.append(labels,track);host.append(line);
        }
        if(pair.period){const period=el('p',pair.period,'government-meta');period.setAttribute('dir','auto');host.append(period);}
        return true;
    }
    function paginated(host,rows,renderRow,{locale,doc,pageSize=10,grid=false,listClass}){
        const copy=COPY[locale],el=elements(doc);let page=1;
        function render(){host.replaceChildren();const list=el(grid?'div':'ol',null,listClass||(grid?'government-baseline-grid':'government-date-list')),pages=Math.max(1,Math.ceil(rows.length/pageSize));
            for(const row of rows.slice((page-1)*pageSize,page*pageSize))list.append(renderRow(row));host.append(list);
            if(pages>1){const nav=el('nav',null,'government-visual-pager');nav.setAttribute('aria-label',copy.page);const prev=el('button',copy.previous),next=el('button',copy.next),status=el('span',`${copy.page} ${model.number(page,locale)} ${copy.of} ${model.number(pages,locale)}`);status.setAttribute('role','status');
                for(const button of [prev,next])button.type='button';prev.disabled=page===1;next.disabled=page===pages;
                prev.onclick=()=>{page--;render();host.focus?.();};next.onclick=()=>{page++;render();host.focus?.();};nav.append(prev,status,next);host.append(nav);}
        }host.setAttribute('tabindex','-1');render();
    }
    function renderDateExplorer(host,entries,{locale,doc,prefix}){
        const el=elements(doc),copy=COPY[locale],words=TIMELINE_COPY[locale],dates=programmeDates(entries),groups=new Map();
        for(const row of dates.dated){const key=(row.years.length===1?'year-':'multi-')+row.years.join('-');
            if(!groups.has(key))groups.set(key,{key,years:row.years,rows:[]});groups.get(key).rows.push(row);}
        const datedSlugs=new Set(dates.dated.map(row=>row.slug));
        const undated=entries.filter(row=>!datedSlugs.has(row.slug));
        if(undated.length)groups.set('undated',{key:'undated',years:[],rows:undated});
        const ui=TIMELINE_UI[locale],section=el('section',null,'government-date-explorer'),heading=el('h2',ui.title);heading.id=prefix+'-title';section.setAttribute('aria-labelledby',heading.id);
        const header=el('header',null,'government-date-header'),guide=el('details',null,'government-date-guide');guide.append(el('summary',ui.help),el('p',words.hint),el('p',copy.datesNote));header.append(heading,guide);section.append(header);
        const panel=el('section',null,'government-date-panel');panel.id=prefix+'-panel';panel.hidden=true;
        const resultHeading=el('h3'),resultTitle=el('bdi'),resultCount=el('span',null,'government-date-selection-count'),resultCountLabel=el('span',null,'sr-only'),status=el('p',null,'sr-only'),results=el('div'),panelHeader=el('div',null,'government-date-panel-header'),close=el('button',ui.close,'government-date-close');
        resultHeading.id=prefix+'-selection';resultCount.setAttribute('aria-hidden','true');resultHeading.append(resultTitle,resultCount,resultCountLabel);panel.setAttribute('aria-labelledby',resultHeading.id);status.setAttribute('role','status');close.type='button';close.id=prefix+'-close';panelHeader.append(resultHeading,close);panel.append(panelHeader,status,results);
        const controls=[];let selected=null,panelSlot=el('div');panelSlot.append(panel);
        const label=group=>group.years.length?group.years.join(' / '):words.undated;
        close.onclick=()=>{const control=controls.find(value=>value.group.key===selected);if(control){select(control.group);control.button.focus();}};
        function select(group){
            selected=selected===group.key?null:group.key;panel.hidden=!selected;
            for(const control of controls){const active=control.group.key===selected;control.button.setAttribute('aria-expanded',String(active));control.button.setAttribute('aria-pressed',String(active));control.indicator.textContent=active?'−':'+';}
            if(!selected){results.replaceChildren();return;}
            panelSlot.replaceChildren();panelSlot=controls.find(control=>control.group.key===selected).slot;panelSlot.append(panel);
            resultTitle.textContent=label(group);resultTitle.setAttribute('dir',group.years.length?'ltr':'auto');resultCount.textContent=model.number(group.rows.length,locale);resultCountLabel.textContent=' — '+words.count+': '+resultCount.textContent;status.textContent=label(group)+resultCountLabel.textContent;
            paginated(results,group.rows,row=>{
                const item=el('li'),entryTitle=el('h4');entryTitle.append(linkToEntry(el,row,locale));item.append(entryTitle);
                const timing=el('p',row.literal||row.programmeContext?.deadlineText||words.noTiming,'government-date-literal');timing.setAttribute('dir','auto');if(/[\u0600-\u06ff]/.test(timing.textContent))timing.setAttribute('lang','ar');item.append(timing);
                const details=el('details',null,'government-date-entry-details');details.append(el('summary',words.expand));
                const target=row.programmeContext?.targetText;
                if(baselineComparison(row)){
                    const chart=el('div');renderBaseline(chart,row,{locale,document:doc,showTitle:false});details.append(el('p',copy.ambitionNote,'government-meta'),chart);
                }else if(target){
                    // The extraction record is not source prose. Render only an explicit scalar,
                    // retaining its operator/unit; fall back to source wording for other shapes.
                    const fields={};let duplicate=false;for(const part of target.split(' · ')){const match=part.match(/^(value|unit|operator): (.+)$/);if(match){if(Object.hasOwn(fields,match[1]))duplicate=true;fields[match[1]]=match[2];}}
                    const operators={equal:'=','>=':'≥','<=':'≤','less-than':'<','greater-than':'>',around:'≈'};
                    if(!duplicate&&/^\d+(?:\.\d+)?$/.test(fields.value||'')&&Number.isFinite(Number(fields.value))&&fields.unit&&Object.hasOwn(operators,fields.operator)){
                        details.append(el('h5',copy.target));const value=el('p'),number=el('bdi',operators[fields.operator]+' '+model.number(Number(fields.value),locale));number.setAttribute('dir','ltr');value.append(number,el('span',' '+(UNITS[fields.unit]?.[locale]||fields.unit)));details.append(value);
                    }else if(row.originalText){const quote=el('blockquote',row.originalText);quote.setAttribute('dir','auto');if(row.originalLanguage)quote.setAttribute('lang',row.originalLanguage);details.append(quote);}
                }
                if(row.programmeContext?.printedPages?.length)details.append(el('p',words.pages+': '+row.programmeContext.printedPages.join('، '),'government-meta'));
                const link=el('a',copy.details,'text-link');link.href='/government/commitments/'+encodeURIComponent(row.slug);details.append(link);item.append(details);return item;
            },{locale,doc,listClass:'government-date-results'});
        }
        const timeline=el('div',null,'government-date-agenda');section.append(timeline);
        for(const row of [...groups.values()].sort((a,b)=>(a.years[0]??Infinity)-(b.years[0]??Infinity)||a.years.length-b.years.length||a.key.localeCompare(b.key))){
                const wrapper=el('div',null,row.years.length?'government-date-event':'government-date-undated'),slot=el('div'),button=el('button',null,'government-date-marker');button.type='button';button.id=prefix+'-'+row.key;button.setAttribute('aria-controls',panel.id);button.setAttribute('aria-expanded','false');button.setAttribute('aria-pressed','false');
                const date=el(row.years.length?'bdi':'span',label(row),'government-date-marker-label');if(row.years.length)date.setAttribute('dir','ltr');
                const preview=el('span',null,'government-date-preview'),previewTitle=el('span',row.years.length?row.rows.slice(0,2).map(entry=>model.localized(entry.title,locale)).join(' · '):ui.undated,'government-date-preview-title');previewTitle.setAttribute('dir','auto');if(/[\u0600-\u06ff]/.test(previewTitle.textContent))previewTitle.setAttribute('lang','ar');preview.append(previewTitle);
                if(row.years.length&&row.rows.length===1&&row.rows[0].literal!==label(row)){const timing=el('span',row.rows[0].literal,'government-date-preview-timing');timing.setAttribute('dir','auto');if(/[\u0600-\u06ff]/.test(timing.textContent))timing.setAttribute('lang','ar');preview.append(timing);}
                const count=el('span',model.number(row.rows.length,locale),'government-date-marker-count');count.setAttribute('aria-hidden','true');const countLabel=el('span',' — '+words.count+': '+count.textContent,'sr-only');
                const indicator=el('span','+','government-date-indicator');indicator.setAttribute('aria-hidden','true');
                button.append(date,preview,count,indicator,countLabel);button.onclick=()=>select(row);controls.push({button,group:row,slot,indicator});wrapper.append(button,slot);(row.years.length?timeline:section).append(wrapper);
        }
        if(!dates.dated.length)section.append(el('p',copy.noDates,'government-meta'));
        section.append(panelSlot);host.append(section);
    }
    function renderProgramme(host,insights,{locale='ar',document:doc=globalThis.document,state={},topics=[],onFilter=()=>{}}={}){
        const copy=COPY[locale],el=elements(doc);host.replaceChildren();host.hidden=!insights;if(!insights)return;
        const topicSection=el('section',null,'government-topic-graphic');topicSection.append(el('h2',copy.topics),el('p',copy.topicsNote,'government-meta'));
        const bars=el('div',null,'government-topic-bars');const topicRows=insights.topics||[];let expanded=false;
        const toggle=el('button',copy.more,'government-filter-chip');toggle.type='button';toggle.hidden=topicRows.length<=8;toggle.setAttribute('aria-expanded','false');
        function renderBars(){bars.replaceChildren();const max=Math.max(1,...topicRows.map(x=>x.count));
            for(const row of topicRows.slice(0,expanded?topicRows.length:8)){const button=el('button',null,'government-topic-bar'),label=el('span',model.programmeTopicLabel(topics.find(t=>t.code===row.code)||row,locale)),count=el('span',`${model.number(row.count,locale)} ${copy.entries}`,'government-topic-count');button.type='button';button.id='trackerTopicBar-'+row.code;button.setAttribute('aria-pressed',String(state.topic===row.code));const track=el('span',null,'government-bar-track'),fill=el('span',null,'government-bar-fill target');fill.style.width=(row.count/max*100)+'%';track.setAttribute('aria-hidden','true');track.append(fill);button.append(label,count,track);button.onclick=()=>onFilter({...state,topic:state.topic===row.code?'':row.code,page:1});bars.append(button);}
        }toggle.onclick=()=>{expanded=!expanded;toggle.textContent=copy[expanded?'less':'more'];toggle.setAttribute('aria-expanded',String(expanded));renderBars();};renderBars();topicSection.append(bars,toggle);if(topicRows.length)host.append(topicSection);
        const entries=insights.entries||[],pairs=entries.filter(row=>baselineComparison(row));
        if(pairs.length){const collapsed=state.tab==='delivery',section=el(collapsed?'details':'section',null,collapsed?'government-timeline':'government-ambitions');section.append(el(collapsed?'summary':'h2',copy.ambitions),el('p',copy.ambitionNote,'government-meta'));
            const charts=el('div');paginated(charts,pairs,row=>{const card=el('article');renderBaseline(card,row,{locale,document:doc});return card;},{locale,doc,pageSize:4,grid:true});section.append(charts);host.append(section);}
        if(entries.length)renderDateExplorer(host,entries,{locale,doc,prefix:(state.tab||'programme')+'-dates'});
    }
    function renderCabinet(host,members,{locale='ar',document:doc=globalThis.document,asOf}={}){
        const copy=COPY[locale],el=elements(doc);host.replaceChildren();const events=[];
        for(const member of members||[])if(member.evidenceIds?.length)for(const [field,type]of [['effectiveFrom','joined'],['effectiveUntil','left']]){const date=member[field];if(/^\d{4}-\d{2}-\d{2}$/.test(date||'')&&date<=asOf)events.push({member,date,type});}
        events.sort((a,b)=>b.date.localeCompare(a.date)||String(a.member.id).localeCompare(String(b.member.id)));host.hidden=!events.length;if(!events.length)return;
        const details=el('details',null,'government-timeline'),body=el('div');details.append(el('summary',copy.changes),el('p',copy.membershipNote,'government-meta'),el('p',copy.asOf+': '+model.date(asOf,locale),'government-meta'));
        paginated(body,events,({member,date,type})=>{const li=el('li'),time=el('time',model.date(date,locale),'government-date-year'),description=el('div'),name=model.localized(member.name,locale),a=el('a',name);time.dateTime=date;a.href='/government/ministers?status=all&q='+encodeURIComponent(name);a.setAttribute('dir','auto');description.append(a,el('p',copy[type],'government-meta'));li.append(time,description);return li;},{locale,doc});details.append(body);host.append(details);
    }
    return {COPY,baselineComparison,programmeDates,renderBaseline,renderProgramme,renderCabinet};
});
