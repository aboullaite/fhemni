(function () {
    fetch('/api/meta', {
        headers: { Accept: 'application/json' },
        credentials: 'same-origin'
    })
        .then(response => response.ok ? response.json() : null)
        .then(meta => configure(meta?.analyticsMeasurementId))
        .catch(() => {
            // Analytics is optional and must never affect application behavior.
        });

    function configure(value) {
        const measurementId = String(value || '').trim();
        if (!/^G-[A-Z0-9]+$/.test(measurementId)) return;

        window.dataLayer = window.dataLayer || [];
        window.gtag = function () {
            window.dataLayer.push(arguments);
        };
        window.gtag('js', new Date());
        window.gtag('config', measurementId);

        const script = document.createElement('script');
        script.async = true;
        script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`;
        document.head.append(script);
    }
})();
