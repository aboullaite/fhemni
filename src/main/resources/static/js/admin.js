(function () {
    const form = document.querySelector('#catalogImportForm');
    const urls = document.querySelector('#importUrls');
    const show = document.querySelector('#importShow');
    const language = document.querySelector('#importLanguage');
    const button = document.querySelector('#importButton');
    const feedback = document.querySelector('#importFeedback');
    const list = document.querySelector('#adminCatalogList');
    const count = document.querySelector('#adminCatalogCount');
    const refreshCatalogDates = document.querySelector('#refreshCatalogDates');
    const catalogMetadataFeedback = document.querySelector('#catalogMetadataFeedback');
    const suggestionList = document.querySelector('#adminSuggestionList');
    const suggestionCount = document.querySelector('#adminSuggestionCount');
    const suggestionFeedback = document.querySelector('#adminSuggestionFeedback');
    const refreshSuggestionMetadata = document.querySelector('#refreshSuggestionMetadata');
    const batchLanguage = document.querySelector('#batchAnalysisLanguage');
    const batchButton = document.querySelector('#batchAnalysisButton');
    const batchFeedback = document.querySelector('#batchAnalysisFeedback');
    const contextMigrationPanel = document.querySelector('#contextMigrationPanel');
    const contextMigrationButton = document.querySelector('#contextMigrationButton');
    const contextMigrationFeedback = document.querySelector('#contextMigrationFeedback');
    const refreshMetricsButton = document.querySelector('#refreshAdminMetrics');
    const metricsFeedback = document.querySelector('#adminMetricsFeedback');
    const registeredUsersMetric = document.querySelector('#registeredUsersMetric');
    const registeredUsersDetail = document.querySelector('#registeredUsersDetail');
    const chatRoundsMetric = document.querySelector('#chatRoundsMetric');
    const chatRoundsDetail = document.querySelector('#chatRoundsDetail');
    const chatTokensMetric = document.querySelector('#chatTokensMetric');
    const chatTokensDetail = document.querySelector('#chatTokensDetail');
    const allAiTokensMetric = document.querySelector('#allAiTokensMetric');
    const allAiTokensDetail = document.querySelector('#allAiTokensDetail');
    const assessmentReportsAlert = document.querySelector('#adminAssessmentReportsAlert');
    const assessmentReportsCount = document.querySelector('#adminAssessmentReportsCount');
    const assessmentReportsDetail = document.querySelector('#adminAssessmentReportsDetail');
    let videos = [];
    let suggestions = [];
    let analysisAvailable = false;
    let batchRunning = false;
    let batchStatus = null;
    let batchPollTimer = null;
    let contextMigrationEnabled = false;
    let contextMigrationPending = 0;
    let contextMigrationStatus = null;
    let contextMigrationRunning = false;
    let contextMigrationPollTimer = null;
    let metrics = null;
    let openAssessmentReportCount = 0;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    async function loadMetrics() {
        if (!refreshMetricsButton) return;
        refreshMetricsButton.disabled = true;
        metricsFeedback.hidden = true;
        try {
            metrics = await window.FhemniCatalog.requestJson('/api/admin/metrics/overview');
            renderMetrics();
        } catch (error) {
            metricsFeedback.textContent = t('admin.metricsFailed', { message: error.message });
            metricsFeedback.hidden = false;
        } finally {
            refreshMetricsButton.disabled = false;
        }
    }

    function renderMetrics() {
        if (!metrics) return;
        registeredUsersMetric.textContent = formatMetric(metrics.users.registered);
        registeredUsersDetail.textContent = t('admin.metricsUsersDetail', {
            newUsers: formatMetric(metrics.users.newLast24Hours),
            activeUsers: formatMetric(metrics.users.activeLast24Hours)
        });
        chatRoundsMetric.textContent = formatMetric(metrics.chat.requests);
        chatRoundsDetail.textContent = t('admin.metricsChatDetail', {
            users: formatMetric(metrics.chat.users),
            succeeded: formatMetric(metrics.chat.succeeded),
            failed: formatMetric(metrics.chat.failed),
            pending: formatMetric(metrics.chat.pending),
            stale: formatMetric(metrics.chat.stale)
        });
        chatTokensMetric.textContent = formatMetric(metrics.chat.recordedTokens, true);
        chatTokensDetail.textContent = t('admin.metricsTokenDetail', {
            input: formatMetric(metrics.chat.inputTokens, true),
            output: formatMetric(metrics.chat.outputTokens, true),
            thought: formatMetric(metrics.chat.thoughtTokens, true)
        });
        allAiTokensMetric.textContent = formatMetric(metrics.allAi.recordedTokens, true);
        allAiTokensDetail.textContent = t('admin.metricsAllAiDetail', {
            requests: formatMetric(metrics.allAi.requests),
            pending: formatMetric(metrics.allAi.pending),
            stale: formatMetric(metrics.allAi.stale),
            cached: formatMetric(metrics.allAi.cachedTokens, true)
        });
    }

    function formatMetric(value, compact = false) {
        return new Intl.NumberFormat(window.FhemniI18n?.locale() || 'en', compact
            ? { notation: 'compact', maximumFractionDigits: 1 }
            : undefined).format(Number(value) || 0);
    }

    async function loadAssessmentReports() {
        if (!assessmentReportsAlert) return;
        try {
            const reports = await window.FhemniCatalog.requestJson('/api/admin/programmes/assessment-reports');
            openAssessmentReportCount = reports.length;
            renderAssessmentReportsAlert();
        } catch (_) {
            assessmentReportsAlert.hidden = true;
        }
    }

    function renderAssessmentReportsAlert() {
        if (!assessmentReportsAlert) return;
        assessmentReportsAlert.hidden = openAssessmentReportCount === 0;
        assessmentReportsCount.textContent = formatMetric(openAssessmentReportCount);
        assessmentReportsDetail.textContent = t('admin.readerReportsAlertCopy', {
            count: formatMetric(openAssessmentReportCount)
        });
    }

    async function loadVideos() {
        if (!list) return;
        try {
            videos = await window.FhemniCatalog.requestJson('/api/admin/catalog/videos');
            renderVideos();
        } catch (error) {
            window.FhemniCatalog.renderError(list, error.message);
        }
    }

    async function loadSuggestions() {
        if (!suggestionList) return;
        try {
            suggestions = await window.FhemniCatalog.requestJson('/api/admin/suggestions');
            renderSuggestions();
        } catch (error) {
            window.FhemniCatalog.renderError(suggestionList, error.message);
        }
    }

    async function loadCapabilities() {
        if (!list) return;
        try {
            const meta = await window.FhemniCatalog.requestJson('/api/meta');
            analysisAvailable = meta.live === true && meta.analysisEnabled === true;
        } catch (_) {
            analysisAvailable = false;
        }
        if (videos.length) renderVideos();
    }

    async function importVideos(event) {
        event.preventDefault();
        feedback.hidden = true;
        let items;
        try {
            items = parseLines(urls.value);
        } catch (error) {
            showFeedback(error.message, true);
            return;
        }
        button.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ items, showName: show.value, sourceLanguage: language.value })
            });
            const result = await window.FhemniCatalog.requestJson('/api/admin/catalog/videos/import', options, 130_000);
            showFeedback(t('admin.importResult', result), result.failed > 0);
            if (result.failed === 0) urls.value = '';
            await Promise.all([loadVideos(), loadSuggestions()]);
        } catch (error) {
            showFeedback(error.message, true);
        } finally {
            button.disabled = false;
        }
    }

    function parseLines(value) {
        const lines = value.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
        if (!lines.length) throw new Error(t('admin.urlsRequired'));
        if (lines.length > 20) throw new Error(t('admin.tooManyUrls'));
        return lines.map(line => {
            const [youtubeUrl, date, ...extra] = line.split('|').map(part => part.trim());
            if (extra.length || (date && !/^\d{4}-\d{2}-\d{2}$/.test(date))) {
                throw new Error(t('admin.invalidLine', { line }));
            }
            return { youtubeUrl, publishedOn: date || null };
        });
    }

    function renderVideos() {
        list.replaceChildren();
        count.textContent = t('catalog.episodeCount', { count: videos.length });
        const pendingCount = pendingVideos().length;
        batchButton.disabled = batchRunning || contextMigrationRunning || !analysisAvailable || pendingCount === 0;
        batchLanguage.disabled = batchRunning || contextMigrationRunning || !analysisAvailable || pendingCount === 0;
        batchButton.textContent = batchRunning
            ? t('admin.batchRunning')
            : t('admin.runBatchCount', { count: pendingCount });
        batchButton.title = !analysisAvailable ? t('admin.analysisUnavailable') : '';
        videos.forEach(video => {
            const row = document.createElement('article');
            row.className = 'admin-video-row';
            const image = document.createElement('img');
            image.src = video.thumbnailUrl;
            image.alt = '';
            const copy = document.createElement('div');
            const title = document.createElement('strong');
            title.dir = 'auto';
            title.textContent = video.title;
            const meta = document.createElement('span');
            meta.textContent = [video.showName, video.authorName, window.FhemniCatalog.formatDate(video.publishedOn)].filter(Boolean).join(' · ');
            copy.append(title, meta);
            const link = document.createElement('a');
            link.className = 'secondary-button';
            link.href = `/videos/${encodeURIComponent(video.slug)}`;
            link.textContent = t('admin.view');

            const actions = document.createElement('div');
            actions.className = 'admin-video-actions';
            const outputLanguage = document.createElement('select');
            outputLanguage.className = 'admin-analysis-language';
            outputLanguage.setAttribute('aria-label', t('admin.outputLanguage'));
            outputLanguage.title = t('admin.outputLanguage');
            [
                ['ary', 'catalog.darija'],
                ['fr', 'catalog.french'],
                ['en', 'catalog.english']
            ].forEach(([value, labelKey]) => {
                const option = document.createElement('option');
                option.value = value;
                option.textContent = t(labelKey);
                outputLanguage.append(option);
            });
            const latestLanguage = {
                DARIJA: 'ary', FRENCH: 'fr', ENGLISH: 'en'
            }[video.latestAnalysisLanguage];
            if (latestLanguage) outputLanguage.value = latestLanguage;

            const analyze = document.createElement('button');
            analyze.type = 'button';
            const hasPreviousAttempt = Boolean(video.latestAnalysisId);
            const forceReprocess = hasPreviousAttempt;
            analyze.className = video.latestAnalysisStatus === 'COMPLETED'
                ? 'secondary-button admin-analyze-button'
                : 'primary-button admin-analyze-button';
            analyze.textContent = t(video.latestAnalysisStatus === 'COMPLETED'
                ? 'admin.reprocessAnalysis'
                : 'admin.runAnalysis');
            analyze.disabled = batchRunning || contextMigrationRunning || !analysisAvailable;
            if (!analysisAvailable) analyze.title = t('admin.analysisUnavailable');
            analyze.addEventListener('click', () => launchAnalysis(
                video, outputLanguage, analyze, forceReprocess));

            const canStartAnalysis = !video.latestAnalysisId || video.latestAnalysisStatus === 'FAILED';
            if (canStartAnalysis || forceReprocess) actions.append(outputLanguage, analyze);
            if (video.latestAnalysisId) {
                const review = document.createElement('a');
                review.className = 'secondary-button';
                review.href = `/analyses/${encodeURIComponent(video.latestAnalysisId)}`;
                review.textContent = t(video.publishedAnalysisId === video.latestAnalysisId
                    ? 'admin.reviewPublished'
                    : 'admin.reviewAnalysis');
                actions.append(review);
            }
            actions.append(link);
            row.append(image, copy, actions);
            list.append(row);
        });
    }

    async function launchAnalysis(video, outputLanguage, analyzeButton, forceReprocess = false) {
        if (!analysisAvailable) {
            showFeedback(t('admin.analysisUnavailable'), true);
            return;
        }
        if (forceReprocess && video.latestAnalysisStatus === 'COMPLETED'
                && !window.confirm(t('admin.confirmReprocess', {
            language: outputLanguage.selectedOptions[0]?.textContent || outputLanguage.value
        }))) return;
        const originalLabel = analyzeButton.textContent;
        analyzeButton.disabled = true;
        outputLanguage.disabled = true;
        analyzeButton.textContent = t('admin.startingAnalysis');
        showFeedback(t('admin.analysisStarting'), false);
        try {
            const analysis = await createAnalysis(video, outputLanguage.value, forceReprocess);
            window.location.assign(`/analyses/${encodeURIComponent(analysis.id)}`);
        } catch (error) {
            showFeedback(t('admin.analysisFailed', { message: error.message }), true);
            analyzeButton.disabled = false;
            outputLanguage.disabled = false;
            analyzeButton.textContent = originalLabel;
        }
    }

    function pendingVideos() {
        return videos.filter(video => !video.latestAnalysisId || video.latestAnalysisStatus === 'FAILED');
    }

    async function createAnalysis(video, languageCode, forceReprocess = false) {
        const options = await window.FhemniAuth.withCsrf({
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ youtubeUrl: video.canonicalUrl, language: languageCode })
        });
        const endpoint = forceReprocess ? '/api/admin/analyses/reprocess' : '/api/analyses';
        const analysis = await window.FhemniCatalog.requestJson(endpoint, options, 30_000);
        if (!analysis?.id) throw new Error(t('common.requestFailed', { status: 502 }));
        return analysis;
    }

    async function runBatchAnalysis() {
        if (batchRunning || !analysisAvailable) return;
        const pending = pendingVideos();
        if (!pending.length) return;

        batchRunning = true;
        renderVideos();
        showBatchFeedback(t('admin.batchStarting', { count: pending.length }), false);
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ language: batchLanguage.value, maxItems: pending.length })
            });
            const status = await window.FhemniCatalog.requestJson(
                '/api/admin/catalog/analysis-batches',
                options,
                30_000
            );
            applyBatchStatus(status);
        } catch (error) {
            showBatchFeedback(t('admin.batchFailed', {
                completed: 0,
                count: pending.length,
                message: error.message
            }), true);
            batchRunning = false;
            renderVideos();
        }
    }

    async function loadBatchStatus() {
        if (!batchButton) return;
        try {
            const status = await window.FhemniCatalog.requestJson(
                '/api/admin/catalog/analysis-batches/latest',
                {},
                30_000
            );
            if (status) applyBatchStatus(status);
        } catch (error) {
            if (batchRunning) {
                showBatchFeedback(t('admin.batchStatusFailed', { message: error.message }), true);
                scheduleBatchPoll();
            }
        }
    }

    function applyBatchStatus(status) {
        batchStatus = status;
        batchRunning = status.state === 'RUNNING';
        const languageCode = {
            DARIJA: 'ary', FRENCH: 'fr', ENGLISH: 'en'
        }[status.language];
        if (languageCode) batchLanguage.value = languageCode;
        renderVideos();
        renderBatchStatus();
        if (batchRunning) {
            scheduleBatchPoll();
        } else {
            window.clearTimeout(batchPollTimer);
            loadVideos();
        }
    }

    function renderBatchStatus() {
        if (!batchStatus) return;
        if (batchStatus.state === 'RUNNING') {
            const current = Math.min(batchStatus.completed + 1, batchStatus.total);
            showBatchFeedback(batchStatus.currentVideoTitle
                ? t('admin.batchProgress', {
                    current,
                    count: batchStatus.total,
                    title: batchStatus.currentVideoTitle
                })
                : t('admin.batchStarting', { count: batchStatus.total }), false);
            return;
        }
        if (batchStatus.state === 'COMPLETED') {
            showBatchFeedback(t('admin.batchComplete', { count: batchStatus.completed }), false);
            return;
        }
        showBatchFeedback(t('admin.batchFailed', {
            completed: batchStatus.completed,
            count: batchStatus.total,
            message: batchStatus.error || t('admin.batchEpisodeFailed')
        }), true);
    }

    function scheduleBatchPoll() {
        window.clearTimeout(batchPollTimer);
        if (!batchRunning) return;
        batchPollTimer = window.setTimeout(loadBatchStatus, 4_000);
    }

    async function loadContextMigrationStatus() {
        if (!contextMigrationPanel) return;
        try {
            const overview = await window.FhemniCatalog.requestJson(
                '/api/admin/catalog/context-migration', {}, 30_000);
            contextMigrationEnabled = overview.enabled === true;
            contextMigrationPending = overview.pending || 0;
            contextMigrationStatus = overview.latest || null;
            contextMigrationRunning = contextMigrationStatus?.state === 'RUNNING';
            renderContextMigrationStatus();
            renderVideos();
            if (contextMigrationRunning) scheduleContextMigrationPoll();
        } catch (error) {
            if (contextMigrationRunning) {
                showContextMigrationFeedback(t('admin.contextMigrationStatusFailed', {
                    message: error.message
                }), true);
                scheduleContextMigrationPoll();
            }
        }
    }

    async function runContextMigration() {
        if (!contextMigrationEnabled || contextMigrationRunning || !analysisAvailable
                || contextMigrationPending === 0) return;
        if (!window.confirm(t('admin.contextMigrationConfirm', { count: contextMigrationPending }))) return;

        contextMigrationRunning = true;
        renderContextMigrationStatus();
        renderVideos();
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ maxItems: Math.min(100, contextMigrationPending) })
            });
            contextMigrationStatus = await window.FhemniCatalog.requestJson(
                '/api/admin/catalog/context-migration', options, 30_000);
            contextMigrationRunning = contextMigrationStatus?.state === 'RUNNING';
            renderContextMigrationStatus();
            if (contextMigrationRunning) scheduleContextMigrationPoll();
        } catch (error) {
            contextMigrationRunning = false;
            showContextMigrationFeedback(t('admin.contextMigrationFailed', {
                message: error.message
            }), true);
            renderContextMigrationStatus();
            renderVideos();
        }
    }

    function renderContextMigrationStatus() {
        contextMigrationPanel.hidden = !contextMigrationEnabled;
        if (!contextMigrationEnabled) return;
        contextMigrationButton.disabled = contextMigrationRunning
            || !analysisAvailable
            || contextMigrationPending === 0;
        contextMigrationButton.textContent = contextMigrationRunning
            ? t('admin.contextMigrationRunning')
            : t('admin.contextMigrationRunCount', { count: contextMigrationPending });
        if (!contextMigrationStatus) return;

        if (contextMigrationStatus.state === 'RUNNING') {
            const current = Math.min(
                contextMigrationStatus.completed + contextMigrationStatus.failed + 1,
                contextMigrationStatus.total);
            showContextMigrationFeedback(t('admin.contextMigrationProgress', {
                current,
                count: contextMigrationStatus.total,
                title: contextMigrationStatus.currentVideoTitle || '…'
            }), false);
            return;
        }
        if (contextMigrationStatus.state === 'COMPLETED') {
            showContextMigrationFeedback(t('admin.contextMigrationComplete', {
                count: contextMigrationStatus.completed
            }), false);
            return;
        }
        showContextMigrationFeedback(t('admin.contextMigrationCompletedWithErrors', {
            completed: contextMigrationStatus.completed,
            failed: contextMigrationStatus.failed,
            message: contextMigrationStatus.error || t('admin.batchEpisodeFailed')
        }), true);
    }

    function scheduleContextMigrationPoll() {
        window.clearTimeout(contextMigrationPollTimer);
        if (!contextMigrationRunning) return;
        contextMigrationPollTimer = window.setTimeout(async () => {
            await loadContextMigrationStatus();
            if (!contextMigrationRunning) await loadVideos();
        }, 5_000);
    }

    function renderSuggestions() {
        suggestionList.replaceChildren();
        suggestionCount.textContent = t('admin.pendingSuggestions', { count: suggestions.length });
        if (!suggestions.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('admin.noSuggestions');
            suggestionList.append(empty);
            return;
        }
        suggestions.forEach(suggestion => suggestionList.append(createSuggestionRow(suggestion)));
    }

    function createSuggestionRow(suggestion) {
        const row = document.createElement('article');
        row.className = 'admin-suggestion-row';

        const copy = document.createElement('div');
        copy.className = 'admin-suggestion-copy';
        const link = document.createElement('a');
        link.href = suggestion.canonicalUrl;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.textContent = suggestion.title || suggestion.canonicalUrl;
        const meta = document.createElement('span');
        meta.textContent = [
            suggestion.suggestedByFirstName
                ? t('suggestion.suggestedBy', { name: suggestion.suggestedByFirstName })
                : t('suggestion.suggestedByCommunity'),
            suggestion.authorName,
            t('admin.suggestionCount', { count: suggestion.submissionCount }),
            t('admin.suggestionScore', { score: suggestion.voteScore }),
            suggestion.moderationStatus === 'REVIEW_REQUIRED' ? t('admin.needsSafetyReview') : null
        ].filter(Boolean).join(' · ');
        if (suggestion.moderationReason) meta.title = suggestion.moderationReason;
        copy.append(link, meta);

        const actions = document.createElement('div');
        actions.className = 'admin-suggestion-actions';
        if (suggestion.moderationStatus === 'REVIEW_REQUIRED') {
            const approve = document.createElement('button');
            approve.type = 'button';
            approve.className = 'secondary-button';
            approve.textContent = t('admin.approveSuggestion');
            approve.addEventListener('click', () => approveSuggestion(suggestion.id, approve));
            actions.append(approve);
        } else {
            const add = document.createElement('button');
            add.type = 'button';
            add.className = 'secondary-button';
            add.textContent = t('admin.addToImporter');
            add.addEventListener('click', () => addToImporter(suggestion.canonicalUrl));
            actions.append(add);
        }
        const dismiss = document.createElement('button');
        dismiss.type = 'button';
        dismiss.className = 'text-button';
        dismiss.textContent = t('admin.dismiss');
        dismiss.addEventListener('click', () => dismissSuggestion(suggestion.id, dismiss));
        actions.append(dismiss);
        row.append(copy, actions);
        return row;
    }

    function addToImporter(youtubeUrl) {
        if (!form || !urls) {
            window.location.assign(`/admin/episodes?youtubeUrl=${encodeURIComponent(youtubeUrl)}`);
            return;
        }
        const lines = urls.value.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
        if (!lines.some(line => line.split('|')[0].trim() === youtubeUrl)) {
            lines.push(youtubeUrl);
            urls.value = lines.join('\n');
        }
        form.scrollIntoView({ behavior: 'smooth', block: 'center' });
        urls.focus({ preventScroll: true });
    }

    async function dismissSuggestion(id, dismissButton) {
        dismissButton.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST' });
            await window.FhemniCatalog.requestJson(`/api/admin/suggestions/${encodeURIComponent(id)}/dismiss`, options);
            await loadSuggestions();
        } catch (error) {
            showSuggestionFeedback(t('admin.dismissFailed', { message: error.message }), true);
            dismissButton.disabled = false;
        }
    }

    async function approveSuggestion(id, approveButton) {
        approveButton.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST' });
            await window.FhemniCatalog.requestJson(
                `/api/admin/suggestions/${encodeURIComponent(id)}/approve`, options);
            showSuggestionFeedback(t('admin.suggestionApproved'), false);
            await loadSuggestions();
        } catch (error) {
            showSuggestionFeedback(t('admin.approveSuggestionFailed', { message: error.message }), true);
            approveButton.disabled = false;
        }
    }

    async function refreshMissingSuggestionMetadata() {
        refreshSuggestionMetadata.disabled = true;
        showSuggestionFeedback(t('admin.refreshingSuggestionMetadata'), false);
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST' });
            const result = await window.FhemniCatalog.requestJson(
                '/api/admin/suggestions/refresh-metadata', options, 130_000);
            showSuggestionFeedback(t('admin.suggestionMetadataResult', result), result.failed > 0);
            await loadSuggestions();
        } catch (error) {
            showSuggestionFeedback(t('admin.refreshSuggestionMetadataFailed', { message: error.message }), true);
        } finally {
            refreshSuggestionMetadata.disabled = false;
        }
    }

    async function refreshMissingCatalogDates() {
        refreshCatalogDates.disabled = true;
        showCatalogMetadataFeedback(t('admin.refreshingCatalogDates'), false);
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST' });
            const result = await window.FhemniCatalog.requestJson(
                '/api/admin/catalog/videos/refresh-dates', options, 130_000);
            showCatalogMetadataFeedback(t('admin.catalogDatesResult', result), result.failed > 0);
            await loadVideos();
        } catch (error) {
            showCatalogMetadataFeedback(t('admin.refreshCatalogDatesFailed', { message: error.message }), true);
        } finally {
            refreshCatalogDates.disabled = false;
        }
    }

    function showFeedback(message, error) {
        feedback.className = `import-feedback ${error ? 'error' : 'success'}`;
        feedback.textContent = message;
        feedback.hidden = false;
    }

    function showBatchFeedback(message, error) {
        batchFeedback.className = `import-feedback ${error ? 'error' : 'success'}`;
        batchFeedback.textContent = message;
        batchFeedback.hidden = false;
    }

    function showSuggestionFeedback(message, error) {
        suggestionFeedback.className = `import-feedback ${error ? 'error' : 'success'}`;
        suggestionFeedback.textContent = message;
        suggestionFeedback.hidden = false;
    }

    function showCatalogMetadataFeedback(message, error) {
        catalogMetadataFeedback.className = `import-feedback ${error ? 'error' : 'success'}`;
        catalogMetadataFeedback.textContent = message;
        catalogMetadataFeedback.hidden = false;
    }

    function showContextMigrationFeedback(message, error) {
        contextMigrationFeedback.className = `import-feedback ${error ? 'error' : 'success'}`;
        contextMigrationFeedback.textContent = message;
        contextMigrationFeedback.hidden = false;
    }

    form?.addEventListener('submit', importVideos);
    batchButton?.addEventListener('click', runBatchAnalysis);
    contextMigrationButton?.addEventListener('click', runContextMigration);
    refreshMetricsButton?.addEventListener('click', () => Promise.all([
        loadMetrics(),
        loadAssessmentReports()
    ]));
    refreshSuggestionMetadata?.addEventListener('click', refreshMissingSuggestionMetadata);
    refreshCatalogDates?.addEventListener('click', refreshMissingCatalogDates);
    document.addEventListener('DOMContentLoaded', () => {
        if (urls) {
            const suggestedUrl = new URLSearchParams(window.location.search).get('youtubeUrl');
            if (suggestedUrl) urls.value = suggestedUrl;
        }
        return Promise.all([
            loadCapabilities(),
            loadMetrics(),
            loadAssessmentReports(),
            loadVideos(),
            loadSuggestions(),
            loadBatchStatus(),
            loadContextMigrationStatus()
        ]);
    });
    document.addEventListener('fhemni:localechange', () => {
        if (list) renderVideos();
        if (suggestionList) renderSuggestions();
        if (refreshMetricsButton) renderMetrics();
        renderAssessmentReportsAlert();
        if (batchButton) renderBatchStatus();
        if (contextMigrationPanel) renderContextMigrationStatus();
    });
})();
