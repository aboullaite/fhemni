(function () {
    const QUESTION_TIMEOUT_MS = 45_000;
    const PRIORITY_STORAGE_KEY = 'fhemni.policy-topic-preferences';
    const PRIORITY_PENDING_KEY = 'fhemni.policy-topic-preferences-pending';
    let profile;
    let programme;
    let partyCode;
    let authSession;
    let meta;
    let chatStateResolved = false;
    let policyTopicCatalog = [];
    let priorityMaxSelections = 3;
    let selectedPolicyTopics = [];
    let localPrioritySyncPending = false;
    let priorityNoticeKey = '';
    let preferenceSaveQueue = Promise.resolve();

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
            const requestedPartyCode = partyCode;
            const profileRequest = window.FhemniCatalog.requestJson(
                `/api/catalog/parties/${encodeURIComponent(requestedPartyCode)}`);
            const programmeRequest = requestProgramme(requestedPartyCode);
            const policyTopicsRequest = window.FhemniCatalog.requestJson('/api/catalog/policy-topics')
                .catch(() => ({ maxSelections: 3, topics: [] }));
            const topicCatalog = await policyTopicsRequest;
            policyTopicCatalog = topicCatalog.topics || [];
            priorityMaxSelections = Number(topicCatalog.maxSelections) || 3;
            selectedPolicyTopics = readLocalPriorities();
            localPrioritySyncPending = readLocalPriorityPending();
            [profile, programme] = await Promise.all([profileRequest, programmeRequest]);
            const programmePartyCode = profile.programmePartyCode || profile.code;
            if (profile.code !== requestedPartyCode) {
                window.history.replaceState({}, '', `/parties/${encodeURIComponent(profile.code)}`);
            }
            partyCode = profile.code;
            if (programmePartyCode.toUpperCase() !== requestedPartyCode.toUpperCase()) {
                programme = await requestProgramme(programmePartyCode);
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
                void initializePriorities(session);
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

    function render() {
        const name = people().partyDisplayName(profile);
        const alt = (people().locale() === 'ar' ? profile.nameFr : profile.nameAr) || '';
        const codeLabel = (profile.memberPartyCodes || [profile.code]).join(' + ');
        document.querySelector('#partyName').textContent = people().locale() === 'ar'
            ? `${name} · ${codeLabel}`
            : `${codeLabel} · ${name}`;
        document.querySelector('#partyNameAlt').textContent = alt;
        const symbol = document.querySelector('#partySymbol');
        symbol.replaceChildren(people().partySymbol(profile, true));
        document.querySelector('#priorityCompareLink').href =
            `/parties/compare?party=${encodeURIComponent(profile.code)}`;
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
        renderPriorities();
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

    async function initializePriorities(session) {
        authSession = session;
        if (!session.authenticated) {
            renderPriorities();
            return;
        }
        try {
            const saved = await window.FhemniCatalog.requestJson('/api/account/policy-topics');
            if ((saved.topicCodes || []).length || !localPrioritySyncPending) {
                selectedPolicyTopics = validPriorityCodes(saved.topicCodes);
                localPrioritySyncPending = false;
                writeLocalPriorities();
                priorityNoticeKey = 'priorities.savedAccount';
            } else if (selectedPolicyTopics.length) {
                await saveAccountPriorities(selectedPolicyTopics);
                localPrioritySyncPending = false;
                writeLocalPriorities();
                priorityNoticeKey = 'priorities.savedAccount';
            }
        } catch (_) {
            priorityNoticeKey = selectedPolicyTopics.length ? 'priorities.saveFailed' : '';
        }
        renderPriorities();
    }

    function renderPriorities() {
        const panel = document.querySelector('#partyPriorities');
        const tab = document.querySelector('#partyPrioritiesTab');
        const selectable = policyTopicCatalog.filter(topic => topic.selectable);
        const available = Boolean(programme && selectable.length);
        tab.hidden = !available;
        if (!available) {
            panel.hidden = true;
            return;
        }
        panel.hidden = tab.getAttribute('aria-selected') !== 'true';

        selectedPolicyTopics = validPriorityCodes(selectedPolicyTopics);
        const choices = document.querySelector('#priorityTopicChoices');
        choices.replaceChildren();
        selectable.forEach(topic => {
            const button = document.createElement('button');
            button.className = 'priority-topic-choice';
            button.type = 'button';
            button.dataset.topicCode = topic.code;
            button.setAttribute('aria-pressed', String(selectedPolicyTopics.includes(topic.code)));
            button.textContent = localized(topic.label);
            button.addEventListener('click', () => togglePriority(topic.code));
            choices.append(button);
        });

        const signIn = document.querySelector('#prioritySignIn');
        signIn.hidden = Boolean(authSession?.authenticated);
        signIn.href = window.FhemniAuth.loginPage(window.location.pathname);
        renderPriorityStatus();
        renderPriorityMatches();
    }

    function togglePriority(code) {
        priorityNoticeKey = '';
        if (selectedPolicyTopics.includes(code)) {
            selectedPolicyTopics = selectedPolicyTopics.filter(item => item !== code);
        } else if (selectedPolicyTopics.length >= priorityMaxSelections) {
            priorityNoticeKey = 'priorities.limitReached';
            renderPriorityStatus();
            return;
        } else {
            selectedPolicyTopics = [...selectedPolicyTopics, code];
        }
        localPrioritySyncPending = true;
        writeLocalPriorities();
        priorityNoticeKey = authSession?.authenticated
            ? 'priorities.savingAccount'
            : selectedPolicyTopics.length ? 'priorities.savedLocal' : '';
        renderPriorities();
        if (authSession?.authenticated) queueAccountPrioritySave();
    }

    function queueAccountPrioritySave() {
        const snapshot = [...selectedPolicyTopics];
        preferenceSaveQueue = preferenceSaveQueue
            .catch(() => undefined)
            .then(() => saveAccountPriorities(snapshot))
            .then(() => {
                if (sameCodes(snapshot, selectedPolicyTopics)) {
                    localPrioritySyncPending = false;
                    writeLocalPriorities();
                    priorityNoticeKey = 'priorities.savedAccount';
                    renderPriorityStatus();
                }
            })
            .catch(() => {
                if (sameCodes(snapshot, selectedPolicyTopics)) {
                    localPrioritySyncPending = true;
                    writeLocalPriorities();
                    priorityNoticeKey = 'priorities.saveFailed';
                    renderPriorityStatus();
                }
            });
    }

    async function saveAccountPriorities(topicCodes) {
        const options = await window.FhemniAuth.withCsrf({
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ topicCodes })
        });
        return window.FhemniCatalog.requestJson('/api/account/policy-topics', options);
    }

    function renderPriorityStatus() {
        const status = document.querySelector('#prioritySelectionStatus');
        const count = t('priorities.selectedCount', {
            count: selectedPolicyTopics.length,
            limit: priorityMaxSelections
        });
        const notice = priorityNoticeKey
            ? t(priorityNoticeKey, { limit: priorityMaxSelections })
            : '';
        status.textContent = [count, notice].filter(Boolean).join(' · ');
    }

    function renderPriorityMatches() {
        const panel = document.querySelector('#partyPriorityMatch');
        panel.hidden = !selectedPolicyTopics.length;
        if (panel.hidden) return;

        const groups = document.querySelector('#priorityMatchGroups');
        groups.replaceChildren();
        const uniqueMatches = new Set();
        selectedPolicyTopics.forEach(code => {
            const matches = (programme?.promises || []).filter(promise =>
                promiseMatchesBroadTopic(promise, code));
            matches.forEach(promise => uniqueMatches.add(promise.slug));
            groups.append(priorityMatchGroup(code, matches));
        });
        document.querySelector('#priorityCoverageSummary').textContent = programme
            ? t('priorities.coverage', {
                matched: uniqueMatches.size,
                selected: selectedPolicyTopics.length
            })
            : t('priorities.noProgramme');
    }

    function priorityMatchGroup(code, promises) {
        const group = document.createElement('section');
        group.className = 'priority-match-group';
        const heading = document.createElement('h4');
        heading.textContent = policyTopicLabel(code);
        group.append(heading);
        if (!programme || !promises.length) {
            const empty = document.createElement('p');
            empty.className = 'priority-empty';
            empty.textContent = programme ? t('priorities.noMatches') : t('priorities.noProgramme');
            group.append(empty);
            return group;
        }

        const direct = promises.filter(promise => topicRelationship(promise, code) === 'DIRECT').length;
        const metaLine = document.createElement('p');
        metaLine.className = 'priority-match-meta';
        metaLine.textContent = t('priorities.topicMeta', {
            count: promises.length,
            direct,
            related: promises.length - direct
        });
        group.append(metaLine);

        const list = document.createElement('div');
        list.className = 'priority-promise-list';
        const extraPromises = [];
        [...promises]
            .sort((first, second) => relationshipRank(first, code) - relationshipRank(second, code))
            .forEach((promise, index) => {
                const link = priorityPromiseLink(promise, code);
                if (index >= 3) {
                    link.hidden = true;
                    extraPromises.push(link);
                }
                list.append(link);
            });
        group.append(list);
        if (extraPromises.length) {
            const more = document.createElement('button');
            more.type = 'button';
            more.className = 'priority-more-count';
            more.setAttribute('aria-expanded', 'false');
            more.textContent = showMorePromisesLabel(extraPromises.length);
            more.addEventListener('click', () => {
                const expanded = more.getAttribute('aria-expanded') !== 'true';
                more.setAttribute('aria-expanded', String(expanded));
                extraPromises.forEach(link => { link.hidden = !expanded; });
                more.textContent = expanded
                    ? t('priorities.showFewerPromises')
                    : showMorePromisesLabel(extraPromises.length);
            });
            group.append(more);
        }
        return group;
    }

    function priorityPromiseLink(promise, broadCode) {
        const link = document.createElement('a');
        link.className = 'priority-promise-link';
        link.href = `/promises/${encodeURIComponent(promise.slug)}`;
        const topline = document.createElement('span');
        topline.className = 'priority-promise-topline';
        const subtopic = document.createElement('span');
        subtopic.className = 'priority-promise-topic';
        const relationship = topicRelationship(promise, broadCode);
        subtopic.textContent = `${promiseTopicLabels(promise, broadCode)} · ${t(`priorities.${relationship.toLowerCase()}`)}`;
        const verdict = document.createElement('span');
        verdict.className = `feasibility-badge ${String(promise.verdict).toLowerCase().replace('_', '-')}`;
        verdict.textContent = t(`promise.verdict.${promise.verdict}`);
        topline.append(subtopic, verdict);
        const title = document.createElement('strong');
        title.textContent = localized(promise.title);
        link.append(topline, title);
        return link;
    }

    function showMorePromisesLabel(count) {
        return count === 1
            ? t('priorities.showOneMorePromise')
            : t('priorities.showMorePromises', { count });
    }

    function promiseMatchesBroadTopic(promise, broadCode) {
        return (promise.policyTopics || []).some(topic => topic.broadCode === broadCode);
    }

    function topicRelationship(promise, broadCode) {
        const matches = (promise.policyTopics || []).filter(topic => topic.broadCode === broadCode);
        return matches.some(topic => topic.relationship === 'DIRECT') ? 'DIRECT' : 'RELATED';
    }

    function relationshipRank(promise, broadCode) {
        return topicRelationship(promise, broadCode) === 'DIRECT' ? 0 : 1;
    }

    function policyTopicLabel(code) {
        return localized(policyTopicCatalog.find(topic => topic.code === code)?.label) || code;
    }

    function promiseTopicLabels(promise, broadCode) {
        const labels = [...new Set((promise.policyTopics || [])
            .filter(topic => topic.broadCode === broadCode)
            .map(topic => policyTopicLabel(topic.code)))];
        return (labels.length ? labels : [policyTopicLabel(broadCode)]).slice(0, 3).join(' / ');
    }

    function validPriorityCodes(codes) {
        const available = new Set(policyTopicCatalog.filter(topic => topic.selectable).map(topic => topic.code));
        return [...new Set(codes || [])].filter(code => available.has(code)).slice(0, priorityMaxSelections);
    }

    function readLocalPriorities() {
        try {
            return validPriorityCodes(JSON.parse(window.localStorage.getItem(PRIORITY_STORAGE_KEY) || '[]'));
        } catch (_) {
            return [];
        }
    }

    function writeLocalPriorities() {
        try {
            window.localStorage.setItem(PRIORITY_STORAGE_KEY, JSON.stringify(selectedPolicyTopics));
            window.localStorage.setItem(PRIORITY_PENDING_KEY, String(localPrioritySyncPending));
        } catch (_) { /* Preferences still work for the current page. */ }
    }

    function readLocalPriorityPending() {
        try {
            return window.localStorage.getItem(PRIORITY_PENDING_KEY) === 'true';
        } catch (_) {
            return false;
        }
    }

    function sameCodes(first, second) {
        return first.length === second.length && first.every((value, index) => value === second[index]);
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
        login.href = window.FhemniAuth.loginPage(window.location.pathname, 'chat');

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

    function setupTabs(hasProgramme) {
        const programmeTab = document.querySelector('#partyProgrammeTab');
        const prioritiesTab = document.querySelector('#partyPrioritiesTab');
        const episodesTab = document.querySelector('#partyEpisodesTab');
        programmeTab.onclick = () => selectTab('programme');
        prioritiesTab.onclick = () => selectTab('priorities');
        episodesTab.onclick = () => selectTab('episodes');
        selectTab(hasProgramme ? 'programme' : 'episodes');
    }

    function selectTab(name) {
        const programmeTab = document.querySelector('#partyProgrammeTab');
        const prioritiesTab = document.querySelector('#partyPrioritiesTab');
        const episodesTab = document.querySelector('#partyEpisodesTab');
        const programmePanel = document.querySelector('#partyProgramme');
        const prioritiesPanel = document.querySelector('#partyPriorities');
        const episodesPanel = document.querySelector('#partyEpisodesSection');
        const prioritiesAvailable = !prioritiesTab.hidden;
        const selected = name === 'programme' && programme
            ? 'programme'
            : name === 'priorities' && prioritiesAvailable
                ? 'priorities'
                : 'episodes';
        [
            ['programme', programmeTab, programmePanel],
            ['priorities', prioritiesTab, prioritiesPanel],
            ['episodes', episodesTab, episodesPanel]
        ].forEach(([tabName, tab, panel]) => {
            const active = selected === tabName;
            tab.setAttribute('aria-selected', String(active));
            tab.tabIndex = active ? 0 : -1;
            panel.hidden = !active;
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
