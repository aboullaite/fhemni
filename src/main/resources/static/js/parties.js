(function () {
    const grid = document.querySelector('#partiesGrid');
    const count = document.querySelector('#partiesCount');
    let lastResponse = [];

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function people() {
        return window.FhemniPeople;
    }

    async function load() {
        try {
            lastResponse = await window.FhemniCatalog.requestJson('/api/catalog/parties');
            render();
        } catch (error) {
            count.textContent = '';
            window.FhemniCatalog.renderError(grid, error.message);
        }
    }

    function render() {
        grid.replaceChildren();
        if (!lastResponse.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('parties.empty');
            grid.append(empty);
            count.textContent = '';
            return;
        }
        lastResponse.forEach(party => grid.append(partyCard(party)));
        count.textContent = t('parties.count', { count: lastResponse.length });
    }

    function partyCard(party) {
        const article = document.createElement('article');
        article.className = 'catalog-card';
        const body = document.createElement('div');
        body.className = 'catalog-card-body';

        const top = document.createElement('div');
        top.className = 'person-card-top';
        const symbol = people().partySymbol(party, true);
        const title = document.createElement('h3');
        title.className = 'person-card-title';
        title.dir = 'auto';
        const link = document.createElement('a');
        link.href = `/parties/${encodeURIComponent(party.code)}`;
        link.textContent = people().partyDisplayName(party);
        title.append(link);
        top.append(symbol, title);

        const alt = document.createElement('p');
        alt.className = 'catalog-card-meta';
        alt.dir = 'auto';
        alt.textContent = (party.memberPartyCodes || [party.code]).join(' + ');

        const stats = document.createElement('div');
        stats.className = 'person-stats';
        const members = document.createElement('span');
        members.className = 'person-stat';
        members.textContent = t('parties.members', { count: party.members });
        const episodes = document.createElement('span');
        episodes.className = 'person-stat';
        episodes.textContent = t('parties.episodes', { count: party.appearances });
        stats.append(members, episodes);

        body.append(top, alt, stats);
        article.append(body);
        return article;
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (lastResponse.length) render();
    });
})();
