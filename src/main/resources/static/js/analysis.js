const state = {
    analysisId: null,
    snapshot: null,
    eventSource: null,
    questionMode: 'VIDEO',
    youtubePlayer: null,
    youtubePlayerReady: false,
    youtubeApiPromise: null,
    youtubeApiFailed: false,
    pendingSeekSeconds: null,
    authSession: null,
    publication: null,
    meta: undefined,
    latestProgress: null,
    activeRequests: new Set()
};

const DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
const QUESTION_REQUEST_TIMEOUT_MS = 40_000;

function t(key, parameters = {}) {
    return window.FhemniI18n?.t(key, parameters) ?? key;
}

const elements = {
    modeBadge: document.querySelector('#modeBadge'),
    progressPanel: document.querySelector('#progressPanel'),
    progressMessage: document.querySelector('#progressMessage'),
    progressStage: document.querySelector('#progressStage'),
    progressPercent: document.querySelector('#progressPercent'),
    progressBar: document.querySelector('#progressBar'),
    errorPanel: document.querySelector('#errorPanel'),
    errorTitle: document.querySelector('#errorTitle'),
    errorMessage: document.querySelector('#errorMessage'),
    errorAction: document.querySelector('#analysisErrorAction'),
    analyzeAnotherLink: document.querySelector('#analyzeAnotherLink'),
    results: document.querySelector('#results'),
    demoNotice: document.querySelector('#demoNotice'),
    reportTitle: document.querySelector('#reportTitle'),
    reportSummary: document.querySelector('#reportSummary'),
    detailedSummary: document.querySelector('#detailedSummary'),
    participants: document.querySelector('#participants'),
    publicationPanel: document.querySelector('#publicationPanel'),
    publicationTitle: document.querySelector('#publicationTitle'),
    publicationText: document.querySelector('#publicationText'),
    publishButton: document.querySelector('#publishButton'),
    publicEpisodeLink: document.querySelector('#publicEpisodeLink'),
    chapters: document.querySelector('#chapters'),
    claimsList: document.querySelector('#claimsList'),
    claimCount: document.querySelector('#claimCount'),
    videoPlayer: document.querySelector('#videoPlayer'),
    questionForm: document.querySelector('#questionForm'),
    chatAuthGate: document.querySelector('#chatAuthGate'),
    chatLoginLink: document.querySelector('#chatLoginLink'),
    chatGateBadge: document.querySelector('#chatGateBadge'),
    chatGateTitle: document.querySelector('#chatGateTitle'),
    chatGateText: document.querySelector('#chatGateText'),
    chatQuota: document.querySelector('#chatQuota'),
    chatQuestionQuota: document.querySelector('#chatQuestionQuota'),
    chatTokenProgress: document.querySelector('#chatTokenProgress'),
    chatTokenRing: document.querySelector('#chatTokenRing'),
    chatTokenPercent: document.querySelector('#chatTokenPercent'),
    questionInput: document.querySelector('#questionInput'),
    askButton: document.querySelector('#askButton'),
    conversation: document.querySelector('#conversation'),
    suggestedQuestions: document.querySelector('#suggestedQuestions'),
    modeExplanationTitle: document.querySelector('#modeExplanationTitle'),
    modeExplanation: document.querySelector('#modeExplanation')
};

document.addEventListener('DOMContentLoaded', () => {
    loadMeta();
    loadAuthentication();
    bindEvents();
    loadAnalysis();
});
document.addEventListener('fhemni:localechange', refreshLocalizedContent);
window.addEventListener('pagehide', () => {
    closeEventSource();
    cancelActiveRequests();
    destroyVideoPlayer();
});

function bindEvents() {
    elements.questionForm.addEventListener('submit', askQuestion);
    elements.publishButton.addEventListener('click', publishAnalysis);
    elements.questionInput.addEventListener('keydown', event => {
        if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;

        event.preventDefault();
        if (!elements.askButton.disabled && elements.questionInput.value.trim()) {
            elements.questionForm.requestSubmit();
        }
    });
    document.querySelectorAll('.tab[data-tab]').forEach(button => {
        button.addEventListener('click', () => activateTab(button.dataset.tab));
    });
    document.querySelectorAll('.filter').forEach(button => {
        button.addEventListener('click', () => filterClaims(button.dataset.filter));
    });
    document.querySelectorAll('.mode-option').forEach(button => {
        button.addEventListener('click', () => selectQuestionMode(button.dataset.mode));
    });
    document.addEventListener('click', event => {
        const timestamp = event.target.closest('[data-seconds]');
        if (timestamp) {
            seekVideo(Number(timestamp.dataset.seconds));
        }
    });
}

