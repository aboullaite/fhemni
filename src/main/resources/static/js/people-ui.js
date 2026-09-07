(function () {
    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function locale() {
        return window.FhemniI18n?.locale() || 'ar';
    }

    function partyDisplayName(party) {
        if (!party) return '';
        if (locale() === 'ar') return party.nameAr || party.nameFr || party.code;
        if (locale() === 'fr') return party.nameFr || party.code;
        return party.nameFr || party.code;
    }

    function personPartyName(person) {
        if (!person) return '';
        if (locale() === 'ar') return person.partyNameAr || person.partyNameFr || person.partyCode;
        return person.partyNameFr || person.partyNameAr || person.partyCode;
    }

    function personDisplayName(person) {
        if (!person) return '';
        if (locale() === 'ar') return person.displayNameAr || person.displayName || person.displayNameFr || '';
        return person.displayName || person.displayNameFr || person.displayNameAr || '';
    }

    function partyClass(code) {
        return `party-${String(code || 'UNKNOWN').toUpperCase()}`;
    }

    function partyBadge(personOrParty, { link = true } = {}) {
        const code = personOrParty.partyCode || personOrParty.code || 'UNKNOWN';
        const name = personOrParty.partyNameFr || personOrParty.nameFr
            ? partyDisplayName({
                nameFr: personOrParty.partyNameFr || personOrParty.nameFr,
                nameAr: personOrParty.partyNameAr || personOrParty.nameAr,
                code
            })
            : code;
        const element = document.createElement(link ? 'a' : 'span');
        element.className = `party-badge ${partyClass(code)}`;
        if (link) element.href = `/parties/${encodeURIComponent(code)}`;
        const dot = document.createElement('span');
        dot.className = 'party-dot';
        dot.setAttribute('aria-hidden', 'true');
        const label = document.createElement('span');
        label.textContent = code === 'UNKNOWN' ? name : `${code} · ${name}`;
        label.dir = 'auto';
        element.append(dot, label);
        return element;
    }

    function initials(name) {
        const cleaned = String(name || '').trim();
        if (!cleaned) return '?';
        const arabic = cleaned.match(/[\u0600-\u06FF]+/g);
        if (arabic && arabic.length) return arabic[0].slice(0, 2);
        const words = cleaned.split(/\s+/);
        return (words[0][0] + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase();
    }

    function formatTime(value) {
        const total = Number.isFinite(Number(value)) ? Math.max(0, Math.floor(Number(value))) : 0;
        const hours = Math.floor(total / 3600);
        const minutes = Math.floor((total % 3600) / 60);
        const seconds = total % 60;
        if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
        return `${minutes}:${String(seconds).padStart(2, '0')}`;
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    window.FhemniPeople = {
        t, locale, partyDisplayName, personPartyName, personDisplayName, partyClass,
        partyBadge, initials, formatTime, escapeHtml
    };
})();
