(function (root) {
    const initialized = new WeakSet();
    function initSiteShell(doc = document) {
        doc.querySelectorAll('[data-shell-toggle]').forEach(toggle => {
            if (initialized.has(toggle)) return;
            const panel=doc.getElementById(toggle.getAttribute('aria-controls'));
            if (!panel) return;
            initialized.add(toggle);
            toggle.hidden=false;
            const setOpen=open=>{
                toggle.setAttribute('aria-expanded',String(open));
                panel.hidden=!open;
            };
            setOpen(false);
            const electionLink=toggle.tagName==='A' && toggle.getAttribute('aria-controls')==='shellElectionLinks';
            let canCollapse=()=>true;
            if(!electionLink) toggle.addEventListener('click',()=>setOpen(toggle.getAttribute('aria-expanded')!=='true'));
            const escape=event=>{
                if(event.key!=='Escape' || !canCollapse() || toggle.hidden || toggle.getAttribute('aria-expanded')!=='true') return;
                event.preventDefault(); event.stopPropagation(); setOpen(false); toggle.focus();
            };
            panel.addEventListener('keydown',escape);
            toggle.addEventListener('keydown',event=>{
                if(electionLink && event.key==='ArrowDown') {
                    event.preventDefault();setOpen(true);panel.querySelector('a')?.focus();
                } else escape(event);
            });
            if (toggle.getAttribute('aria-controls')==='shellElectionLinks') {
                const group=toggle.closest('.shell-election-group');
                const view=doc.defaultView;
                const hover=view?.matchMedia('(min-width: 1100px) and (hover: hover) and (pointer: fine)');
                if(group && view) {
                    group.setAttribute('data-shell-enhanced','true');
                    canCollapse=()=>Boolean(hover?.matches);
                    setOpen(!hover?.matches);
                    let closeTimer;
                    const cancelClose=()=>view.clearTimeout(closeTimer);
                    const close=()=>{cancelClose();setOpen(!hover?.matches);};
                    group.addEventListener('pointerenter',event=>{
                        cancelClose();
                        if(hover?.matches && event.pointerType==='mouse') setOpen(true);
                    });
                    group.addEventListener('pointerleave',event=>{
                        if(!hover?.matches || event.pointerType!=='mouse') return;
                        cancelClose();
                        closeTimer=view.setTimeout(()=>{if(!panel.contains(doc.activeElement))setOpen(false);},180);
                    });
                    group.addEventListener('focusout',event=>{if(!group.contains(event.relatedTarget))close();});
                    doc.addEventListener('pointerdown',event=>{if(!group.contains(event.target))close();});
                    hover?.addEventListener('change',()=>{
                        if(hover?.matches && panel.contains(doc.activeElement)) toggle.focus();
                        close();
                    });
                }
            }
            if (toggle.getAttribute('aria-controls')==='shellPrimaryNav') {
                const desktop=doc.defaultView?.matchMedia('(min-width: 1100px)');
                if (desktop) {
                    const resize=()=>{
                        const focused=doc.activeElement;
                        toggle.hidden=desktop.matches;
                        setOpen(desktop.matches);
                        if(desktop.matches && focused===toggle) panel.querySelector('a')?.focus();
                        else if(!desktop.matches && panel.contains(focused)) toggle.focus();
                    };
                    resize(); desktop.addEventListener('change',resize);
                }
            }
        });
    }
    if(typeof module==='object' && module.exports) module.exports={initSiteShell};
    else {root.FhemniShell={initSiteShell}; document.addEventListener('DOMContentLoaded',()=>initSiteShell());}
})(typeof window==='object'?window:globalThis);
