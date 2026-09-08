(function () {
    // Extraction, the parallel first pass, and reconciliation all fit inside this admin deadline.
    const INGESTION_TIMEOUT_MS = 65 * 60 * 1000;
    const ingestForm = document.querySelector('#programmeIngestForm');
    const sourceUrl = document.querySelector('#programmeSourceUrl');
    const pdf = document.querySelector('#programmePdf');
    const replaceOption = document.querySelector('#programmeReplaceOption');
    const replaceExisting = document.querySelector('#programmeReplaceExisting');
    const ingestButton = document.querySelector('#programmeIngestButton');
    const list = document.querySelector('#programmeList');
    const feedback = document.querySelector('#programmeFeedback');
    const refresh = document.querySelector('#refreshProgrammes');
    let programmes = [];

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
        refresh.disabled = true;
        try {
            programmes = await window.FhemniCatalog.requestJson('/api/admin/programmes');
            render();
            syncReplacementOption();
        } catch (error) {
            window.FhemniCatalog.renderError(list, error.message);
        } finally {
            refresh.disabled = false;
        }
    }

    async function ingest(event) {
        event.preventDefault();
        ingestButton.disabled = true;
        ingestButton.textContent = t('admin.programmeIngesting');
        showFeedback(t('admin.programmeIngestWait'), false);
        try {
            const document = pdf.files[0];
            const body = document ? new FormData() : null;
            if (body) {
                body.append('sourceUrl', sourceUrl.value);
                body.append('document', document);
                body.append('replaceExistingDraft', String(replaceExisting.checked));
            }
            const options = await window.FhemniAuth.withCsrf(document
                ? { method: 'POST', body }
                : {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sourceUrl: sourceUrl.value })
                });
            const result = await window.FhemniCatalog.requestJson(
                document ? '/api/admin/programmes/ingest-pdf' : '/api/admin/programmes/ingest',
                options,
                INGESTION_TIMEOUT_MS);
            const message = result.assessmentPending
                ? t('admin.programmeAssessmentPending', {
                    party: result.programme.partyCode,
                    count: result.programme.promises.length
                })
                : result.cacheHit
                ? t('admin.programmeCacheHit')
                : t('admin.programmeIngested', {
                    party: result.programme.partyCode,
                    count: result.programme.promises.length
                });
            const warnings = (result.warnings || []).join(' · ');
            showFeedback(warnings ? `${message} ${t('admin.programmeWarnings', { warnings })}` : message, false);
            sourceUrl.value = '';
            pdf.value = '';
            replaceExisting.checked = false;
            await load();
        } catch (error) {
            showFeedback(programmeError(error), true);
            await load();
        } finally {
            ingestButton.disabled = false;
            ingestButton.textContent = t('admin.programmeIngestButton');
        }
    }

    function programmeError(error) {
        const messages = {
            AI_SOURCE_OR_REQUEST_INVALID: 'admin.programmeAiSourceInvalid',
            AI_CREDENTIAL_REJECTED: 'admin.programmeAiCredentialRejected',
            AI_RATE_LIMITED: 'admin.programmeAiRateLimited',
            AI_RESULT_INVALID: 'admin.programmeAiResultInvalid',
            AI_TEMPORARY_FAILURE: 'admin.programmeAiTemporaryFailure',
            PROGRAMME_PDF_TOO_LARGE: 'admin.programmePdfTooLarge'
        };
        return messages[error.code] ? t(messages[error.code]) : error.message;
    }

    function syncReplacementOption() {
        const normalized = normalizedSourceUrl(sourceUrl.value);
        const hasDraft = normalized && programmes.some(programme =>
            programme.status === 'DRAFT' && normalizedSourceUrl(programme.sourceUrl) === normalized
        );
        replaceOption.hidden = !(pdf.files.length && hasDraft);
        if (replaceOption.hidden) replaceExisting.checked = false;
    }

    function normalizedSourceUrl(value) {
        try {
            const url = new URL(value);
            url.hash = '';
            [...url.searchParams.keys()].forEach(key => {
                const normalized = key.toLowerCase();
                if (normalized.startsWith('utm_')
                    || ['dclid', 'fbclid', 'gclid', 'msclkid', 'mc_cid', 'mc_eid'].includes(normalized)) {
                    url.searchParams.delete(key);
                }
            });
            url.searchParams.sort();
            return url.toString();
        } catch (_) {
            return '';
        }
    }

    function render() {
        list.replaceChildren();
        if (!programmes.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('admin.noProgrammes');
            list.append(empty);
            return;
        }
        programmes.forEach(programme => list.append(programmeCard(programme)));
    }

    function programmeCard(programme) {
        const article = document.createElement('article');
        article.className = 'programme-review-card';

        const heading = document.createElement('div');
        heading.className = 'programme-review-top';
        const copy = document.createElement('div');
        const title = document.createElement('h3');
        title.dir = 'auto';
        title.textContent = `${programme.partyCode} · ${localized(programme.title)}`;
        const source = document.createElement('a');
        source.href = programme.sourceUrl;
        source.target = '_blank';
        source.rel = 'noopener noreferrer';
        source.textContent = programme.sourceLabel;
        const meta = document.createElement('p');
        meta.textContent = `${programme.electionYear}–${programme.termEndYear} · SHA-256 ${programme.sourceSha256.slice(0, 10)}…`;
        copy.append(title, source, meta);
        heading.append(copy, statusBadge(programme.status));
        article.append(heading);

        if (programme.status === 'DRAFT' && !programme.sourceVerified) {
            const verification = document.createElement('div');
            verification.className = 'programme-verification';
            const note = document.createElement('span');
            note.textContent = t('admin.programmeNeedsVerification');
            const verify = actionButton(t('admin.verifyProgrammeSource'), true,
                () => verifySource(programme.id, verify));
            verification.append(note, verify);
            article.append(verification);
        }

        const promiseList = document.createElement('div');
        promiseList.className = 'programme-promise-list';
        programme.promises.forEach(item => promiseList.append(promiseRow(item)));
        if (!programme.promises.length) {
            const empty = document.createElement('p');
            empty.className = 'video-meta';
            empty.textContent = t('admin.noPromises');
            promiseList.append(empty);
        }
        article.append(promiseList);

        if (programme.status === 'DRAFT') {
            const actions = document.createElement('div');
            actions.className = 'admin-video-actions';
            const needsAssessment = programme.promises.some(item => !item.assessments.length);
            if (needsAssessment) {
                const retry = actionButton(t('admin.retryProgrammeAssessment'), true,
                    () => retryAssessment(programme.sourceUrl, retry));
                actions.append(retry);
            }
            const publishable = programme.sourceVerified
                && programme.promises.length > 0
                && programme.promises.every(item => item.promise.status === 'PUBLISHED'
                    || item.assessments.some(assessment => ['DRAFT', 'PUBLISHED'].includes(assessment.status)));
            const publish = actionButton(t('admin.publishAll'), publishable,
                () => publishAll(programme, publish));
            publish.className = 'primary-button programme-action programme-publish-all';
            if (!publishable) publish.title = t('admin.publishAllHint');
            const discard = actionButton(t('admin.discardDraft'), true,
                () => discardResource(`/api/admin/programmes/${programme.id}`, discard));
            discard.className = 'secondary-button programme-action programme-action-danger';
            actions.append(publish, discard);
            article.append(actions);
        }
        return article;
    }

    function promiseRow(item) {
        const row = document.createElement('details');
        row.className = 'programme-promise-row';
        const top = document.createElement('summary');
        top.className = 'programme-review-top';
        const title = document.createElement('strong');
        title.dir = 'auto';
        title.textContent = localized(item.promise.title);
        top.append(title, statusBadge(item.promise.status));
        row.append(top);

        const sourceText = document.createElement('blockquote');
        sourceText.dir = 'auto';
        sourceText.textContent = item.promise.promiseText;
        const locator = document.createElement('small');
        locator.textContent = t('admin.promiseLocator', { locator: item.promise.sourceLocator });
        row.append(sourceText, locator);
        appendOptional(row, t('admin.mechanism'), item.promise.mechanism);
        appendOptional(row, t('admin.financing'), item.promise.financing);

        item.assessments.forEach(assessment => row.append(assessmentCard(assessment)));
        if (item.promise.status === 'DRAFT') {
            const canPublish = item.assessments.some(assessment => assessment.status === 'PUBLISHED');
            const actions = document.createElement('div');
            actions.className = 'admin-video-actions';
            const publish = actionButton(t('admin.publishPromise'), canPublish,
                () => publishResource(`/api/admin/programmes/promises/${item.promise.id}/publish`, publish));
            if (!canPublish) publish.title = t('admin.promisePublishHint');
            const discard = actionButton(t('admin.discardDraft'), true,
                () => discardResource(`/api/admin/programmes/promises/${item.promise.id}`, discard));
            discard.className = 'secondary-button programme-action programme-action-danger';
            actions.append(publish, discard);
            row.append(actions);
        } else {
            const view = document.createElement('a');
            view.className = 'text-link';
            view.href = `/promises/${encodeURIComponent(item.promise.slug)}`;
            view.textContent = t('admin.viewPublicPromise');
            row.append(view);
        }
        return row;
    }

    function assessmentCard(assessment) {
        const card = document.createElement('section');
        card.className = 'programme-assessment-review';
        const heading = document.createElement('div');
        heading.className = 'programme-review-top';
        const verdictLine = document.createElement('div');
        verdictLine.className = 'programme-verdict-line';
        const verdict = verdictBadge(assessment.verdict);
        const revision = document.createElement('span');
        revision.className = 'video-meta';
        revision.textContent = `v${assessment.revisionNumber}`;
        verdictLine.append(verdict, revision);
        heading.append(verdictLine, statusBadge(assessment.status));
        card.append(heading);

        const assessmentSummary = localized(assessment.summary);
        if (assessmentSummary) {
            const summary = document.createElement('p');
            summary.className = 'programme-assessment-summary';
            summary.dir = 'auto';
            summary.textContent = assessmentSummary;
            card.append(summary);
        }

        const details = document.createElement('details');
        details.className = 'programme-assessment-details';
        const detailsLabel = document.createElement('summary');
        detailsLabel.textContent = t('admin.showAssessmentDetails');
        const detailsBody = document.createElement('div');
        detailsBody.className = 'programme-assessment-details-body';
        appendOptional(detailsBody, t('admin.requirements'), localized(assessment.requirements));
        appendOptional(detailsBody, t('admin.assumptions'), localized(assessment.assumptions));
        appendOptional(detailsBody, t('admin.calculation'), localized(assessment.calculationNotes));

        const evidence = document.createElement('ul');
        evidence.className = 'programme-evidence-review';
        assessment.evidence.forEach(item => {
            const row = document.createElement('li');
            const link = document.createElement('a');
            link.href = item.url;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            link.textContent = `${item.publisher} · ${item.title}`;
            row.append(link);
            if (item.note) {
                const note = document.createElement('span');
                note.textContent = item.note;
                row.append(note);
            }
            evidence.append(row);
        });
        if (assessment.evidence.length) detailsBody.append(evidence);
        details.append(detailsLabel, detailsBody);
        card.append(details);

        if (assessment.status === 'DRAFT') {
            const actions = document.createElement('div');
            actions.className = 'admin-video-actions';
            const publish = actionButton(t('admin.publishAssessment'), true,
                () => publishResource(`/api/admin/programmes/assessments/${assessment.id}/publish`, publish));
            const discard = actionButton(t('admin.discardDraft'), true,
                () => discardResource(`/api/admin/programmes/assessments/${assessment.id}`, discard));
            discard.className = 'secondary-button programme-action programme-action-danger';
            actions.append(publish, discard);
            card.append(actions);
        }
        return card;
    }

    function verdictBadge(value) {
        const badge = document.createElement('span');
        badge.className = `feasibility-badge ${String(value).toLowerCase().replace('_', '-')}`;
        badge.textContent = t(`promise.verdict.${value}`);
        const description = t(`promise.verdictDescription.${value}`);
        badge.title = description;
        badge.setAttribute('aria-label', description);
        return badge;
    }

    function appendOptional(parent, label, value) {
        if (!value) return;
        const block = document.createElement('p');
        block.dir = 'auto';
        const strong = document.createElement('strong');
        strong.textContent = `${label}: `;
        block.append(strong, document.createTextNode(value));
        parent.append(block);
    }

    function statusBadge(value) {
        const badge = document.createElement('span');
        badge.className = `catalog-status ${value === 'PUBLISHED' ? 'published' : 'catalogued'}`;
        badge.textContent = t(`admin.editorialStatus.${value}`);
        return badge;
    }

    function actionButton(label, enabled, handler) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'secondary-button programme-action';
        button.textContent = label;
        button.disabled = !enabled;
        button.addEventListener('click', handler);
        return button;
    }

    async function retryAssessment(programmeSourceUrl, button) {
        button.disabled = true;
        button.textContent = t('admin.programmeAssessmentRetrying');
        showFeedback(t('admin.programmeAssessmentRetryWait'), false);
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sourceUrl: programmeSourceUrl })
            });
            const result = await window.FhemniCatalog.requestJson(
                '/api/admin/programmes/ingest', options, INGESTION_TIMEOUT_MS);
            showFeedback(result.assessmentPending
                ? t('admin.programmeAiTemporaryFailure')
                : t('admin.programmeAssessmentRetried'), result.assessmentPending);
            await load();
        } catch (error) {
            showFeedback(programmeError(error), true);
            button.disabled = false;
            button.textContent = t('admin.retryProgrammeAssessment');
        }
    }

    async function verifySource(programmeId, button) {
        if (!window.confirm(t('admin.verifyProgrammeSourceConfirm'))) return;
        await postAction(`/api/admin/programmes/${programmeId}/verify-source`, button, t('admin.programmeSourceVerified'));
    }

    async function publishResource(url, button) {
        if (!window.confirm(t('admin.publishConfirm'))) return;
        await postAction(url, button, t('admin.published'));
    }

    async function publishAll(programme, button) {
        if (!window.confirm(t('admin.publishAllConfirm', {
            party: programme.partyCode,
            count: programme.promises.length
        }))) return;
        await postAction(
            `/api/admin/programmes/${programme.id}/publish-all`,
            button,
            t('admin.publishedAll', { party: programme.partyCode }));
    }

    async function postAction(url, button, successMessage) {
        button.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST' });
            await window.FhemniCatalog.requestJson(url, options, 30_000);
            showFeedback(successMessage, false);
            await load();
        } catch (error) {
            showFeedback(error.message, true);
            button.disabled = false;
        }
    }

    async function discardResource(url, button) {
        if (!window.confirm(t('admin.discardConfirm'))) return;
        button.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'DELETE' });
            await window.FhemniCatalog.requestJson(url, options, 30_000);
            showFeedback(t('admin.discarded'), false);
            await load();
        } catch (error) {
            showFeedback(error.message, true);
            button.disabled = false;
        }
    }

    function showFeedback(message, error) {
        feedback.className = `import-feedback ${error ? 'error' : 'success'}`;
        feedback.textContent = message;
        feedback.hidden = false;
    }

    ingestForm.addEventListener('submit', ingest);
    sourceUrl.addEventListener('input', syncReplacementOption);
    pdf.addEventListener('change', syncReplacementOption);
    refresh.addEventListener('click', load);
    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', render);
})();
