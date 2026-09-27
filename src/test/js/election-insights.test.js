const test = require('node:test');
const assert = require('node:assert/strict');

const {
    auditSnapshot,
    deriveBallotComponents,
    deriveBallotSeatComparison,
    deriveConcentration,
    buildRegionMatrix,
    deriveRegionDelegation,
    derivePartyGeography,
    deriveConstituencyDistribution,
    indexRepresentatives
} = require('../../main/resources/static/js/election-insights.js');

// [code, local ballots, regional-list ballots, combined ballots, local seats, regional-list seats, all seats]
const APPROVED_ROWS = [
    ['PAM', 1043781, 1065371, 2109152, 85, 12, 97],
    ['PI', 751672, 753944, 1505616, 52, 13, 65],
    ['PJD', 696031, 730315, 1426346, 44, 10, 54],
    ['RNI', 718630, 640468, 1359098, 57, 9, 66],
    ['MP', 356172, 346299, 702471, 20, 9, 29],
    ['USFP', 327741, 330284, 658025, 15, 11, 26],
    ['PPS', 247154, 241141, 488295, 10, 9, 19],
    ['UC', 225121, 219599, 444720, 13, 4, 17],
    ['MDS', 170472, 177861, 348333, 4, 4, 8],
    ['FGD', 134379, 136982, 271361, 3, 5, 8],
    ['FFD', 45988, 45158, 91146, 0, 2, 2],
    ['ND', 28882, 24161, 53043, 0, 1, 1],
    ['PE', 21551, 23405, 44956, 1, 1, 2],
    ['PML', 18816, 16451, 35267, 0, 0, 0],
    ['PUD', 24731, 4983, 29714, 1, 0, 1],
    ['PEDD', 15349, 14148, 29497, 0, 0, 0],
    ['UMD', 12866, 12615, 25481, 0, 0, 0],
    ['PDN', 12932, 11957, 24889, 0, 0, 0],
    ['PLJS', 6872, 7910, 14782, 0, 0, 0],
    ['ALAMAL', 7267, 6236, 13503, 0, 0, 0],
    ['PCI', 6347, 6301, 12648, 0, 0, 0],
    ['PVM', 6510, 4947, 11457, 0, 0, 0],
    ['PCS', 5412, 5910, 11322, 0, 0, 0],
    ['PA', 3213, 5319, 8532, 0, 0, 0],
    ['ANNAHDA', 5241, 2888, 8129, 0, 0, 0],
    ['PSD', 3544, 1827, 5371, 0, 0, 0],
    ['PRD', 2529, 1669, 4198, 0, 0, 0],
    ['IND', 1174, 0, 1174, 0, 0, 0]
];

function snapshot(rows = APPROVED_ROWS, status = 'FINAL') {
    return {
        election: { status, totalSeats: 395, declaredSeats: 395 },
        parties: rows.map(([code, localVotes, regionalVotes, votes, localSeats, regionalListSeats, totalSeats]) => ({
            code, name: code, localVotes, regionalVotes, votes, localSeats, regionalListSeats, totalSeats
        }))
    };
}

