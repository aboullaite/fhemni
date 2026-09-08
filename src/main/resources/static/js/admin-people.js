(function () {
    const unmatchedList = document.querySelector('#unmatchedPeople');
    const unmatchedCount = document.querySelector('#unmatchedPeopleCount');
    const directoryList = document.querySelector('#directoryPeople');
    const directoryCount = document.querySelector('#directoryPeopleCount');
    const nonAffiliatedList = document.querySelector('#nonAffiliatedPeople');
    const nonAffiliatedCount = document.querySelector('#nonAffiliatedPeopleCount');
    const form = document.querySelector('#affiliationForm');
    const personSelect = document.querySelector('#affiliationPerson');
    const partySelect = document.querySelector('#affiliationParty');
    const validFromInput = document.querySelector('#affiliationValidFrom');
    const validUntilInput = document.querySelector('#affiliationValidUntil');
    const episodesList = document.querySelector('#affiliationEpisodes');
    const saveButton = document.querySelector('#affiliationSave');
    const feedback = document.querySelector('#affiliationFeedback');
    let data = { parties: [], people: [], unmatched: [] };
    const profileCache = new Map();
    let profileRequest = 0;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function locale() {
        return window.FhemniI18n?.locale() || 'ar';
    }

    function partyName(party) {
        return locale() === 'ar' ? party.nameAr : party.nameFr;
    }

    function personName(person) {
        return locale() === 'ar'
            ? person.displayNameAr || person.displayName
            : person.displayNameFr || person.displayName;
    }

    function allPeople() {
        return [
            ...data.unmatched.map(person => ({ ...person, unmatched: true })),
            ...data.people.map(person => ({ ...person, unmatched: false }))
        ];
    }

    function affiliatedPeople() {
        return data.people.filter(person => person.partyCode !== 'UNKNOWN');
    }

    function nonAffiliatedPeople() {
        return data.people.filter(person => person.partyCode === 'UNKNOWN');
    }

    async function load(selectedSlug) {
        try {
            data = await window.FhemniCatalog.requestJson('/api/admin/people');
            render(selectedSlug);
        } catch (error) {
            window.FhemniCatalog.renderError(unmatchedList, error.message);
            window.FhemniCatalog.renderError(directoryList, error.message);
            window.FhemniCatalog.renderError(nonAffiliatedList, error.message);
        }
    }

    function render(selectedSlug) {
        renderUnmatched();
        renderDirectory();
        fillPartyOptions();
        fillPersonOptions(selectedSlug || personSelect.value);
        syncPartySelection();
    }

    function renderUnmatched() {
        unmatchedList.replaceChildren();
        unmatchedCount.textContent = t('admin.peopleCount', { count: data.unmatched.length });
        if (!data.unmatched.length) {
            const empty = document.createElement('div');
            empty.className = 'catalog-empty';
            empty.textContent = t('admin.peopleQueueEmpty');
            unmatchedList.append(empty);
            return;
        }
        data.unmatched.forEach(person => unmatchedList.append(personRow(person, null)));
    }

    function renderDirectory() {
        directoryList.replaceChildren();
        nonAffiliatedList.replaceChildren();
        const affiliated = affiliatedPeople();
        const nonAffiliated = nonAffiliatedPeople();
        directoryCount.textContent = t('admin.peopleCount', { count: affiliated.length });
        nonAffiliatedCount.textContent = t('admin.peopleCount', { count: nonAffiliated.length });
        affiliated.forEach(person => {
            const party = data.parties.find(candidate => candidate.code === person.partyCode);
            directoryList.append(personRow(person, party));
        });
        nonAffiliated.forEach(person => nonAffiliatedList.append(personRow(person, null)));
    }

    function personRow(person, party) {
        const row = document.createElement('article');
        row.className = 'admin-person-row';
        const copy = document.createElement('div');
        const name = document.createElement('strong');
        name.dir = 'auto';
        name.textContent = personName(person);
        copy.append(name);
        if (Number.isFinite(person.appearances)) {
            const meta = document.createElement('span');
            meta.textContent = t('parties.episodes', { count: person.appearances });
            copy.append(meta);
        }
        if (party) copy.append(window.FhemniPeople.partyBadge(party, { link: false }));
        const map = document.createElement('button');
        map.type = 'button';
        map.className = 'secondary-button';
        map.textContent = party ? t('common.edit') : t('admin.peopleAssign');
        map.addEventListener('click', () => selectPerson(person.slug));
        row.append(copy, map);
        return row;
    }

    function fillPersonOptions(selected) {
        const unmatched = data.unmatched.map(person => ({ ...person, unmatched: true }));
        const affiliated = affiliatedPeople().map(person => ({ ...person, unmatched: false }));
        const nonAffiliated = nonAffiliatedPeople().map(person => ({ ...person, unmatched: false }));
        const people = [...unmatched, ...affiliated, ...nonAffiliated];
        personSelect.replaceChildren();
        appendPersonGroup(t('admin.peopleQueueTitle'), unmatched);
        appendPersonGroup(t('admin.directoryTitle'), affiliated);
        appendPersonGroup(t('admin.nonAffiliatedTitle'), nonAffiliated);
        if (selected && people.some(person => person.slug === selected)) personSelect.value = selected;
    }

    function appendPersonGroup(label, people) {
        if (!people.length) return;
        const group = document.createElement('optgroup');
        group.label = label;
        people.forEach(person => {
            const option = document.createElement('option');
            option.value = person.slug;
            option.textContent = personName(person);
            group.append(option);
        });
        personSelect.append(group);
    }

    function fillPartyOptions() {
        const selected = partySelect.value;
        partySelect.replaceChildren();
        const noAffiliation = document.createElement('option');
        noAffiliation.value = 'UNKNOWN';
        noAffiliation.textContent = t('admin.affiliationNone');
        partySelect.append(noAffiliation);
        data.parties.filter(party => party.code !== 'UNKNOWN').forEach(party => {
            const option = document.createElement('option');
            option.value = party.code;
            option.textContent = `${party.code} · ${partyName(party)}`;
            partySelect.append(option);
        });
        if (selected && data.parties.some(party => party.code === selected)) partySelect.value = selected;
    }

    function selectedPerson() {
        return allPeople().find(person => person.slug === personSelect.value);
    }

    function currentAffiliation(person) {
        if (!person || person.unmatched || !person.affiliations?.length) return null;
        return person.affiliations.find(affiliation => affiliation.partyCode === person.partyCode)
            || person.affiliations.at(-1);
    }

    function syncPartySelection() {
        const person = selectedPerson();
        const affiliation = currentAffiliation(person);
        partySelect.value = affiliation?.partyCode || 'UNKNOWN';
        validFromInput.value = affiliation?.validFrom || '';
        validUntilInput.value = affiliation?.validUntil || '';
        syncPeriodInputs();
        feedback.hidden = true;
        showEpisodes(person);
    }

    async function showEpisodes(person) {
        const request = ++profileRequest;
        episodesList.replaceChildren();
        if (!person) return;
        const loading = document.createElement('span');
        loading.className = 'programme-source-help';
        loading.textContent = t('catalog.loading');
        episodesList.append(loading);
        try {
            let profile = profileCache.get(person.slug);
            if (!profile) {
                profile = await window.FhemniCatalog.requestJson(
                    `/api/catalog/people/${encodeURIComponent(person.slug)}`
                );
                profileCache.set(person.slug, profile);
            }
            if (request !== profileRequest || personSelect.value !== person.slug) return;
            episodesList.replaceChildren();
            (profile.episodes || []).forEach(episode => episodesList.append(episodeLink(episode)));
            if (!validFromInput.value && partySelect.value !== 'UNKNOWN') {
                validFromInput.value = earliestEpisodeDate(profile.episodes) || today();
            }
            if (!profile.episodes?.length) {
                loading.textContent = t('admin.affiliationEpisodesEmpty');
                episodesList.append(loading);
            }
        } catch (error) {
            if (request !== profileRequest) return;
            episodesList.replaceChildren();
            const message = document.createElement('span');
            message.className = 'programme-source-help';
            message.textContent = error.status === 404
                ? t('admin.affiliationEpisodesEmpty')
                : error.message;
            episodesList.append(message);
        }
    }

    function earliestEpisodeDate(episodes) {
        return (episodes || [])
            .map(episode => episode.publishedOn)
            .filter(Boolean)
            .sort()[0] || '';
    }

    function today() {
        const date = new Date();
        const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
        return local.toISOString().slice(0, 10);
    }

    function syncPeriodInputs() {
        const affiliated = partySelect.value !== 'UNKNOWN';
        validFromInput.disabled = !affiliated;
        validUntilInput.disabled = !affiliated;
        validFromInput.required = affiliated;
        if (!affiliated) {
            validFromInput.value = '';
            validUntilInput.value = '';
        } else if (!validFromInput.value) {
            const cached = profileCache.get(personSelect.value);
            validFromInput.value = earliestEpisodeDate(cached?.episodes) || today();
        }
    }

    function episodeLink(episode) {
        const link = document.createElement('a');
        link.className = 'admin-guest-episode';
        link.href = `/videos/${encodeURIComponent(episode.slug)}`;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        const image = document.createElement('img');
        image.src = window.FhemniCatalog.safeImage(episode.thumbnailUrl, episode.youtubeVideoId);
        image.alt = '';
        image.loading = 'lazy';
        const copy = document.createElement('span');
        const title = document.createElement('strong');
        title.dir = 'auto';
        title.textContent = episode.title;
        const meta = document.createElement('small');
        meta.textContent = [episode.role, window.FhemniCatalog.formatDate(episode.publishedOn)]
            .filter(Boolean).join(' · ');
        copy.append(title, meta);
        link.append(image, copy);
        return link;
    }

    function selectPerson(slug) {
        fillPersonOptions(slug);
        syncPartySelection();
        document.querySelector('#affiliationEditorTitle').scrollIntoView({ behavior: 'smooth', block: 'center' });
        partySelect.focus({ preventScroll: true });
    }

    async function save(event) {
        event.preventDefault();
        const person = selectedPerson();
        if (!person) return;
        saveButton.disabled = true;
        feedback.hidden = true;
        const affiliation = {
            partyCode: partySelect.value,
            validFrom: validFromInput.value || null,
            validUntil: validUntilInput.value || null
        };
        try {
            let endpoint;
            let method;
            let body;
            const existing = currentAffiliation(person);
            if (person.unmatched) {
                endpoint = '/api/admin/people';
                method = 'POST';
                body = {
                    slug: person.slug,
                    displayNameFr: person.displayName,
                    displayNameAr: person.displayNameAr,
                    aliases: person.spellings,
                    affiliation
                };
            } else if (existing) {
                const changingParty = existing.partyCode !== affiliation.partyCode;
                endpoint = changingParty
                    ? `/api/admin/people/${encodeURIComponent(person.slug)}/affiliations/${existing.id}/transition`
                    : `/api/admin/people/${encodeURIComponent(person.slug)}/affiliations/${existing.id}`;
                method = changingParty ? 'POST' : 'PUT';
                body = affiliation;
            } else {
                endpoint = `/api/admin/people/${encodeURIComponent(person.slug)}/affiliations`;
                method = 'POST';
                body = affiliation;
            }
            const options = await window.FhemniAuth.withCsrf({
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            await window.FhemniCatalog.requestJson(endpoint, options);
            await load(person.slug);
            showFeedback(t('admin.affiliationSaved'), false);
        } catch (error) {
            showFeedback(error.message, true);
        } finally {
            saveButton.disabled = false;
        }
    }

    function showFeedback(message, error) {
        feedback.className = `import-feedback ${error ? 'error' : 'success'}`;
        feedback.textContent = message;
        feedback.hidden = false;
    }

    form.addEventListener('submit', save);
    personSelect.addEventListener('change', syncPartySelection);
    partySelect.addEventListener('change', syncPeriodInputs);
    document.addEventListener('DOMContentLoaded', () => load());
    document.addEventListener('fhemni:localechange', () => render(personSelect.value));
})();
