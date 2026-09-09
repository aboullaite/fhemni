(function () {
    const QUESTION_TIMEOUT_MS = 45_000;
    let profile;
    let programme;
    let partyCode;
    let authSession;
    let meta;
    let chatStateResolved = false;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function people() {
        return window.FhemniPeople;
    }

    async function load() {
        partyCode = decodeURIComponent(window.location.pathname.split('/').filter(Boolean).at(-1) || '');
        const chatStateRequest = Promise.all([
            window.FhemniAuth.session().catch(() => ({ authenticated: false })),
            window.FhemniCatalog.requestJson('/api/meta').catch(() => null)
        ]);
        try {
            const programmeRequest = window.FhemniCatalog.requestJson(
                `/api/catalog/parties/${encodeURIComponent(partyCode)}/programme`)
                .catch(error => {
                    if (error.status === 404) return null;
                    throw error;
                });
            [profile, programme] = await Promise.all([
                window.FhemniCatalog.requestJson(`/api/catalog/parties/${encodeURIComponent(partyCode)}`),
                programmeRequest
            ]);
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

    function render() {
        const name = people().partyDisplayName(profile);
        const alt = (people().locale() === 'ar' ? profile.nameFr : profile.nameAr) || '';
        document.querySelector('#partyName').textContent = `${profile.code} · ${name}`;
        document.querySelector('#partyNameAlt').textContent = alt;
        const symbol = document.querySelector('#partySymbol');
        symbol.replaceChildren(people().partySymbol(profile, true));
        const hasProgramme = renderProgramme();

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
        setupTabs(hasProgramme);
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
        const weeklyExhausted = authenticated && quota && Number(quota.remaining) <= 0;
        const dailyExhausted = authenticated && quota && Number(quota.dailyOutputTokensRemaining) <= 0;
        const canAsk = enabled && authenticated && !weeklyExhausted && !dailyExhausted;
        form.hidden = !canAsk;
        gate.hidden = canAsk;
        login.hidden = authenticated || !enabled;
        login.href = window.FhemniAuth.loginPage(window.location.pathname, 'chat');

        const title = document.querySelector('#programmeChatGateTitle');
        const text = document.querySelector('#programmeChatGateText');
        if (!enabled) {
            title.textContent = t('programme.chatUnavailableTitle');
            text.textContent = t('programme.chatUnavailableText');
        } else if (dailyExhausted) {
            title.textContent = t('analysis.chatDailyTokenLimitTitle');
            text.textContent = t('analysis.chatDailyTokenLimit');
        } else if (weeklyExhausted) {
            title.textContent = t('analysis.chatQuotaUsedTitle');
            text.textContent = t('analysis.chatQuotaUsedText', { limit: quota.weeklyLimit });
        } else {
            title.textContent = t('programme.chatSignInTitle');
            text.textContent = t('analysis.chatPrivacy');
        }
        quotaLabel.textContent = quota
            ? t('programme.chatQuota', { remaining: quota.remaining, limit: quota.weeklyLimit })
            : '';
        quotaLabel.title = quota ? t('analysis.chatUsageSummary', {
            percent: dailyTokenPercent(quota), remaining: quota.remaining, limit: quota.weeklyLimit
        }) : '';
    }

    async function askProgramme(event) {
        event.preventDefault();
        if (!authSession?.authenticated) {
            window.location.assign(window.FhemniAuth.loginPage(window.location.pathname, 'chat'));
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
                `/api/catalog/parties/${encodeURIComponent(profile.code)}/programme/questions`,
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
        if (error.code === 'CHAT_WEEKLY_LIMIT') {
            return t('analysis.chatQuotaUsedText', { limit: authSession?.chatQuota?.weeklyLimit || 20 });
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
            const url = new URL(value);
            return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
        } catch (_) {
            return '';
        }
    }

    function setupTabs(hasProgramme) {
        const programmeTab = document.querySelector('#partyProgrammeTab');
        const episodesTab = document.querySelector('#partyEpisodesTab');
        programmeTab.onclick = () => selectTab('programme');
        episodesTab.onclick = () => selectTab('episodes');
        selectTab(hasProgramme ? 'programme' : 'episodes');
    }

    function selectTab(name) {
        const programmeSelected = name === 'programme' && programme;
        const programmeTab = document.querySelector('#partyProgrammeTab');
        const episodesTab = document.querySelector('#partyEpisodesTab');
        const programmePanel = document.querySelector('#partyProgramme');
        const episodesPanel = document.querySelector('#partyEpisodesSection');
        programmeTab.setAttribute('aria-selected', String(Boolean(programmeSelected)));
        programmeTab.tabIndex = programmeSelected ? 0 : -1;
        episodesTab.setAttribute('aria-selected', String(!programmeSelected));
        episodesTab.tabIndex = programmeSelected ? -1 : 0;
        programmePanel.hidden = !programmeSelected;
        episodesPanel.hidden = Boolean(programmeSelected);
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