function completeAtlasSnapshot() {
    const input = snapshot();
    const regionCodes = Array.from({ length: 12 }, (_, index) => `R${String(index + 1).padStart(2, '0')}`);
    const groups = regionCodes.map(code => ({ code, name: code, status: 'FINAL', allocatedSeats: 0, declaredSeats: 0, parties: [] }));
    const byRegionParty = new Map();
    const getGroup = (regionIndex, partyCode) => {
        const key = `${regionIndex}:${partyCode}`;
        if (!byRegionParty.has(key)) {
            const party = input.parties.find(row => row.code === partyCode);
            const group = { code: partyCode, name: party.name, localSeats: 0, regionalListSeats: 0,
                totalSeats: 0, winners: [], regionalListWinners: [] };
            byRegionParty.set(key, group);
            groups[regionIndex].parties.push(group);
        }
        return byRegionParty.get(key);
    };
    const localCodes = input.parties.flatMap(party => Array(party.localSeats).fill(party.code));
    const regionalCodes = input.parties.flatMap(party => Array(party.regionalListSeats).fill(party.code));
    const allocations = [
        ...Array(21).fill(2), ...Array(38).fill(3), ...Array(22).fill(4),
        ...Array(5).fill(5), ...Array(6).fill(6)
    ];
    let localIndex = 0;
    allocations.forEach((allocatedSeats, constituencyIndex) => {
        const regionIndex = constituencyIndex % 12;
        const constituencyCode = `C${String(constituencyIndex + 1).padStart(3, '0')}`;
        for (let seat = 0; seat < allocatedSeats; seat++) {
            const group = getGroup(regionIndex, localCodes[localIndex]);
            group.localSeats++;
            group.totalSeats++;
            group.winners.push({ constituencyCode, constituencyName: constituencyCode,
                candidateKey: `local-${localIndex + 1}`,
                candidateName: `Local Candidate ${localIndex + 1}`,
                votes: localIndex === 0 ? null : localIndex === 1 ? 0 : localIndex + 100,
                allocatedSeats, status: 'FINAL' });
            localIndex++;
        }
    });
    regionalCodes.forEach((partyCode, regionalIndex) => {
        const regionIndex = regionalIndex % 12;
        const group = getGroup(regionIndex, partyCode);
        group.regionalListSeats++;
        group.totalSeats++;
        group.regionalListWinners.push({ candidateKey: `regional-${regionalIndex + 1}`,
            candidateName: `Regional Candidate ${regionalIndex + 1}`, status: 'FINAL' });
    });
    for (const region of groups) {
        region.parties.sort((a, b) => a.code.localeCompare(b.code));
        region.allocatedSeats = region.parties.reduce((sum, party) => sum + party.totalSeats, 0);
        region.declaredSeats = region.allocatedSeats;
    }
    input.regions = groups;
    return input;
}

test('complete approved 28-row ballot fixture is available with exact raw denominators', () => {
    const audit = auditSnapshot(snapshot());
    assert.equal(audit.available, true);
    assert.deepEqual(audit.diagnostics, []);
    assert.deepEqual([audit.partyCount, audit.localTotal, audit.regionalTotal, audit.combinedTotal, audit.seatTotal],
        [28, 4900377, 4838149, 9738526, 395]);
});

test('a 27-row snapshot is unavailable', () => {
    const result = auditSnapshot(snapshot(APPROVED_ROWS.slice(0, -1)));
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('party-count'));
});

test('one missing component is unavailable rather than treated as zero', () => {
    const input = snapshot();
    input.parties[27].regionalVotes = null;
    const result = auditSnapshot(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('missing-component'));
});

test('one negative component is unavailable', () => {
    const input = snapshot();
    input.parties[27].regionalVotes = -1;
    const result = auditSnapshot(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('invalid-component'));
});

test('one row-level sum mismatch is unavailable', () => {
    const input = snapshot();
    input.parties[0].votes++;
    const result = auditSnapshot(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('row-total'));
});

test('one national denominator mismatch is unavailable even when rows reconcile', () => {
    const input = snapshot();
    input.parties[0].localVotes++;
    input.parties[0].votes++;
    const result = auditSnapshot(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('local-total'));
    assert.ok(result.diagnostics.includes('combined-total'));
});

test('nonfinal elections keep the Atlas discoverable but all ballot projections unavailable', () => {
    for (const status of ['SCHEDULED', 'COUNTING', 'PRELIMINARY']) {
        const input = snapshot(APPROVED_ROWS, status);
        const audit = auditSnapshot(input);
        assert.equal(audit.discoverable, true);
        assert.equal(audit.available, false);
        assert.ok(audit.diagnostics.includes('election-status'));
        assert.equal(deriveBallotComponents(input).available, false);
        assert.equal(deriveBallotSeatComparison(input).available, false);
        assert.equal(deriveConcentration(input).available, false);
    }
    assert.equal(auditSnapshot(snapshot(APPROVED_ROWS, 'CORRECTED')).available, true);
});

