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
            toggle.addEventListener('click',()=>setOpen(toggle.getAttribute('aria-expanded')!=='true'));
            const escape=event=>{
                if(event.key!=='Escape' || toggle.hidden || toggle.getAttribute('aria-expanded')!=='true') return;
                event.preventDefault(); event.stopPropagation(); setOpen(false); toggle.focus();
            };
            panel.addEventListener('keydown',escape);
            toggle.addEventListener('keydown',escape);
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