async function loadMeta() {
    try {
        state.meta = await request('/api/meta');
        renderModeBadge();
        configureChatAccess();
    } catch (_) {
        state.meta = null;
        renderModeBadge();
        configureChatAccess();
    }
}

function renderModeBadge() {
    const meta = state.meta;
    if (state.snapshot?.report) {
        elements.modeBadge.className = 'mode-badge stored';
        elements.modeBadge.textContent = t('common.savedReport');
        elements.modeBadge.title = t('common.savedReportHint');
        elements.modeBadge.setAttribute('aria-label', t('common.savedReportHint'));
    } else if (meta === undefined) {
        elements.modeBadge.className = 'mode-badge loading';
        elements.modeBadge.textContent = t('common.checkingConnection');
        elements.modeBadge.removeAttribute('title');
        elements.modeBadge.removeAttribute('aria-label');
    } else if (meta) {
        elements.modeBadge.className = `mode-badge ${meta.live ? 'live' : 'demo'}`;
        elements.modeBadge.textContent = meta.live
            ? t('common.liveMode', { model: meta.model })
            : t('common.demoMode');
        const hint = meta.live ? t('common.liveModeHint') : '';
        if (hint) {
            elements.modeBadge.title = hint;
            elements.modeBadge.setAttribute('aria-label', hint);
        } else {
            elements.modeBadge.removeAttribute('title');
            elements.modeBadge.removeAttribute('aria-label');
        }
    } else {
        elements.modeBadge.className = 'mode-badge demo';
        elements.modeBadge.textContent = t('common.connectionUnavailable');
        elements.modeBadge.removeAttribute('title');
        elements.modeBadge.removeAttribute('aria-label');
    }
}

function refreshLocalizedContent() {
    renderModeBadge();
    configureChatAccess();
    configureAdminNavigation();
    renderPublication();
    selectQuestionMode(state.questionMode);
    if (state.latestProgress) updateProgress(state.latestProgress);
    if (state.snapshot?.report) {
        const activeFilter = document.querySelector('.filter.active')?.dataset.filter || 'ALL';
        elements.claimsList.innerHTML = state.snapshot.report.claims.map(renderClaim).join('');
        filterClaims(activeFilter);
        document.title = `${state.snapshot.report.title} — Fhemni`;
    }
    const welcome = elements.conversation?.querySelector('.welcome-message p');
    if (welcome) welcome.textContent = t('analysis.welcome');
}

async function loadAuthentication() {
    try {
        state.authSession = await window.FhemniAuth.session();
    } catch (_) {
        state.authSession = { authenticated: false };
    }
    configureChatAccess();
    configureAdminNavigation();
    configurePublication();
}

function configureAdminNavigation() {
    const administrator = state.authSession?.user?.role === 'ADMIN';
    elements.analyzeAnotherLink.hidden = !administrator;
    elements.errorAction.href = administrator ? '/admin' : '/videos';
    elements.errorAction.textContent = t(administrator ? 'analysis.backAdmin' : 'video.back');
}

function configureChatAccess() {
    if (!elements.questionForm || !elements.chatAuthGate) return;
    const authenticated = Boolean(state.authSession?.authenticated);
    const chatEnabled = Boolean(state.meta?.chatEnabled);
    const quota = state.authSession?.chatQuota;
    const weeklyQuotaExhausted = authenticated && chatEnabled && quota && Number(quota.remaining) <= 0;
    const dailyTokenQuotaExhausted = authenticated && chatEnabled && quota
        && Number(quota.dailyOutputTokensRemaining) <= 0;
    const quotaExhausted = weeklyQuotaExhausted || dailyTokenQuotaExhausted;
    const canChat = authenticated && chatEnabled && !quotaExhausted;
    elements.questionForm.hidden = !canChat;
    elements.chatAuthGate.hidden = canChat;
    elements.chatLoginLink.hidden = authenticated || !chatEnabled;
    elements.chatGateBadge.hidden = chatEnabled;
    elements.chatQuota.hidden = !authenticated || !chatEnabled || !quota;
    renderChatQuota(quota);
    elements.chatGateTitle.textContent = dailyTokenQuotaExhausted
        ? t('analysis.chatDailyTokenLimitTitle')
        : (weeklyQuotaExhausted
            ? t('analysis.chatQuotaUsedTitle')
            : t(chatEnabled ? 'analysis.signInToChat' : 'analysis.chatComingSoonTitle'));
    elements.chatGateText.hidden = !chatEnabled;
    elements.chatGateText.textContent = dailyTokenQuotaExhausted
        ? t('analysis.chatDailyTokenLimit')
        : (weeklyQuotaExhausted
            ? t('analysis.chatQuotaUsedText', { limit: quota.weeklyLimit })
            : (chatEnabled ? t('analysis.chatPrivacy') : ''));
    elements.chatLoginLink.href = window.FhemniAuth.loginPage(window.location.pathname);
}