test('source PND code is unavailable in place of canonical ND', () => {
    const input = snapshot();
    input.parties.find(party => party.code === 'ND').code = 'PND';
    const result = auditSnapshot(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('canonical-code'));
});

test('ND and PDN remain separate entries', () => {
    const result = deriveBallotComponents(snapshot(), { expanded: true });
    assert.deepEqual(result.rows.filter(row => row.code === 'ND' || row.code === 'PDN')
        .map(row => [row.code, row.votes]), [['ND', 53043], ['PDN', 24889]]);
});

test('IND has a real zero regional ballot count and remains available', () => {
    const result = deriveBallotComponents(snapshot(), { expanded: true });
    const ind = result.rows.find(row => row.code === 'IND');
    assert.equal(result.available, true);
    assert.equal(ind.regionalVotes, 0);
    assert.equal(ind.regionalShare, 0);
});

test('IND regional ballots must remain zero even if all national totals reconcile', () => {
    const input = snapshot();
    const ind = input.parties.find(party => party.code === 'IND');
    const pam = input.parties.find(party => party.code === 'PAM');
    ind.regionalVotes++;
    ind.votes++;
    pam.regionalVotes--;
    pam.votes--;
    const result = auditSnapshot(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('ind-regional-zero'));
});

test('audits and projections leave the input snapshot untouched', () => {
    const input = snapshot();
    const original = structuredClone(input);
    for (const party of input.parties) Object.freeze(party);
    Object.freeze(input.parties);
    Object.freeze(input.election);
    Object.freeze(input);
    auditSnapshot(input);
    deriveBallotComponents(input, { order: 'regional', expanded: true });
    deriveBallotSeatComparison(input, { order: 'seats', expanded: true });
    deriveConcentration(input);
    assert.deepEqual(input, original);
});

test('local and regional ballot shares use their separate denominators', () => {
    const result = deriveBallotComponents(snapshot());
    const pam = result.rows[0];
    assert.equal(result.localTotal, 4900377);
    assert.equal(result.regionalTotal, 4838149);
    assert.equal(pam.localShare, 100 * 1043781 / 4900377);
    assert.equal(pam.regionalShare, 100 * 1065371 / 4838149);
});

test('initial ballot rows are the leading ten by combined ballots', () => {
    const result = deriveBallotComponents(snapshot());
    assert.deepEqual(result.rows.map(row => row.code),
        ['PAM', 'PI', 'PJD', 'RNI', 'MP', 'USFP', 'PPS', 'UC', 'MDS', 'FGD']);
    assert.equal(result.totalRows, 28);
});

test('ballot sort modes use raw local or regional counts with code tie-breaks', () => {
    const input = snapshot();
    assert.deepEqual(deriveBallotComponents(input, { order: 'local' }).rows.slice(0, 4).map(row => row.code),
        ['PAM', 'PI', 'RNI', 'PJD']);
    assert.deepEqual(deriveBallotComponents(input, { order: 'regional' }).rows.slice(0, 4).map(row => row.code),
        ['PAM', 'PI', 'PJD', 'RNI']);
    const localTie = snapshot([...APPROVED_ROWS].reverse());
    const pml = localTie.parties.find(party => party.code === 'PML');
    const umd = localTie.parties.find(party => party.code === 'UMD');
    pml.localVotes = umd.localVotes = 15841;
    pml.regionalVotes = 19426;
    umd.regionalVotes = 9640;
    assert.equal(auditSnapshot(localTie).available, true);
    const localCodes = deriveBallotComponents(localTie, { order: 'local', expanded: true }).rows.map(row => row.code);
    assert.ok(localCodes.indexOf('PML') < localCodes.indexOf('UMD'));
    const regionalTie = snapshot([...APPROVED_ROWS].reverse());
    const regionalPml = regionalTie.parties.find(party => party.code === 'PML');
    const pdn = regionalTie.parties.find(party => party.code === 'PDN');
    regionalPml.regionalVotes = pdn.regionalVotes = 14204;
    regionalPml.localVotes = 21063;
    pdn.localVotes = 10685;
    assert.equal(auditSnapshot(regionalTie).available, true);
    const regionalCodes = deriveBallotComponents(regionalTie, { order: 'regional', expanded: true }).rows.map(row => row.code);
    assert.ok(regionalCodes.indexOf('PDN') < regionalCodes.indexOf('PML'));
});

