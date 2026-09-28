(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    if (root) root.FhemniElectionHistoryCampaign = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    const CARD_IDS = [
        'campaign-01-overview',
        'campaign-02-parties',
        'campaign-03-transhumance',
        'campaign-04-regions',
        'campaign-05-profiles',
        'campaign-06-quotient',
        'campaign-story'
    ];
    const LOCAL_SEATS_QUALIFIER = 'المقاعد المحلية فقط';
    const SIMULATION_QUALIFIER = 'محاكاة، ماشي نتيجة رسمية';
    const YEAR_COLORS = ['#0B4F49', '#176B63', '#E86F3C'];

    function element(document, tagName, className, text) {
        const node = document.createElement(tagName);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function appendBdi(parent, value, className) {
        const isolate = parent.ownerDocument.createElement('bdi');
        if (className) isolate.className = className;
        isolate.textContent = String(value);
        parent.append(isolate);
        return isolate;
    }

    function formatNumber(value, fractionDigits = 0) {
        return new Intl.NumberFormat('en-US', {
            minimumFractionDigits: fractionDigits,
            maximumFractionDigits: fractionDigits
        }).format(value);
    }

    function formatYear(value) {
        return String(value);
    }

    function scaledPercentage(value, maximum, minimumPositive = 2) {
        if (!Number.isFinite(value) || value <= 0) return 0;
        return Math.max(minimumPositive, value / Math.max(maximum, 1) * 100);
    }

    function publishedComparison(rows, categoryAr, fromYear = 2016, toYear = 2026) {
        const row = rows.find(candidate => candidate.categoryAr === categoryAr);
        const from = row?.points.find(point => point.year === fromYear);
        const to = row?.points.find(point => point.year === toYear);
        if (!Number.isFinite(from?.percentage) || !Number.isFinite(to?.percentage)) return null;
        return {
            fromYear,
            fromPercentage: from.percentage,
            toYear,
            toPercentage: to.percentage
        };
    }

    function campaignBody(document, id) {
        const root = document.getElementById(id);
        if (!root) throw new Error(`Missing campaign root: ${id}`);
        const body = root.querySelector('[data-campaign-body]');
        if (!body) throw new Error(`Missing campaign body: ${id}`);
        body.replaceChildren();
        return { root, body };
    }

    function title(document, text) {
        return element(document, 'h1', 'campaign-title', text);
    }

    function headlineWithQualifier(document, text, qualifier) {
        const row = element(document, 'div', 'headline-row');
        row.append(title(document, text), element(document, 'span', 'qualifier', qualifier));
        return row;
    }

    function partyLabel(document, row) {
        const label = element(document, 'span', 'party-label');
        appendBdi(label, row.abbreviation);
        label.append(element(document, 'span', 'party-name', row.nameAr));
        return label;
    }

    function movementGroup(document, heading, rows, scale, kind, limit = 3) {
        const group = element(document, 'section', `movement-group ${kind}`);
        group.append(element(document, 'h2', 'movement-group-title', heading));
        rows.slice(0, limit).forEach(row => {
            const item = element(document, 'div', 'movement-row');
            const meta = element(document, 'div', 'movement-meta');
            const value = element(document, 'span', 'movement-value');
            value.append(document.createTextNode(row.delta > 0 ? '+' : '−'));
            appendBdi(value, formatNumber(Math.abs(row.delta)));
            meta.append(partyLabel(document, row), value);

            const track = element(document, 'div', 'bar-track');
            const fill = element(document, 'div', 'bar-fill');
            fill.style.width = `${scaledPercentage(Math.abs(row.delta), scale)}%`;
            track.append(fill);
            item.append(meta, track);
            group.append(item);
        });
        return group;
    }

    function transhumanceGroup(document, heading, rows, scale, kind) {
        const projected = rows.map(row => ({
            ...row,
            delta: kind === 'gain' ? row.count : -row.count
        }));
        return movementGroup(document, heading, projected, scale, kind);
    }

    function renderOverview(document, data) {
        const { root, body } = campaignBody(document, 'campaign-01-overview');
        const layout = element(document, 'div', 'overview-layout');
        const copy = element(document, 'div', 'overview-copy');
        copy.append(title(document, '10 سنين ديال الانتخابات فمكان واحد'));
        copy.append(element(document, 'p', 'campaign-note',
            'أرشيف واحد باش تقارن النتائج، الجهات، وخصائص المنتخبين والمنتخبات.'));

        const timelineCard = element(document, 'section', 'timeline-card');
        const timeline = element(document, 'div', 'timeline');
        data.overview.years.forEach(year => {
            const yearNode = element(document, 'span', 'timeline-year');
            appendBdi(yearNode, formatYear(year));
            timeline.append(yearNode);
        });
        const features = element(document, 'div', 'feature-strip');
        ['مقارنة الأحزاب', '12 جهة', 'خصائص المنتخبين', 'محاكاة القاسم'].forEach(label => {
            features.append(element(document, 'span', 'feature-pill', label));
        });
        timelineCard.append(timeline, features);

        layout.append(copy, timelineCard);
        body.append(layout);
        root.dataset.ready = 'true';
    }

    function renderParties(document, data) {
        const { root, body } = campaignBody(document, 'campaign-02-parties');
        const heading = title(document, 'شكون طلع وشكون هبط بين ');
        const years = element(document, 'span', 'nowrap');
        appendBdi(years, '2021');
        years.append(document.createTextNode(' و'));
        appendBdi(years, '2026');
        years.append(document.createTextNode('؟'));
        heading.append(years);
        body.append(heading);
        body.append(element(document, 'p', 'campaign-note',
            'المقاعد كاملة: المحلية وزايد اللائحة الوطنية أو الجهوية حسب كل انتخابات. الأسماء الحزبية باقية كيف نشرها المصدر.'));
        const movements = [...data.partyMovement.gains, ...data.partyMovement.losses];
        const scale = Math.max(...movements.map(row => Math.abs(row.delta)), 1);
        const layout = element(document, 'div', 'movement-layout');
        layout.append(
            movementGroup(document, 'أكبر المكاسب', data.partyMovement.gains, scale, 'gain'),
            movementGroup(document, 'أكبر الخسائر', data.partyMovement.losses, scale, 'loss')
        );
        body.append(layout);
        root.dataset.ready = 'true';
    }

    function renderPoliticalTranshumance(document, data) {
        const { root, body } = campaignBody(document, 'campaign-03-transhumance');
        const heading = title(document, 'الترحال السياسي بين ');
        const years = element(document, 'span', 'nowrap');
        appendBdi(years, data.politicalTranshumance.fromYear);
        years.append(document.createTextNode(' و'));
        appendBdi(years, data.politicalTranshumance.toYear);
        heading.append(years);
        body.append(heading);

        const total = element(document, 'p', 'transhumance-total');
        appendBdi(total, formatNumber(data.politicalTranshumance.totalMovements));
        total.append(document.createTextNode(' حالة مرصودة بين لوائح المنتخبين'));
        body.append(total);

        const scale = Math.max(data.politicalTranshumance.maximum || 0,
            ...data.politicalTranshumance.gains.map(row => row.count),
            ...data.politicalTranshumance.losses.map(row => row.count), 1);
        const layout = element(document, 'div', 'movement-layout transhumance-layout');
        layout.append(
            transhumanceGroup(document, 'الأحزاب الأكثر استقبالاً',
                data.politicalTranshumance.gains, scale, 'gain'),
            transhumanceGroup(document, 'الأحزاب الأكثر فقداناً',
                data.politicalTranshumance.losses, scale, 'loss')
        );
        body.append(layout);
        root.dataset.ready = 'true';
    }

    function renderRegions(document, data) {
        const { root, body } = campaignBody(document, 'campaign-04-regions');
        body.classList.add('region-body');
        body.append(headlineWithQualifier(document,
            'الانتخابات ما كتبدلش بنفس الشكل فكل جهة',
            data.region.qualifier || LOCAL_SEATS_QUALIFIER));
        const note = element(document, 'p', 'campaign-note');
        note.append(document.createTextNode('مثال جهة '));
        note.append(element(document, 'strong', '', data.region.regionNameAr));
        note.append(document.createTextNode(' عبر ثلاث انتخابات.'));
        body.append(note);

        const surface = element(document, 'section', 'chart-surface region-chart');
        const legend = element(document, 'div', 'region-legend');
        data.region.years.forEach((year, index) => {
            const item = element(document, 'span', 'legend-item');
            const swatch = element(document, 'span', 'legend-swatch');
            swatch.style.backgroundColor = YEAR_COLORS[index];
            item.append(swatch);
            appendBdi(item, formatYear(year));
            legend.append(item);
        });
        const rows = element(document, 'div', 'region-rows');
        const maxSeats = Math.max(...data.region.rows.flatMap(row =>
            row.points.map(point => point.localSeats)), 1);
        data.region.rows.forEach(row => {
            const rowNode = element(document, 'div', 'region-row');
            appendBdi(rowNode, row.abbreviation, 'region-party');
            const series = element(document, 'div', 'region-series');
            row.points.forEach((point, index) => {
                const cell = element(document, 'div', `region-cell region-year-${index}`);
                const fill = element(document, 'span', 'region-cell-fill');
                fill.style.width = `${scaledPercentage(point.localSeats, maxSeats)}%`;
                fill.style.backgroundColor = YEAR_COLORS[index];
                cell.append(fill);
                appendBdi(cell, formatNumber(point.localSeats), 'region-cell-value');
                series.append(cell);
            });
            rowNode.append(series);
            rows.append(rowNode);
        });
        surface.append(legend, rows);
        body.append(surface);
        root.dataset.ready = 'true';
    }

    function renderProfiles(document, data) {
        const { root, body } = campaignBody(document, 'campaign-05-profiles');
        body.append(title(document, 'شكون كيمثلنا؟'));
        body.append(element(document, 'p', 'campaign-note',
            'نسبة النساء من المنتخبين والمنتخبات، كيف ما نشرها elections.ma — بلا تحويل النِّسب لأعداد.'));
        const layout = element(document, 'div', 'profiles-layout');
        const rings = element(document, 'section', 'profile-rings');
        data.demographics.women.forEach(point => {
            const card = element(document, 'article', 'profile-ring-card');
            const ring = element(document, 'div', 'profile-ring');
            ring.style.setProperty('--percentage', point.percentage);
            const value = element(document, 'span', 'profile-ring-value');
            appendBdi(value, `${formatNumber(point.percentage, point.percentage % 1 ? 2 : 0)}%`);
            ring.append(value);
            const year = element(document, 'span', 'profile-year');
            appendBdi(year, formatYear(point.year));
            card.append(ring, year);
            rings.append(card);
        });
        const age = publishedComparison(data.demographics.age, 'أكبر من 55');
        const education = publishedComparison(data.demographics.education, 'عالي');
        const comparisons = [
            ['فوق 55 سنة', age],
            ['تعليم عالي', education]
        ].filter(([, comparison]) => comparison);
        if (comparisons.length) {
            const cues = element(document, 'aside', 'profile-cues');
            comparisons.forEach(([label, comparison]) => {
                const cue = element(document, 'div', 'profile-cue');
                cue.append(element(document, 'strong', '', label));
                const values = element(document, 'span', 'profile-cue-values');
                appendBdi(values, `${formatNumber(comparison.fromPercentage, 2)}%`);
                values.append(document.createTextNode(' → '));
                appendBdi(values, `${formatNumber(comparison.toPercentage, 2)}%`);
                const cueYears = element(document, 'span', 'profile-cue-years');
                appendBdi(cueYears, formatYear(comparison.fromYear));
                cueYears.append(document.createTextNode(' → '));
                appendBdi(cueYears, formatYear(comparison.toYear));
                cue.append(values, cueYears);
                cues.append(cue);
            });
            layout.append(rings, cues);
        } else {
            layout.classList.add('rings-only');
            layout.append(rings);
        }
        body.append(layout);
        root.dataset.ready = 'true';
    }

    function quotientBar(document, value, scale, kind) {
        const wrapper = element(document, 'div', `quotient-bars ${kind}`);
        const track = element(document, 'div', 'bar-track');
        const fill = element(document, 'div', 'bar-fill');
        fill.style.width = `${scaledPercentage(value, scale)}%`;
        track.append(fill);
        wrapper.append(track);
        appendBdi(wrapper, formatNumber(value));
        return wrapper;
    }

    function renderQuotient(document, data) {
        const { root, body } = campaignBody(document, 'campaign-06-quotient');
        body.append(headlineWithQualifier(document,
            'واش القاسم القديم كان غادي يبدل النتيجة؟',
            data.quotient.qualifier || SIMULATION_QUALIFIER));
        body.append(element(document, 'p', 'campaign-note',
            'مقارنة النتيجة الرسمية ديال 2026 مع نموذج كيطبّق قواعد 2016 على المجلس كامل.'));

        const layout = element(document, 'div', 'quotient-layout');
        const surface = element(document, 'section', 'chart-surface');
        const legend = element(document, 'div', 'legend');
        [['النتيجة الرسمية', '#0B4F49'], ['المحاكاة', '#E86F3C']].forEach(([label, color]) => {
            const item = element(document, 'span', 'legend-item');
            const swatch = element(document, 'span', 'legend-swatch');
            swatch.style.backgroundColor = color;
            item.append(swatch, document.createTextNode(label));
            legend.append(item);
        });
        const rows = element(document, 'div', 'quotient-rows');
        const selected = data.quotient.rows.slice(0, 5);
        const scale = Math.max(...selected.flatMap(row =>
            [row.officialSeats, row.simulatedSeats]), 1);
        selected.forEach(row => {
            const rowNode = element(document, 'div', 'quotient-row');
            appendBdi(rowNode, row.abbreviation, 'region-party');
            rowNode.append(
                quotientBar(document, row.officialSeats, scale, 'official'),
                quotientBar(document, row.simulatedSeats, scale, 'simulated')
            );
            rows.append(rowNode);
        });
        surface.append(legend, rows);

        const summary = element(document, 'aside', 'quotient-summary');
        const official = element(document, 'div', 'total-card');
        official.append(element(document, 'span', '', 'المجموع الرسمي'));
        appendBdi(official, formatNumber(data.quotient.officialSeatTotal), 'total-value');
        const simulated = element(document, 'div', 'total-card simulated');
        simulated.append(element(document, 'span', '', 'مجموع المحاكاة'));
        appendBdi(simulated, formatNumber(data.quotient.simulatedSeatTotal), 'total-value');
        summary.append(official, simulated);
        layout.append(surface, summary);
        body.append(layout);
        root.dataset.ready = 'true';
    }

    function renderStory(document, data) {
        const { root, body } = campaignBody(document, 'campaign-story');
        const heading = element(document, 'h1', 'story-title');
        heading.append(document.createTextNode('10 سنين ديال الانتخابات… '));
        heading.append(element(document, 'em', '', 'دابا كتقدر تقارنهم'));
        body.append(heading, element(document, 'p', 'story-subtitle',
            'شوف شكون طلع وشكون هبط بين 2021 و2026'));

        const movements = [...data.partyMovement.gains, ...data.partyMovement.losses];
        const scale = Math.max(...movements.map(row => Math.abs(row.delta)), 1);
        const chart = element(document, 'section', 'story-chart');
        chart.append(
            movementGroup(document, 'أكبر المكاسب', data.partyMovement.gains, scale, 'gain', 2),
            movementGroup(document, 'أكبر الخسائر', data.partyMovement.losses, scale, 'loss', 2)
        );
        body.append(chart);

        const features = element(document, 'div', 'story-features');
        ['12 جهة', 'خصائص المنتخبين', 'محاكاة القاسم'].forEach(label => {
            features.append(element(document, 'span', 'story-feature', label));
        });
        const cta = element(document, 'div', 'story-cta');
        const years = element(document, 'span', 'story-years');
        data.overview.years.forEach((year, index) => {
            if (index) years.append(document.createTextNode(' · '));
            appendBdi(years, formatYear(year));
        });
        const action = element(document, 'span', 'story-action');
        action.append(document.createTextNode('جرّب المقارنة فـ '));
        appendBdi(action, 'fhemni.ma');
        cta.append(years, action);
        body.append(features, cta);
        root.dataset.ready = 'true';
    }

    function renderCampaign(document, data) {
        if (!document || !data) throw new TypeError('renderCampaign needs a document and campaign data');
        const view = document.defaultView
            || (typeof window !== 'undefined' ? window : undefined);
        if (view) view.__FHEMNI_CAMPAIGN_READY__ = false;

        renderOverview(document, data);
        renderParties(document, data);
        renderPoliticalTranshumance(document, data);
        renderRegions(document, data);
        renderProfiles(document, data);
        renderQuotient(document, data);
        renderStory(document, data);

        const allReady = CARD_IDS.every(id =>
            document.getElementById(id)?.dataset.ready === 'true');
        if (!allReady) throw new Error('Campaign render completed without all roots ready');
        if (view) view.__FHEMNI_CAMPAIGN_READY__ = true;
        return CARD_IDS.map(id => document.getElementById(id));
    }

    async function loadCampaign(document) {
        const response = await fetch('./campaign-data.json', { cache: 'no-store' });
        if (!response.ok) throw new Error(`Campaign data request failed: ${response.status}`);
        return renderCampaign(document, await response.json());
    }

    if (typeof document !== 'undefined') {
        const start = () => loadCampaign(document).catch(error => {
            document.body.dataset.campaignError = error.message;
            console.error(error);
        });
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', start, { once: true });
        } else {
            start();
        }
    }

    return { renderCampaign, formatYear, scaledPercentage, publishedComparison };
}));
