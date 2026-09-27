(function (root, factory) {
    const insights = factory();
    if (typeof module === 'object' && module.exports) module.exports = insights;
    else root.FhemniElectionInsights = insights;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
    const LOCAL_TOTAL = 4900377;
    const REGIONAL_TOTAL = 4838149;
    const COMBINED_TOTAL = 9738526;
    const LOCAL_SEAT_TOTAL = 305;
    const REGIONAL_SEAT_TOTAL = 90;
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
        let localTotal = 0;
        let regionalTotal = 0;
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
            localTotal += party.localSeats;
            regionalTotal += party.regionalListSeats;
            total += party.totalSeats;
        }
        if (localTotal !== LOCAL_SEAT_TOTAL) addDiagnostic(diagnostics, 'local-seat-total');
        if (regionalTotal !== REGIONAL_SEAT_TOTAL) addDiagnostic(diagnostics, 'regional-seat-total');
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

    function seatDiagnostics(snapshot) {
        const diagnostics = [];
        const parties = Array.isArray(snapshot?.parties) ? snapshot.parties : [];
        if (!['FINAL', 'CORRECTED'].includes(snapshot?.election?.status)) addDiagnostic(diagnostics, 'election-status');
        if (parties.length !== 28) addDiagnostic(diagnostics, 'party-count');
        const codes = new Set();
        let seats = 0;
        let localSeats = 0;
        let regionalSeats = 0;
        for (const party of parties) {
            if (!party || !CANONICAL_CODES.has(party.code) || codes.has(party.code)) {
                addDiagnostic(diagnostics, 'canonical-code');
                continue;
            }
            codes.add(party.code);
            if (![party.localSeats, party.regionalListSeats, party.totalSeats].every(integer)
                    || party.localSeats + party.regionalListSeats !== party.totalSeats) {
                addDiagnostic(diagnostics, 'seat-row-total');
                continue;
            }
            localSeats += party.localSeats;
            regionalSeats += party.regionalListSeats;
            seats += party.totalSeats;
        }
        if (codes.size !== 28) addDiagnostic(diagnostics, 'canonical-code');
        if (localSeats !== LOCAL_SEAT_TOTAL) addDiagnostic(diagnostics, 'local-seat-total');
        if (regionalSeats !== REGIONAL_SEAT_TOTAL) addDiagnostic(diagnostics, 'regional-seat-total');
        if (seats !== SEAT_TOTAL || snapshot?.election?.totalSeats !== SEAT_TOTAL
                || snapshot?.election?.declaredSeats !== SEAT_TOTAL) addDiagnostic(diagnostics, 'seat-total');
        return diagnostics;
    }

    function buildRegionMatrix(snapshot) {
        const diagnostics = seatDiagnostics(snapshot);
        const regions = Array.isArray(snapshot?.regions) ? snapshot.regions : [];
        if (regions.length !== 12) addDiagnostic(diagnostics, 'region-count');
        const regionCodes = new Set();
        const represented = (snapshot?.parties || []).filter(party => integer(party?.totalSeats) && party.totalSeats > 0);
        const partyCodes = represented.map(party => party.code);
        if (represented.length !== 14) addDiagnostic(diagnostics, 'represented-party-count');
        const partyTotals = new Map(partyCodes.map(code => [code, 0]));
        const localPartyTotals = new Map(partyCodes.map(code => [code, 0]));
        const regionalPartyTotals = new Map(partyCodes.map(code => [code, 0]));
        let totalSeats = 0;
        let maxSeats = 0;
        const rows = [];
        for (const region of regions) {
            if (!region?.code || regionCodes.has(region.code)) addDiagnostic(diagnostics, 'region-code');
            else regionCodes.add(region.code);
            if (region?.status !== 'FINAL') addDiagnostic(diagnostics, 'region-status');
            if (!integer(region?.allocatedSeats) || !integer(region?.declaredSeats)) {
                addDiagnostic(diagnostics, 'region-allocation');
            }
            const entries = Array.isArray(region?.parties) ? region.parties : [];
            if (!Array.isArray(region?.parties)) addDiagnostic(diagnostics, 'region-party-rows');
            const byCode = new Map();
            let rowSeats = 0;
            for (const party of entries) {
                if (!partyCodes.includes(party?.code) || byCode.has(party.code)) {
                    addDiagnostic(diagnostics, 'region-party-code');
                    continue;
                }
                byCode.set(party.code, party);
                if (![party.localSeats, party.regionalListSeats, party.totalSeats].every(integer)
                        || party.localSeats + party.regionalListSeats !== party.totalSeats) {
                    addDiagnostic(diagnostics, 'region-party-seats');
                    continue;
                }
                rowSeats += party.totalSeats;
                partyTotals.set(party.code, partyTotals.get(party.code) + party.totalSeats);
                localPartyTotals.set(party.code, localPartyTotals.get(party.code) + party.localSeats);
                regionalPartyTotals.set(party.code, regionalPartyTotals.get(party.code) + party.regionalListSeats);
            }
            if (rowSeats !== region?.allocatedSeats || rowSeats !== region?.declaredSeats) {
                addDiagnostic(diagnostics, 'region-allocation');
            }
            const cells = partyCodes.map(code => {
                const seats = byCode.get(code)?.totalSeats ?? 0;
                if (integer(seats)) maxSeats = Math.max(maxSeats, seats);
                return { code, seats };
            });
            rows.push({ code: region?.code, name: region?.name, totalSeats: rowSeats, cells });
            totalSeats += rowSeats;
        }
        for (const party of represented) {
            if (partyTotals.get(party.code) !== party.totalSeats
                    || localPartyTotals.get(party.code) !== party.localSeats
                    || regionalPartyTotals.get(party.code) !== party.regionalListSeats) {
                addDiagnostic(diagnostics, 'party-region-total');
            }
        }
        if (totalSeats !== SEAT_TOTAL) addDiagnostic(diagnostics, 'region-total');
        if (diagnostics.length) return { available: false, diagnostics, rows: [], partyCodes: [], totalSeats: null, maxSeats: null };
        return {
            available: true,
            diagnostics: [],
            rows: rows.map(row => ({ ...row, cells: row.cells.map(cell => ({ ...cell, maxSeats })) })),
            partyCodes,
            totalSeats,
            maxSeats
        };
    }

    function deriveRegionDelegation(snapshot, regionCode) {
        const matrix = buildRegionMatrix(snapshot);
        if (!matrix.available) return { available: false, diagnostics: matrix.diagnostics, rows: [] };
        const region = matrix.rows.find(row => row.code === regionCode);
        if (!region) return { available: false, diagnostics: ['unknown-region'], rows: [] };
        const parties = new Map(snapshot.parties.map(party => [party.code, party]));
        const rows = region.cells.filter(cell => cell.seats > 0).map(cell => ({
            code: cell.code,
            name: parties.get(cell.code).name,
            seats: cell.seats,
            share: 100 * cell.seats / region.totalSeats
        })).sort((left, right) => right.seats - left.seats || codeOrder(left, right));
        return {
            available: true,
            diagnostics: [],
            regionCode,
            delegationSeats: region.totalSeats,
            representedPartyCount: rows.length,
            largestPartyCodes: rows.filter(row => row.seats === rows[0]?.seats).map(row => row.code),
            rows
        };
    }

    function derivePartyGeography(snapshot, partyCode) {
        const matrix = buildRegionMatrix(snapshot);
        if (!matrix.available) return { available: false, diagnostics: matrix.diagnostics, rows: [] };
        const representatives = indexRepresentatives(snapshot);
        if (!representatives.available) return { available: false, diagnostics: representatives.diagnostics, rows: [] };
        const party = snapshot.parties.find(row => row.code === partyCode && row.totalSeats > 0);
        if (!party) return { available: false, diagnostics: ['unknown-party'], rows: [] };
        const rows = matrix.rows.map(region => {
            const seats = region.cells.find(cell => cell.code === partyCode).seats;
            return {
                code: region.code,
                name: region.name,
                seats,
                delegationSeats: region.totalSeats,
                share: 100 * seats / party.totalSeats
            };
        });
        const constituencyCodes = new Set();
        for (const region of snapshot.regions) {
            for (const regionalParty of region.parties) {
                if (regionalParty.code !== partyCode) continue;
                for (const winner of regionalParty.winners || []) {
                    if (winner.constituencyCode) constituencyCodes.add(winner.constituencyCode);
                }
            }
        }
        return {
            available: true,
            diagnostics: [],
            partyCode,
            totalSeats: party.totalSeats,
            localSeats: party.localSeats,
            regionalListSeats: party.regionalListSeats,
            representedRegionCount: rows.filter(row => row.seats > 0).length,
            constituencyBreadth: constituencyCodes.size,
            rows
        };
    }

    function deriveUrbanizationRepresentation(snapshot, demographics) {
        const representatives = indexRepresentatives(snapshot);
        if (!representatives.available) {
            return { available: false, diagnostics: [...representatives.diagnostics], rows: [], national: null, source: null };
        }

        const diagnostics = [];
        const censusRows = Array.isArray(demographics?.constituencies) ? demographics.constituencies : [];
        if (censusRows.length !== 92) addDiagnostic(diagnostics, 'constituency-demographics-count');
        if (!Number.isSafeInteger(demographics?.censusYear) || demographics.censusYear <= 0
                || typeof demographics?.datasetVersion !== 'string' || !demographics.datasetVersion.trim()
                || typeof demographics?.sourceUrl !== 'string' || !demographics.sourceUrl.trim()
                || typeof demographics?.decreeUrl !== 'string' || !demographics.decreeUrl.trim()
                || typeof demographics?.revisedAt !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(demographics.revisedAt)) {
            addDiagnostic(diagnostics, 'constituency-demographics-source');
        }
        if (demographics?.localSeatTotal !== LOCAL_SEAT_TOTAL
                || demographics?.excludedRegionalSeatTotal !== REGIONAL_SEAT_TOTAL) {
            addDiagnostic(diagnostics, 'constituency-demographics-seat-totals');
        }

        const byCode = new Map();
        for (const row of censusRows) {
            if (typeof row?.constituencyCode !== 'string' || !row.constituencyCode.trim()
                    || byCode.has(row.constituencyCode)) {
                addDiagnostic(diagnostics, 'constituency-demographics-code');
            } else byCode.set(row.constituencyCode, row);
            if (!integer(row?.allocatedSeats) || row.allocatedSeats < 2 || row.allocatedSeats > 6) {
                addDiagnostic(diagnostics, 'constituency-demographics-seat-totals');
            }
            if (!Array.isArray(row?.componentCodes) || !row.componentCodes.length
                    || row.componentCodes.some(code => typeof code !== 'string' || !code.trim())) {
                addDiagnostic(diagnostics, 'constituency-demographics-components');
            }
            if (typeof row?.urbanShare !== 'number' || !Number.isFinite(row.urbanShare)
                    || row.urbanShare < 0 || row.urbanShare > 100) {
                addDiagnostic(diagnostics, 'constituency-demographics-population');
                continue;
            }
            if (row.populationCoverage === 'exact') {
                if (![row.totalPopulation, row.urbanPopulation, row.ruralPopulation].every(integer)
                        || row.totalPopulation === 0) {
                    addDiagnostic(diagnostics, 'constituency-demographics-population');
                    continue;
                }
                if (row.urbanPopulation + row.ruralPopulation !== row.totalPopulation
                        || Math.abs(row.urbanShare - 100 * row.urbanPopulation / row.totalPopulation) > 1e-9) {
                    addDiagnostic(diagnostics, 'constituency-demographics-total');
                }
            } else if (row.populationCoverage !== 'share-only' || row.urbanShare !== 100) {
                addDiagnostic(diagnostics, 'constituency-demographics-population');
            }
        }

        const localRecords = representatives.records.filter(row => row.seatType === 'LOCAL');
        const electionConstituencies = new Map();
        for (const record of localRecords) {
            const previous = electionConstituencies.get(record.constituencyCode);
            if (previous && (previous.allocatedSeats !== record.allocatedSeats
                    || previous.name !== record.constituencyName)) {
                addDiagnostic(diagnostics, 'constituency-demographics-coverage');
            } else if (!previous) electionConstituencies.set(record.constituencyCode, {
                name: record.constituencyName,
                allocatedSeats: record.allocatedSeats
            });
        }
        if (byCode.size !== electionConstituencies.size
                || [...electionConstituencies].some(([code, row]) => !byCode.has(code)
                    || byCode.get(code).allocatedSeats !== row.allocatedSeats)
                || [...byCode.keys()].some(code => !electionConstituencies.has(code))) {
            addDiagnostic(diagnostics, 'constituency-demographics-coverage');
        }
        if (censusRows.reduce((sum, row) => sum + (integer(row?.allocatedSeats) ? row.allocatedSeats : 0), 0)
                !== LOCAL_SEAT_TOTAL) {
            addDiagnostic(diagnostics, 'constituency-demographics-seat-totals');
        }
        if (diagnostics.length) {
            return { available: false, diagnostics, rows: [], national: null, source: null };
        }

        const nationalUrbanizationIndex = censusRows.reduce((sum, row) =>
            sum + row.allocatedSeats * row.urbanShare, 0) / LOCAL_SEAT_TOTAL;
        const national = {
            constituencyCount: censusRows.length,
            localSeatTotal: LOCAL_SEAT_TOTAL,
            excludedRegionalSeatTotal: REGIONAL_SEAT_TOTAL,
            urbanizationIndex: nationalUrbanizationIndex,
            ruralityIndex: 100 - nationalUrbanizationIndex
        };
        const rows = snapshot.parties.filter(party => party.localSeats > 0).map(party => {
            const partyRecords = localRecords.filter(record => record.partyCode === party.code);
            const partyConstituencies = new Map();
            for (const record of partyRecords) {
                if (!partyConstituencies.has(record.constituencyCode)) partyConstituencies.set(record.constituencyCode, {
                    code: record.constituencyCode,
                    name: record.constituencyName,
                    allocatedSeats: record.allocatedSeats,
                    partyLocalSeats: 0
                });
                partyConstituencies.get(record.constituencyCode).partyLocalSeats++;
            }
            const constituencyRows = [...partyConstituencies.values()].map(row => {
                const census = byCode.get(row.code);
                return {
                    ...row,
                    totalPopulation: census.totalPopulation ?? null,
                    urbanPopulation: census.urbanPopulation ?? null,
                    ruralPopulation: census.ruralPopulation ?? null,
                    populationCoverage: census.populationCoverage,
                    urbanShare: census.urbanShare,
                    ruralShare: 100 - census.urbanShare
                };
            });
            const urbanizationIndex = constituencyRows.reduce((sum, row) =>
                sum + row.partyLocalSeats * row.urbanShare, 0) / party.localSeats;
            return {
                code: party.code,
                name: party.name,
                color: party.color,
                symbolAsset: party.symbolAsset,
                localSeats: party.localSeats,
                urbanizationIndex,
                ruralityIndex: 100 - urbanizationIndex,
                differenceFromNational: urbanizationIndex - national.urbanizationIndex,
                constituencyRows
            };
        }).sort((left, right) => right.urbanizationIndex - left.urbanizationIndex || codeOrder(left, right));

        return {
            available: true,
            diagnostics: [],
            source: {
                datasetVersion: demographics.datasetVersion,
                censusYear: demographics.censusYear,
                sourceUrl: demographics.sourceUrl,
                decreeUrl: demographics.decreeUrl,
                revisedAt: demographics.revisedAt
            },
            national,
            rows
        };
    }

    function deriveConstituencyDistribution(snapshot) {
        const matrix = buildRegionMatrix(snapshot);
        if (!matrix.available) return { available: false, diagnostics: matrix.diagnostics, bins: [] };
        const diagnostics = [];
        const constituencies = new Map();
        let winnerCount = 0;
        for (const region of snapshot.regions) {
            for (const party of region.parties) {
                for (const winner of party.winners || []) {
                    winnerCount++;
                    if (!winner.constituencyCode || !integer(winner.allocatedSeats)
                            || winner.allocatedSeats < 2 || winner.allocatedSeats > 6) {
                        addDiagnostic(diagnostics, 'constituency-allocation');
                        continue;
                    }
                    const previous = constituencies.get(winner.constituencyCode);
                    if (previous && (previous.allocatedSeats !== winner.allocatedSeats
                            || previous.regionCode !== region.code)) {
                        addDiagnostic(diagnostics, 'constituency-allocation');
                    } else if (previous) previous.winnerCount++;
                    else constituencies.set(winner.constituencyCode, {
                        code: winner.constituencyCode,
                        name: winner.constituencyName,
                        regionCode: region.code,
                        allocatedSeats: winner.allocatedSeats,
                        winnerCount: 1
                    });
                }
            }
        }
        if (winnerCount !== 305 || constituencies.size !== 92) addDiagnostic(diagnostics, 'constituency-count');
        const allocatedTotal = [...constituencies.values()].reduce((sum, row) => sum + row.allocatedSeats, 0);
        if (allocatedTotal !== 305 || [...constituencies.values()].some(row => row.winnerCount !== row.allocatedSeats)) {
            addDiagnostic(diagnostics, 'constituency-allocation');
        }
        if (diagnostics.length) return { available: false, diagnostics, bins: [] };
        const bins = [2, 3, 4, 5, 6].map(seats => ({
            seats,
            constituencies: [...constituencies.values()].filter(row => row.allocatedSeats === seats).length,
            items: [...constituencies.values()].filter(row => row.allocatedSeats === seats)
        }));
        if (bins.some((bin, index) => bin.constituencies !== [21, 38, 22, 5, 6][index])) {
            return { available: false, diagnostics: ['constituency-bin-count'], bins: [] };
        }
        return { available: true, diagnostics: [], constituencyCount: 92, localSeats: 305, bins };
    }

    function normalized(value) {
        return String(value ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
    }

    function indexRepresentatives(snapshot, filters = {}) {
        const matrix = buildRegionMatrix(snapshot);
        if (!matrix.available) return { available: false, diagnostics: matrix.diagnostics, records: [] };
        const constituencies = deriveConstituencyDistribution(snapshot);
        if (!constituencies.available) return { available: false, diagnostics: constituencies.diagnostics, records: [] };
        const diagnostics = [];
        const identities = new Set();
        const records = [];
        let localCount = 0;
        let regionalCount = 0;
        for (const region of snapshot.regions) {
            for (const party of region.parties) {
                const localWinners = Array.isArray(party.winners) ? party.winners : [];
                const regionalWinners = Array.isArray(party.regionalListWinners) ? party.regionalListWinners : [];
                if (localWinners.length !== party.localSeats || regionalWinners.length !== party.regionalListSeats) {
                    addDiagnostic(diagnostics, 'winner-seat-total');
                }
                for (const winner of localWinners) {
                    localCount++;
                    const candidateKey = typeof winner.candidateKey === 'string' ? winner.candidateKey.trim() : '';
                    if (!candidateKey) addDiagnostic(diagnostics, 'candidate-key');
                    else {
                        const identity = `key:${candidateKey}`;
                        if (identities.has(identity)) addDiagnostic(diagnostics, 'duplicate-candidate');
                        identities.add(identity);
                    }
                    if (!winner.candidateName || !winner.constituencyCode || !integer(winner.allocatedSeats)) {
                        addDiagnostic(diagnostics, 'winner-identity');
                    }
                    records.push({ candidateKey: candidateKey || null, candidateName: winner.candidateName,
                        partyCode: party.code, partyName: party.name, regionCode: region.code, regionName: region.name,
                        constituencyCode: winner.constituencyCode, constituencyName: winner.constituencyName,
                        allocatedSeats: winner.allocatedSeats, seatType: 'LOCAL', votes: winner.votes ?? null });
                }
                for (const winner of regionalWinners) {
                    regionalCount++;
                    const candidateKey = typeof winner.candidateKey === 'string' ? winner.candidateKey.trim() : '';
                    if (!candidateKey) addDiagnostic(diagnostics, 'candidate-key');
                    else {
                        const identity = `key:${candidateKey}`;
                        if (identities.has(identity)) addDiagnostic(diagnostics, 'duplicate-candidate');
                        identities.add(identity);
                    }
                    if (!winner.candidateName) addDiagnostic(diagnostics, 'winner-identity');
                    records.push({ candidateKey: candidateKey || null, candidateName: winner.candidateName,
                        partyCode: party.code, partyName: party.name, regionCode: region.code, regionName: region.name,
                        constituencyCode: null, constituencyName: null, allocatedSeats: null,
                        seatType: 'REGIONAL', votes: null });
                }
            }
        }
        if (localCount !== 305 || regionalCount !== 90 || records.length !== 395) {
            addDiagnostic(diagnostics, 'representative-count');
        }
        if (diagnostics.length) return { available: false, diagnostics, records: [], totalRecords: null };
        if (filters.constituencySeats !== null && filters.constituencySeats !== undefined
                && (!integer(filters.constituencySeats) || filters.constituencySeats < 2
                    || filters.constituencySeats > 6)) {
            return { available: false, diagnostics: ['constituency-size-filter'], records: [], totalRecords: null };
        }
        const query = normalized(filters.query);
        const filtered = records.filter(row =>
            (!query || [row.candidateName, row.constituencyName, row.constituencyCode]
                .some(value => normalized(value).includes(query)))
            && (!filters.regionCode || row.regionCode === filters.regionCode)
            && (!filters.constituencyCode || row.constituencyCode === filters.constituencyCode)
            && (filters.constituencySeats === null || filters.constituencySeats === undefined
                || row.allocatedSeats === filters.constituencySeats)
            && (!filters.partyCode || row.partyCode === filters.partyCode)
            && (!filters.seatType || filters.seatType === 'ALL' || row.seatType === filters.seatType));
        return { available: true, diagnostics: [], totalRecords: 395, localCount, regionalCount,
            filteredCount: filtered.length, records: filtered };
    }

    return { auditSnapshot, deriveBallotComponents, deriveBallotSeatComparison, deriveConcentration,
        buildRegionMatrix, deriveRegionDelegation, derivePartyGeography, deriveUrbanizationRepresentation,
        deriveConstituencyDistribution, indexRepresentatives };
});
