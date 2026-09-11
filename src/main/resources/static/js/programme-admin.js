(function () {
    // Only source extraction stays in the request. Feasibility work runs in the durable queue.
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
    let jobsByProgramme = {};
    let mediaByProgramme = {};
    let pollTimer = null;
    const expandedProgrammes = new Map();

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
            [programmes, jobsByProgramme, mediaByProgramme] = await Promise.all([
                window.FhemniCatalog.requestJson('/api/admin/programmes'),
                window.FhemniCatalog.requestJson('/api/admin/programmes/assessment-jobs'),
                window.FhemniCatalog.requestJson('/api/admin/programmes/media')
            ]);
            render();
            syncReplacementOption();
            schedulePolling();
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
            const message = t('admin.programmeQueued', {
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
            AI_TIMEOUT: 'admin.programmeAiTimeout',
            AI_REQUEST_REJECTED: 'admin.programmeAiRequestRejected',
            AI_RESULT_INVALID: 'admin.programmeAiResultInvalid',
            AI_TEMPORARY_FAILURE: 'admin.programmeAiTemporaryFailure',
            EVIDENCE_REQUIRED: 'admin.programmeAssessmentEvidenceRequired',
            WORKER_INTERRUPTED: 'admin.programmeWorkerInterrupted',
            PROVIDER_MODE_CHANGED: 'admin.programmeProviderModeChanged',
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

        const body = document.createElement('div');
        body.className = 'programme-card-body';

        const job = jobsByProgramme[programme.id];
        const jobIsActive = ['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(job?.status);
        const { complete, total } = assessmentCounts(programme);

        const heading = document.createElement('button');
        heading.type = 'button';
        heading.className = 'programme-card-summary';
        heading.setAttribute('aria-expanded', String(expandedProgrammes.get(programme.id) ?? jobIsActive));
        const copy = document.createElement('div');
        copy.className = 'programme-card-heading';
        const title = document.createElement('h3');
        title.dir = 'auto';
        title.textContent = `${programme.partyCode} · ${localized(programme.title)}`;
        const summary = document.createElement('p');
        summary.className = 'programme-card-meta';
        summary.textContent = t('admin.programmeCardProgress', {
            complete,
            total,
            promises: programme.promises.length
        });
        copy.append(title, summary);

        const indicators = document.createElement('span');
        indicators.className = 'programme-card-indicators';
        indicators.append(statusBadge(programme.status));
        if (jobIsActive) {
            const running = document.createElement('span');
            running.className = 'programme-card-running';
            running.textContent = t(`admin.programmeJob.${job.status}.title`);
            indicators.append(running);
        }
        const chevron = document.createElement('span');
        chevron.className = 'programme-card-chevron';
        chevron.setAttribute('aria-hidden', 'true');
        indicators.append(chevron);
        heading.append(copy, indicators);

        const expanded = expandedProgrammes.get(programme.id) ?? jobIsActive;
        body.hidden = !expanded;
        article.classList.toggle('expanded', expanded);
        heading.addEventListener('click', () => {
            const next = body.hidden;
            body.hidden = !next;
            article.classList.toggle('expanded', next);
            heading.setAttribute('aria-expanded', String(next));
            expandedProgrammes.set(programme.id, next);
        });
        article.append(heading);

        const sourceDetails = document.createElement('div');
        sourceDetails.className = 'programme-card-source';
        const source = document.createElement('a');
        source.href = programme.sourceUrl;
        source.target = '_blank';
        source.rel = 'noopener noreferrer';
        source.textContent = programme.sourceLabel;
        const meta = document.createElement('p');
        meta.textContent = `${programme.electionYear}–${programme.termEndYear} · SHA-256 ${programme.sourceSha256.slice(0, 10)}…`;
        sourceDetails.append(source, meta);
        body.append(sourceDetails);

        if (programme.status === 'PUBLISHED') {
            body.append(programmeMediaPanel(programme, mediaByProgramme[programme.id]));
        }

        if (programme.status === 'DRAFT' && !programme.sourceVerified) {
            const verification = document.createElement('div');
            verification.className = 'programme-verification';
            const note = document.createElement('span');
            note.textContent = t('admin.programmeNeedsVerification');
            const verify = actionButton(t('admin.verifyProgrammeSource'), true,
                () => verifySource(programme.id, verify));
            verification.append(note, verify);
            body.append(verification);
        }

        const needsAssessment = programme.promises.some(item => !item.assessments.length);
        if (programme.status === 'DRAFT' && programme.promises.length) {
            body.append(needsAssessment
                ? assessmentJobPanel(programme, job)
                : assessmentCompletePanel(programme));
        }

        if (programme.status === 'DRAFT' && programme.promises.length < 10) {
            body.append(extractionCoveragePanel(programme));
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
        body.append(promiseList);

        if (programme.status === 'DRAFT') {
            const actions = document.createElement('div');
            actions.className = 'admin-video-actions';
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
            body.append(actions);
        }
        article.append(body);
        return article;
    }

    function programmeMediaPanel(programme, media) {
        const panel = document.createElement('section');
        panel.className = 'programme-media-admin';
        const heading = document.createElement('div');
        heading.className = 'programme-review-top';
        const copy = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = t('admin.programmeMediaTitle');
        const intro = document.createElement('p');
        intro.textContent = t('admin.programmeMediaIntro');
        copy.append(title, intro);
        if (media) {
            const state = document.createElement('span');
            state.className = `programme-media-state ${media.status.toLowerCase()}`;
            state.textContent = t(`admin.programmeMediaStatus.${media.status}`);
            heading.append(copy, state);
        } else {
            heading.append(copy);
        }
        panel.append(heading);

        if (!media || media.status === 'STALE') {
            const start = actionButton(t('admin.programmeMediaStart'), true,
                () => startProgrammeMedia(programme.id, Boolean(media), start));
            start.className = 'primary-button programme-action';
            panel.append(start);
            return panel;
        }
        if (['QUEUED_SCRIPT', 'GENERATING_SCRIPT', 'QUEUED_MEDIA', 'RENDERING_MEDIA'].includes(media.status)) {
            const progress = document.createElement('p');
            progress.className = 'programme-media-progress';
            progress.textContent = t(`admin.programmeMediaProgress.${media.status}`);
            panel.append(progress);
            return panel;
        }
        if (media.status === 'SCRIPT_REVIEW') {
            panel.append(mediaScriptEditor(media));
            return panel;
        }
        if (media.status === 'FAILED') {
            const error = document.createElement('p');
            error.className = 'programme-media-error';
            error.textContent = media.lastErrorMessage || t('admin.programmeMediaFailed');
            const retry = actionButton(t('admin.programmeMediaRetry'), true,
                () => postAction(`/api/admin/programmes/media/${media.id}/retry`, retry,
                    t('admin.programmeMediaRetryQueued')));
            panel.append(error, retry);
            return panel;
        }
        if (['MEDIA_REVIEW', 'PUBLISHED'].includes(media.status)) {
            const preview = document.createElement('div');
            preview.className = 'programme-media-preview';
            const video = document.createElement('video');
            video.controls = true;
            video.preload = 'metadata';
            video.playsInline = true;
            video.src = `/api/admin/programmes/media/${media.id}/asset/video`;
            const track = document.createElement('track');
            track.kind = 'captions';
            track.srclang = 'ary';
            track.label = t('admin.programmeMediaDarijaCaptions');
            track.src = `/api/admin/programmes/media/${media.id}/asset/captions`;
            video.append(track);
            const details = document.createElement('div');
            const headline = document.createElement('strong');
            headline.dir = 'rtl';
            headline.textContent = media.script?.headline || '';
            const duration = document.createElement('p');
            duration.textContent = t('admin.programmeMediaDuration', {
                minutes: Math.max(1, Math.round((media.durationMs || 0) / 60_000)),
                illustrations: media.illustrationCount || 0
            });
            const audio = document.createElement('audio');
            audio.controls = true;
            audio.preload = 'metadata';
            audio.src = `/api/admin/programmes/media/${media.id}/asset/audio`;
            details.append(headline, duration, audio);
            preview.append(video, details);
            panel.append(preview);
            const actions = document.createElement('div');
            actions.className = 'admin-video-actions';
            if (media.status === 'MEDIA_REVIEW') {
                const publish = actionButton(t('admin.programmeMediaPublish'), true,
                    () => publishProgrammeMedia(media.id, publish));
                publish.className = 'primary-button programme-action';
                actions.append(publish);
            }
            const regenerate = actionButton(t('admin.programmeMediaRegenerate'), true,
                () => startProgrammeMedia(programme.id, true, regenerate));
            actions.append(regenerate);
            panel.append(actions);
        }
        return panel;
    }

    function mediaScriptEditor(media) {
        const form = document.createElement('form');
        form.className = 'programme-media-script';
        const headlineLabel = document.createElement('label');
        headlineLabel.textContent = t('admin.programmeMediaHeadline');
        const headline = document.createElement('input');
        headline.required = true;
        headline.maxLength = 120;
        headline.dir = 'rtl';
        headline.value = media.script.headline;
        headline.dataset.mediaHeadline = '';
        headlineLabel.append(headline);
        form.append(headlineLabel);
        media.script.segments.forEach((segment, index) => {
            const fieldset = document.createElement('fieldset');
            fieldset.dataset.mediaSegment = '';
            const legend = document.createElement('legend');
            legend.textContent = t('admin.programmeMediaSegment', { number: index + 1 });
            const messageLabel = document.createElement('label');
            messageLabel.textContent = t('admin.programmeMediaMessage');
            const message = document.createElement('input');
            message.required = true;
            message.maxLength = 100;
            message.dir = 'rtl';
            message.value = segment.message;
            message.dataset.mediaMessage = '';
            messageLabel.append(message);
            const narrationLabel = document.createElement('label');
            narrationLabel.textContent = t('admin.programmeMediaNarration');
            const narration = document.createElement('textarea');
            narration.required = true;
            narration.maxLength = 700;
            narration.rows = 4;
            narration.dir = 'rtl';
            narration.value = segment.narration;
            narration.dataset.mediaNarration = '';
            narrationLabel.append(narration);
            const refs = document.createElement('small');
            refs.dataset.mediaRefs = JSON.stringify(segment.sourceRefs);
            refs.textContent = `${t('admin.programmeMediaSources')}: ${segment.sourceRefs.join(' · ')}`;
            fieldset.append(legend, messageLabel, narrationLabel, refs);
            form.append(fieldset);
        });
        const stats = document.createElement('output');
        stats.className = 'programme-media-script-stats';
        stats.dataset.mediaScriptStats = '';
        stats.setAttribute('aria-live', 'polite');
        const updateStats = () => {
            const words = [...form.querySelectorAll('[data-media-narration]')]
                .map(field => field.value.trim())
                .filter(Boolean)
                .join(' ')
                .split(/\s+/u)
                .filter(Boolean)
                .length;
            stats.textContent = t('admin.programmeMediaScriptLength', {
                words,
                minutes: Math.max(1, Math.round(words / 130))
            });
        };
        form.addEventListener('input', updateStats);
        updateStats();
        form.append(stats);
        const actions = document.createElement('div');
        actions.className = 'admin-video-actions';
        const save = actionButton(t('admin.programmeMediaSaveScript'), true,
            () => saveProgrammeMediaScript(media.id, form, save, false));
        const approve = actionButton(t('admin.programmeMediaApproveScript'), true,
            () => saveProgrammeMediaScript(media.id, form, approve, true));
        approve.className = 'primary-button programme-action';
        actions.append(save, approve);
        form.append(actions);
        form.addEventListener('submit', event => event.preventDefault());
        return form;
    }

    function scriptPayload(form) {
        return {
            headline: form.querySelector('[data-media-headline]').value.trim(),
            segments: [...form.querySelectorAll('[data-media-segment]')].map(segment => ({
                message: segment.querySelector('[data-media-message]').value.trim(),
                narration: segment.querySelector('[data-media-narration]').value.trim(),
                sourceRefs: JSON.parse(segment.querySelector('[data-media-refs]').dataset.mediaRefs)
            }))
        };
    }

    async function startProgrammeMedia(programmeId, regenerate, button) {
        if (regenerate && !window.confirm(t('admin.programmeMediaRegenerateConfirm'))) return;
        await postAction(
            `/api/admin/programmes/${programmeId}/media?regenerate=${regenerate}`,
            button,
            t('admin.programmeMediaQueued'));
    }

    async function saveProgrammeMediaScript(mediaId, form, button, approve) {
        if (!form.reportValidity()) return;
        if (approve && !window.confirm(t('admin.programmeMediaApproveConfirm'))) return;
        button.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: approve ? 'POST' : 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(scriptPayload(form))
            });
            await window.FhemniCatalog.requestJson(
                `/api/admin/programmes/media/${mediaId}/${approve ? 'approve-script' : 'script'}`,
                options,
                30_000);
            showFeedback(t(approve ? 'admin.programmeMediaRenderQueued' : 'admin.programmeMediaScriptSaved'), false);
            await load();
        } catch (error) {
            showFeedback(error.message, true);
            button.disabled = false;
        }
    }

    async function publishProgrammeMedia(mediaId, button) {
        if (!window.confirm(t('admin.programmeMediaPublishConfirm'))) return;
        await postAction(
            `/api/admin/programmes/media/${mediaId}/publish`,
            button,
            t('admin.programmeMediaPublished'));
    }

    function assessmentJobPanel(programme, job) {
        const panel = document.createElement('section');
        panel.className = 'programme-recovery-panel';
        const copy = document.createElement('div');
        const title = document.createElement('strong');
        const body = document.createElement('p');
        const { complete, total, remaining } = assessmentCounts(programme);
        const state = job?.status || 'READY';
        title.textContent = t(`admin.programmeJob.${state}.title`);
        body.textContent = t(`admin.programmeJob.${state}.body`, {
            complete, total, remaining,
            current: job?.currentPromiseSlug || '',
            jobComplete: job?.completedItems || 0,
            jobTotal: job?.totalItems || remaining,
            failed: job?.failedItems || 0,
            mode: t(`admin.programmeMode.${job?.providerMode || 'unknown'}`)
        });
        copy.append(title, body);
        if (job?.lastErrorCode) {
            const error = document.createElement('small');
            error.className = 'video-meta';
            error.textContent = programmeError({
                code: job.lastErrorCode,
                message: job.lastErrorMessage
            });
            copy.append(error);
        }
        panel.append(copy);
        if (!['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(state)) {
            const label = state === 'READY'
                ? t('admin.startProgrammeAssessment')
                : t('admin.retryProgrammeAssessment');
            const retry = actionButton(label, true,
                () => startAssessment(programme.id, retry));
            retry.className = 'primary-button programme-action';
            panel.append(retry);
        }
        return panel;
    }

    function assessmentCompletePanel(programme) {
        const panel = document.createElement('section');
        panel.className = 'programme-complete-panel';
        const title = document.createElement('strong');
        title.textContent = t('admin.programmeAssessmentCompleteTitle');
        const body = document.createElement('p');
        const { complete, total } = assessmentCounts(programme);
        body.textContent = t('admin.programmeAssessmentCompleteBody', { complete, total });
        panel.append(title, body);
        return panel;
    }

    function assessmentCounts(programme) {
        const total = programme.promises.length;
        const complete = programme.promises.filter(item => item.assessments.length).length;
        return { complete, total, remaining: total - complete };
    }

    function extractionCoveragePanel(programme) {
        const panel = document.createElement('section');
        panel.className = 'programme-coverage-panel';
        const note = document.createElement('p');
        note.textContent = t('admin.programmeLowExtractionCount', {
            count: programme.promises.length
        });
        const rescan = actionButton(t('admin.rescanProgrammePdf'), true, () => {
            sourceUrl.value = programme.sourceUrl;
            ingestForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
            pdf.focus();
        });
        panel.append(note, rescan);
        return panel;
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

    async function startAssessment(programmeId, button) {
        button.disabled = true;
        button.textContent = t('admin.programmeAssessmentRetrying');
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST' });
            await window.FhemniCatalog.requestJson(
                `/api/admin/programmes/${programmeId}/assessment-jobs`, options, 30_000);
            showFeedback(t('admin.programmeAssessmentQueued'), false);
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

    function schedulePolling() {
        window.clearTimeout(pollTimer);
        const running = Object.values(jobsByProgramme)
            .some(job => ['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(job.status))
            || Object.values(mediaByProgramme)
                .some(media => ['QUEUED_SCRIPT', 'GENERATING_SCRIPT', 'QUEUED_MEDIA', 'RENDERING_MEDIA'].includes(media.status));
        if (running) pollTimer = window.setTimeout(load, 3000);
    }

    ingestForm.addEventListener('submit', ingest);
    sourceUrl.addEventListener('input', syncReplacementOption);
    pdf.addEventListener('change', syncReplacementOption);
    refresh.addEventListener('click', load);
    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', render);
})();
