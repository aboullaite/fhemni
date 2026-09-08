(function () {
    let promise;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function locale() {
        return window.FhemniI18n?.locale() || 'ar';
    }

    function localized(value) {
        return value?.[locale()] || value?.ar || value?.fr || value?.en || '';
    }

    async function load() {
        const slug = decodeURIComponent(window.location.pathname.split('/').filter(Boolean).at(-1) || '');
        try {
            promise = await window.FhemniCatalog.requestJson(
                `/api/catalog/promises/${encodeURIComponent(slug)}`);
            render();
            document.querySelector('#promiseLoading').hidden = true;
            document.querySelector('#promiseDetail').hidden = false;
        } catch (error) {
            document.querySelector('#promiseLoading').hidden = true;
            const panel = document.querySelector('#promiseError');
            panel.textContent = error.message;
            panel.hidden = false;
        }
    }

    function render() {
        const assessment = promise.assessment;
        document.querySelector('#promiseTitle').textContent = localized(promise.title);
        document.querySelector('#promiseText').textContent = promise.promiseText;
        document.querySelector('#promiseParty').textContent = promise.partyCode;
        document.querySelector('#promiseTerm').textContent = `${promise.termStartYear}–${promise.termEndYear}`;
        document.querySelector('#promiseBack').href = `/parties/${encodeURIComponent(promise.partyCode)}`;
        const source = document.querySelector('#promiseSource');
        source.href = promise.programmeSourceUrl;
        source.title = promise.programmeSourceLabel;
        const verdict = document.querySelector('#promiseVerdict');
        verdict.className = `feasibility-badge ${String(assessment.verdict).toLowerCase().replace('_', '-')}`;
        verdict.textContent = t(`promise.verdict.${assessment.verdict}`);
        const verdictDescription = t(`promise.verdictDescription.${assessment.verdict}`);
        verdict.title = verdictDescription;
        verdict.setAttribute('aria-label', verdictDescription);
        document.querySelector('#promiseSummary').textContent = cleanSummary(localized(assessment.summary));
        document.querySelector('#promiseRequirements').textContent = localized(assessment.requirements);
        document.querySelector('#promiseAssumptions').textContent = localized(assessment.assumptions);
        document.querySelector('#promiseCalculation').textContent = localized(assessment.calculationNotes);
        document.querySelector('#promiseMethodology').textContent = t('promise.methodology', {
            version: assessment.methodologyVersion,
            revision: assessment.revisionNumber
        });
        document.querySelector('#promiseDataCutoff').textContent = t('promise.dataCutoff', {
            date: formatDate(assessment.dataCutoff)
        });
        const provider = ['gemini', 'openai', 'consensus', 'editorial'].includes(assessment.providerMode)
            ? assessment.providerMode
            : 'gemini';
        document.querySelector('#promiseAiAttribution').textContent = t(`promise.aiAttribution.${provider}`, {
            models: assessment.modelNames || 'Gemini 3.8 Flash'
        });
        const correctionParameters = new URLSearchParams({
            title: `[Correction] ${localized(promise.title)}`,
            body: `Assessment: ${window.location.href.split('#')[0]}\n\nWhat appears incorrect?\n`
        });
        document.querySelector('#promiseReportErrorLink').href =
            `https://github.com/aboullaite/fhemni/issues/new?${correctionParameters}`;
        const evidence = document.querySelector('#promiseEvidence');
        evidence.replaceChildren();
        assessment.evidence.forEach(item => evidence.append(evidenceItem(item)));
        document.title = `${localized(promise.title)} — Fhemni`;
    }

    function evidenceItem(item) {
        const row = document.createElement('li');
        const link = document.createElement('a');
        link.href = item.url;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.textContent = item.title;
        const meta = document.createElement('span');
        meta.textContent = [item.publisher, item.publishedOn ? formatDate(item.publishedOn) : '']
            .filter(Boolean).join(' · ');
        row.append(link, meta);
        if (item.note) {
            const note = document.createElement('p');
            note.dir = 'auto';
            note.textContent = item.note;
            row.append(note);
        }
        return row;
    }

    function formatDate(value) {
        if (!value) return '';
        const dateLocale = locale() === 'ar' ? 'ar-MA' : locale();
        return new Intl.DateTimeFormat(dateLocale, { year: 'numeric', month: 'short', day: 'numeric' })
            .format(new Date(`${value}T12:00:00Z`));
    }

    function cleanSummary(value) {
        return String(value || '')
            .replace(/\b(?:POSSIBLE|HARD|NOT_ACHIEVABLE|INSUFFICIENT_DATA)\b[.:؛،-]?/g, '')
            .replace(/[ \t]+([.,؛،])/g, '$1')
            .replace(/[ \t]{2,}/g, ' ')
            .trim();
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (promise) render();
    });
})();
