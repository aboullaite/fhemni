(function () {
    const PRIORITY_STORAGE_KEY = 'fhemni.policy-topic-preferences';
    const PRIORITY_PENDING_KEY = 'fhemni.policy-topic-preferences-pending';
    const COMPARE_PARTIES_STORAGE_KEY = 'fhemni.policy-party-comparison';
    const MAX_COMPARE_PARTIES = 4;
    const grid = document.querySelector('#partiesGrid');
    const count = document.querySelector('#partiesCount');
    const programmeCache = new Map();
    let parties = [];
    let topicCatalog = [];
    let maxTopics = 3;
    let selectedTopics = [];
    let selectedParties = [];
    let authSession = { authenticated: false };
    let localSyncPending = false;
    let topicNoticeKey = '';
    let partyNoticeKey = '';
    let saveQueue = Promise.resolve();
    let comparisonRevision = 0;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function localized(value) {
        const locale = people()?.locale() || 'ar';
        return value?.[locale] || value?.ar || value?.fr || value?.en || '';
    }

    function people() {
        return window.FhemniPeople;
    }

    async function load() {
        try {
            const [partyResponse, topics, session] = await Promise.all([
                window.FhemniCatalog.requestJson('/api/catalog/parties'),
                window.FhemniCatalog.requestJson('/api/catalog/policy-topics'),
                window.FhemniAuth.session().catch(() => ({ authenticated: false }))
            ]);
            parties = partyResponse;
            topicCatalog = topics.topics || [];
            maxTopics = Number(topics.maxSelections) || 3;
            authSession = session;
            selectedTopics = readLocalTopics();
            selectedParties = readLocalParties();
            localSyncPending = readLocalPending();
            await initializeAccountTopics();
            render();
        } catch (error) {
            count.textContent = '';
            window.FhemniCatalog.renderError(grid, error.message);
        }
    }

    async function initializeAccountTopics() {
        if (!authSession.authenticated) return;
        try {
            const saved = await window.FhemniCatalog.requestJson('/api/account/policy-topics');
            if ((saved.topicCodes || []).length || !localSyncPending) {
                selectedTopics = validTopicCodes(saved.topicCodes);
                localSyncPending = false;
                writeLocalTopics();
            } else if (selectedTopics.length) {
                await saveAccountTopics(selectedTopics);
                localSyncPending = false;
                writeLocalTopics();
            }
            topicNoticeKey = 'priorities.savedAccount';
        } catch (_) {
            topicNoticeKey = selectedTopics.length ? 'priorities.saveFailed' : '';
        }
    }

    function render() {
        renderDirectory();
        renderExplorer();
    }

    function renderDirectory() {
        grid.replaceChildren();
        if (!parties.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('parties.empty');
            grid.append(empty);
            count.textContent = '';
            return;
        }
        parties.forEach(party => grid.append(partyCard(party)));
        count.textContent = t('parties.count', { count: parties.length });
    }

    function renderExplorer() {
        const explorer = document.querySelector('#priorityExplorer');
        const selectableTopics = topicCatalog.filter(topic => topic.selectable);
        const programmeParties = parties.filter(party => party.programmePartyCode);
        explorer.hidden = !selectableTopics.length || !programmeParties.length;
        if (explorer.hidden) return;

        selectedTopics = validTopicCodes(selectedTopics);
        selectedParties = validPartyCodes(selectedParties);
        renderTopicChoices(selectableTopics);
        renderPartyChoices(programmeParties);
        renderSelectionStatus();
        const signIn = document.querySelector('#comparePrioritySignIn');
        signIn.hidden = Boolean(authSession.authenticated);
        signIn.href = window.FhemniAuth.loginPage(window.location.pathname);
        void renderComparison();
    }

    function renderTopicChoices(selectableTopics) {
        const choices = document.querySelector('#compareTopicChoices');
        choices.replaceChildren();
        selectableTopics.forEach(topic => {
            const button = document.createElement('button');
            button.className = 'priority-topic-choice';
            button.type = 'button';
            button.dataset.topicCode = topic.code;
            button.setAttribute('aria-pressed', String(selectedTopics.includes(topic.code)));
            button.textContent = localized(topic.label);
            button.addEventListener('click', () => toggleTopic(topic.code));
            choices.append(button);
        });
    }

    function renderPartyChoices(programmeParties) {
        const choices = document.querySelector('#comparePartyChoices');
        choices.replaceChildren();
        programmeParties.forEach(party => {
            const button = document.createElement('button');
            button.className = 'priority-party-choice';
            button.type = 'button';
            button.dataset.partyCode = party.code;
            button.setAttribute('aria-pressed', String(selectedParties.includes(party.code)));
            button.style.setProperty('--party-color', party.color || '#176b63');
            const symbol = people().partySymbol(party, true);
            const label = document.createElement('span');
            label.textContent = party.code;
            button.append(symbol, label);
            button.addEventListener('click', () => toggleParty(party.code));
            choices.append(button);
        });
    }

    function renderSelectionStatus() {
        const topicCount = t('priorities.selectedCount', {
            count: selectedTopics.length,
            limit: maxTopics
        });
        const notice = topicNoticeKey ? t(topicNoticeKey, { limit: maxTopics }) : '';
        document.querySelector('#compareTopicStatus').textContent = [topicCount, notice]
            .filter(Boolean).join(' · ');
        const partyCount = t('priorities.partiesSelected', {
            count: selectedParties.length,
            limit: MAX_COMPARE_PARTIES
        });
        const partyNotice = partyNoticeKey ? t(partyNoticeKey) : '';
        document.querySelector('#comparePartyStatus').textContent = [partyCount, partyNotice]
            .filter(Boolean).join(' · ');
    }

    function toggleTopic(code) {
        topicNoticeKey = '';
        if (selectedTopics.includes(code)) {
            selectedTopics = selectedTopics.filter(item => item !== code);
        } else if (selectedTopics.length >= maxTopics) {
            topicNoticeKey = 'priorities.limitReached';
            renderSelectionStatus();
            return;
        } else {
            selectedTopics = [...selectedTopics, code];
        }
        localSyncPending = true;
        writeLocalTopics();
        topicNoticeKey = authSession.authenticated
            ? 'priorities.savingAccount'
            : selectedTopics.length ? 'priorities.savedLocal' : '';
        renderExplorer();
        if (authSession.authenticated) queueAccountSave();
    }

    function toggleParty(code) {
        partyNoticeKey = '';
        if (selectedParties.includes(code)) {
            selectedParties = selectedParties.filter(item => item !== code);
        } else if (selectedParties.length >= MAX_COMPARE_PARTIES) {
            partyNoticeKey = 'priorities.partyLimitReached';
            renderSelectionStatus();
            return;
        } else {
            selectedParties = [...selectedParties, code];
        }
        writeLocalParties();
        renderExplorer();
    }

    function queueAccountSave() {
        const snapshot = [...selectedTopics];
        saveQueue = saveQueue
            .catch(() => undefined)
            .then(() => saveAccountTopics(snapshot))
            .then(() => {
                if (sameCodes(snapshot, selectedTopics)) {
                    localSyncPending = false;
                    writeLocalTopics();
                    topicNoticeKey = 'priorities.savedAccount';
                    renderSelectionStatus();
                }
            })
            .catch(() => {
                if (sameCodes(snapshot, selectedTopics)) {
                    localSyncPending = true;
                    writeLocalTopics();
                    topicNoticeKey = 'priorities.saveFailed';
                    renderSelectionStatus();
                }
            });
    }

    async function saveAccountTopics(topicCodes) {
        const options = await window.FhemniAuth.withCsrf({
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ topicCodes })
        });
        return window.FhemniCatalog.requestJson('/api/account/policy-topics', options);
    }

    async function renderComparison() {
        const container = document.querySelector('#priorityComparison');
        const revision = ++comparisonRevision;
        if (!selectedTopics.length) {
            renderComparisonState(container, 'priorities.chooseTopicsFirst');
            return;
        }
        if (!selectedParties.length) {
            renderComparisonState(container, 'priorities.choosePartiesFirst');
            return;
        }
        renderComparisonState(container, 'priorities.loadingComparison');
        const selected = selectedParties.map(code => parties.find(party => party.code === code)).filter(Boolean);
        const results = await Promise.all(selected.map(loadProgramme));
        if (revision !== comparisonRevision) return;

        container.replaceChildren();
        container.className = 'priority-comparison priority-comparison-grid';
        selected.forEach((party, index) => container.append(comparisonCard(party, results[index])));
    }

    async function loadProgramme(party) {
        const code = party.programmePartyCode;
        if (!programmeCache.has(code)) {
            programmeCache.set(code, window.FhemniCatalog
                .requestJson(`/api/catalog/parties/${encodeURIComponent(code)}/programme?view=policy-topics-v1`)
                .then(programme => ({ programme, error: null }))
                .catch(error => ({ programme: null, error })));
        }
        return programmeCache.get(code);
    }

    function comparisonCard(party, result) {
        const article = document.createElement('article');
        article.className = 'priority-comparison-card';
        article.style.setProperty('--party-color', party.color || '#176b63');
        const heading = document.createElement('div');
        heading.className = 'priority-comparison-party';
        const symbol = people().partySymbol(party, true);
        const title = document.createElement('div');
        const code = document.createElement('span');
        code.textContent = party.code;
        const name = document.createElement('h3');
        name.textContent = people().partyDisplayName(party);
        title.append(code, name);
        const open = document.createElement('a');
        open.className = 'text-link';
        open.href = `/parties/${encodeURIComponent(party.code)}`;
        open.textContent = t('priorities.openParty');
        heading.append(symbol, title, open);
        article.append(heading);

        if (result.error || !result.programme) {
            const error = document.createElement('p');
            error.className = 'priority-comparison-error';
            error.textContent = t('priorities.programmeUnavailable');
            article.append(error);
            return article;
        }

        selectedTopics.forEach(topicCode => {
            const matches = (result.programme.promises || [])
                .filter(promise => promiseMatches(promise, topicCode))
                .sort((first, second) => relationshipRank(first, topicCode) - relationshipRank(second, topicCode));
            article.append(comparisonTopic(topicCode, matches));
        });
        return article;
    }

    function comparisonTopic(topicCode, matches) {
        const section = document.createElement('section');
        section.className = 'priority-comparison-topic';
        const top = document.createElement('div');
        top.className = 'priority-comparison-topic-heading';
        const title = document.createElement('h4');
        title.textContent = topicLabel(topicCode);
        const count = document.createElement('span');
        count.textContent = t('priorities.promiseCount', { count: matches.length });
        top.append(title, count);
        section.append(top);
        if (!matches.length) {
            const empty = document.createElement('p');
            empty.className = 'priority-empty';
            empty.textContent = t('priorities.noMatchesShort');
            section.append(empty);
            return section;
        }
        const list = document.createElement('div');
        list.className = 'priority-comparison-promises';
        matches.slice(0, 2).forEach(promise => list.append(comparisonPromise(promise, topicCode)));
        section.append(list);
        if (matches.length > 2) {
            const more = document.createElement('span');
            more.className = 'priority-more-count';
            more.textContent = t('priorities.morePromises', { count: matches.length - 2 });
            section.append(more);
        }
        return section;
    }

    function comparisonPromise(promise, broadCode) {
        const link = document.createElement('a');
        link.className = 'priority-comparison-promise';
        link.href = `/promises/${encodeURIComponent(promise.slug)}`;
        const meta = document.createElement('span');
        const relationship = topicRelationship(promise, broadCode);
        meta.textContent = `${promiseTopicLabels(promise, broadCode)} · ${t(`priorities.${relationship.toLowerCase()}`)}`;
        const title = document.createElement('strong');
        title.textContent = localized(promise.title);
        link.append(meta, title);
        return link;
    }

    function renderComparisonState(container, key) {
        container.className = 'priority-comparison';
        container.replaceChildren();
        const state = document.createElement('p');
        state.className = 'priority-comparison-state';
        state.textContent = t(key);
        container.append(state);
    }

    function partyCard(party) {
        const article = document.createElement('article');
        article.className = 'catalog-card';
        const body = document.createElement('div');
        body.className = 'catalog-card-body';

        const top = document.createElement('div');
        top.className = 'person-card-top';
        const symbol = people().partySymbol(party, true);
        const title = document.createElement('h3');
        title.className = 'person-card-title';
        title.dir = 'auto';
        const link = document.createElement('a');
        link.href = `/parties/${encodeURIComponent(party.code)}`;
        link.textContent = people().partyDisplayName(party);
        title.append(link);
        top.append(symbol, title);

        const alt = document.createElement('p');
        alt.className = 'catalog-card-meta';
        alt.dir = 'auto';
        alt.textContent = (party.memberPartyCodes || [party.code]).join(' + ');

        const stats = document.createElement('div');
        stats.className = 'person-stats';
        const members = document.createElement('span');
        members.className = 'person-stat';
        members.textContent = t('parties.members', { count: party.members });
        const episodes = document.createElement('span');
        episodes.className = 'person-stat';
        episodes.textContent = t('parties.episodes', { count: party.appearances });
        stats.append(members, episodes);
        if (party.programmePartyCode) {
            const programme = document.createElement('span');
            programme.className = 'person-stat programme-available';
            programme.textContent = t('priorities.programmeAvailable');
            stats.append(programme);
        }

        body.append(top, alt, stats);
        article.append(body);
        return article;
    }

    function promiseMatches(promise, broadCode) {
        return (promise.policyTopics || []).some(topic => topic.broadCode === broadCode);
    }

    function topicRelationship(promise, broadCode) {
        const matches = (promise.policyTopics || []).filter(topic => topic.broadCode === broadCode);
        return matches.some(topic => topic.relationship === 'DIRECT') ? 'DIRECT' : 'RELATED';
    }

    function relationshipRank(promise, broadCode) {
        return topicRelationship(promise, broadCode) === 'DIRECT' ? 0 : 1;
    }

    function topicLabel(code) {
        return localized(topicCatalog.find(topic => topic.code === code)?.label) || code;
    }

    function promiseTopicLabels(promise, broadCode) {
        const labels = [...new Set((promise.policyTopics || [])
            .filter(topic => topic.broadCode === broadCode)
            .map(topic => topicLabel(topic.code)))];
        return (labels.length ? labels : [topicLabel(broadCode)]).slice(0, 3).join(' / ');
    }

    function validTopicCodes(codes) {
        const available = new Set(topicCatalog.filter(topic => topic.selectable).map(topic => topic.code));
        return [...new Set(codes || [])].filter(code => available.has(code)).slice(0, maxTopics);
    }

    function validPartyCodes(codes) {
        const available = new Set(parties.filter(party => party.programmePartyCode).map(party => party.code));
        return [...new Set(codes || [])].filter(code => available.has(code)).slice(0, MAX_COMPARE_PARTIES);
    }

    function readLocalTopics() {
        try {
            return validTopicCodes(JSON.parse(window.localStorage.getItem(PRIORITY_STORAGE_KEY) || '[]'));
        } catch (_) {
            return [];
        }
    }

    function readLocalParties() {
        try {
            return validPartyCodes(JSON.parse(window.localStorage.getItem(COMPARE_PARTIES_STORAGE_KEY) || '[]'));
        } catch (_) {
            return [];
        }
    }

    function readLocalPending() {
        try {
            return window.localStorage.getItem(PRIORITY_PENDING_KEY) === 'true';
        } catch (_) {
            return false;
        }
    }

    function writeLocalTopics() {
        try {
            window.localStorage.setItem(PRIORITY_STORAGE_KEY, JSON.stringify(selectedTopics));
            window.localStorage.setItem(PRIORITY_PENDING_KEY, String(localSyncPending));
        } catch (_) { /* Keep the current-page selection in memory. */ }
    }

    function writeLocalParties() {
        try {
            window.localStorage.setItem(COMPARE_PARTIES_STORAGE_KEY, JSON.stringify(selectedParties));
        } catch (_) { /* Keep the current-page selection in memory. */ }
    }

    function sameCodes(first, second) {
        return first.length === second.length && first.every((value, index) => value === second[index]);
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (parties.length) render();
    });
})();
