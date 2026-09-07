(function () {
    const form = document.querySelector('#videoSuggestionForm');
    const url = document.querySelector('#videoSuggestionUrl');
    const button = document.querySelector('#videoSuggestionButton');
    const feedback = document.querySelector('#videoSuggestionFeedback');
    const suggestionAuthGate = document.querySelector('#suggestionAuthGate');
    const suggestionLoginLink = document.querySelector('#suggestionLoginLink');
    const community = document.querySelector('#suggestionCommunity');
    const communityCount = document.querySelector('#suggestionCommunityCount');
    const boardGate = document.querySelector('#suggestionBoardGate');
    const boardLoginLink = document.querySelector('#suggestionBoardLoginLink');
    const board = document.querySelector('#suggestionBoard');
    const voteFeedback = document.querySelector('#suggestionVoteFeedback');
    if (!form || !url || !button || !feedback) return;
    let lastSuccess;
    let authSession = { authenticated: false };
    let rankedSuggestions = [];

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    async function submitSuggestion(event) {
        event.preventDefault();
        if (!authSession.authenticated) {
            configureSuggestionAccess();
            suggestionLoginLink?.focus();
            return;
        }
        feedback.hidden = true;
        lastSuccess = null;
        button.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ youtubeUrl: url.value })
            });
            const result = await window.FhemniCatalog.requestJson('/api/suggestions', options);
            const key = {
                CREATED: 'suggestion.created',
                HELD_FOR_REVIEW: 'suggestion.heldForReview',
                ALREADY_SUGGESTED: 'suggestion.duplicate',
                ALREADY_CATALOGUED: 'suggestion.catalogued'
            }[result.outcome] || 'suggestion.created';
            lastSuccess = { key, parameters: { count: result.submissionCount, title: result.title } };
            showFeedback(t(lastSuccess.key, lastSuccess.parameters), false);
            url.value = '';
            if (board) await loadBoard();
        } catch (error) {
            showFeedback(error.message, true);
        } finally {
            button.disabled = false;
        }
    }

    function showFeedback(message, error) {
        feedback.className = `suggestion-feedback ${error ? 'error' : 'success'}`;
        feedback.textContent = message;
        feedback.hidden = false;
    }

    async function configureSuggestions() {
        try {
            authSession = await window.FhemniAuth.session();
        } catch (_) {
            authSession = { authenticated: false };
        }
        configureSuggestionAccess();
        if (!community || !board || !boardGate) return;
        community.hidden = false;
        boardGate.hidden = true;
        board.hidden = false;
        if (boardLoginLink) boardLoginLink.href = window.FhemniAuth.loginPage(window.location.pathname, 'vote');
        await loadBoard();
    }

    function configureSuggestionAccess() {
        const authenticated = Boolean(authSession.authenticated);
        form.hidden = !authenticated;
        if (suggestionAuthGate) suggestionAuthGate.hidden = authenticated;
        if (suggestionLoginLink) {
            suggestionLoginLink.href = window.FhemniAuth.loginPage(window.location.pathname, 'suggest');
        }
    }

    async function loadBoard() {
        board.replaceChildren();
        const loading = document.createElement('div');
        loading.className = 'catalog-loading';
        loading.textContent = t('suggestion.loading');
        board.append(loading);
        try {
            rankedSuggestions = await window.FhemniCatalog.requestJson('/api/suggestions');
            renderBoard();
        } catch (error) {
            window.FhemniCatalog.renderError(board, error.message);
        }
    }

    function renderBoard() {
        if (!board) return;
        board.replaceChildren();
        communityCount.textContent = t('suggestion.videoCount', { count: rankedSuggestions.length });
        if (!rankedSuggestions.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('suggestion.empty');
            board.append(empty);
            return;
        }
        rankedSuggestions.forEach((suggestion, index) => {
            board.append(createSuggestionCard(suggestion, index + 1));
        });
    }

    function createSuggestionCard(suggestion, rank) {
        const article = document.createElement('article');
        article.className = 'suggestion-board-row';

        const rankLabel = document.createElement('span');
        rankLabel.className = 'suggestion-rank';
        rankLabel.textContent = `#${rank}`;

        const image = document.createElement('img');
        image.src = window.FhemniCatalog.safeImage(suggestion.thumbnailUrl, suggestion.youtubeVideoId);
        image.alt = '';
        image.loading = 'lazy';

        const copy = document.createElement('div');
        copy.className = 'suggestion-board-copy';
        const link = document.createElement('a');
        link.href = suggestion.canonicalUrl;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.textContent = suggestion.title || t('suggestion.videoLabel', { id: suggestion.youtubeVideoId });
        const meta = document.createElement('span');
        meta.textContent = [
            suggestion.authorName,
            t('suggestion.requestCount', { count: suggestion.submissionCount })
        ].filter(Boolean).join(' · ');
        const attribution = document.createElement('span');
        attribution.className = 'suggestion-attribution';
        attribution.textContent = suggestion.suggestedByFirstName
            ? t('suggestion.suggestedBy', { name: suggestion.suggestedByFirstName })
            : t('suggestion.suggestedByCommunity');
        copy.append(link, attribution, meta);

        const voting = document.createElement('div');
        voting.className = 'suggestion-voting';
        const down = voteButton(suggestion, -1, '−1', 'suggestion.downvote');
        const score = document.createElement('strong');
        score.className = 'suggestion-score';
        score.textContent = String(suggestion.voteScore);
        score.setAttribute('aria-label', t('suggestion.score', { score: suggestion.voteScore }));
        const up = voteButton(suggestion, 1, '+1', 'suggestion.upvote');
        voting.append(down, score, up);

        article.append(rankLabel, image, copy, voting);
        return article;
    }

    function voteButton(suggestion, value, label, labelKey) {
        const vote = document.createElement('button');
        vote.type = 'button';
        vote.className = `vote-button${suggestion.viewerVote === value ? ' active' : ''}`;
        vote.textContent = label;
        vote.setAttribute('aria-label', t(labelKey));
        vote.setAttribute('aria-pressed', String(suggestion.viewerVote === value));
        vote.addEventListener('click', () => castVote(suggestion.id, value, vote));
        return vote;
    }

    async function castVote(suggestionId, value, voteButtonElement) {
        voteFeedback.hidden = true;
        if (!authSession.authenticated) {
            boardGate.hidden = false;
            boardLoginLink?.focus();
            return;
        }
        voteButtonElement.disabled = true;
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ value })
            });
            await window.FhemniCatalog.requestJson(
                `/api/suggestions/${encodeURIComponent(suggestionId)}/votes`, options);
            await loadBoard();
        } catch (error) {
            voteFeedback.className = 'suggestion-feedback error';
            voteFeedback.textContent = t('suggestion.voteFailed', { message: error.message });
            voteFeedback.hidden = false;
            voteButtonElement.disabled = false;
        }
    }

    form.addEventListener('submit', submitSuggestion);
    document.addEventListener('DOMContentLoaded', configureSuggestions);
    document.addEventListener('fhemni:localechange', () => {
        if (lastSuccess) feedback.textContent = t(lastSuccess.key, lastSuccess.parameters);
        if (rankedSuggestions.length || board) renderBoard();
    });
})();
