(function () {
    const form = document.querySelector('#catalogFilters');
    const search = document.querySelector('#catalogSearch');
    const language = document.querySelector('#catalogLanguage');
    const grid = document.querySelector('#catalogVideos');
    const count = document.querySelector('#catalogCount');
    const pagination = document.querySelector('#catalogPagination');
    let currentPage = 0;
    let lastResponse;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    async function load(page = 0) {
        currentPage = page;
        grid.replaceChildren();
        const loading = document.createElement('div');
        loading.className = 'catalog-loading';
        loading.textContent = t('catalog.loading');
        grid.append(loading);
        const parameters = new URLSearchParams({ page: String(page), size: '12' });
        if (search.value.trim()) parameters.set('q', search.value.trim());
        if (language.value) parameters.set('language', language.value);
        try {
            lastResponse = await window.FhemniCatalog.requestJson(`/api/catalog/videos?${parameters}`);
            render();
        } catch (error) {
            count.textContent = '';
            pagination.replaceChildren();
            window.FhemniCatalog.renderError(grid, error.message);
        }
    }

    function render() {
        window.FhemniCatalog.renderGrid(grid, lastResponse.items);
        count.textContent = t('catalog.episodeCount', { count: lastResponse.totalElements });
        pagination.replaceChildren();
        if (lastResponse.totalPages <= 1) return;
        const previous = pageButton(t('catalog.previous'), currentPage - 1, currentPage === 0);
        const pageLabel = document.createElement('span');
        pageLabel.textContent = t('catalog.pageCount', { page: currentPage + 1, pages: lastResponse.totalPages });
        const next = pageButton(t('catalog.next'), currentPage + 1, currentPage + 1 >= lastResponse.totalPages);
        pagination.append(previous, pageLabel, next);
    }

    function pageButton(label, page, disabled) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'secondary-button';
        button.textContent = label;
        button.disabled = disabled;
        button.addEventListener('click', () => {
            load(page);
            window.scrollTo({ top: document.querySelector('.catalog-results').offsetTop - 30, behavior: 'smooth' });
        });
        return button;
    }

    form.addEventListener('submit', event => {
        event.preventDefault();
        load(0);
    });
    document.addEventListener('DOMContentLoaded', () => load());
    document.addEventListener('fhemni:localechange', () => {
        if (lastResponse) render();
    });
})();
