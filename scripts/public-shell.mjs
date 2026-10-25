// Canonical public shell. Page bodies and administrative controls are not generated here.
export const PUBLIC_PAGES = {
    'government.html':'/government', 'commitment.html':'/government/commitments/', 'measure.html':'/government/measures', 'analyse.html':'/analyse',
    'following.html':'/following', 'government-history.html':'/government/history', 'government-ministers.html':'/government/ministers',
    'my-analyses.html':'/account/analyses', 'member-analysis.html':'/account/analyses/',
    'index.html':'/', '404.html':'/404', 'analysis.html':'/analyses/',
    'community.html':'/community', 'compare-programmes.html':'/parties/compare',
    'election-results.html':'/elections/2026', 'historical-elections.html':'/elections/history',
    'login.html':'/login', 'methodology.html':'/methodology', 'parties.html':'/parties',
    'party.html':'/parties/', 'person.html':'/people/', 'promise.html':'/promises/',
    'priorities.html':'/priorities', 'privacy.html':'/privacy', 'terms.html':'/terms',
    'video.html':'/videos/', 'videos.html':'/videos'
};
const link = (href,key,text,current='') => `<a href="${href}" data-i18n="${key}"${current === href ? ' aria-current="page"' : ''}>${text}</a>`;
export function renderPublicShell({pagePath='/'}) {
    const electionArea = ['/elections', '/parties', '/promises', '/priorities'].some(prefix=>pagePath.startsWith(prefix));
    const header = `<header class="site-header public-header shared-header">
    <a class="brand" href="/" aria-label="Fhemni home" data-i18n-aria-label="common.brandHome">
        <img class="brand-logo brand-logo-full" src="/assets/brand/fhemni-logo.png" alt="">
        <img class="brand-logo brand-logo-icon" src="/assets/brand/fhemni-icon.png" alt="">
    </a>
    <button class="shell-mobile-toggle" type="button" data-shell-toggle aria-controls="shellPrimaryNav" aria-expanded="false" hidden><span data-i18n="shell.menu">القائمة</span><span aria-hidden="true">☰</span></button>
    <nav class="primary-nav" id="shellPrimaryNav" aria-label="Primary navigation" data-i18n-aria-label="common.primaryNavigation">
        ${link('/','shell.home','الرئيسية',pagePath)}
        ${link('/government','shell.government','تتبع الحكومة',pagePath)}
        ${link('/analyse','shell.analyse','حلّل فيديو',pagePath)}
        <div class="shell-election-group">
            <div class="shell-election-heading"${electionArea?' data-active-area="true"':''}><a href="/elections/2026" data-shell-toggle aria-controls="shellElectionLinks" aria-expanded="false" data-i18n="shell.elections">الانتخابات</a></div>
            <div id="shellElectionLinks" class="shell-election-links">
                ${link('/elections/2026','common.electionResults','نتائج الانتخابات')}
                ${link('/elections/2026#graphs','shell.graphs','الرسوم والتحليلات')}
                ${link('/elections/history','shell.history','مقارنة الانتخابات',pagePath)}
                ${link('/parties','shell.programmes','الأحزاب والبرامج',pagePath)}
                ${link('/elections/2026#electionGraphRepresentatives','shell.representatives','شكون كيمثلني؟')}
            </div>
        </div>
    </nav>
    <div class="header-actions">
        <label class="site-language"><span class="sr-only" data-i18n="common.siteLanguage">لغة الموقع</span><select data-site-language data-shell-language aria-label="لغة الموقع" data-i18n-aria-label="common.siteLanguage"><option value="ar">الدارجة</option><option value="fr">FR</option><option value="en">EN</option></select></label>
        <div id="authNav"></div>
    </div>
</header>`;
    const footer = `<footer class="shared-footer">
    <a href="/" class="footer-brand"><img class="footer-logo" src="/assets/brand/fhemni-logo.png" alt="Fhemni — فهّمني"></a>
    <div class="footer-navigation">${link('/methodology','common.methodology','المنهجية',pagePath)}${link('/videos','shell.archive','أرشيف الفيديوهات',pagePath)}${link('/community','shell.suggest','اقترح فيديو',pagePath)}${link('https://github.com/aboullaite/fhemni/issues','shell.report','بلّغ على مشكل')}</div>
    <span class="footer-legal-links" data-legal-links>${link('/terms','common.terms','شروط الاستخدام',pagePath)}${link('/privacy','common.privacyPolicy','الخصوصية',pagePath)}</span>
    <a href="https://x.com/laytoun" target="_blank" rel="me noopener noreferrer" data-i18n="common.footer">© 2026 Mohammed Aboullaite</a>
</footer>`;
    return {header,footer};
}

export function replaceShell(source,{header,footer}) {
    let result=source;
    for (const [name,markup] of Object.entries({header,footer})) {
        const start=`<!-- public-shell:${name}:start -->`, end=`<!-- public-shell:${name}:end -->`;
        const region=new RegExp(`${start}[\\s\\S]*?${end}`);
        const bounded=`${start}\n${markup}\n${end}`;
        if (region.test(result)) result=result.replace(region,()=>bounded);
        else {
            const original=new RegExp(`<${name}\\b[^>]*>[\\s\\S]*?<\\/${name}>`);
            if (!original.test(result)) throw new Error(`Missing shell ${name}`);
            result=result.replace(original,()=>bounded);
        }
    }
    return result;
}
