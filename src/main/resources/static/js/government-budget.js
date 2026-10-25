(function(root,factory){const api=factory(typeof module==='object'?require('./government-model.js'):root.FhemniGovernmentModel);if(typeof module==='object'&&module.exports)module.exports=api;else root.FhemniGovernmentBudget=api;})(typeof window==='object'?window:globalThis,function(model){
    const COPY={
        ar:{title:'شنو جا فمشروع قانون المالية 2027؟',PROPOSED_MEASURE:'إجراء مقترح · مالية 2027',PROPOSED_FUNDING:'تمويل مقترح · مالية 2027',note:'هادشي مقترح فمشروع قانون المالية، ماشي دليل على التنفيذ.',reviewed:'مراجعة الوثائق',published:'تاريخ نشر الوثيقة',pages:'الصفحات المطبوعة',pdfPages:'صفحات PDF',source:'قرا المصدر',host:'نسخة الوثيقة منشورة عند Médias24.'},
        fr:{title:'Que prévoit le PLF 2027 ?',PROPOSED_MEASURE:'Mesure proposée · PLF 2027',PROPOSED_FUNDING:'Financement proposé · PLF 2027',note:'Proposition du projet de loi de finances, pas une preuve de réalisation.',reviewed:'Documents examinés le',published:'Document publié le',pages:'Pages imprimées',pdfPages:'Pages PDF',source:'Lire la source',host:'Copie du document hébergée par Médias24.'},
        en:{title:'What does the 2027 Finance Bill propose?',PROPOSED_MEASURE:'Proposed measure · Budget 2027',PROPOSED_FUNDING:'Proposed funding · Budget 2027',note:'A Finance Bill proposal, not evidence of delivery.',reviewed:'Documents checked',published:'Document published',pages:'Printed pages',pdfPages:'PDF pages',source:'Read the source',host:'Document copy hosted by Médias24.'}
    };
    function render(host,view,{locale='ar',document:doc=globalThis.document,compact=false}={}){
        const copy=COPY[locale]||COPY.ar,local=value=>model.localized(value,locale);
        const el=(tag,text,cls)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;if(cls)node.className=cls;return node;};
        const proposals=view.origin==='CAMPAIGN'?[]:(view.budgetProposals||[]).filter(row=>['PROPOSED_MEASURE','PROPOSED_FUNDING'].includes(row.kind));
        host.replaceChildren();host.hidden=!proposals.length;
        if(!proposals.length)return;
        host.className=compact?'government-budget-compact':'government-budget public-panel public-stack';
        if(!compact)host.append(el('h2',copy.title),el('p',copy.note,'government-meta'));
        for(const proposal of proposals){
            const item=el('article',null,'government-budget-item');
            const label=el('p',copy[proposal.kind],'government-budget-label');label.setAttribute('data-proposal-kind',proposal.kind);
            item.append(label,el(compact?'p':'h3',local(proposal.summary),'government-budget-summary'));
            if(!compact){
                item.append(el('p',local(proposal.detail),'government-budget-detail'));
                const source=proposal.source;
                if(source){
                    const reference=el('div',null,'government-budget-reference'),url=model.sourceUrl(source.url);
                    if(url){const link=el('a',copy.source+' · '+local(source.title));const page=source.pdfPages?.find(Number.isInteger);link.href=url.split('#')[0]+(page>0?'#page='+page:'');link.rel='noopener noreferrer';reference.append(link);}
                    reference.append(el('p',copy.pages+': '+(source.printedPages||[]).join('، ')+' · '+copy.pdfPages+': '+(source.pdfPages||[]).join('، ')),el('p',copy.published+': '+model.date(source.publishedOn,locale)+' · '+copy.reviewed+': '+model.date(proposal.reviewedOn,locale)),el('p',copy.host));
                    item.append(reference);
                }
            }
            host.append(item);
        }
    }
    const MILESTONE_COPY={
        ar:{title:'من الوعد للتنفيذ',label:'مواد من البرنامج مرتبطة بمقترحات مالية 2027',note:'إجراءات وتمويل مقترح، ماشي إنجاز فعلي.',open:'شوف المقترحات',close:'خبي المقترحات',details:'شوف التفاصيل والمصدر',reviewed:'التقييمات المنشورة',search:'قلب فالوعود والتطورات',all:'جميع التقييمات المنشورة',context:'حسب الموضوع والبحث المختار'},
        fr:{title:'De la promesse à la mise en œuvre',label:'entrées du programme liées aux propositions du PLF 2027',note:'Mesures et financements proposés, pas des réalisations.',open:'Voir les propositions',close:'Masquer les propositions',details:'Voir le détail et la source',reviewed:'Évaluations publiées',search:'Rechercher un engagement ou une évolution',all:'Toutes les évaluations publiées',context:'Selon le thème et la recherche sélectionnés'},
        en:{title:'From promise to implementation',label:'programme entries linked to 2027 Finance Bill proposals',note:'Proposed measures and funding, not delivered results.',open:'Explore proposals',close:'Hide proposals',details:'View details and source',reviewed:'Published assessments',search:'Search commitments and developments',all:'All published assessments',context:'For the selected topic and search'}
    };
    function renderMilestone(host,insights,{locale='ar',document:doc=globalThis.document,expandable=false,expanded=false}={}){
        const words=MILESTONE_COPY[locale]||MILESTONE_COPY.ar;
        const entries=[...new Map((insights?.budgetEntries||[]).filter(row=>row?.slug).map(row=>[row.slug,row])).values()];
        const el=(tag,text,cls)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;if(cls)node.className=cls;return node;};
        host.replaceChildren();host.hidden=!entries.length;if(!entries.length)return;
        host.className='government-budget-milestone';
        const intro=el('div',null,'government-budget-intro'),copy=el('div'),count=el('strong',model.number(entries.length,locale),'government-budget-count');
        copy.append(el('h3',words.title),el('p',words.label),el('p',words.note,'government-meta'));
        intro.append(count,copy);host.append(intro);
        if(!expandable){const link=el('a',words.open,'text-link');link.href='/government?tab=delivery&origin=GOVERNMENT#budget-proposals';host.append(link);return;}
        const button=el('button',words.open,'secondary-button'),grid=el('div',null,'government-card-grid');
        button.type='button';button.id='trackerBudgetToggle';grid.id='budget-proposals';grid.hidden=!expanded;button.setAttribute('aria-controls',grid.id);
        function sync(){button.setAttribute('aria-expanded',String(!grid.hidden));button.textContent=grid.hidden?words.open:words.close;}
        button.onclick=()=>{grid.hidden=!grid.hidden;sync();};sync();
        for(const entry of entries){const card=el('article',null,'public-panel government-commitment-card'),heading=el('h3',model.localized(entry.title,locale)),summary=el('p',model.localized(entry.summary,locale)),link=el('a',words.details,'text-link');heading.setAttribute('dir','auto');summary.setAttribute('dir','auto');link.href='/government/commitments/'+encodeURIComponent(entry.slug);card.append(heading,summary,link);grid.append(card);}
        host.append(button,grid);
    }
    return {COPY,MILESTONE_COPY,render,renderMilestone};
});