test('RNI/PJD reversal exposes unrounded ballot inputs and three literal differences', () => {
    const reversal = deriveBallotComponents(snapshot()).reversal;
    assert.deepEqual(reversal, {
        rniLocalVotes: 718630,
        pjdLocalVotes: 696031,
        rniLocalLead: 22599,
        pjdRegionalVotes: 730315,
        rniRegionalVotes: 640468,
        pjdRegionalLead: 89847,
        pjdCombinedLead: 67248
    });
});

test('comparison rows expose raw ballot and seat shares and neutral difference', () => {
    const result = deriveBallotSeatComparison(snapshot());
    const pam = result.rows[0];
    assert.deepEqual([pam.code, pam.votes, pam.totalSeats, pam.localSeats, pam.regionalListSeats],
        ['PAM', 2109152, 97, 85, 12]);
    assert.equal(pam.ballotShare, 100 * 2109152 / 9738526);
    assert.equal(pam.seatShare, 100 * 97 / 395);
    assert.equal(pam.difference, 100 * 97 / 395 - 100 * 2109152 / 9738526);
    assert.equal(result.rows.length, 10);
});

test('USFP retains its seat and ballot mark ordering from raw values', () => {
    const usfp = deriveBallotSeatComparison(snapshot()).rows.find(row => row.code === 'USFP');
    assert.equal(usfp.votes, 658025);
    assert.equal(usfp.totalSeats, 26);
    assert.ok(usfp.seatShare < usfp.ballotShare);
    assert.ok(usfp.difference < 0);
});

test('tied seat totals have a shared rank and code-order tie break', () => {
    const result = deriveBallotSeatComparison(snapshot(), { order: 'seats', expanded: true });
    assert.deepEqual(result.rows.slice(0, 4).map(row => [row.code, row.seatRank]),
        [['PAM', 1], ['RNI', 2], ['PI', 3], ['PJD', 4]]);
    assert.deepEqual(result.rows.filter(row => row.totalSeats === 8).map(row => [row.code, row.seatRank]),
        [['FGD', 9], ['MDS', 9]]);
    assert.deepEqual(result.rows.filter(row => row.totalSeats === 2).map(row => [row.code, row.seatRank]),
        [['FFD', 11], ['PE', 11]]);
});

test('expanded comparison includes every zero-seat entry', () => {
    const result = deriveBallotSeatComparison(snapshot(), { expanded: true });
    assert.equal(result.rows.length, 28);
    assert.equal(result.rows.filter(row => row.totalSeats === 0).length, 14);
    assert.equal(result.rows.at(-1).code, 'IND');
});

test('seat projections reject a 304/91 aggregate split even when all row and chamber totals reconcile', () => {
    const input = snapshot();
    input.parties[0].localSeats--;
    input.parties[0].regionalListSeats++;

    const comparison = deriveBallotSeatComparison(input);
    const concentration = deriveConcentration(input);
    assert.equal(comparison.available, false);
    assert.ok(comparison.diagnostics.includes('local-seat-total'));
    assert.ok(comparison.diagnostics.includes('regional-seat-total'));
    assert.equal(concentration.available, false);
    assert.ok(concentration.diagnostics.includes('local-seat-total'));
    assert.ok(concentration.diagnostics.includes('regional-seat-total'));
});

