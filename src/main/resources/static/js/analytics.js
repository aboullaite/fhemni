(function () {
    const MAX_QUEUED_EVENTS = 20;
    const queuedEvents = [];
    let configured = false;

    window.FhemniAnalytics = Object.freeze({
        trackEvent
    });

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
        window.gtag('config', measurementId, { send_page_view: false });
        configured = true;

        const page = classifyPage(window.location.pathname);
        window.gtag('event', 'page_view', compact({
            page_title: page.title,
            page_location: `${window.location.origin}${window.location.pathname}`,
            content_group: page.group,
            fhemni_page_type: page.type,
            fhemni_content_id: page.contentId
        }));
        queuedEvents.splice(0).forEach(event => sendEvent(event.name, event.parameters));

        const script = document.createElement('script');
        script.async = true;
        script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`;
        document.head.append(script);
    }

    function trackEvent(name, parameters = {}) {
        const eventName = String(name || '').trim();
        if (!/^[a-z][a-z0-9_]{0,39}$/.test(eventName)) return;
        const event = { name: eventName, parameters: compact(parameters) };
        if (configured) {
            sendEvent(event.name, event.parameters);
            return;
        }
        if (queuedEvents.length < MAX_QUEUED_EVENTS) queuedEvents.push(event);
    }

    function sendEvent(name, parameters) {
        window.gtag('event', name, parameters);
    }

    function classifyPage(pathname) {
        const path = String(pathname || '/').replace(/\/+$/, '') || '/';
        const segments = path.split('/').filter(Boolean);
        if (path === '/' || path === '/index.html') return page('home', 'Home');
        if (path === '/videos' || path === '/videos.html' || path === '/catalog') {
            return page('catalogue', 'Catalogue');
        }
        if (path === '/parties' || path === '/parties.html') return page('parties', 'Parties');
        if (path === '/parties/compare' || path === '/compare-programmes.html') {
            return page('programme_compare', 'Programme comparison');
        }
        if (segments[0] === 'parties' && segments.length > 1) {
            return page('party', 'Party', safeValue(segments[1]));
        }
        if (segments[0] === 'promises' && segments.length > 1) {
            return page('promise', 'Promise', safeValue(segments[1]));
        }
        if (segments[0] === 'people' && segments.length > 1) {
            return page('person', 'Person', safeValue(segments[1]));
        }
        if (segments[0] === 'videos' && segments.length > 1) {
            return page('video', 'Video', safeValue(segments[1]));
        }
        if (path === '/video.html') return page('video', 'Video');
        if (segments[0] === 'analyses' && segments.length > 1) {
            return page('analysis', 'Analysis', safeValue(segments[1]));
        }
        if (path === '/analysis.html') return page('analysis', 'Analysis');
        if (path === '/community' || path === '/community.html'
                || path === '/suggestions') return page('community', 'Community');
        if (path === '/login' || path === '/login.html') return page('login', 'Login');
        if (path === '/admin' || path === '/admin.html') return page('admin', 'Admin');
        return page('other', 'Other');
    }

    function page(type, label, contentId = null) {
        return {
            type,
            title: `Fhemni | ${label}`,
            group: label,
            contentId
        };
    }

    function compact(parameters) {
        return Object.fromEntries(Object.entries(parameters)
            .filter(([key, value]) => /^[a-z][a-z0-9_]{0,39}$/.test(key)
                && value !== null && value !== undefined && value !== '')
            .slice(0, 20)
            .map(([key, value]) => [key, safeValue(value)]));
    }

    function safeValue(value) {
        if (typeof value === 'boolean') return value;
        if (typeof value === 'number') return Number.isFinite(value) ? value : 0;
        return String(value).slice(0, 100);
    }
})();
