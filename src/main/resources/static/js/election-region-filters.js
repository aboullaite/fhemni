(function (root, factory) {
    const filters = factory();
    if (typeof module === 'object' && module.exports) module.exports = filters;
    else root.FhemniElectionRegionFilters = filters;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
    const SEAT_TYPES = Object.freeze({ ALL: 'ALL', LOCAL: 'LOCAL', REGIONAL: 'REGIONAL' });

    function normalizeState(state = {}) {
        const seatType = Object.values(SEAT_TYPES).includes(state.seatType) ? state.seatType : SEAT_TYPES.ALL;
        return {
            partyCode: String(state.partyCode || ''),
            constituencyCode: seatType === SEAT_TYPES.REGIONAL ? '' : String(state.constituencyCode || ''),
            seatType
        };
    }

    function activeFilterCount(state) {
        const normalized = normalizeState(state);
        return Number(Boolean(normalized.partyCode))
            + Number(Boolean(normalized.constituencyCode))
            + Number(normalized.seatType !== SEAT_TYPES.ALL);
    }

    function defaultFiltersExpanded() {
        return false;
    }

    function createDeferredAction(scheduleTask = callback => queueMicrotask(callback)) {
        let pending = false;
        let pendingValue;
        let version = 0;
        return {
            defer(value) {
                version++;
                pending = true;
                pendingValue = value;
            },
            cancel() {
                version++;
                pending = false;
                pendingValue = undefined;
            },
            flush(action) {
                if (!pending) return false;
                const value = pendingValue;
                const scheduledVersion = version;
                pending = false;
                pendingValue = undefined;
                scheduleTask(() => {
                    if (scheduledVersion === version) action(value);
                });
                return true;
            }
        };
    }

    function createLiveRegionAnnouncer(status, scheduleTask = callback => queueMicrotask(callback)) {
        let version = 0;
        return {
            announce(message, options = {}) {
                const announcementVersion = ++version;
                if (options.repeat && status.textContent === message) {
                    status.textContent = '';
                    scheduleTask(() => {
                        if (announcementVersion === version) status.textContent = message;
                    });
                    return;
                }
                status.textContent = message;
            }
        };
    }

    function regionFilterOptions(region) {
        const parties = (region?.parties || []).map(party => ({ code: party.code, name: party.name }));
        const constituencies = [];
        const seen = new Set();
        (region?.parties || []).forEach(party => (party.winners || []).forEach(winner => {
            if (!winner.constituencyCode || seen.has(winner.constituencyCode)) return;
            seen.add(winner.constituencyCode);
            constituencies.push({ code: winner.constituencyCode, name: winner.constituencyName });
        }));
        return { parties, constituencies };
    }

    function numericSeatCount(party, seatType) {
        if (seatType === SEAT_TYPES.LOCAL) return Number(party.localSeats || 0);
        if (seatType === SEAT_TYPES.REGIONAL) return Number(party.regionalListSeats || 0);
        return Number(party.totalSeats || 0);
    }

    function visibleNames(party, seatType, constituencyCode) {
        let visibleWinners = seatType === SEAT_TYPES.REGIONAL ? [] : [...(party.winners || [])];
        let visibleRegionalListWinners = seatType === SEAT_TYPES.LOCAL ? [] : [...(party.regionalListWinners || [])];
        if (constituencyCode) {
            visibleWinners = visibleWinners.filter(winner => winner.constituencyCode === constituencyCode);
            visibleRegionalListWinners = [];
        }
        return { visibleWinners, visibleRegionalListWinners };
    }

    function filterRegion(region, state = {}) {
        const normalized = normalizeState(state);
        const countKind = 'seats';
        const parties = (region?.parties || []).flatMap(party => {
            if (normalized.partyCode && party.code !== normalized.partyCode) return [];
            const names = visibleNames(party, normalized.seatType, normalized.constituencyCode);
            const publishedNameCount = names.visibleWinners.length + names.visibleRegionalListWinners.length;
            const numericCount = numericSeatCount(party, normalized.seatType);
            const displayCount = normalized.constituencyCode ? publishedNameCount : numericCount;
            if (displayCount <= 0) return [];
            return [{
                ...party,
                ...names,
                displayCount,
                missingNameCount: normalized.constituencyCode ? 0 : Math.max(0, numericCount - publishedNameCount)
            }];
        });
        return {
            state: normalized,
            parties,
            totalCount: parties.reduce((sum, party) => sum + party.displayCount, 0),
            countKind
        };
    }

    return { SEAT_TYPES, activeFilterCount, createDeferredAction, createLiveRegionAnnouncer, defaultFiltersExpanded, filterRegion, normalizeState, regionFilterOptions };
});
