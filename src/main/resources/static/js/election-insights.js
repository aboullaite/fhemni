(function (root, factory) {
    const insights = factory();
    if (typeof module === 'object' && module.exports) module.exports = insights;
    else root.FhemniElectionInsights = insights;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
    const LOCAL_TOTAL = 4900377;
    const REGIONAL_TOTAL = 4838149;
    const COMBINED_TOTAL = 9738526;
    const SEAT_TOTAL = 395;
    const CANONICAL_CODES = new Set([
        'PAM', 'PI', 'PJD', 'RNI', 'MP', 'USFP', 'PPS', 'UC', 'MDS', 'FGD',
        'FFD', 'ND', 'PE', 'PML', 'PUD', 'PEDD', 'UMD', 'PDN', 'PLJS', 'ALAMAL',
        'PCI', 'PVM', 'PCS', 'PA', 'ANNAHDA', 'PSD', 'PRD', 'IND'
    ]);

    function integer(value) {
        return Number.isSafeInteger(value) && value >= 0;
    }

    function codeOrder(left, right) {
        return left.code < right.code ? -1 : left.code > right.code ? 1 : 0;
    }

    function addDiagnostic(diagnostics, code) {
        if (!diagnostics.includes(code)) diagnostics.push(code);
    }

    function auditSnapshot(snapshot) {
        const diagnostics = [];
        const parties = Array.isArray(snapshot?.parties) ? snapshot.parties : [];
        const codes = new Set();
        let localTotal = 0;
        let regionalTotal = 0;
        let combinedTotal = 0;
        let seatTotal = 0;
        let completeBallots = true;

        if (!['FINAL', 'CORRECTED'].includes(snapshot?.election?.status)) {
            addDiagnostic(diagnostics, 'election-status');
        }
        if (parties.length !== 28) addDiagnostic(diagnostics, 'party-count');

        for (const party of parties) {
            if (!party || !CANONICAL_CODES.has(party.code) || codes.has(party.code)) {
                addDiagnostic(diagnostics, 'canonical-code');
            }
            if (party?.code) codes.add(party.code);

            const values = [party?.localVotes, party?.regionalVotes, party?.votes];
            if (values.some(value => value == null)) {
                addDiagnostic(diagnostics, 'missing-component');
                completeBallots = false;
                continue;
            }
            if (values.some(value => !integer(value))) {
                addDiagnostic(diagnostics, 'invalid-component');
                completeBallots = false;
                continue;
            }
            localTotal += party.localVotes;
            regionalTotal += party.regionalVotes;
            combinedTotal += party.votes;
            if (party.votes !== party.localVotes + party.regionalVotes) {
                addDiagnostic(diagnostics, 'row-total');
            }
            if (party.code === 'IND' && party.regionalVotes !== 0) {
                addDiagnostic(diagnostics, 'ind-regional-zero');
            }
            if (integer(party.totalSeats)) seatTotal += party.totalSeats;
        }

        if (codes.size !== 28 || [...CANONICAL_CODES].some(code => !codes.has(code))) {
            addDiagnostic(diagnostics, 'canonical-code');
        }
        if (completeBallots) {
            if (localTotal !== LOCAL_TOTAL) addDiagnostic(diagnostics, 'local-total');
            if (regionalTotal !== REGIONAL_TOTAL) addDiagnostic(diagnostics, 'regional-total');
            if (combinedTotal !== COMBINED_TOTAL) addDiagnostic(diagnostics, 'combined-total');
        }

        return {
            discoverable: true,
            available: diagnostics.length === 0,
            diagnostics,
            partyCount: parties.length,
            localTotal: completeBallots ? localTotal : null,
            regionalTotal: completeBallots ? regionalTotal : null,
            combinedTotal: completeBallots ? combinedTotal : null,
            seatTotal
        };
    }

    function unavailable(audit) {
        return { available: false, diagnostics: [...audit.diagnostics], rows: [], totalRows: 0 };
    }

    function ordered(rows, field) {
        return [...rows].sort((left, right) => right[field] - left[field] || codeOrder(left, right));
    }

    function visibleRows(rows, options) {
        const query = String(options.query || '').trim().toLowerCase();
        const filtered = query
            ? rows.filter(row => row.code.toLowerCase().includes(query) || String(row.name || '').toLowerCase().includes(query))
            : rows;
        return options.expanded || query ? filtered : filtered.slice(0, 10);
    }

    function deriveBallotComponents(snapshot, options = {}) {
        const audit = auditSnapshot(snapshot);
        if (!audit.available) return unavailable(audit);

        const field = { local: 'localVotes', regional: 'regionalVotes' }[options.order] || 'votes';
        const allRows = ordered(snapshot.parties.map(party => ({
            code: party.code,
            name: party.name,
            color: party.color,
            symbolAsset: party.symbolAsset,
            localVotes: party.localVotes,
            regionalVotes: party.regionalVotes,
            votes: party.votes,
            localShare: 100 * party.localVotes / LOCAL_TOTAL,
            regionalShare: 100 * party.regionalVotes / REGIONAL_TOTAL
        })), field);
        const rni = snapshot.parties.find(party => party.code === 'RNI');
        const pjd = snapshot.parties.find(party => party.code === 'PJD');
        return {
            available: true,
            diagnostics: [],
            localTotal: LOCAL_TOTAL,
            regionalTotal: REGIONAL_TOTAL,
            combinedTotal: COMBINED_TOTAL,
            totalRows: allRows.length,
            rows: visibleRows(allRows, options),
            reversal: {
                rniLocalVotes: rni.localVotes,
                pjdLocalVotes: pjd.localVotes,
                rniLocalLead: rni.localVotes - pjd.localVotes,
                pjdRegionalVotes: pjd.regionalVotes,
                rniRegionalVotes: rni.regionalVotes,
                pjdRegionalLead: pjd.regionalVotes - rni.regionalVotes,
                pjdCombinedLead: pjd.votes - rni.votes
            }
        };
    }

    function auditSeats(snapshot, audit) {
        const diagnostics = [...audit.diagnostics];
        if (!audit.available) return diagnostics;
        let total = 0;
        for (const party of snapshot.parties) {
            const values = [party.localSeats, party.regionalListSeats, party.totalSeats];
            if (values.some(value => value == null)) {
                addDiagnostic(diagnostics, 'missing-seat');
                continue;
            }
            if (values.some(value => !integer(value))) {
                addDiagnostic(diagnostics, 'invalid-seat');
                continue;
            }
            if (party.totalSeats !== party.localSeats + party.regionalListSeats) {
                addDiagnostic(diagnostics, 'seat-row-total');
            }
            total += party.totalSeats;
        }
        if (total !== SEAT_TOTAL || snapshot.election.totalSeats !== SEAT_TOTAL
                || snapshot.election.declaredSeats !== SEAT_TOTAL) {
            addDiagnostic(diagnostics, 'seat-total');
        }
        return diagnostics;
    }

    function sharedRanks(rows, field) {
        const ranks = new Map();
        ordered(rows, field).forEach((row, index, ranked) => {
            ranks.set(row.code, index && row[field] === ranked[index - 1][field]
                ? ranks.get(ranked[index - 1].code) : index + 1);
        });
        return ranks;
    }

    function deriveBallotSeatComparison(snapshot, options = {}) {
        const audit = auditSnapshot(snapshot);
        const diagnostics = auditSeats(snapshot, audit);
        if (diagnostics.length) return unavailable({ diagnostics });

        const ballotRanks = sharedRanks(snapshot.parties, 'votes');
        const seatRanks = sharedRanks(snapshot.parties, 'totalSeats');
        const field = options.order === 'seats' ? 'totalSeats' : 'votes';
        const allRows = ordered(snapshot.parties.map(party => {
            const ballotShare = 100 * party.votes / COMBINED_TOTAL;
            const seatShare = 100 * party.totalSeats / SEAT_TOTAL;
            return {
                code: party.code,
                name: party.name,
                color: party.color,
                symbolAsset: party.symbolAsset,
                votes: party.votes,
                totalSeats: party.totalSeats,
                localSeats: party.localSeats,
                regionalListSeats: party.regionalListSeats,
                ballotShare,
                seatShare,
                difference: seatShare - ballotShare,
                ballotRank: ballotRanks.get(party.code),
                seatRank: seatRanks.get(party.code)
            };
        }), field);
        return {
            available: true,
            diagnostics: [],
            combinedTotal: COMBINED_TOTAL,
            seatTotal: SEAT_TOTAL,
            totalRows: allRows.length,
            rows: visibleRows(allRows, options)
        };
    }

    function concentrationGroup(parties) {
        const ballots = parties.reduce((total, party) => total + party.votes, 0);
        const seats = parties.reduce((total, party) => total + party.totalSeats, 0);
        return {
            codes: parties.map(party => party.code),
            counts: { ballots, seats },
            ballotShare: 100 * ballots / COMBINED_TOTAL,
            seatShare: 100 * seats / SEAT_TOTAL
        };
    }

    function deriveConcentration(snapshot) {
        const audit = auditSnapshot(snapshot);
        const diagnostics = auditSeats(snapshot, audit);
        if (diagnostics.length) return { available: false, diagnostics };

        const ballotOrder = ordered(snapshot.parties, 'votes');
        return {
            available: true,
            diagnostics: [],
            combinedTotal: COMBINED_TOTAL,
            seatTotal: SEAT_TOTAL,
            topFour: concentrationGroup(ballotOrder.slice(0, 4)),
            topTen: concentrationGroup(ballotOrder.slice(0, 10)),
            remaining: concentrationGroup(ballotOrder.slice(10)),
            zeroSeat: concentrationGroup(ballotOrder.filter(party => party.totalSeats === 0))
        };
    }

    return { auditSnapshot, deriveBallotComponents, deriveBallotSeatComparison, deriveConcentration };
});
