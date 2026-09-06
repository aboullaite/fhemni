(function () {
    let videos = [];

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

    document.addEventListener('DOMContentLoaded', loadFeatured);
    document.addEventListener('fhemni:localechange', () => {
        if (videos.length) window.FhemniCatalog.renderGrid(document.querySelector('#featuredVideos'), videos);
    });
})();
