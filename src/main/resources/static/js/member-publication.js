(function(root,factory){const api=factory(root);if(typeof module==='object'&&module.exports)module.exports=api;else document.addEventListener('DOMContentLoaded',api.init);})(typeof window==='object'?window:globalThis,function(root){
    const COPY={ar:{title:'طلبات نشر التحليلات',intro:'غير التقارير اللي طلب أصحابها النشر. المحادثات الخاصة ما كتبانش هنا. زيد الفيديو للأرشيف قبل نشر نسخة مراجعة.',open:'فتح التقرير للمراجعة',note:'ملاحظة المراجعة',accept:'قبول للمراجعة النهائية',reject:'رفض الطلب',publish:'نشر نسخة مستقلة',confirm:'تنشر نسخة مستقلة من هاد التقرير؟ الأصل والمحادثات غيبقاو خاصين.',REQUESTED:'طلب جديد',REVIEWED:'مراجع',REJECTED:'مرفوض',PUBLISHED:'منشور'},fr:{title:'Demandes de publication',intro:'Uniquement les rapports proposés par leurs propriétaires. Aucune conversation privée. Ajoutez la vidéo au catalogue avant de publier une version révisée.',open:'Ouvrir le rapport',note:'Note de révision',accept:'Valider la révision',reject:'Refuser',publish:'Publier une version distincte',confirm:'Publier une version distincte ? Le rapport original et les conversations restent privés.',REQUESTED:'Demandée',REVIEWED:'Révisée',REJECTED:'Refusée',PUBLISHED:'Publiée'},en:{title:'Analysis publication requests',intro:'Only reports explicitly submitted by their owners. No private conversations. Add the video to the catalogue before publishing a reviewed version.',open:'Open report for review',note:'Review note',accept:'Approve review',reject:'Reject request',publish:'Publish separate version',confirm:'Publish a separate version? The original report and conversations remain private.',REQUESTED:'Requested',REVIEWED:'Reviewed',REJECTED:'Rejected',PUBLISHED:'Published'}};
    function reportText(job){return JSON.stringify(job.report||{},null,2);}
    const extra={
        ar:{summary:'الخلاصة',details:'تفاصيل التحليل',claims:'الادعاءات والأدلة',source:'الفيديو الأصلي',evidence:'المصادر',speaker:'المتحدث',assessment:'التقييم',none:'ما كاين حتى ادعاء فهاد التقرير.',noEvidence:'ما كايناش مصادر مرفقة بهاد الادعاء.',reviewSaved:'تقبلات المراجعة. النشر باقي خطوة منفصلة.',rejectSaved:'ترفض طلب النشر.',publishedSaved:'تنشرات نسخة مستقلة. الأصل باقي خاص.',publicReport:'شوف النسخة المنشورة',openFailed:'تعذّر فتح التقرير المطلوب. عاود فتحه من اللائحة.'},
        fr:{summary:'Résumé',details:'Analyse détaillée',claims:'Affirmations et preuves',source:'Vidéo source',evidence:'Sources',speaker:'Intervenant',assessment:'Évaluation',none:'Aucune affirmation dans ce rapport.',noEvidence:'Aucune source jointe à cette affirmation.',reviewSaved:'Révision approuvée. La publication reste une étape distincte.',rejectSaved:'Demande de publication refusée.',publishedSaved:'Version distincte publiée. L’original reste privé.',publicReport:'Voir la version publiée',openFailed:'Impossible d’ouvrir le rapport demandé. Réessayez depuis la liste.'},
        en:{summary:'Summary',details:'Detailed analysis',claims:'Claims and evidence',source:'Source video',evidence:'Sources',speaker:'Speaker',assessment:'Assessment',none:'No claims in this report.',noEvidence:'No sources attached to this claim.',reviewSaved:'Review approved. Publication remains a separate step.',rejectSaved:'Publication request rejected.',publishedSaved:'Separate version published. The original remains private.',publicReport:'View published version',openFailed:'The requested report could not be opened. Try opening it again from the list.'}
    };
    for(const locale of Object.keys(COPY))Object.assign(COPY[locale],extra[locale]);
    function safeUrl(value){try{const url=new URL(value);return ['http:','https:'].includes(url.protocol)&&!url.username&&!url.password?url.href:null;}catch{return null;}}
    function createSelection(){let version=0,value=null;return {get value(){return value;},begin(){value=null;return ++version;},resolve(token,next){if(token!==version)return false;value=next;return true;},fail(token){if(token!==version)return false;value=null;return true;}};}
    function renderReport(host,job,locale,doc){
        const copy=COPY[locale],report=job.report||{},el=(tag,text)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;return node;};
        const prose=(tag,text)=>{const node=el(tag,text);node.lang=job.language||locale;node.dir='auto';return node;};
        host.replaceChildren();host.className='public-stack member-publication-report';
        const source=safeUrl(job.videoUrl);if(source){const link=el('a',copy.source);link.href=source;link.rel='noopener noreferrer';host.append(link);}
        host.append(el('h4',copy.summary),prose('p',report.summary||''));
        if(report.detailedSummary)host.append(el('h4',copy.details),prose('p',report.detailedSummary));
        host.append(el('h4',copy.claims));if(!report.claims?.length)host.append(el('p',copy.none));
        for(const claim of report.claims||[]){
            const article=el('article');article.className='public-stack member-publication-claim';article.append(prose('h5',claim.statement));
            if(claim.speaker)article.append(prose('p',`${copy.speaker}: ${claim.speaker}`));
            if(source&&Number.isInteger(claim.startSeconds)&&claim.startSeconds>=0){const url=new URL(source);url.searchParams.set('t',String(claim.startSeconds));const link=el('a',`${copy.source} · ${Math.floor(claim.startSeconds/60)}:${String(claim.startSeconds%60).padStart(2,'0')}`);link.href=url.href;link.rel='noopener noreferrer';article.append(link);}
            if(claim.verdict){const translated=root.FhemniI18n?.t('label.'+claim.verdict);article.append(el('p',`${copy.assessment}: ${translated&&translated!=='label.'+claim.verdict?translated:claim.verdict}`));}
            if(claim.explanation)article.append(prose('p',claim.explanation));if(claim.evidenceStrength)article.append(prose('p',claim.evidenceStrength));
            article.append(el('h6',copy.evidence));if(!claim.sources?.length)article.append(el('p',copy.noEvidence));
            for(const item of claim.sources||[]){const url=safeUrl(item.url),citation=prose(url?'a':'p',item.title||copy.evidence);if(url){citation.href=url;citation.rel='noopener noreferrer';}article.append(citation);if(item.publishedDate){const date=el('time',item.publishedDate);date.dir='ltr';article.append(date);}}
            host.append(article);
        }
    }
    function init(){
        const doc=root.document,host=doc.getElementById('memberPublication');if(!host)return;
        const api=root.FhemniFollowing,base='/api/admin/member-analysis-publication-requests',notes=new Map(),selection=createSelection();
        let rows=[],total=0,page=1,pager,state='loading',busy=false,epoch=0,feedback='',opening=false;
        const locale=()=>root.FhemniI18n?.locale()||'ar',el=(tag,text)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;return node;};
        function render(){
            const copy=COPY[locale()],common=root.FhemniGovernmentModel.COPY[locale()],selected=selection.value;
            doc.getElementById('memberPublicationTitle').textContent=copy.title;doc.getElementById('memberPublicationIntro').textContent=copy.intro;
            doc.getElementById('memberPublicationStatus').textContent=feedback?(copy[feedback]||common[feedback]):state==='ready'?'':common[state==='error'?'error':'loading'];
            const retry=doc.getElementById('memberPublicationRetry');retry.textContent=common.retry;retry.hidden=state!=='error';host.setAttribute('aria-busy',String(busy||state==='loading'));
            host.replaceChildren();
            for(const row of rows){const item=el('article');item.className='public-panel public-stack';const title=row.title||row.videoUrl||copy.open,heading=el('h3',title);heading.dir='auto';item.append(heading);
                const source=safeUrl(row.videoUrl);if(source){const link=el('a',source);link.href=source;link.dir='ltr';link.rel='noopener noreferrer';item.append(link);}
                const open=el('button',copy.open);open.type='button';open.disabled=busy||state==='loading';open.setAttribute('aria-label',`${copy.open}: ${title}`);open.onclick=()=>loadReport(row.id);
                item.append(el('span',copy[row.state]),el('time',root.FhemniGovernmentModel.date(row.requestedAt,locale())),open);host.append(item);}
            if(!rows.length&&state==='ready')host.append(el('p',common.empty));
            const paging={page,pages:Math.max(1,Math.ceil(total/10)),total,locale:locale()};if(!pager){pager=root.FhemniUI.createPager({...paging,onChange:load});doc.getElementById('memberPublicationPager').append(pager);}else pager.update(paging);
            if(busy||state==='loading')for(const button of pager.querySelectorAll('button'))button.disabled=true;
            const preview=doc.getElementById('memberPublicationPreview');preview.replaceChildren();preview.hidden=!selected&&!opening&&feedback!=='openFailed';preview.setAttribute('aria-busy',String(busy));
            if(!selected){if(opening||feedback==='openFailed'){const heading=el('h3',opening?common.loading:copy.openFailed);heading.tabIndex=-1;preview.append(heading);}return;}
            const title=el('h3',selected.report.report?.title||copy.open);title.tabIndex=-1;title.dir='auto';preview.append(title,el('p',copy[selected.request.state]));
            const report=el('div');renderReport(report,selected.report,locale(),doc);preview.append(report);
            const publicPath=rows.find(row=>row.id===selected.request.id)?.publicPath;
            if(selected.request.state==='PUBLISHED'&&/^\/videos\/[^/?#]+$/.test(publicPath||'')){const link=el('a',copy.publicReport);link.href=publicPath;preview.append(link);}
            if(selected.request.state==='REQUESTED'){const label=el('label',copy.note),note=el('textarea');label.className='member-publication-note';note.maxLength=4000;note.required=true;note.value=notes.get(selected.request.id)||'';note.disabled=busy||state==='loading';note.oninput=()=>notes.set(selected.request.id,note.value);label.append(note);preview.append(label);
                for(const[accept,key]of [[true,'accept'],[false,'reject']]){const button=el('button',copy[key]);button.type='button';button.disabled=busy||state==='loading';button.onclick=()=>{if(!note.value.trim()){note.focus();return;}return act('/review',{accept,note:note.value});};preview.append(button);}}
            if(selected.request.state==='REVIEWED'){const publish=el('button',copy.publish);publish.type='button';publish.disabled=busy||state==='loading';publish.onclick=()=>{if(root.confirm(copy.confirm))return act('/publish',{});};preview.append(publish);}
            if(selected.request.reviewNote)preview.append(el('p',selected.request.reviewNote));
        }
        async function load(next=page){const version=++epoch;state='loading';render();try{const data=await api.request(base+'?page='+next);if(version!==epoch)return;rows=data.items;total=data.totalItems;page=next;state='ready';render();}catch{if(version!==epoch)return;state='error';render();}}
        function focusPreview(){doc.getElementById('memberPublicationPreview').querySelector('h3')?.focus();}
        async function loadReport(id){if(busy)return;const token=selection.begin();busy=true;opening=true;feedback='';render();try{selection.resolve(token,await api.request(base+'/'+id));}catch{if(selection.fail(token))feedback='openFailed';}finally{busy=false;opening=false;render();focusPreview();}}
        async function act(path,body){const selected=selection.value;if(busy||!selected)return;busy=true;feedback='';render();const id=selected.request.id;
            try{await api.request(base+'/'+id+path,'POST',body);notes.delete(id);const token=selection.begin();selection.resolve(token,await api.request(base+'/'+id));await load();feedback=path==='/publish'?'publishedSaved':body.accept?'reviewSaved':'rejectSaved';}
            catch{feedback='error';}finally{busy=false;render();focusPreview();root.FhemniUI.announceStatus(doc.getElementById('memberPublicationStatus'),COPY[locale()][feedback]||root.FhemniGovernmentModel.COPY[locale()].error);}}
        doc.getElementById('memberPublicationRetry').onclick=()=>load();doc.addEventListener('fhemni:localechange',render);load();
    }
    return {COPY,reportText,createSelection,renderReport,init};
});
