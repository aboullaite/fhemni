const test = require('node:test');
const assert = require('node:assert/strict');

const {
    SEAT_TYPES,
    activeFilterCount,
    createDeferredAction,
    createLiveRegionAnnouncer,
    filterRegion,
    regionFilterOptions
} = require('../../main/resources/static/js/election-region-filters.js');

test('deferred region refresh never runs re-entrantly inside the current render', () => {
    const scheduled = [];
    const deferred = createDeferredAction(callback => scheduled.push(callback));
    let rendering = true;
    let ranDuringRender = false;

    deferred.defer();
    assert.equal(deferred.flush(() => { ranDuringRender = rendering; }), true);
    assert.equal(ranDuringRender, false);
    assert.equal(scheduled.length, 1);

    rendering = false;
    scheduled[0]();
    assert.equal(ranDuringRender, false);
    assert.equal(deferred.flush(() => {}), false);
});

test('cancelling a deferred region refresh invalidates an already scheduled action', () => {
    const scheduled = [];
    const deferred = createDeferredAction(callback => scheduled.push(callback));
    let ran = false;

    deferred.defer('MA-07');
    deferred.flush(() => { ran = true; });
    deferred.cancel();
    scheduled[0]();

    assert.equal(ran, false);
});

test('repeated poll summaries remount identical live-region text', () => {
    const scheduled = [];
    const status = { textContent: '' };
    const announcer = createLiveRegionAnnouncer(status, callback => scheduled.push(callback));

    announcer.announce('13 seats declared');
    announcer.announce('13 seats declared', { repeat: true });

    assert.equal(status.textContent, '');
    assert.equal(scheduled.length, 1);
    scheduled[0]();
    assert.equal(status.textContent, '13 seats declared');
});

test('a newer live-region message supersedes a queued repeated poll summary', () => {
    const scheduled = [];
    const status = { textContent: '13 seats declared' };
    const announcer = createLiveRegionAnnouncer(status, callback => scheduled.push(callback));

    announcer.announce('13 seats declared', { repeat: true });
    announcer.announce('Filters cleared');
    scheduled[0]();

    assert.equal(status.textContent, 'Filters cleared');
});

const region = {
    mapKey: 'MA-03',
    parties: [
        {
            code: 'PAM',
            name: 'PAM',
            totalSeats: 5,
            localSeats: 3,
            regionalListSeats: 2,
            winners: [
                { constituencyCode: 'TAZA', constituencyName: 'Taza', candidateName: 'Winner one' },
                { constituencyCode: 'FES-SUD', constituencyName: 'Fès-Sud', candidateName: 'Winner two' }
            ],
            regionalListWinners: []
        },
        {
            code: 'RNI',
            name: 'RNI',
            totalSeats: 3,
            localSeats: 2,
            regionalListSeats: 1,
            winners: [
                { constituencyCode: 'TAZA', constituencyName: 'Taza', candidateName: 'Winner three' }
            ],
            regionalListWinners: [
                { candidateKey: 'rni-regional-1', candidateName: 'Regional winner' }
            ]
        },
        {
            code: 'PI',
            name: 'Istiqlal',
            totalSeats: 1,
            localSeats: 1,
            regionalListSeats: 0,
            winners: [],
            regionalListWinners: []
        }
    ]
};

test('regional-list filtering keeps parties with numeric seats when winner names are missing', () => {
    const result = filterRegion(region, { seatType: SEAT_TYPES.REGIONAL });

    assert.deepEqual(result.parties.map(party => party.code), ['PAM', 'RNI']);
    assert.equal(result.parties[0].displayCount, 2);
    assert.equal(result.parties[0].missingNameCount, 2);
    assert.deepEqual(result.parties[0].visibleRegionalListWinners, []);
    assert.equal(result.totalCount, 3);
    assert.equal(result.countKind, 'seats');
});

test('constituency filtering counts only named winners, not all seats', () => {
    const result = filterRegion(region, {
        constituencyCode: 'TAZA',
        seatType: SEAT_TYPES.ALL
    });

    assert.deepEqual(result.parties.map(party => party.code), ['PAM', 'RNI']);
    assert.deepEqual(result.parties.map(party => party.displayCount), [1, 1]);
    assert.deepEqual(result.parties[0].visibleWinners.map(winner => winner.constituencyCode), ['TAZA']);
    assert.deepEqual(result.parties[0].visibleRegionalListWinners, []);
    assert.equal(result.totalCount, 2);
    assert.equal(result.countKind, 'publishedWinners');
});

test('regional-list filtering clears a constituency that no longer applies', () => {
    const result = filterRegion(region, {
        constituencyCode: 'TAZA',
        seatType: SEAT_TYPES.REGIONAL
    });

    assert.equal(result.state.constituencyCode, '');
    assert.equal(activeFilterCount(result.state), 1);
});

test('party filtering combines with seat type without changing seat totals', () => {
    const result = filterRegion(region, {
        partyCode: 'RNI',
        seatType: SEAT_TYPES.LOCAL
    });

    assert.deepEqual(result.parties.map(party => party.code), ['RNI']);
    assert.equal(result.parties[0].displayCount, 2);
    assert.equal(result.parties[0].missingNameCount, 1);
    assert.equal(result.totalCount, 2);
});

test('filter options contain each published constituency once', () => {
    const options = regionFilterOptions(region);

    assert.deepEqual(options.parties.map(party => party.code), ['PAM', 'RNI', 'PI']);
    assert.deepEqual(options.constituencies, [
        { code: 'TAZA', name: 'Taza' },
        { code: 'FES-SUD', name: 'Fès-Sud' }
    ]);
});