test('concentration groups keep ballot-order membership for ballots and seats', () => {
    const result = deriveConcentration(snapshot());
    assert.equal(result.available, true);
    assert.deepEqual(result.topFour.codes, ['PAM', 'PI', 'PJD', 'RNI']);
    assert.deepEqual(result.topFour.counts, { ballots: 6400212, seats: 282 });
    assert.deepEqual(result.topTen.counts, { ballots: 9313417, seats: 389 });
    assert.deepEqual(result.remaining.counts, { ballots: 425109, seats: 6 });
    assert.deepEqual(result.zeroSeat.counts, { ballots: 206250, seats: 0 });
    assert.equal(result.topFour.ballotShare, 100 * 6400212 / 9738526);
    assert.equal(result.topFour.seatShare, 100 * 282 / 395);
    assert.equal(result.topTen.ballotShare, 100 * 9313417 / 9738526);
    assert.equal(result.topTen.seatShare, 100 * 389 / 395);
    assert.equal(result.remaining.ballotShare, 100 * 425109 / 9738526);
    assert.equal(result.remaining.seatShare, 100 * 6 / 395);
    assert.equal(result.zeroSeat.ballotShare, 100 * 206250 / 9738526);
});

test('the complete regional matrix has 12 by 14 seats including real zero cells', () => {
    const result = buildRegionMatrix(completeAtlasSnapshot());
    assert.equal(result.available, true);
    assert.equal(result.rows.length, 12);
    assert.equal(result.partyCodes.length, 14);
    assert.equal(result.totalSeats, 395);
    assert.equal(result.maxSeats, 9);
    assert.ok(result.rows.every(row => row.cells.length === 14));
    assert.ok(result.rows.flatMap(row => row.cells).some(cell => cell.seats === 0));
    assert.ok(result.rows.flatMap(row => row.cells).every(cell => cell.maxSeats === result.maxSeats));
    assert.equal(result.rows.reduce((sum, row) => sum + row.totalSeats, 0), 395);
});

test('region and party projections use complete allocations and preserve zero regions', () => {
    const input = completeAtlasSnapshot();
    const firstRegion = input.regions[0];
    const delegation = deriveRegionDelegation(input, firstRegion.code);
    const pamInRegion = firstRegion.parties.find(party => party.code === 'PAM');
    assert.equal(delegation.available, true);
    assert.equal(delegation.delegationSeats, firstRegion.allocatedSeats);
    assert.equal(delegation.representedPartyCount, firstRegion.parties.length);
    assert.equal(delegation.rows.find(row => row.code === 'PAM').share,
        100 * pamInRegion.totalSeats / firstRegion.allocatedSeats);
    assert.deepEqual(delegation.largestPartyCodes, ['PAM', 'PI']);

    const geography = derivePartyGeography(input, 'PE');
    assert.equal(geography.available, true);
    assert.equal(geography.rows.length, 12);
    assert.ok(geography.rows.some(row => row.seats === 0));
    assert.equal(geography.representedRegionCount, geography.rows.filter(row => row.seats > 0).length);
    assert.equal(geography.totalSeats, 2);
    assert.equal(geography.localSeats, 1);
    assert.equal(geography.regionalListSeats, 1);
    assert.equal(geography.constituencyBreadth, 1);
    assert.equal(geography.rows.reduce((sum, row) => sum + row.seats, 0), 2);
    assert.ok(geography.rows.every(row => row.share === 100 * row.seats / 2));
});

test('regional projections fail closed when the 395-seat matrix is incomplete', () => {
    const missing = completeAtlasSnapshot();
    missing.regions.pop();
    assert.equal(buildRegionMatrix(missing).available, false);
    assert.equal(deriveRegionDelegation(missing, 'R01').available, false);
    assert.equal(derivePartyGeography(missing, 'PAM').available, false);

    const inconsistent = completeAtlasSnapshot();
    inconsistent.regions[0].parties[0].totalSeats++;
    assert.equal(buildRegionMatrix(inconsistent).available, false);

    const mismatchedComponents = completeAtlasSnapshot();
    const pamRegion = mismatchedComponents.regions[0].parties.find(party => party.code === 'PAM');
    pamRegion.localSeats++;
    pamRegion.regionalListSeats--;
    assert.equal(buildRegionMatrix(mismatchedComponents).available, false);
});

