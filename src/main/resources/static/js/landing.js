(function () {
    let videos = [];
    let promises = [];

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function localized(value) {
        const locale = window.FhemniI18n?.locale() || 'ar';
        return value?.[locale] || value?.ar || value?.fr || value?.en || '';
    }

    function verdictClass(verdict) {
        if (verdict === 'POSSIBLE') return 'possible';
        if (verdict === 'HARD') return 'hard';
        if (verdict === 'NOT_ACHIEVABLE') return 'not-achievable';
        return '';
    }

    function promiseCard(promise) {
        const article = document.createElement('article');
        article.className = 'featured-promise-card';

        const top = document.createElement('div');
        top.className = 'promise-card-topline';
        const party = document.createElement('span');
        party.className = 'featured-promise-party';
        party.textContent = promise.partyCode;
        const verdict = document.createElement('span');
        verdict.className = `feasibility-badge ${verdictClass(promise.verdict)}`;
        verdict.textContent = t(`promise.verdict.${promise.verdict}`);
        const actions = document.createElement('span');
        actions.className = 'promise-card-actions';
        actions.append(verdict, window.FhemniCatalog.createPromiseReportButton(promise.slug));
        top.append(party, actions);

        const title = document.createElement('h3');
        const link = document.createElement('a');
        link.href = `/promises/${encodeURIComponent(promise.slug)}`;
        link.textContent = localized(promise.title);
        title.append(link);

        const summary = document.createElement('p');
        summary.textContent = localized(promise.assessmentSummary);

        const action = document.createElement('a');
        action.className = 'text-link';
        action.href = link.href;
        action.textContent = t('landing.openCheck');
        article.append(top, title, summary, action);
        return article;
    }

    function renderPromises() {
        const container = document.querySelector('#featuredPromises');
        container.replaceChildren();
        if (!promises.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty featured-promises-empty';
            empty.textContent = t('landing.checksEmpty');
            container.append(empty);
            return;
        }
        promises.forEach(promise => container.append(promiseCard(promise)));
    }

    async function loadPromises() {
        const container = document.querySelector('#featuredPromises');
        try {
            promises = await window.FhemniCatalog.requestJson('/api/catalog/promises?size=3');
            renderPromises();
        } catch (error) {
            window.FhemniCatalog.renderError(container, error.message);
        }
    }

    async function loadFeatured() {
        const container = document.querySelector('#featuredVideos');
        try {
            const response = await window.FhemniCatalog.requestJson('/api/catalog/videos?size=3');
            videos = response.items;
            window.FhemniCatalog.renderGrid(container, videos);
        } catch (error) {
            window.FhemniCatalog.renderError(container, error.message);
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        loadPromises();
        loadFeatured();
    });
    document.addEventListener('fhemni:localechange', () => {
        renderPromises();
        if (videos.length) window.FhemniCatalog.renderGrid(document.querySelector('#featuredVideos'), videos);
    });
})();
