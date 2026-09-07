(function () {
    let video;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    async function load() {
        const slug = decodeURIComponent(window.location.pathname.split('/').filter(Boolean).at(-1) || '');
        try {
            video = await window.FhemniCatalog.requestJson(`/api/catalog/videos/${encodeURIComponent(slug)}`);
            render();
            trackVideoView();
            document.querySelector('#videoLoading').hidden = true;
            document.querySelector('#videoDetail').hidden = false;
        } catch (error) {
            document.querySelector('#videoLoading').hidden = true;
            const panel = document.querySelector('#videoError');
            panel.textContent = error.message;
            panel.hidden = false;
        }
    }

    function render() {
        document.querySelector('#videoTitle').textContent = video.title;
        document.querySelector('#videoShow').textContent = video.showName || video.authorName;
        document.querySelector('#videoMeta').textContent = [
            video.authorName,
            window.FhemniCatalog.formatDate(video.publishedOn),
            languageLabel(video.sourceLanguage)
        ].filter(Boolean).join(' · ');
        const status = document.querySelector('#videoStatus');
        status.className = `catalog-status ${String(video.status).toLowerCase()}`;
        status.textContent = window.FhemniCatalog.statusLabel(video.status);
        document.querySelector('#videoFrame').src = embedUrl(video.embedUrl);
        document.querySelector('#youtubeLink').href = video.canonicalUrl;
        const ready = video.status === 'PUBLISHED' && Boolean(video.publishedAnalysisId);
        document.querySelector('#videoStateKicker').textContent = t(ready ? 'video.readyKicker' : 'video.cataloguedKicker');
        document.querySelector('#videoStateTitle').textContent = t(ready ? 'video.readyTitle' : 'video.awaitingTitle');
        document.querySelector('#videoStateText').textContent = t(ready ? 'video.readyText' : 'video.awaitingText');
        const analysisLink = document.querySelector('#readAnalysis');
        analysisLink.hidden = !ready;
        if (ready) analysisLink.href = `/analyses/${encodeURIComponent(video.publishedAnalysisId)}`;
        document.title = `${video.title} — Fhemni`;
    }

    function languageLabel(code) {
        const labels = { ar: 'catalog.darija', ary: 'catalog.darija', fr: 'catalog.french', en: 'catalog.english' };
        return t(labels[code] || code);
    }

    function embedUrl(base) {
        const parameters = new URLSearchParams({ rel: '0' });
        const start = Math.floor(Number(new URLSearchParams(window.location.search).get('t')));
        if (Number.isFinite(start) && start > 0) {
            parameters.set('start', String(start));
        }
        return `${base}?${parameters}`;
    }

    function trackVideoView() {
        window.FhemniAnalytics?.trackEvent('video_view', {
            video_id: video.youtubeVideoId,
            video_slug: video.slug,
            video_status: String(video.status || '').toLowerCase(),
            source_language: video.sourceLanguage,
            analysis_available: video.status === 'PUBLISHED' && Boolean(video.publishedAnalysisId)
        });
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (video) render();
    });
})();
