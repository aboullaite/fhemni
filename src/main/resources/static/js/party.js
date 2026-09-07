(function () {
    let profile;
    let programme;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function people() {
        return window.FhemniPeople;
    }

    async function load() {
        const code = decodeURIComponent(window.location.pathname.split('/').filter(Boolean).at(-1) || '');
        try {
            const programmeRequest = window.FhemniCatalog.requestJson(
                `/api/catalog/parties/${encodeURIComponent(code)}/programme`)
                .catch(error => {
                    if (error.status === 404) return null;
                    throw error;
                });
            [profile, programme] = await Promise.all([
                window.FhemniCatalog.requestJson(`/api/catalog/parties/${encodeURIComponent(code)}`),
                programmeRequest
            ]);
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
        const symbol = document.querySelector('#partySymbol');
        symbol.replaceChildren(people().partySymbol(profile, true));
        const hasProgramme = renderProgramme();

        const stats = document.querySelector('#partyStats');
        stats.replaceChildren();
        const members = document.createElement('span');
        members.className = 'person-stat';
        members.textContent = t('parties.members', { count: profile.members });
        const episodes = document.createElement('span');
        episodes.className = 'person-stat';
        episodes.textContent = t('parties.episodes', { count: profile.appearances });
        stats.append(members, episodes);

        document.querySelector('#partyEpisodesCount').textContent =
            t('parties.episodes', { count: profile.episodes.length });
        const episodeGrid = document.querySelector('#partyEpisodes');
        episodeGrid.replaceChildren();
        if (!profile.episodes.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('party.noEpisodes');
            episodeGrid.append(empty);
        } else {
            profile.episodes.forEach(episode => episodeGrid.append(episodeCard(episode)));
        }

        setupTabs(hasProgramme);

        document.title = `${profile.code} — Fhemni`;
    }

    function renderProgramme() {
        const section = document.querySelector('#partyProgramme');
        const tab = document.querySelector('#partyProgrammeTab');
        if (!programme) {
            section.hidden = true;
            tab.hidden = true;
            return false;
        }
        tab.hidden = false;
        document.querySelector('#partyProgrammeSummary').textContent = localized(programme.summary);
        const source = document.querySelector('#partyProgrammeSource');
        source.href = programme.sourceUrl;
        source.title = programme.sourceLabel;
        const grid = document.querySelector('#partyPromises');
        grid.replaceChildren();
        programme.promises.forEach(promise => grid.append(promiseCard(promise)));
        return true;
    }

    function setupTabs(hasProgramme) {
        const programmeTab = document.querySelector('#partyProgrammeTab');
        const episodesTab = document.querySelector('#partyEpisodesTab');
        programmeTab.onclick = () => selectTab('programme');
        episodesTab.onclick = () => selectTab('episodes');
        selectTab(hasProgramme ? 'programme' : 'episodes');
    }

    function selectTab(name) {
        const programmeSelected = name === 'programme' && programme;
        const programmeTab = document.querySelector('#partyProgrammeTab');
        const episodesTab = document.querySelector('#partyEpisodesTab');
        const programmePanel = document.querySelector('#partyProgramme');
        const episodesPanel = document.querySelector('#partyEpisodesSection');
        programmeTab.setAttribute('aria-selected', String(Boolean(programmeSelected)));
        programmeTab.tabIndex = programmeSelected ? 0 : -1;
        episodesTab.setAttribute('aria-selected', String(!programmeSelected));
        episodesTab.tabIndex = programmeSelected ? -1 : 0;
        programmePanel.hidden = !programmeSelected;
        episodesPanel.hidden = Boolean(programmeSelected);
    }

    function episodeCard(episode) {
        const article = document.createElement('article');
        article.className = 'catalog-card';
        const imageLink = document.createElement('a');
        imageLink.className = 'catalog-thumbnail';
        imageLink.href = `/videos/${encodeURIComponent(episode.slug)}`;
        const image = document.createElement('img');
        image.src = window.FhemniCatalog.safeImage(episode.thumbnailUrl, episode.youtubeVideoId);
        image.alt = '';
        image.loading = 'lazy';
        imageLink.append(image);
        const body = document.createElement('div');
        body.className = 'catalog-card-body';
        const title = document.createElement('h3');
        title.dir = 'auto';
        const link = document.createElement('a');
        link.href = imageLink.href;
        link.textContent = episode.title;
        title.append(link);
        const meta = document.createElement('p');
        meta.className = 'catalog-card-meta';
        meta.textContent = window.FhemniCatalog.formatDate(episode.publishedOn);
        const action = document.createElement('a');
        action.className = 'text-link';
        action.href = imageLink.href;
        action.textContent = t('party.viewEpisode');
        body.append(title, meta, action);
        article.append(imageLink, body);
        return article;
    }

    function promiseCard(promise) {
        const article = document.createElement('article');
        article.className = 'promise-card';
        const top = document.createElement('div');
        top.className = 'promise-card-topline';
        const topic = document.createElement('span');
        topic.className = 'section-kicker';
        topic.textContent = promise.topic;
        const verdict = document.createElement('span');
        verdict.className = `feasibility-badge ${String(promise.verdict).toLowerCase().replace('_', '-')}`;
        verdict.textContent = t(`promise.verdict.${promise.verdict}`);
        const verdictDescription = t(`promise.verdictDescription.${promise.verdict}`);
        verdict.title = verdictDescription;
        verdict.setAttribute('aria-label', verdictDescription);
        top.append(topic, verdict);
        const title = document.createElement('h3');
        const link = document.createElement('a');
        link.href = `/promises/${encodeURIComponent(promise.slug)}`;
        link.textContent = localized(promise.title);
        title.append(link);
        const summary = document.createElement('p');
        summary.dir = 'auto';
        summary.textContent = localized(promise.assessmentSummary);
        const more = document.createElement('a');
        more.className = 'text-link';
        more.href = link.href;
        more.textContent = t('programme.readAssessment');
        article.append(top, title, summary, more);
        return article;
    }

    function localized(value) {
        const locale = window.FhemniPeople?.locale() || 'ar';
        return value?.[locale] || value?.ar || value?.fr || value?.en || '';
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (profile) render();
    });
})();