test('regional matrix rejects a reconciled 304/91 aggregate split', () => {
    const input = completeAtlasSnapshot();
    const nationalPam = input.parties.find(party => party.code === 'PAM');
    const regionalPam = input.regions
        .flatMap(region => region.parties)
        .find(party => party.code === 'PAM' && party.localSeats > 0);
    nationalPam.localSeats--;
    nationalPam.regionalListSeats++;
    regionalPam.localSeats--;
    regionalPam.regionalListSeats++;

    const result = buildRegionMatrix(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('local-seat-total'));
    assert.ok(result.diagnostics.includes('regional-seat-total'));
});

test('party geography is unavailable if local constituency winners are incomplete', () => {
    const input = completeAtlasSnapshot();
    input.regions[0].parties.find(party => party.winners.length).winners.pop();
    assert.equal(derivePartyGeography(input, 'PAM').available, false);
});

test('party geography rejects a winner moved to another party while seat totals stay balanced', () => {
    const input = completeAtlasSnapshot();
    const region = input.regions[0];
    const donor = region.parties.find(party => party.winners.length > 0);
    const receiver = region.parties.find(party => party !== donor && party.winners.length > 0);
    receiver.winners.push(donor.winners.pop());
    assert.equal(buildRegionMatrix(input).available, true);
    const result = derivePartyGeography(input, donor.code);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('winner-seat-total'));
});

test('constituency distribution counts each identifier once across 305 local winners', () => {
    const result = deriveConstituencyDistribution(completeAtlasSnapshot());
    assert.equal(result.available, true);
    assert.equal(result.constituencyCount, 92);
    assert.equal(result.localSeats, 305);
    assert.deepEqual(result.bins.map(({ seats, constituencies }) => ({ seats, constituencies })), [
        { seats: 2, constituencies: 21 },
        { seats: 3, constituencies: 38 },
        { seats: 4, constituencies: 22 },
        { seats: 5, constituencies: 5 },
        { seats: 6, constituencies: 6 }
    ]);
    assert.equal(result.bins[0].items.length, 21);
    assert.equal(result.bins[0].items[0].code, 'C001');
});

test('constituency distribution rejects missing and contradictory allocations', () => {
    const missing = completeAtlasSnapshot();
    missing.regions[0].parties.find(party => party.winners.length).winners[0].allocatedSeats = null;
    assert.equal(deriveConstituencyDistribution(missing).available, false);

    const contradictory = completeAtlasSnapshot();
    contradictory.regions[0].parties.find(party => party.winners.length).winners[0].allocatedSeats = 3;
    assert.equal(deriveConstituencyDistribution(contradictory).available, false);
});

test('constituency distribution rejects balanced but incorrect seat-size bins', () => {
    const input = completeAtlasSnapshot();
    const winners = input.regions.flatMap(region => region.parties.flatMap(party => party.winners));
    const twoSeat = winners.filter(winner => winner.constituencyCode === 'C001');
    const fourSeat = winners.filter(winner => winner.constituencyCode === 'C061');
    assert.equal(twoSeat.length, 2);
    assert.equal(fourSeat.length, 4);
    fourSeat[0].constituencyCode = 'C001';
    [...twoSeat, ...fourSeat].forEach(winner => { winner.allocatedSeats = 3; });
    const result = deriveConstituencyDistribution(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('constituency-bin-count'));
});

test('representative index retains 305 local and 90 regional identities and vote unknowns', () => {
    const result = indexRepresentatives(completeAtlasSnapshot());
    assert.equal(result.available, true);
    assert.equal(result.totalRecords, 395);
    assert.equal(result.localCount, 305);
    assert.equal(result.regionalCount, 90);
    assert.equal(result.records.filter(row => row.seatType === 'LOCAL').length, 305);
    assert.equal(result.records.filter(row => row.seatType === 'REGIONAL').length, 90);
    const local = result.records.find(row => row.candidateName === 'Local Candidate 1');
    const zero = result.records.find(row => row.candidateName === 'Local Candidate 2');
    const regional = result.records.find(row => row.candidateName === 'Regional Candidate 1');
    assert.deepEqual([local.regionCode, local.constituencyCode, local.votes], ['R01', 'C001', null]);
    assert.equal(zero.votes, 0);
    assert.equal(regional.regionCode, 'R01');
    assert.equal(regional.constituencyCode, null);
    assert.equal(regional.votes, null);
});