function renderChatQuota(quota) {
    if (!quota) return;
    const dailyLimit = Math.max(0, Number(quota.dailyOutputTokenLimit) || 0);
    const dailyUsed = Math.max(0, Number(quota.dailyOutputTokensUsed) || 0);
    const tokenPercent = dailyLimit > 0
        ? Math.min(100, Math.round((dailyUsed / dailyLimit) * 100))
        : 0;
    const questionQuotaLabel = t('analysis.chatQuota', {
        remaining: quota.remaining,
        limit: quota.weeklyLimit
    });
    elements.chatQuestionQuota.textContent = `${quota.remaining}/${quota.weeklyLimit}`;
    elements.chatQuestionQuota.setAttribute('aria-label', questionQuotaLabel);
    elements.chatQuestionQuota.title = questionQuotaLabel;
    elements.chatTokenPercent.textContent = `${tokenPercent}%`;
    elements.chatTokenRing.setAttribute('stroke-dashoffset', String(100 - tokenPercent));
    elements.chatTokenProgress.setAttribute('aria-valuemax', String(dailyLimit));
    elements.chatTokenProgress.setAttribute('aria-valuenow', String(Math.min(dailyUsed, dailyLimit)));
    elements.chatTokenProgress.setAttribute('aria-label', t('analysis.chatTokenProgress', {
        percent: tokenPercent
    }));
}

async function loadAnalysis() {
    const match = window.location.pathname.match(/^\/analyses\/([0-9a-f-]{36})\/?$/i);
    if (!match) {
        showError(t('analysis.invalidLink'), t('analysis.invalidLinkHelp'));
        return;
    }

    state.analysisId = match[1];
    showOnly('progress');
    try {
        const snapshot = await request(`/api/analyses/${state.analysisId}`);
        if (snapshot.status === 'COMPLETED') {
            renderResult(snapshot);
        } else if (snapshot.status === 'FAILED') {
            showError(t('analysis.failedTitle'), snapshot.error || t('analysis.failedFallback'));
        } else {
            updateProgress({
                status: snapshot.status,
                progress: snapshot.progress,
                message: snapshot.progressMessage
            });
            subscribe(state.analysisId);
        }
    } catch (error) {
        showRequestError(error);
    }
}

function subscribe(id) {
    closeEventSource();
    const source = new EventSource(`/api/analyses/${id}/events`);
    state.eventSource = source;
    source.addEventListener('progress', async event => {
        const progress = JSON.parse(event.data);
        updateProgress(progress);
        if (progress.status === 'COMPLETED') {
            closeEventSource();
            await loadResult(id);
        } else if (progress.status === 'FAILED') {
            closeEventSource();
            showError(t('analysis.failedTitle'), progress.message || t('analysis.failedFallback'));
        }
    });
    source.onerror = async () => {
        closeEventSource();
        try {
            const snapshot = await request(`/api/analyses/${id}`);
            if (snapshot.status === 'COMPLETED') {
                renderResult(snapshot);
            } else if (snapshot.status === 'FAILED') {
                showError(t('analysis.failedTitle'), snapshot.error || t('analysis.failedFallback'));
            } else {
                window.setTimeout(() => subscribe(id), 1500);
            }
        } catch (error) {
            showRequestError(error);
        }
    };
}

async function loadResult(id) {
    try {
        renderResult(await request(`/api/analyses/${id}`));
    } catch (error) {
        showRequestError(error);
    }
}

