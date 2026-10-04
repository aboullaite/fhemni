(function(root,factory){
    const api=factory();
    if(typeof module==='object'&&module.exports) module.exports=api;
    else root.FhemniUI=api;
})(typeof window==='object'?window:globalThis,function(){
    function paginate(items,requested=1){
        const pages=Math.max(1,Math.ceil(items.length/10));
        const page=Number.isInteger(Number(requested))&&Number(requested)>0?Math.min(pages,Number(requested)):1;
        return {items:items.slice((page-1)*10,page*10),page,pages,total:items.length};
    }
    const FORMS={
        promise:{ar:['لا وعود','وعد واحد','وعدان','{n} وعود','{n} وعداً','{n} وعد'],dual:'وعدين',en:['promise','promises'],fr:['promesse','promesses']},
        update:{ar:['لا مستجدات','مستجد واحد','مستجدان','{n} مستجدات','{n} مستجداً','{n} مستجد'],dual:'مستجدين',en:['update','updates'],fr:['mise à jour','mises à jour']},
        result:{ar:['لا نتائج','نتيجة واحدة','نتيجتان','{n} نتائج','{n} نتيجةً','{n} نتيجة'],dual:'نتيجتين',en:['result','results'],fr:['résultat','résultats']}
    };
    function countPhrase(value,noun='result',locale='ar',options={}){
        const count=Number(value), forms=FORMS[noun]||FORMS.result;
        const lang=['ar','fr','en'].includes(locale)?locale:'ar';
        const formatted=new Intl.NumberFormat(lang==='ar'?'ar-MA-u-nu-latn':lang).format(count);
        if(lang!=='ar') return `${formatted} ${forms[lang][count===1?0:1]}`;
        const category=new Intl.PluralRules('ar').select(count);
        if(category==='two'&&options.oblique) return forms.dual;
        return forms.ar[['zero','one','two','few','many','other'].indexOf(category)].replace('{n}',formatted);
    }
    // Same version-fenced repeat semantics as the existing election announcer.
    const versions=new WeakMap();
    function announceStatus(node,text,schedule=callback=>queueMicrotask(callback)){
        const version=(versions.get(node)||0)+1; versions.set(node,version);
        if(node.textContent===text){node.textContent='';schedule(()=>{if(versions.get(node)===version)node.textContent=text;});}
        else node.textContent=text;
    }
    function mountTabs(root,{selected,onChange=()=>{}}={}){
        const tabs=[...root.querySelectorAll('[role="tab"]')],doc=root.ownerDocument;
        const removers=[];
        function select(id,notify=false){
            if(!tabs.some(tab=>tab.id===id)) id=tabs[0]?.id;
            const losingFocus=tabs.some(tab=>tab.id!==id && doc.getElementById(tab.getAttribute('aria-controls'))?.contains?.(doc.activeElement));
            tabs.forEach(tab=>{
                const active=tab.id===id;
                tab.setAttribute('aria-selected',String(active));tab.setAttribute('tabindex',active?'0':'-1');
                const panel=doc.getElementById(tab.getAttribute('aria-controls'));if(panel)panel.hidden=!active;
            });
            if(losingFocus)tabs.find(tab=>tab.id===id)?.focus();
            if(notify)onChange(id);
        }
        tabs.forEach((tab,index)=>{
            const click=()=>select(tab.id,true);
            const keydown=event=>{
                let next;
                if(event.key==='Home')next=0;
                else if(event.key==='End')next=tabs.length-1;
                else if(['ArrowLeft','ArrowRight'].includes(event.key)){
                    const forward=event.key===(doc.documentElement.dir==='rtl'?'ArrowLeft':'ArrowRight');
                    next=(index+(forward?1:-1)+tabs.length)%tabs.length;
                }else return;
                event.preventDefault();select(tabs[next].id,true);tabs[next].focus();
            };
            tab.addEventListener('click',click);tab.addEventListener('keydown',keydown);
            removers.push(()=>{tab.removeEventListener('click',click);tab.removeEventListener('keydown',keydown);});
        });
        select(selected);return {select,destroy(){removers.forEach(remove=>remove());}};
    }
    function createPager({page,pages,total,onChange,locale='ar',document:doc=globalThis.document}){
        const nav=doc.createElement('nav');nav.className='public-pager';
        const previous=doc.createElement('button'),next=doc.createElement('button'),status=doc.createElement('span');
        previous.type=next.type='button';
        status.setAttribute('aria-live','polite');
        status.setAttribute('aria-atomic','true');
        nav.update=state=>{
            ({page,pages,total,locale}=state);
            const copy={ar:['السابق','التالي','الصفحة','من'],fr:['Précédent','Suivant','Page','sur'],en:['Previous','Next','Page','of']}[locale]||['Previous','Next','Page','of'];
            const isolate=value=>locale==='ar'?`\u2068${value}\u2069`:String(value);
            previous.textContent=copy[0];next.textContent=copy[1];nav.setAttribute('aria-label',copy[2]);
            previous.disabled=page<=1;next.disabled=page>=pages;
            const range=`${total?(page-1)*10+1:0}–${Math.min(page*10,total)} ${copy[3]} ${total}`;
            announceStatus(status,`${isolate(range)} · ${copy[2]} ${isolate(`${page} / ${pages}`)} · ${countPhrase(total,'result',locale)}`);
        };
        previous.addEventListener('click',()=>{if(!previous.disabled)onChange(page-1);});
        next.addEventListener('click',()=>{if(!next.disabled)onChange(page+1);});
        nav.append(previous,status,next);nav.update({page,pages,total,locale});return nav;
    }
    return {paginate,countPhrase,announceStatus,mountTabs,createPager};
});