test('representative filters compose across text, geography, party, and seat type', () => {
    const input = completeAtlasSnapshot();
    const filtered = indexRepresentatives(input, {
        query: 'candidate 1', regionCode: 'R01', constituencyCode: 'C001',
        partyCode: 'PAM', seatType: 'LOCAL'
    });
    assert.equal(filtered.available, true);
    assert.deepEqual(filtered.records.map(row => row.candidateName), ['Local Candidate 1']);
    assert.equal(indexRepresentatives(input, { regionCode: 'R01', constituencyCode: 'C001',
        seatType: 'REGIONAL' }).records.length, 0);
    assert.ok(indexRepresentatives(input, { query: 'c001' }).records.length >= 2);
    assert.equal(indexRepresentatives(input, { seatType: 'ALL' }).records.length, 395);
});

test('constituency-size filter composes and retains explicit size on local records', () => {
    const input = completeAtlasSnapshot();
    const twoSeat = indexRepresentatives(input, { constituencySeats: 2 });
    assert.equal(twoSeat.available, true);
    assert.equal(twoSeat.totalRecords, 395);
    assert.equal(twoSeat.filteredCount, 42);
    assert.ok(twoSeat.records.every(row => row.seatType === 'LOCAL' && row.allocatedSeats === 2));
    assert.equal(indexRepresentatives(input, { constituencySeats: 3 }).filteredCount, 114);
    const combined = indexRepresentatives(input, { constituencySeats: 2, regionCode: 'R01',
        constituencyCode: 'C001', partyCode: 'PAM', seatType: 'LOCAL', query: 'candidate 1' });
    assert.deepEqual(combined.records.map(row => row.candidateName), ['Local Candidate 1']);
    assert.equal(indexRepresentatives(input, { constituencySeats: 2, seatType: 'REGIONAL' }).filteredCount, 0);
    assert.equal(indexRepresentatives(input, { constituencySeats: 7 }).available, false);
    assert.equal(indexRepresentatives(input).records.find(row => row.seatType === 'REGIONAL').allocatedSeats, null);
});

test('representative index rejects contradictory local allocation metadata', () => {
    const input = completeAtlasSnapshot();
    input.regions[0].parties.find(party => party.winners.length).winners[0].allocatedSeats = 3;
    assert.equal(indexRepresentatives(input).available, false);
});

test('duplicate candidate identities make the representative index unavailable', () => {
    const input = completeAtlasSnapshot();
    const firstRegional = input.regions[0].parties.find(party => party.regionalListWinners.length)
        .regionalListWinners[0];
    const secondRegional = input.regions[1].parties.find(party => party.regionalListWinners.length)
        .regionalListWinners[0];
    secondRegional.candidateKey = firstRegional.candidateKey;
    const result = indexRepresentatives(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('duplicate-candidate'));
});

test('duplicate local candidate keys across constituencies make the representative index unavailable', () => {
    const input = completeAtlasSnapshot();
    const localWinners = input.regions.flatMap(region => region.parties.flatMap(party => party.winners));
    const first = localWinners[0];
    const second = localWinners.find(winner => winner.constituencyCode !== first.constituencyCode);
    second.candidateKey = first.candidateKey;

    const result = indexRepresentatives(input);
    assert.equal(result.available, false);
    assert.ok(result.diagnostics.includes('duplicate-candidate'));
});

test('new projections do not mutate the snapshot', () => {
    const input = completeAtlasSnapshot();
    const original = structuredClone(input);
    buildRegionMatrix(input);
    deriveRegionDelegation(input, 'R01');
    derivePartyGeography(input, 'PAM');
    deriveConstituencyDistribution(input);
    indexRepresentatives(input, { query: 'candidate', regionCode: 'R01' });
    assert.deepEqual(input, original);
});