function updateProgress(progress) {
    state.latestProgress = progress;
    const value = Math.max(0, Math.min(100, progress.progress || 0));
    const status = progress.status || 'ANALYZING';
    const progressKey = `analysis.progress.${status}`;
    const stageKey = `analysis.stage.${status}`;
    const localizedProgress = t(progressKey);
    const localizedStage = t(stageKey);
    elements.progressMessage.textContent = localizedProgress === progressKey
        ? (progress.message || t('analysis.progress.ANALYZING'))
        : localizedProgress;
    elements.progressStage.textContent = localizedStage === stageKey
        ? status.replaceAll('_', ' ').toLowerCase()
        : localizedStage;
    elements.progressPercent.textContent = `${value}%`;
    elements.progressBar.value = value;
}

function renderResult(snapshot) {
    state.snapshot = snapshot;
    renderModeBadge();
    const report = snapshot.report;
    if (!report) {
        showError(t('analysis.failedTitle'), snapshot.error || t('analysis.emptyReport'));
        return;
    }

    elements.reportTitle.textContent = report.title;
    elements.reportSummary.textContent = report.summary;
    elements.detailedSummary.textContent = report.detailedSummary;
    elements.claimCount.textContent = report.claims.length;
    initializeVideoPlayer(snapshot.videoId);
    elements.demoNotice.hidden = !snapshot.demo;
    configurePublication();

    const rtl = snapshot.language === 'DARIJA';
    const reportLocale = { DARIJA: 'ary', FRENCH: 'fr', ENGLISH: 'en' }[snapshot.language] || 'en';
    [elements.reportTitle, elements.reportSummary, elements.detailedSummary, elements.participants,
        elements.chapters, elements.claimsList, elements.conversation, elements.suggestedQuestions]
        .forEach(element => {
            element.dir = rtl ? 'rtl' : 'ltr';
            element.lang = reportLocale;
        });

    elements.participants.innerHTML = report.participants.map(person => `
        <div class="participant rounded-box border border-base-300 bg-base-100/70 px-3 py-2 text-xs">
            <strong class="block text-sm">${escapeHtml(person.name)}</strong>
            <span class="text-base-content/60">${escapeHtml(person.role)}</span>
        </div>`).join('');

    elements.chapters.innerHTML = report.chapters.map(chapter => `
        <div class="chapter grid grid-cols-[3.5rem_1fr] gap-3 border-b border-base-300 py-3 last:border-b-0">
            <button class="timestamp-button btn btn-primary btn-soft btn-xs h-auto min-h-7 self-start font-mono font-extrabold" type="button" data-seconds="${safeSeconds(chapter.startSeconds)}">${formatTime(chapter.startSeconds)}</button>
            <div><strong class="block text-sm">${escapeHtml(chapter.title)}</strong><p class="mb-0 mt-1 text-xs text-base-content/60">${escapeHtml(chapter.summary)}</p></div>
        </div>`).join('');

    elements.claimsList.innerHTML = report.claims.map(renderClaim).join('');
    elements.suggestedQuestions.innerHTML = report.suggestedQuestions.map(question => `
        <button class="suggested-question btn btn-ghost h-auto min-h-10 justify-start whitespace-normal border border-base-300 bg-base-100/50 px-3 py-2 text-start text-xs font-normal hover:border-primary hover:bg-primary/10" type="button">${escapeHtml(question)}</button>`).join('');
    elements.suggestedQuestions.querySelectorAll('button').forEach((button, index) => {
        button.addEventListener('click', () => {
            activateTab('ask');
            if (!state.authSession?.authenticated || !state.meta?.chatEnabled) {
                elements.chatAuthGate.scrollIntoView({ behavior: 'smooth', block: 'center' });
                return;
            }
            elements.questionInput.value = report.suggestedQuestions[index];
            elements.questionInput.focus();
        });
    });

    renderConversation(snapshot.conversation || []);
    configureChatAccess();
    filterClaims('ALL');
    activateTab('overview');
    showOnly('results');
    document.title = `${report.title} — Fhemni`;
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function configurePublication() {
    const administrator = state.authSession?.authenticated
        && state.authSession?.user?.role === 'ADMIN';
    const completed = state.snapshot?.status === 'COMPLETED';
    elements.publicationPanel.hidden = !(administrator && completed);
    if (!administrator || !completed || state.publication) {
        renderPublication();
        return;
    }

    elements.publishButton.disabled = true;
    elements.publicationTitle.textContent = t('analysis.publicationLoading');
    elements.publicationText.textContent = '';
    try {
        state.publication = await request(`/api/admin/analyses/${state.analysisId}/publication`);
        renderPublication();
    } catch (error) {
        elements.publicationTitle.textContent = t('analysis.publicationUnavailable');
        elements.publicationText.textContent = error.message;
        elements.publishButton.hidden = true;
    }
}

function renderPublication() {
    if (elements.publicationPanel.hidden || !state.publication) return;
    const publication = state.publication;
    elements.publishButton.hidden = publication.published || !publication.catalogued;
    elements.publishButton.disabled = false;
    elements.publicEpisodeLink.hidden = !publication.published;

    if (publication.published) {
        elements.publicationTitle.textContent = t('analysis.publishedTitle');
        elements.publicationText.textContent = t('analysis.publishedText');
        elements.publicEpisodeLink.href = `/videos/${encodeURIComponent(publication.catalogSlug)}`;
    } else if (!publication.catalogued) {
        elements.publicationTitle.textContent = t('analysis.notCataloguedTitle');
        elements.publicationText.textContent = t('analysis.notCataloguedText');
    } else {
        elements.publicationTitle.textContent = t('analysis.readyToPublishTitle');
        elements.publicationText.textContent = t('analysis.readyToPublishText');
    }
}

async function publishAnalysis() {
    if (!state.publication) {
        await configurePublication();
    }
    if (!state.publication) {
        elements.publicationTitle.textContent = t('analysis.publicationUnavailable');
        return;
    }
    if (!state.publication.catalogued) {
        renderPublication();
        return;
    }
    if (state.publication.published) {
        renderPublication();
        return;
    }

    elements.publishButton.disabled = true;
    elements.publishButton.textContent = t('analysis.publishing');
    elements.publicationPanel.setAttribute('aria-busy', 'true');
    elements.publicationTitle.textContent = t('analysis.publishing');
    elements.publicationText.textContent = '';
    try {
        state.publication = await request(`/api/admin/analyses/${state.analysisId}/publish`, {
            method: 'POST'
        });
        if (state.snapshot) {
            state.snapshot.published = true;
            state.snapshot.catalogSlug = state.publication.catalogSlug;
        }
        renderPublication();
    } catch (error) {
        elements.publicationTitle.textContent = t('analysis.publishFailed');
        elements.publicationText.textContent = error.message;
        elements.publishButton.disabled = false;
    } finally {
        elements.publicationPanel.removeAttribute('aria-busy');
        elements.publishButton.textContent = t('analysis.publishButton');
    }
}

function renderClaim(claim) {
    const verdictClass = {
        SUPPORTED: 'badge-success',
        CONTRADICTED: 'badge-error',
        NEEDS_CONTEXT: 'badge-warning',
        UNVERIFIABLE: 'badge-neutral bg-unverifiable text-fhemni-dark'
    }[claim.verdict] || 'badge-neutral bg-unverifiable text-fhemni-dark';
    const sources = (claim.sources || []).map(sourceLink).join('');
    return `
        <article class="claim-card card border border-base-300 bg-base-100/90 p-5 shadow-md" data-kind="${escapeHtml(claim.kind)}">
            <div class="claim-meta flex items-center justify-between gap-3">
                <div class="claim-badges flex flex-wrap gap-2">
                    <span class="badge badge-ghost badge-sm font-black uppercase tracking-wide">${escapeHtml(labelFor(claim.kind))}</span>
                    ${claim.kind === 'FACT' ? `<span class="badge badge-sm font-black uppercase tracking-wide ${verdictClass}">${escapeHtml(labelFor(claim.verdict))}</span>` : ''}
                </div>
                <button class="timestamp-button btn btn-primary btn-soft btn-xs h-auto min-h-7 shrink-0 font-mono font-extrabold" type="button" data-seconds="${safeSeconds(claim.startSeconds)}">${formatTime(claim.startSeconds)}</button>
            </div>
            <p class="claim-statement mb-2 mt-4 font-serif text-lg font-medium leading-snug rtl:font-arabic">${escapeHtml(claim.statement)}</p>
            <span class="claim-speaker text-xs text-base-content/60">${escapeHtml(claim.speaker || t('analysis.speakerUnknown'))}</span>
            ${claim.explanation ? `<p class="claim-explanation mb-0 mt-4 border-t border-base-300 pt-4 text-sm text-base-content/70">${escapeHtml(claim.explanation)}</p>` : ''}
            ${sources ? `<div class="sources mt-3 flex flex-wrap gap-2">${sources}</div>` : ''}
        </article>`;
}

async function askQuestion(event) {
    event.preventDefault();
    if (!state.authSession?.authenticated) {
        window.location.assign(window.FhemniAuth.loginPage(window.location.pathname));
        return;
    }
    if (!state.meta?.chatEnabled) {
        configureChatAccess();
        return;
    }
    const question = elements.questionInput.value.trim();
    if (!question || !state.analysisId) return;

    appendUserMessage(question);
    elements.questionInput.value = '';
    elements.askButton.disabled = true;
    const thinking = appendThinkingMessage();

    try {
        const answer = await request(`/api/analyses/${state.analysisId}/questions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question, mode: state.questionMode })
        }, QUESTION_REQUEST_TIMEOUT_MS);
        thinking.remove();
        appendAssistantMessage(answer);
    } catch (error) {
        thinking.remove();
        appendAssistantMessage({ answer: chatErrorMessage(error), sources: [] }, true);
    } finally {
        await refreshChatQuota();
        elements.askButton.disabled = false;
        if (!elements.questionForm.hidden) elements.questionInput.focus();
    }
}

async function refreshChatQuota() {
    try {
        state.authSession = await window.FhemniAuth.refreshSession();
    } catch (_) { /* retain the last known quota */ }
    configureChatAccess();
}

function chatErrorMessage(error) {
    if (error.code === 'CHAT_WEEKLY_LIMIT') {
        return t('analysis.chatQuotaUsedText', {
            limit: state.authSession?.chatQuota?.weeklyLimit || 5
        });
    }
    if (error.code === 'CHAT_HOURLY_LIMIT') return t('analysis.chatHourlyLimit');
    if (error.code === 'CHAT_DAILY_LIMIT') return t('analysis.chatDailyLimit');
    if (error.code === 'CHAT_DAILY_TOKEN_LIMIT') return t('analysis.chatDailyTokenLimit');
    return error.message;
}

function renderConversation(conversation) {
    elements.conversation.innerHTML = `
        <div class="assistant-message welcome-message chat chat-start rtl:chat-end">
            <div class="chat-image avatar"><div class="size-8 overflow-hidden rounded-xl shadow-md"><img class="size-full object-contain" src="/assets/brand/fhemni-icon.png" alt=""></div></div>
            <div class="chat-bubble max-w-[82%] bg-primary/10 text-sm text-base-content"><p class="m-0 whitespace-pre-wrap">${escapeHtml(t('analysis.welcome'))}</p></div>
        </div>`;
    conversation.forEach(turn => {
        appendUserMessage(turn.question);
        appendAssistantMessage(turn);
    });
}

function appendUserMessage(text) {
    const element = document.createElement('div');
    element.className = 'user-message chat chat-end rtl:chat-start';
    element.innerHTML = `<div class="chat-bubble max-w-[78%] bg-neutral text-sm text-neutral-content"><p class="m-0 whitespace-pre-wrap">${escapeHtml(text)}</p></div>`;
    elements.conversation.append(element);
    scrollConversation();
}

function appendAssistantMessage(answer, error = false) {
    const element = document.createElement('div');
    element.className = 'assistant-message chat chat-start rtl:chat-end';
    const sources = (answer.sources || []).map(sourceLink).join('');
    element.innerHTML = `
        <div class="chat-image avatar"><div class="size-8 overflow-hidden rounded-xl shadow-md"><img class="size-full object-contain" src="/assets/brand/fhemni-icon.png" alt=""></div></div>
        <div class="chat-bubble max-w-[82%] text-sm text-base-content ${error ? 'message-error chat-bubble-error' : 'bg-primary/10'}">
            <p class="m-0 whitespace-pre-wrap">${renderTimestamps(answer.answer || '')}</p>
            ${sources ? `<div class="message-sources mt-3 border-t border-base-300 pt-2">${sources}</div>` : ''}
        </div>`;
    elements.conversation.append(element);
    scrollConversation();
}

function appendThinkingMessage() {
    const element = document.createElement('div');
    element.className = 'assistant-message thinking-message chat chat-start rtl:chat-end';
    element.innerHTML = `<div class="chat-image avatar"><div class="size-8 overflow-hidden rounded-xl shadow-md"><img class="size-full object-contain" src="/assets/brand/fhemni-icon.png" alt=""></div></div><div class="chat-bubble max-w-[82%] bg-primary/10 text-sm text-base-content"><p class="m-0 animate-pulse">${escapeHtml(t('analysis.thinking'))}</p></div>`;
    elements.conversation.append(element);
    scrollConversation();
    return element;
}

function selectQuestionMode(mode) {
    state.questionMode = mode;
    document.querySelectorAll('.mode-option').forEach(button => {
        const selected = button.dataset.mode === mode;
        button.classList.toggle('active', selected);
        button.classList.toggle('tab-active', selected);
        button.setAttribute('aria-selected', String(selected));
    });
    if (mode === 'VIDEO') {
        elements.modeExplanationTitle.textContent = t('analysis.videoAnswerTitle');
        elements.modeExplanation.textContent = t('analysis.videoAnswerText');
    } else {
        elements.modeExplanationTitle.textContent = t('analysis.evidenceAnswerTitle');
        elements.modeExplanation.textContent = t('analysis.evidenceAnswerText');
    }
}

function activateTab(name) {
    document.querySelectorAll('.tab[data-tab]').forEach(button => {
        const selected = button.dataset.tab === name;
        button.classList.toggle('active', selected);
        button.classList.toggle('tab-active', selected);
        button.setAttribute('aria-selected', String(selected));
    });
    document.querySelectorAll('.tab-panel').forEach(panel => {
        const selected = panel.id === `${name}Tab`;
        panel.classList.toggle('active', selected);
        panel.classList.toggle('hidden', !selected);
    });
}

function filterClaims(filter) {
    document.querySelectorAll('.filter').forEach(button => {
        const selected = button.dataset.filter === filter;
        button.classList.toggle('active', selected);
        button.classList.toggle('btn-primary', selected);
        button.classList.toggle('btn-ghost', !selected);
    });
    document.querySelectorAll('.claim-card').forEach(card => {
        card.hidden = filter !== 'ALL' && card.dataset.kind !== filter;
    });
}

function seekVideo(seconds) {
    if (!state.snapshot) return;
    const targetSeconds = safeSeconds(seconds);
    const playerElement = state.youtubePlayer?.getIframe?.() || elements.videoPlayer;
    playerElement.scrollIntoView({ behavior: 'smooth', block: 'center' });

    if (state.youtubePlayerReady) {
        state.youtubePlayer.seekTo(targetSeconds, true);
        state.youtubePlayer.playVideo();
        return;
    }

    state.pendingSeekSeconds = targetSeconds;
    if (state.youtubeApiFailed) {
        elements.videoPlayer.src = youtubeEmbedUrl(state.snapshot.videoId, targetSeconds);
    }
}

function initializeVideoPlayer(videoId) {
    state.youtubePlayerReady = false;
    state.youtubeApiFailed = false;
    state.pendingSeekSeconds = null;
    elements.videoPlayer.src = youtubeEmbedUrl(videoId);

    loadYouTubeIframeApi()
        .then(() => {
            if (!elements.videoPlayer.isConnected || state.youtubePlayer) return;
            state.youtubePlayer = new window.YT.Player(elements.videoPlayer, {
                events: {
                    onReady: event => {
                        state.youtubePlayer = event.target;
                        state.youtubePlayerReady = true;
                        flushPendingSeek();
                    }
                }
            });
        })
        .catch(() => {
            state.youtubeApiFailed = true;
            if (state.pendingSeekSeconds !== null) {
                elements.videoPlayer.src = youtubeEmbedUrl(videoId, state.pendingSeekSeconds);
                state.pendingSeekSeconds = null;
            }
        });
}

function loadYouTubeIframeApi() {
    if (window.YT?.Player) return Promise.resolve(window.YT);
    if (state.youtubeApiPromise) return state.youtubeApiPromise;

    state.youtubeApiPromise = new Promise((resolve, reject) => {
        const previousReadyCallback = window.onYouTubeIframeAPIReady;
        window.onYouTubeIframeAPIReady = () => {
            try {
                previousReadyCallback?.();
            } finally {
                resolve(window.YT);
            }
        };

        let script = document.querySelector('script[data-youtube-iframe-api]');
        if (!script) {
            script = document.createElement('script');
            script.src = 'https://www.youtube.com/iframe_api';
            script.async = true;
            script.dataset.youtubeIframeApi = 'true';
            document.head.append(script);
        }
        script.addEventListener('error', () => reject(new Error(t('analysis.playerUnavailable'))), { once: true });
    });

    return state.youtubeApiPromise;
}

function flushPendingSeek() {
    if (state.pendingSeekSeconds === null || !state.youtubePlayerReady) return;
    const targetSeconds = state.pendingSeekSeconds;
    state.pendingSeekSeconds = null;
    state.youtubePlayer.seekTo(targetSeconds, true);
    state.youtubePlayer.playVideo();
}

function youtubeEmbedUrl(videoId, startSeconds = null) {
    const parameters = new URLSearchParams({
        enablejsapi: '1',
        rel: '0',
        playsinline: '1',
        origin: window.location.origin
    });
    if (startSeconds !== null) {
        parameters.set('start', String(safeSeconds(startSeconds)));
        parameters.set('autoplay', '1');
    }
    return `https://www.youtube.com/embed/${encodeURIComponent(videoId)}?${parameters}`;
}

function destroyVideoPlayer() {
    if (!state.youtubePlayer) return;
    try {
        state.youtubePlayer.destroy();
    } catch (_) { /* the page is already unloading */ }
    state.youtubePlayer = null;
    state.youtubePlayerReady = false;
}

function showOnly(view) {
    elements.progressPanel.hidden = view !== 'progress';
    elements.errorPanel.hidden = view !== 'error';
    elements.results.hidden = view !== 'results';
}

function showRequestError(error) {
    if (error.status === 404) {
        showError(t('analysis.expired'), t('analysis.expiredHelp'));
    } else {
        showError(t('analysis.loadFailed'), error.message);
    }
}

function showError(title, message) {
    closeEventSource();
    elements.errorTitle.textContent = title;
    elements.errorMessage.textContent = message || t('analysis.tryAgain');
    showOnly('error');
    document.title = `${title} — Fhemni`;
}

function closeEventSource() {
    if (state.eventSource) {
        state.eventSource.close();
        state.eventSource = null;
    }
}

async function request(url, options = {}, timeoutMs = DEFAULT_REQUEST_TIMEOUT_MS) {
    const controller = new AbortController();
    let timedOut = false;
    const timeout = window.setTimeout(() => {
        timedOut = true;
        controller.abort();
    }, timeoutMs);
    state.activeRequests.add(controller);

    try {
        const securedOptions = await window.FhemniAuth.withCsrf(options);
        const response = await fetch(url, { ...securedOptions, signal: controller.signal });
        if (!response.ok) {
            let message = t('common.requestFailed', { status: response.status });
            let errorCode = null;
            try {
                const problem = await response.json();
                message = problem.detail || problem.message || message;
                errorCode = problem.code || null;
            } catch (_) { /* keep the HTTP message */ }
            const error = new Error(message);
            error.status = response.status;
            error.code = errorCode;
            throw error;
        }
        return await response.json();
    } catch (error) {
        if (error.name === 'AbortError') {
            throw new Error(timedOut
                ? t('common.requestTimedOut', { seconds: Math.round(timeoutMs / 1000) })
                : t('common.requestCancelled'));
        }
        throw error;
    } finally {
        window.clearTimeout(timeout);
        state.activeRequests.delete(controller);
    }
}

function cancelActiveRequests() {
    state.activeRequests.forEach(controller => controller.abort());
    state.activeRequests.clear();
}

function sourceLink(source) {
    const url = safeUrl(source.url);
    if (!url) return '';
    const date = source.publishedDate ? ` · ${escapeHtml(source.publishedDate)}` : '';
    return `<a class="source-link badge badge-soft badge-primary h-auto max-w-full justify-start overflow-hidden text-ellipsis whitespace-nowrap px-2 py-1 text-xs font-bold no-underline" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(source.title || t('analysis.source'))}${date}</a>`;
}

function safeUrl(value) {
    try {
        const url = new URL(value);
        return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
    } catch (_) {
        return '';
    }
}

function renderTimestamps(value) {
    return escapeHtml(value).replace(/\[(\d{1,2}):([0-5]\d)(?::([0-5]\d))?\]/g, (match, first, second, third) => {
        const seconds = third === undefined
            ? Number(first) * 60 + Number(second)
            : Number(first) * 3600 + Number(second) * 60 + Number(third);
        return `<button class="timestamp-button btn btn-primary btn-soft btn-xs h-auto min-h-7 font-mono font-extrabold" type="button" data-seconds="${seconds}">${match}</button>`;
    });
}

function formatTime(value) {
    const total = safeSeconds(value);
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;
    if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function safeSeconds(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0;
}

function labelFor(value = '') {
    const key = `label.${value}`;
    const translated = t(key);
    return translated === key
        ? value.replaceAll('_', ' ').toLowerCase().replace(/(^|\s)\S/g, letter => letter.toUpperCase())
        : translated;
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function scrollConversation() {
    elements.conversation.scrollTop = elements.conversation.scrollHeight;
}
