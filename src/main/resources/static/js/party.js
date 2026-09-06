(function () {
    let profile;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function people() {
        return window.FhemniPeople;
    }

    async function load() {
        const code = decodeURIComponent(window.location.pathname.split('/').filter(Boolean).at(-1) || '');
        try {
            profile = await window.FhemniCatalog.requestJson(`/api/catalog/parties/${encodeURIComponent(code)}`);
            render();
            document.querySelector('#partyLoading').hidden = true;
            document.querySelector('#partyDetail').hidden = false;
        } catch (error) {
            document.querySelector('#partyLoading').hidden = true;
            const panel = document.querySelector('#partyError');
            panel.textContent = error.message;
            panel.hidden = false;
        }
    }

    function render() {
        const name = people().partyDisplayName(profile);
        const alt = (window.FhemniPeople.locale() === 'ar' ? profile.nameFr : profile.nameAr) || '';
        document.querySelector('#partyName').textContent = `${profile.code} · ${name}`;
        document.querySelector('#partyNameAlt').textContent = alt;

        const stats = document.querySelector('#partyStats');
        stats.replaceChildren();
        const members = document.createElement('span');
        members.className = 'person-stat';
        members.textContent = t('parties.members', { count: profile.members });
        const episodes = document.createElement('span');
        episodes.className = 'person-stat';
        episodes.textContent = t('parties.episodes', { count: profile.appearances });
        const statements = document.createElement('span');
        statements.className = 'person-stat';
        statements.textContent = t('parties.statements', { count: profile.claims });
        stats.append(members, episodes, statements);

        document.querySelector('#partyMembersCount').textContent =
            t('parties.members', { count: profile.topMembers.length });
        const memberGrid = document.querySelector('#partyMembers');
        memberGrid.replaceChildren();
        if (!profile.topMembers.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('party.noMembers');
            memberGrid.append(empty);
        } else {
            profile.topMembers.forEach(member => memberGrid.append(memberCard(member)));
        }

        const claimGrid = document.querySelector('#partyStatements');
        claimGrid.replaceChildren();
        if (!profile.recentClaims.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('party.noStatements');
            claimGrid.append(empty);
        } else {
            const claimsByPerson = new Map();
            profile.recentClaims.forEach(claim => {
                if (!claimsByPerson.has(claim.personSlug)) claimsByPerson.set(claim.personSlug, []);
                claimsByPerson.get(claim.personSlug).push(claim);
            });
            profile.topMembers.forEach(member => {
                const claims = claimsByPerson.get(member.slug) || [];
                if (!claims.length) return;
                const heading = document.createElement('h3');
                heading.className = 'person-card-title person-group-heading span-all';
                heading.dir = 'auto';
                const link = document.createElement('a');
                link.href = `/people/${encodeURIComponent(member.slug)}`;
                link.textContent = people().personDisplayName(member);
                heading.append(link);
                const tally = document.createElement('span');
                tally.className = 'person-stat';
                tally.textContent = t('parties.statements', { count: claims.length });
                heading.append(' ', tally);
                claimGrid.append(heading);
                const list = document.createElement('ul');
                list.className = 'statement-list span-all';
                claims.forEach(claim => list.append(statementBullet(claim)));
                claimGrid.append(list);
            });
        }

        document.title = `${profile.code} — Fhemni`;
    }

    function memberCard(member) {
        const article = document.createElement('article');
        article.className = 'catalog-card';
        const body = document.createElement('div');
        body.className = 'catalog-card-body';
        const top = document.createElement('div');
        top.className = 'person-card-top';
        const shownName = people().personDisplayName(member);
        const title = document.createElement('h3');
        title.className = 'person-card-title';
        title.dir = 'auto';
        const link = document.createElement('a');
        link.href = `/people/${encodeURIComponent(member.slug)}`;
        link.textContent = shownName;
        title.append(link);
        top.append(title);
        const stats = document.createElement('div');
        stats.className = 'person-stats';
        const episodes = document.createElement('span');
        episodes.className = 'person-stat';
        episodes.textContent = t('parties.episodes', { count: member.appearances });
        stats.append(episodes);
        body.append(top, stats);
        article.append(body);
        return article;
    }

    function statementBullet(claim) {
        const item = document.createElement('li');
        const text = document.createElement('p');
        text.dir = 'auto';
        text.textContent = claim.statement;
        const meta = document.createElement('div');
        meta.className = 'statement-meta';
        const video = document.createElement('a');
        video.className = 'text-link';
        const startSeconds = Math.max(0, Math.floor(Number(claim.startSeconds) || 0));
        video.href = `/videos/${encodeURIComponent(claim.episodeSlug)}?t=${startSeconds}`;
        video.textContent = t('party.watchVideo');
        meta.append(video);
        if (claim.kind === 'FACT' && claim.verdict) {
            const verdict = document.createElement('span');
            verdict.className = 'catalog-status published';
            verdict.textContent = claim.verdict;
            meta.append(verdict);
        }
        item.append(text, meta);
        if (claim.explanation) {
            const details = document.createElement('details');
            const summary = document.createElement('summary');
            summary.textContent = t('party.details');
            const explanation = document.createElement('p');
            explanation.className = 'video-meta';
            explanation.dir = 'auto';
            explanation.textContent = claim.explanation;
            details.append(summary, explanation);
            item.append(details);
        }
        return item;
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (profile) render();
    });
})();
