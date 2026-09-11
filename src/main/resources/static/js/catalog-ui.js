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

    function createPromiseReportButton(promiseSlug) {
        const button = document.createElement('button');
        button.className = 'promise-report-icon';
        button.type = 'button';
        button.dataset.promiseReportButton = '';
        button.dataset.tooltip = t('promise.reportTooltip');
        button.setAttribute('aria-label', t('promise.reportButton'));
        button.setAttribute('aria-haspopup', 'dialog');

        const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        icon.setAttribute('viewBox', '0 0 24 24');
        icon.setAttribute('aria-hidden', 'true');
        const bubble = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        bubble.setAttribute('d', 'M21 15a4 4 0 0 1-4 4H7l-4 4V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z');
        const mark = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        mark.setAttribute('d', 'M12 7v4');
        const dot = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        dot.setAttribute('d', 'M12 15h.01');
        icon.append(bubble, mark, dot);
        button.append(icon);
        button.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            openPromiseReport(promiseSlug).catch(() => {
                window.location.assign(window.FhemniAuth.loginPage(
                    `/promises/${encodeURIComponent(promiseSlug)}#report`, 'report'));
            });
        });
        return button;
    }

    async function openPromiseReport(promiseSlug) {
        const current = await window.FhemniAuth.session();
        if (!current.authenticated) {
            window.location.assign(window.FhemniAuth.loginPage(
                `/promises/${encodeURIComponent(promiseSlug)}#report`, 'report'));
            return;
        }
        const dialog = promiseReportDialog();
        dialog.dataset.promiseSlug = promiseSlug;
        const form = dialog.querySelector('form');
        const submit = dialog.querySelector('[data-report-submit]');
        const feedback = dialog.querySelector('[data-report-feedback]');
        const success = dialog.querySelector('[data-report-success]');
        form.reset();
        form.hidden = false;
        success.hidden = true;
        submit.disabled = false;
        submit.textContent = t('promise.reportSubmit');
        feedback.classList.remove('error');
        feedback.textContent = '';
        localizePromiseReportDialog(dialog);
        dialog.reportTrigger = document.activeElement instanceof HTMLElement
            ? document.activeElement
            : null;
        if (!dialog.open) dialog.showModal();
        dialog.querySelector('[data-report-details]').focus();
    }

    function promiseReportDialog() {
        let dialog = document.querySelector('[data-promise-report-dialog]');
        if (dialog) return dialog;

        dialog = document.createElement('dialog');
        dialog.className = 'promise-report-dialog';
        dialog.dataset.promiseReportDialog = '';
        dialog.setAttribute('aria-labelledby', 'promiseReportDialogTitle');

        const close = document.createElement('button');
        close.className = 'promise-report-dialog-close';
        close.type = 'button';
        close.dataset.reportClose = '';
        close.textContent = '×';

        const heading = document.createElement('div');
        heading.className = 'promise-report-dialog-heading';
        const title = document.createElement('h2');
        title.id = 'promiseReportDialogTitle';
        title.dataset.reportTitle = '';
        const intro = document.createElement('p');
        intro.dataset.reportIntro = '';
        heading.append(title, intro);

        const form = document.createElement('form');
        form.className = 'promise-report-form';
        const detailsLabel = document.createElement('label');
        const detailsText = document.createElement('span');
        detailsText.dataset.reportDetailsLabel = '';
        const details = document.createElement('textarea');
        details.dataset.reportDetails = '';
        details.minLength = 20;
        details.maxLength = 1500;
        details.rows = 5;
        details.required = true;
        detailsLabel.append(detailsText, details);

        const sourceLabel = document.createElement('label');
        const sourceText = document.createElement('span');
        sourceText.dataset.reportSourceLabel = '';
        const source = document.createElement('input');
        source.dataset.reportSource = '';
        source.type = 'url';
        source.inputMode = 'url';
        source.maxLength = 2048;
        source.placeholder = 'https://…';
        sourceLabel.append(sourceText, source);

        const actions = document.createElement('div');
        actions.className = 'promise-report-actions';
        const submit = document.createElement('button');
        submit.className = 'primary-button';
        submit.type = 'submit';
        submit.dataset.reportSubmit = '';
        const cancel = document.createElement('button');
        cancel.className = 'secondary-button';
        cancel.type = 'button';
        cancel.dataset.reportCancel = '';
        actions.append(submit, cancel);

        const feedback = document.createElement('p');
        feedback.className = 'promise-report-feedback';
        feedback.dataset.reportFeedback = '';
        feedback.setAttribute('role', 'status');
        feedback.setAttribute('aria-live', 'polite');
        form.append(detailsLabel, sourceLabel, actions, feedback);

        const success = document.createElement('div');
        success.className = 'promise-report-success';
        success.dataset.reportSuccess = '';
        success.tabIndex = -1;
        success.hidden = true;
        success.setAttribute('role', 'status');
        success.setAttribute('aria-live', 'polite');
        const successMark = document.createElement('span');
        successMark.className = 'promise-report-success-mark';
        successMark.setAttribute('aria-hidden', 'true');
        successMark.textContent = '✓';
        const successCopy = document.createElement('div');
        const successTitle = document.createElement('strong');
        successTitle.dataset.reportSuccessTitle = '';
        const successText = document.createElement('p');
        successText.dataset.reportSuccessText = '';
        successCopy.append(successTitle, successText);
        success.append(successMark, successCopy);

        dialog.append(close, heading, form, success);
        document.body.append(dialog);

        close.addEventListener('click', () => dialog.close());
        cancel.addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            if (event.target === dialog) dialog.close();
        });
        dialog.addEventListener('close', () => {
            dialog.reportTrigger?.focus();
            dialog.reportTrigger = null;
        });
        form.addEventListener('submit', submitPromiseReport);
        return dialog;
    }

    function localizePromiseReportDialog(dialog) {
        dialog.querySelector('[data-report-close]').title = t('common.cancel');
        dialog.querySelector('[data-report-close]').setAttribute('aria-label', t('common.cancel'));
        dialog.querySelector('[data-report-title]').textContent = t('promise.reportTitle');
        dialog.querySelector('[data-report-intro]').textContent = t('promise.reportIntro');
        dialog.querySelector('[data-report-details-label]').textContent = t('promise.reportDetails');
        dialog.querySelector('[data-report-details]').placeholder = t('promise.reportDetailsPlaceholder');
        dialog.querySelector('[data-report-source-label]').textContent = t('promise.reportSource');
        dialog.querySelector('[data-report-submit]').textContent = t('promise.reportSubmit');
        dialog.querySelector('[data-report-cancel]').textContent = t('common.cancel');
        dialog.querySelector('[data-report-success-title]').textContent = t('promise.reportSaved');
        dialog.querySelector('[data-report-success-text]').textContent = t('promise.reportThanks');
    }

    async function submitPromiseReport(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const dialog = form.closest('[data-promise-report-dialog]');
        const submit = dialog.querySelector('[data-report-submit]');
        const feedback = dialog.querySelector('[data-report-feedback]');
        submit.disabled = true;
        feedback.classList.remove('error');
        feedback.textContent = t('promise.reportSending');
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    details: dialog.querySelector('[data-report-details]').value.trim(),
                    sourceUrl: dialog.querySelector('[data-report-source]').value.trim() || null
                })
            });
            await requestJson(
                `/api/catalog/promises/${encodeURIComponent(dialog.dataset.promiseSlug)}/reports`,
                options,
                15_000);
            form.reset();
            form.hidden = true;
            const success = dialog.querySelector('[data-report-success]');
            success.hidden = false;
            success.focus();
        } catch (error) {
            feedback.classList.add('error');
            feedback.textContent = error.message;
            submit.disabled = false;
        }
    }

    window.FhemniCatalog = {
        requestJson,
        renderGrid,
        renderError,
        statusLabel,
        formatDate,
        safeImage,
        createPromiseReportButton,
        openPromiseReport
    };
    document.addEventListener('fhemni:localechange', () => {
        document.querySelectorAll('[data-promise-report-button]').forEach(button => {
            button.dataset.tooltip = t('promise.reportTooltip');
            button.setAttribute('aria-label', t('promise.reportButton'));
        });
        const dialog = document.querySelector('[data-promise-report-dialog]');
        if (dialog) localizePromiseReportDialog(dialog);
    });
})();
