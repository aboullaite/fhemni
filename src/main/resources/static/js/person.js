(function () {
    let profile;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function people() {
        return window.FhemniPeople;
    }

    async function load() {
        const slug = decodeURIComponent(window.location.pathname.split('/').filter(Boolean).at(-1) || '');
        try {
            profile = await window.FhemniCatalog.requestJson(`/api/catalog/people/${encodeURIComponent(slug)}`);
            render();
            document.querySelector('#personLoading').hidden = true;
            document.querySelector('#personDetail').hidden = false;
        } catch (error) {
            document.querySelector('#personLoading').hidden = true;
            const panel = document.querySelector('#personError');
            panel.textContent = error.message;
            panel.hidden = false;
        }
    }

    function render() {
        const person = profile.person;
        const shownName = people().personDisplayName(person);
        document.querySelector('#personName').textContent = shownName;

        const kicker = document.querySelector('#personParty');
        kicker.replaceChildren();
        if (person.partyCode && person.partyCode !== 'UNKNOWN' && person.partyCode !== 'IND') {
            kicker.append(people().partyBadge(person));
        }

        document.querySelector('#personAffiliation').textContent =
            `${t('person.affiliation')} · ${people().personPartyName(person)}`;

        const stats = document.querySelector('#personStats');
        stats.replaceChildren();
        const episodes = document.createElement('span');
        episodes.className = 'person-stat';
        episodes.textContent = t('parties.episodes', { count: person.appearances });
        const statements = document.createElement('span');
        statements.className = 'person-stat';
        statements.textContent = t('parties.statements', { count: person.claims });
        stats.append(episodes, statements);

        const topics = document.querySelector('#personTopics');
        topics.replaceChildren();
        if (person.topics && person.topics.length) {
            const label = document.createElement('div');
            label.className = 'section-kicker';
            label.textContent = t('person.topics');
            topics.append(label);
            const chips = document.createElement('div');
            person.topics.forEach(topic => {
                const chip = document.createElement('span');
                chip.className = 'topic-chip';
                chip.textContent = topic;
                chip.dir = 'auto';
                chips.append(chip);
            });
            topics.append(chips);
        }

        document.querySelector('#personEpisodesCount').textContent =
            t('parties.episodes', { count: profile.episodes.length });
        const episodeGrid = document.querySelector('#personEpisodes');
        episodeGrid.replaceChildren();
        profile.episodes.forEach(episode => episodeGrid.append(episodeCard(episode)));

        document.querySelector('#personStatementsCount').textContent =
            t('parties.statements', { count: profile.claims.length });
        const statementGrid = document.querySelector('#personStatements');
        statementGrid.replaceChildren();
        if (!profile.claims.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('person.noStatements');
            statementGrid.append(empty);
        } else {
            profile.claims.forEach(claim => statementGrid.append(statementCard(claim)));
        }

        const comparisonsSection = document.querySelector('#personComparisonsSection');
        const comparisonsGrid = document.querySelector('#personComparisons');
        comparisonsGrid.replaceChildren();
        if (profile.comparisons && profile.comparisons.length) {
            comparisonsSection.hidden = false;
            profile.comparisons.forEach(pair => comparisonsGrid.append(comparisonCard(pair, profile.person)));
        } else {
            comparisonsSection.hidden = true;
        }

        const crossSection = document.querySelector('#personCrossSection');
        const crossGrid = document.querySelector('#personCrossComparisons');
        crossGrid.replaceChildren();
        if (profile.crossComparisons && profile.crossComparisons.length) {
            crossSection.hidden = false;
            profile.crossComparisons.forEach(pair => crossGrid.append(crossCard(pair)));
        } else {
            crossSection.hidden = true;
        }

        document.title = `${shownName} — Fhemni`;
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
        const meta = document.createElement('p');
        meta.className = 'catalog-card-meta';
        meta.textContent = [
            episode.role,
            window.FhemniCatalog.formatDate(episode.publishedOn)
        ].filter(Boolean).join(' · ');
        const action = document.createElement('a');
        action.className = 'text-link';
        action.href = imageLink.href;
        action.textContent = t('person.viewEpisode');
        body.append(title, meta, action);
        article.append(imageLink, body);
        return article;
    }

    function statementCard(claim) {
        const article = document.createElement('article');
        article.className = 'statement-card';
        const text = document.createElement('p');
        text.dir = 'auto';
        text.textContent = claim.statement;
        const meta = document.createElement('div');
        meta.className = 'statement-meta';
        const startSeconds = Math.max(0, Math.floor(Number(claim.startSeconds) || 0));
        const episode = document.createElement('a');
        episode.className = 'text-link';
        episode.href = `/videos/${encodeURIComponent(claim.episodeSlug)}?t=${startSeconds}`;
        episode.textContent = claim.episodeTitle;
        episode.dir = 'auto';
        meta.append(episode);
        if (claim.topic) {
            const topic = document.createElement('span');
            topic.textContent = claim.topic;
            topic.dir = 'auto';
            meta.append(topic);
        }
        if (claim.kind) {
            const kind = document.createElement('span');
            kind.className = 'catalog-status';
            kind.textContent = claim.kind;
            meta.append(kind);
        }
        if (claim.kind === 'FACT' && claim.verdict) {
            const verdict = document.createElement('span');
            verdict.className = `catalog-status ${verdictClass(claim.verdict)}`;
            verdict.textContent = verdictLabel(claim.verdict);
            meta.append(verdict);
        }
        article.append(text, meta);
        if (claim.kind === 'FACT' && claim.explanation) {
            const explanation = document.createElement('p');
            explanation.className = 'video-meta';
            explanation.dir = 'auto';
            explanation.textContent = claim.explanation;
            article.append(explanation);
        }
        return article;
    }

    function comparisonCard(pair, person) {
        const article = document.createElement('article');
        article.className = 'compare-card catalog-grid span-all';
        const notice = document.createElement('p');
        notice.className = 'compare-notice span-all';
        notice.textContent = t('person.comparisonsIntro');
        const grid = document.createElement('div');
        grid.className = 'compare-grid span-all';
        grid.append(quoteCard(pair.first, person, false), quoteCard(pair.second, person, false));
        article.append(notice, grid);
        return article;
    }

    function crossCard(pair) {
        const article = document.createElement('article');
        article.className = 'compare-card catalog-grid span-all';
        const grid = document.createElement('div');
        grid.className = 'compare-grid span-all';
        grid.append(quoteCard(pair.first, pair.firstSpeaker, true), quoteCard(pair.second, pair.secondSpeaker, true));
        article.append(grid);
        return article;
    }

    function quoteCard(claim, speaker, showName) {
        const quote = document.createElement('blockquote');
        quote.dir = 'auto';
        if (showName && speaker) {
            if (speaker.partyCode && speaker.partyCode !== 'UNKNOWN') {
                quote.classList.add(people().partyClass(speaker.partyCode));
            }
            const who = document.createElement('div');
            who.className = 'compare-speaker';
            const link = document.createElement('a');
            link.className = 'text-link';
            link.href = `/people/${encodeURIComponent(speaker.slug)}`;
            link.textContent = people().personDisplayName(speaker);
            link.dir = 'auto';
            who.append(link);
            if (speaker.partyCode && speaker.partyCode !== 'UNKNOWN') {
                who.append(people().partyBadge(speaker));
            }
            quote.append(who);
        }
        const text = document.createElement('p');
        text.dir = 'auto';
        text.textContent = claim.statement;
        quote.append(text);
        const meta = document.createElement('div');
        meta.className = 'compare-meta';
        const startSeconds = Math.max(0, Math.floor(Number(claim.startSeconds) || 0));
        const episode = document.createElement('a');
        episode.className = 'text-link';
        episode.href = `/videos/${encodeURIComponent(claim.episodeSlug)}?t=${startSeconds}`;
        episode.textContent = claim.episodeTitle;
        episode.dir = 'auto';
        meta.append(episode);
        if (claim.verdict) {
            const verdict = document.createElement('span');
            verdict.className = `catalog-status ${verdictClass(claim.verdict)}`;
            verdict.textContent = verdictLabel(claim.verdict);
            meta.append(verdict);
        }
        quote.append(meta);
        return quote;
    }

    function verdictClass(verdict) {
        return {
            SUPPORTED: 'verdict-supported',
            CONTRADICTED: 'verdict-contradicted',
            NEEDS_CONTEXT: 'verdict-needs-context',
            UNVERIFIABLE: 'verdict-unverifiable'
        }[verdict] || '';
    }

    function verdictLabel(verdict) {
        const key = `label.${verdict}`;
        const translated = t(key);
        return translated === key ? verdict : translated;
    }

    document.addEventListener('DOMContentLoaded', load);
    document.addEventListener('fhemni:localechange', () => {
        if (profile) render();
    });
})();
