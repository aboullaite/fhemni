(function () {
    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    async function requestJson(url, options = {}, timeoutMs = 15_000) {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
        try {
            const response = await fetch(url, { ...options, signal: controller.signal });
            if (!response.ok) {
                let message = t('common.requestFailed', { status: response.status });
                let code = null;
                try {
                    const problem = await response.json();
                    message = problem.detail || message;
                    code = problem.code || null;
                } catch (_) { /* use the status message */ }
                const error = new Error(message);
                error.status = response.status;
                error.code = code;
                throw error;
            }
            if (response.status === 204) return null;
            return await response.json();
        } catch (error) {
            if (error.name === 'AbortError') {
                throw new Error(t('common.requestTimedOut', { seconds: Math.round(timeoutMs / 1000) }));
            }
            throw error;
        } finally {
            window.clearTimeout(timeout);
        }
    }

    function renderGrid(container, videos) {
        container.replaceChildren();
        if (!videos.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('catalog.empty');
            container.append(empty);
            return;
        }
        videos.forEach(video => container.append(createCard(video)));
    }

    function createCard(video) {
        const article = document.createElement('article');
        article.className = 'catalog-card';
        const imageLink = document.createElement('a');
        imageLink.className = 'catalog-thumbnail';
        imageLink.href = `/videos/${encodeURIComponent(video.slug)}`;
        const image = document.createElement('img');
        image.src = safeImage(video.thumbnailUrl, video.youtubeVideoId);
        image.alt = '';
        image.loading = 'lazy';
        imageLink.append(image);

        const body = document.createElement('div');
        body.className = 'catalog-card-body';
        const top = document.createElement('div');
        top.className = 'catalog-card-topline';
        const show = document.createElement('span');
        show.textContent = video.showName || video.authorName;
        const status = document.createElement('span');
        status.className = `catalog-status ${String(video.status).toLowerCase()}`;
        status.textContent = statusLabel(video.status);
        top.append(show, status);

        const title = document.createElement('h3');
        title.dir = 'auto';
        const titleLink = document.createElement('a');
        titleLink.href = imageLink.href;
        titleLink.textContent = video.title;
        title.append(titleLink);

        const meta = document.createElement('p');
        meta.className = 'catalog-card-meta';
        meta.textContent = [video.authorName, formatDate(video.publishedOn)].filter(Boolean).join(' · ');
        body.append(top, title, meta);
        article.append(imageLink, body);
        return article;
    }

    function renderError(container, message) {
        container.replaceChildren();
        const error = document.createElement('div');
        error.className = 'catalog-error';
        error.textContent = message;
        container.append(error);
    }

    function statusLabel(status) {
        return status === 'PUBLISHED' ? t('catalog.ready') : t('catalog.awaitingAnalysis');
    }

    function formatDate(value) {
        if (!value) return t('catalog.dateUnavailable');
        const locale = window.FhemniI18n?.locale() || 'en';
        const dateLocale = locale === 'ar' ? 'ar-MA' : locale;
        return new Intl.DateTimeFormat(dateLocale, { year: 'numeric', month: 'short', day: 'numeric' })
            .format(new Date(`${value}T12:00:00Z`));
    }

    function safeImage(value, youtubeVideoId) {
        try {
            const url = new URL(value);
            if (url.protocol === 'https:' && (url.hostname === 'i.ytimg.com' || url.hostname.endsWith('.ytimg.com'))) {
                return url.href;
            }
        } catch (_) { /* use the canonical YouTube thumbnail */ }
        return `https://i.ytimg.com/vi/${encodeURIComponent(youtubeVideoId)}/hqdefault.jpg`;
    }

    window.FhemniCatalog = { requestJson, renderGrid, renderError, statusLabel, formatDate, safeImage };
})();
