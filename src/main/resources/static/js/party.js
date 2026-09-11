(function () {
    const QUESTION_TIMEOUT_MS = 45_000;
    let profile;
    let programme;
    let programmeMedia;
    let partyCode;
    let authSession;
    let meta;
    let chatStateResolved = false;
    let tabsBound = false;
    let programmePlayer;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function people() {
        return window.FhemniPeople;
    }

    async function load() {
        partyCode = decodeURIComponent(window.location.pathname.split('/').filter(Boolean).at(-1) || '');
        const sessionRequest = window.FhemniAuth.session().catch(() => ({ authenticated: false }));
        const chatStateRequest = Promise.all([
            sessionRequest,
            window.FhemniCatalog.requestJson('/api/meta').catch(() => null)
        ]);
        try {
            const requestedPartyCode = partyCode;
            const profileRequest = window.FhemniCatalog.requestJson(
                `/api/catalog/parties/${encodeURIComponent(requestedPartyCode)}`);
            const programmeRequest = requestProgramme(requestedPartyCode);
            const mediaRequest = requestProgrammeMedia(requestedPartyCode);
            [profile, programme, programmeMedia, authSession] = await Promise.all([
                profileRequest,
                programmeRequest,
                mediaRequest,
                sessionRequest
            ]);
            const programmePartyCode = profile.programmePartyCode || profile.code;
            if (profile.code !== requestedPartyCode) {
                const location = new URL(window.location.href);
                location.pathname = `/parties/${encodeURIComponent(profile.code)}`;
                window.history.replaceState({}, '', `${location.pathname}${location.search}`);
            }
            partyCode = profile.code;
            if (programmePartyCode.toUpperCase() !== requestedPartyCode.toUpperCase()) {
                [programme, programmeMedia] = await Promise.all([
                    requestProgramme(programmePartyCode),
                    requestProgrammeMedia(programmePartyCode)
                ]);
            }
            render();
            bindChat();
            document.querySelector('#partyLoading').hidden = true;
            document.querySelector('#partyDetail').hidden = false;
            void chatStateRequest.then(([session, metadata]) => {
                authSession = session;
                meta = metadata;
                chatStateResolved = true;
                configureChatAccess();
            });
        } catch (error) {
            document.querySelector('#partyLoading').hidden = true;
            const panel = document.querySelector('#partyError');
            panel.textContent = error.message;
            panel.hidden = false;
        }
    }

    function requestProgramme(code) {
        return window.FhemniCatalog.requestJson(
            `/api/catalog/parties/${encodeURIComponent(code)}/programme?view=policy-topics-v1`)
            .catch(error => {
                if (error.status === 404) return null;
                throw error;
            });
    }

    function requestProgrammeMedia(code) {
        return window.FhemniCatalog.requestJson(
            `/api/catalog/parties/${encodeURIComponent(code)}/programme/media`)
            .catch(error => {
                if (error.status === 404) return null;
                console.warn('Programme media could not be loaded.', error);
                return null;
            });
    }

    function render() {
        const name = people().partyDisplayName(profile);
        const alt = (people().locale() === 'ar' ? profile.nameFr : profile.nameAr) || '';
        const codeLabel = (profile.memberPartyCodes || [profile.code]).join(' + ');
        document.querySelector('#partyName').textContent = name;
        document.querySelector('#partyCodeLabel').textContent = codeLabel;
        document.querySelector('#partyNameAlt').textContent = alt;
        const symbol = document.querySelector('#partySymbol');
        symbol.replaceChildren(people().partySymbol(profile, true));
        const compareUrl = `/parties/compare?party=${encodeURIComponent(profile.code)}`;
        document.querySelector('#partyCompareLink').href = compareUrl;
        document.querySelectorAll('[data-party-compare]').forEach(link => link.href = compareUrl);
        const hasProgramme = renderProgramme();
        renderProgrammeMedia();

        const stats = document.querySelector('#partyStats');
        stats.replaceChildren();
        const members = document.createElement('span');
        members.className = 'person-stat';
        members.textContent = t('parties.members', { count: profile.members });
        const episodes = document.createElement('span');
        episodes.className = 'person-stat';
        episodes.textContent = t('parties.episodes', { count: profile.appearances });
        stats.append(members, episodes);

        document.querySelector('#partyEpisodesCount').textContent =
            t('parties.episodes', { count: profile.episodes.length });
        const episodeGrid = document.querySelector('#partyEpisodes');
        episodeGrid.replaceChildren();
        if (!profile.episodes.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('party.noEpisodes');
            episodeGrid.append(empty);
        } else {
            profile.episodes.forEach(episode => episodeGrid.append(episodeCard(episode)));
        }

        renderChatSuggestions();
        refreshChatTranslations();
        configureChatAccess();
        setupTabs();
        document.title = `${profile.code} — Fhemni`;
    }

    function renderProgramme() {
        const section = document.querySelector('#partyProgramme');
        const tab = document.querySelector('#partyProgrammeTab');
        if (!programme) {
            section.hidden = true;
            tab.hidden = true;
            return false;
        }
        tab.hidden = false;
        document.querySelector('#partyProgrammeSummary').textContent = localized(programme.summary);
        const source = document.querySelector('#partyProgrammeSource');
        source.href = programme.sourceUrl;
        source.title = programme.sourceLabel;
        const grid = document.querySelector('#partyPromises');
        grid.replaceChildren();
        programme.promises.forEach(promise => grid.append(promiseCard(promise)));
        return true;
    }

    function renderProgrammeMedia() {
        const section = document.querySelector('#programmeMedia');
        if (!section || !programmeMedia) {
            if (section) section.hidden = true;
            return;
        }
        section.hidden = false;
        const video = document.querySelector('#programmeMediaVideo');
        const captions = document.querySelector('#programmeMediaCaptions');
        if (video.src !== new URL(programmeMedia.videoUrl, window.location.origin).href) {
            video.src = programmeMedia.videoUrl;
        }
        if (captions && captions.src !== new URL(programmeMedia.captionsUrl, window.location.origin).href) {
            captions.src = programmeMedia.captionsUrl;
        }
        setupProgrammeMediaPlayer();
    }

    function bindChat() {
        const form = document.querySelector('#programmeQuestionForm');
        const input = document.querySelector('#programmeQuestion');
        form.addEventListener('submit', askProgramme);
        input.addEventListener('keydown', event => {
            if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
            event.preventDefault();
            if (!document.querySelector('#programmeAskButton').disabled && input.value.trim()) {
                form.requestSubmit();
            }
        });
    }

    function renderChatSuggestions() {
        const container = document.querySelector('#programmeChatSuggestions');
        container.replaceChildren();
        ['economy', 'funding', 'hardest', 'jobs'].forEach(key => {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = t(`programme.chatSuggestion.${key}`);
            button.addEventListener('click', () => {
                const input = document.querySelector('#programmeQuestion');
                input.value = button.textContent;
                input.focus();
            });
            container.append(button);
        });
    }

    function refreshChatTranslations() {
        const welcome = document.querySelector('#programmeConversation .welcome-message .programme-message-bubble');
        if (welcome) welcome.textContent = t('programme.chatWelcome');
    }

    function configureChatAccess() {
        if (!programme) return;
        const form = document.querySelector('#programmeQuestionForm');
        const gate = document.querySelector('#programmeChatGate');
        const login = document.querySelector('#programmeChatLogin');
        const quotaLabel = document.querySelector('#programmeChatQuota');
        if (!chatStateResolved) {
            form.hidden = true;
            gate.hidden = true;
            quotaLabel.textContent = '';
            return;
        }
        const authenticated = Boolean(authSession?.authenticated);
        const enabled = Boolean(meta?.programmeChatEnabled);
        const quota = authSession?.chatQuota;
        const dailyRequestsExhausted = authenticated && quota
            && Number(quota.dailyRequestsRemaining) <= 0;
        const weeklyExhausted = authenticated && quota && Number(quota.remaining) <= 0;
        const dailyTokensExhausted = authenticated && quota
            && Number(quota.dailyOutputTokensRemaining) <= 0;
        const canAsk = enabled && authenticated
            && !dailyRequestsExhausted && !weeklyExhausted && !dailyTokensExhausted;
        form.hidden = !canAsk;
        gate.hidden = canAsk;
        login.hidden = authenticated || !enabled;
        login.href = window.FhemniAuth.loginPage(`${window.location.pathname}?view=ask`, 'programme');

        const title = document.querySelector('#programmeChatGateTitle');
        const text = document.querySelector('#programmeChatGateText');
        if (!enabled) {
            title.textContent = t('programme.chatUnavailableTitle');
            text.textContent = t('programme.chatUnavailableText');
        } else if (weeklyExhausted) {
            title.textContent = t('analysis.chatQuotaUsedTitle');
            text.textContent = t('analysis.chatQuotaUsedText', { limit: quota.weeklyLimit });
        } else if (dailyRequestsExhausted) {
            title.textContent = t('analysis.chatDailyQuotaUsedTitle');
            text.textContent = t('analysis.chatDailyQuotaUsedText', { limit: quota.dailyRequestLimit });
        } else if (dailyTokensExhausted) {
            title.textContent = t('analysis.chatDailyTokenLimitTitle');
            text.textContent = t('analysis.chatDailyTokenLimit');
        } else {
            title.textContent = t('programme.chatSignInTitle');
            text.textContent = t('analysis.chatPrivacy');
        }
        quotaLabel.textContent = quota
            ? t('programme.chatQuota', {
                remaining: Math.min(
                    Math.max(0, Number(quota.dailyRequestsRemaining) || 0),
                    Math.max(0, Number(quota.remaining) || 0)),
                limit: quota.dailyRequestLimit
            })
            : '';
        quotaLabel.title = quota ? t('analysis.chatUsageSummary', {
            percent: dailyTokenPercent(quota),
            dailyRemaining: quota.dailyRequestsRemaining,
            dailyLimit: quota.dailyRequestLimit,
            weeklyRemaining: quota.remaining,
            weeklyLimit: quota.weeklyLimit
        }) : '';
    }

    async function askProgramme(event) {
        event.preventDefault();
        if (!authSession?.authenticated) {
            window.location.assign(window.FhemniAuth.loginPage(`${window.location.pathname}?view=ask`, 'chat'));
            return;
        }
        const input = document.querySelector('#programmeQuestion');
        const button = document.querySelector('#programmeAskButton');
        const question = input.value.trim();
        if (!question || !programme) return;
        appendUserMessage(question);
        input.value = '';
        button.disabled = true;
        const thinking = appendThinkingMessage();
        window.FhemniAnalytics?.trackEvent('programme_chat_started', { party_code: profile.code });
        try {
            const answer = await request(
                `/api/catalog/parties/${encodeURIComponent(programme.partyCode)}/programme/questions`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ question, language: people().locale() })
                });
            thinking.remove();
            appendAssistantMessage(answer);
            window.FhemniAnalytics?.trackEvent('programme_chat_answered', {
                party_code: profile.code,
                answer_basis: answer.basis
            });
        } catch (error) {
            thinking.remove();
            appendAssistantMessage({ answer: chatErrorMessage(error), sources: [] }, true);
            window.FhemniAnalytics?.trackEvent('programme_chat_failed', {
                party_code: profile.code,
                error_code: error.code || 'request_failed'
            });
        } finally {
            try {
                authSession = await window.FhemniAuth.refreshSession();
            } catch (_) { /* Retain the last known session. */ }
            configureChatAccess();
            button.disabled = false;
            if (!document.querySelector('#programmeQuestionForm').hidden) input.focus();
        }
    }

    function appendUserMessage(text) {
        const row = document.createElement('div');
        row.className = 'programme-message programme-message-user';
        const bubble = document.createElement('div');
        bubble.className = 'programme-message-bubble';
        bubble.textContent = text;
        row.append(bubble);
        appendConversation(row);
    }

    function appendAssistantMessage(answer, error = false) {
        const row = document.createElement('div');
        row.className = `programme-message assistant-message${error ? ' programme-message-error' : ''}`;
        const avatar = document.createElement('img');
        avatar.src = '/assets/brand/fhemni-icon.png';
        avatar.alt = '';
        const bubble = document.createElement('div');
        bubble.className = 'programme-message-bubble';
        if (!error && answer.basis) {
            const basis = document.createElement('span');
            basis.className = `programme-answer-basis ${String(answer.basis).toLowerCase()}`;
            basis.textContent = t(`programme.chatBasis.${answer.basis}`);
            bubble.append(basis);
        }
        const body = document.createElement('div');
        body.className = 'chat-markdown';
        body.innerHTML = window.FhemniMarkdown.render(answer.answer || '');
        bubble.append(body);
        const sources = sourceLinks(answer.sources || []);
        if (sources.childElementCount) bubble.append(sources);
        row.append(avatar, bubble);
        appendConversation(row);
    }

    function appendThinkingMessage() {
        const row = document.createElement('div');
        row.className = 'programme-message assistant-message programme-message-thinking';
        const avatar = document.createElement('img');
        avatar.src = '/assets/brand/fhemni-icon.png';
        avatar.alt = '';
        const bubble = document.createElement('div');
        bubble.className = 'programme-message-bubble';
        bubble.textContent = t('programme.chatThinking');
        row.append(avatar, bubble);
        appendConversation(row);
        return row;
    }

    function appendConversation(row) {
        const conversation = document.querySelector('#programmeConversation');
        conversation.append(row);
        conversation.scrollTop = conversation.scrollHeight;
    }

    function sourceLinks(items) {
        const container = document.createElement('div');
        container.className = 'programme-message-sources';
        items.forEach(source => {
            const url = safeUrl(source.url);
            if (!url) return;
            const link = document.createElement('a');
            link.href = url;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            link.textContent = `${source.title || t('analysis.source')}${source.publishedDate ? ` · ${source.publishedDate}` : ''}`;
            container.append(link);
        });
        return container;
    }

    function chatErrorMessage(error) {
        if (error.code === 'CHAT_USER_DAILY_LIMIT') {
            return t('analysis.chatDailyQuotaUsedText', {
                limit: authSession?.chatQuota?.dailyRequestLimit || 20
            });
        }
        if (error.code === 'CHAT_WEEKLY_LIMIT') {
            return t('analysis.chatQuotaUsedText', { limit: authSession?.chatQuota?.weeklyLimit || 100 });
        }
        if (error.code === 'CHAT_HOURLY_LIMIT') return t('analysis.chatHourlyLimit');
        if (error.code === 'CHAT_DAILY_LIMIT') return t('analysis.chatDailyLimit');
        if (error.code === 'CHAT_DAILY_TOKEN_LIMIT') return t('analysis.chatDailyTokenLimit');
        return error.message;
    }

    async function request(url, options) {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), QUESTION_TIMEOUT_MS);
        try {
            const secured = await window.FhemniAuth.withCsrf(options);
            const response = await fetch(url, { ...secured, signal: controller.signal });
            if (!response.ok) {
                let message = t('common.requestFailed', { status: response.status });
                let code = null;
                try {
                    const problem = await response.json();
                    message = problem.detail || message;
                    code = problem.code || null;
                } catch (_) { /* Keep the HTTP message. */ }
                const error = new Error(message);
                error.code = code;
                throw error;
            }
            return await response.json();
        } catch (error) {
            if (error.name === 'AbortError') {
                throw new Error(t('common.requestTimedOut', { seconds: QUESTION_TIMEOUT_MS / 1000 }));
            }
            throw error;
        } finally {
            window.clearTimeout(timeout);
        }
    }

    function dailyTokenPercent(quota) {
        const limit = Math.max(0, Number(quota.dailyOutputTokenLimit) || 0);
        const used = Math.max(0, Number(quota.dailyOutputTokensUsed) || 0);
        return limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 0;
    }

    function safeUrl(value) {
        try {
            const url = new URL(value, window.location.origin);
            return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
        } catch (_) {
            return '';
        }
    }

    function programmeViews() {
        return [
            ['programme', document.querySelector('#partyProgrammeTab'), document.querySelector('#partyProgramme'), Boolean(programme)],
            ['ask', document.querySelector('#partyChatTab'), document.querySelector('#programmeChat'), Boolean(programme)],
            ['episodes', document.querySelector('#partyEpisodesTab'), document.querySelector('#partyEpisodesSection'), true]
        ];
    }

    function setupTabs() {
        const views = programmeViews();
        views.forEach(([, tab, , available]) => { tab.hidden = !available; });
        if (!tabsBound) {
            views.forEach(([name, tab]) => {
                tab.addEventListener('click', () => selectTab(name, true));
            });
            document.querySelector('.party-tabs').addEventListener('keydown', event => {
                if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
                const visibleTabs = programmeViews().filter(([, tab]) => !tab.hidden);
                const current = visibleTabs.findIndex(([, tab]) => tab === event.target);
                if (current < 0) return;
                event.preventDefault();
                const rtl = document.documentElement.dir === 'rtl';
                const direction = event.key === 'ArrowRight' ? (rtl ? -1 : 1) : (rtl ? 1 : -1);
                const next = event.key === 'Home'
                    ? 0
                    : event.key === 'End'
                        ? visibleTabs.length - 1
                        : (current + direction + visibleTabs.length) % visibleTabs.length;
                visibleTabs[next][1].focus();
                selectTab(visibleTabs[next][0], true);
            });
            document.querySelectorAll('[data-party-view]').forEach(button => {
                button.addEventListener('click', () => selectTab(button.dataset.partyView, true));
            });
            window.addEventListener('popstate', () => selectTab(viewFromUrl(), false));
            tabsBound = true;
        }
        selectTab(viewFromUrl(), false);
    }

    function viewFromUrl() {
        const requested = new URLSearchParams(window.location.search).get('view');
        const available = programmeViews().filter(([, , , enabled]) => enabled).map(([name]) => name);
        if (requested === 'briefing' && available.includes('programme')) return 'programme';
        if (available.includes(requested)) return requested;
        return available.includes('programme') ? 'programme' : 'episodes';
    }

    function selectTab(name, updateHistory) {
        const views = programmeViews();
        const selected = views.some(([view, , , available]) => view === name && available)
            ? name
            : viewFromUrl();
        views.forEach(([view, tab, panel]) => {
            const active = selected === view;
            tab.setAttribute('aria-selected', String(active));
            tab.tabIndex = active ? 0 : -1;
            panel.hidden = !active;
        });
        if (updateHistory) {
            const location = new URL(window.location.href);
            location.searchParams.set('view', selected);
            window.history.pushState({}, '', `${location.pathname}${location.search}${location.hash}`);
        }
        if (selected === 'ask') {
            window.requestAnimationFrame(() => document.querySelector('#programmeQuestion')?.focus());
        }
    }

    function setupProgrammeMediaPlayer() {
        if (programmePlayer || typeof window.videojs !== 'function') return;
        const video = document.querySelector('#programmeMediaVideo');
        if (!video) return;
        const language = () => ['ar', 'fr'].includes(document.documentElement.lang)
            ? document.documentElement.lang
            : 'en';
        programmePlayer = window.videojs(video, {
            aspectRatio: '4:5',
            fluid: true,
            language: language(),
            playbackRates: [0.75, 1, 1.25, 1.5, 2],
            responsive: true,
            controlBar: {
                children: [
                    'playToggle',
                    'volumePanel',
                    'currentTimeDisplay',
                    'progressControl',
                    'remainingTimeDisplay',
                    'playbackRateMenuButton',
                    'pictureInPictureToggle',
                    'fullscreenToggle'
                ]
            },
            userActions: { hotkeys: true }
        });
        document.addEventListener('fhemni:localechange', () => {
            programmePlayer.language(language());
        });
    }

    function episodeCard(episode) {
        const article = document.createElement('article');
        article.className = 'catalog-card';
        const imageLink = document.createElement('a');
        imageLink.className = 'catalog-thumbnail';
        imageLink.href = `/videos/${encodeURIComponent(episode.slug)}`;
        const image = document.createElement('img');
        image.src = window.FhemniCatalog.safeImage(episode.thumbnailUrl, episode.youtubeVideoId);
        image.alt = '';
        image.loading = 'lazy';
        imageLink.append(image);
        const body = document.createElement('div');
        body.className = 'catalog-card-body';
        const title = document.createElement('h3');
        title.dir = 'auto';
        const link = document.createElement('a');
        link.href = imageLink.href;
        link.textContent = episode.title;
        title.append(link);
        const metaLine = document.createElement('p');
        metaLine.className = 'catalog-card-meta';
        metaLine.textContent = window.FhemniCatalog.formatDate(episode.publishedOn);
        const action = document.createElement('a');
        action.className = 'text-link';
        action.href = imageLink.href;
        action.textContent = t('party.viewEpisode');
        body.append(title, metaLine, action);
        article.append(imageLink, body);
        return article;
    }

    function promiseCard(promise) {
        const article = document.createElement('article');
        article.className = 'promise-card';
        const top = document.createElement('div');
        top.className = 'promise-card-topline';
        const topic = document.createElement('span');
        topic.className = 'section-kicker';
        topic.textContent = localizedTopic(promise.topic);
        const verdict = document.createElement('span');
        verdict.className = `feasibility-badge ${String(promise.verdict).toLowerCase().replace('_', '-')}`;
        verdict.textContent = t(`promise.verdict.${promise.verdict}`);
        const verdictDescription = t(`promise.verdictDescription.${promise.verdict}`);
        verdict.title = verdictDescription;
        verdict.setAttribute('aria-label', verdictDescription);
        top.append(topic, verdict);
        const title = document.createElement('h3');
        const link = document.createElement('a');
        link.href = `/promises/${encodeURIComponent(promise.slug)}`;
        link.textContent = localized(promise.title);
        title.append(link);
        const summary = document.createElement('p');
        summary.dir = 'auto';
        summary.textContent = localized(promise.assessmentSummary);
        const more = document.createElement('a');
        more.className = 'text-link';
        more.href = link.href;
        more.textContent = t('programme.readAssessment');
        article.append(top, title, summary, more);
        return article;
    }

    function localizedTopic(rawTopic) {
        const topic = String(rawTopic || '')
            .normalize('NFD')
            .replace(/\p{M}/gu, '')
            .toLowerCase();
        const topics = [
            ['agriculture', /agriculture|rural|فلاح|زراع|قروي/],
            ['transportInfrastructure', /transport|infrastructure|نقل|طرق|بنية تحتية/],
            ['sport', /(^|[^a-z])sport([^a-z]|$)|رياض/],
            ['cultureIdentity', /(^|[^a-z])culture([^a-z]|$)|amazigh|identity|ثقاف|امازيغ|هوي/],
            ['familyYouth', /women|woman|youth|family|femme|jeunesse|famille|مرأة|المراة|نساء|شباب|اسر/],
            ['higherEducation', /higher education|enseignement superieur|research|recherche|universit|تعليم عالي|التعليم العالي|بحث علمي|البحث العلمي|جامع/],
            ['employment', /employment|emploi|labou?r|travail|chomage|salaire|wage|pouvoir d'achat|تشغيل|شغل|عمل|بطال|اجور|الأجور|قدرة شرائية/],
            ['socialProtection', /social protection|protection sociale|securite sociale|pension|retrait|حماية اجتماعية|الحماية الاجتماعية|تقاعد|معاش|دعم اجتماعي/],
            ['health', /health|sante|medical|صحة|الصحة|طب|استشف/],
            ['financeTax', /finance|financement|fiscal|tax|douan|budget|مالي|تمويل|ضريب|جبا|ميزاني|ادخار|قروض/],
            ['economy', /econom|industrie|entrepris|pme|market|concurrence|اقتصاد|صناع|مقاول|استثمار|تنافس/],
            ['education', /education|enseignement|school|تعليم|تربية|مدرس/],
            ['energy', /energy|energie|طاق/],
            ['waterEnvironment', /water|eau|environment|environnement|climat|ماء|مياه|بيئ|مناخ/],
            ['housing', /housing|habitat|logement|سكن/],
            ['digital', /digital|numerique|data|artificial intelligence|رقم|بيانات|ذكاء اصطناعي/],
            ['governance', /governance|justice|transparen|moralisation|rights|election|حكامة|عدال|قضاء|شفاف|انتخاب|حقوق/],
            ['diplomacy', /diplom|foreign|mre|diaspora|جالية|دبلوماس|خارجية/]
        ];
        const match = topics.find(([, pattern]) => pattern.test(topic));
        return t(`programme.topic.${match?.[0] || 'other'}`);
    }

    function localized(value) {
        const locale = people()?.locale() || 'ar';
        return value?.[locale] || value?.ar || value?.fr || value?.en || '';
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (profile) render();
    });
})();
