(function (root, factory) {
    const quotient = factory();
    if (typeof module === 'object' && module.exports) module.exports = quotient;
    else root.FhemniHistoricalElectoralQuotient = quotient;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
    const RULE_ID = 'MOROCCO_2016_LOCAL_3_PERCENT_ELIGIBLE_QUOTIENT_LARGEST_REMAINDER';
    const SOURCE_FACT_STATUS = 'REPORTED_ELECTIONS_MA';
    const AGGREGATE_FACT_STATUS = 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA';
    const COUNTERFACTUAL_FACT_STATUS = 'FHEMNI_COUNTERFACTUAL_ANALYSIS_BASED_ON_ELECTIONS_MA';
    const PROOF_STATUS = 'VERIFIED_BY_EXACT_2016_LOCAL_SEAT_REPRODUCTION';
    const NATIONAL_LIST_PROOF_STATUS = 'VERIFIED_BY_EXACT_2016_NATIONAL_LIST_SEAT_REPRODUCTION';
    const ABBREVIATION_STATUS = 'ARCHIVE_FIELD_NOT_VERIFIED_IDENTITY';
    const EXPECTED_CONSTITUENCIES = 92;
    const EXPECTED_LOCAL_SEATS = 305;
    const EXPECTED_LIST_SEATS = 90;
    const EXPECTED_2016_NATIONAL_LIST_PARTIES = 24;

    function text(value) {
        return typeof value === 'string' && value.trim().length > 0;
    }

    function nonnegativeInteger(value) {
        return Number.isSafeInteger(value) && value >= 0;
    }

    function positiveInteger(value) {
        return Number.isSafeInteger(value) && value > 0;
    }

    function records(value) {
        return Array.isArray(value) ? value : [];
    }

    function addDiagnostic(diagnostics, code) {
        if (!diagnostics.includes(code)) diagnostics.push(code);
    }

    function officialUrl(value) {
        try {
            const parsed = new URL(value);
            return parsed.protocol === 'https:' && parsed.hostname === 'www.elections.ma';
        } catch {
            return false;
        }
    }

    function codepointOrder(left, right) {
        const a = String(left ?? '');
        const b = String(right ?? '');
        return a < b ? -1 : a > b ? 1 : 0;
    }

    function rational(numerator, denominator) {
        return { numerator, denominator, value: numerator / denominator };
    }

    function unavailable(diagnostics, extras = {}) {
        return { available: false, diagnostics: [...new Set(diagnostics)], ...extras };
    }

    function auditAllocatorInput(contest) {
        const diagnostics = [];
        if (!contest || !text(contest.constituencyId) || !positiveInteger(contest.allocatedSeats)
                || !Array.isArray(contest.parties) || contest.parties.length === 0) {
            addDiagnostic(diagnostics, 'local-contest-shape');
            return diagnostics;
        }
        const partyIds = new Set();
        for (const party of contest.parties) {
            if (!party || !text(party.partyId) || partyIds.has(party.partyId)
                    || !nonnegativeInteger(party.votes)) {
                addDiagnostic(diagnostics, 'local-party-roster');
            }
            if (party?.partyId) partyIds.add(party.partyId);
        }
        const voteTotal = contest.parties.reduce((sum, party) =>
            sum + (nonnegativeInteger(party?.votes) ? party.votes : 0), 0);
        if (!nonnegativeInteger(contest.totalVotes) || contest.totalVotes !== voteTotal) {
            addDiagnostic(diagnostics, 'local-vote-total');
        }
        return diagnostics;
    }

    function allocateLocalSeatsUnder2016Rules(contest) {
        return allocateSeats(contest, 3, RULE_ID);
    }

    function allocateSeats(contest, thresholdPercent, ruleId) {
        const diagnostics = auditAllocatorInput(contest);
        if (diagnostics.length) return unavailable(diagnostics, { parties: [] });

        const totalVotes = contest.totalVotes;
        const allocatedSeats = contest.allocatedSeats;
        const thresholdNumerator = totalVotes * thresholdPercent;
        const eligibleParties = contest.parties.filter(party => party.votes * 100 >= thresholdNumerator);
        const eligibleVotes = eligibleParties.reduce((sum, party) => sum + party.votes, 0);
        if (!positiveInteger(eligibleVotes)) {
            return unavailable(['no-eligible-lists'], { parties: [] });
        }

        const allocations = new Map();
        let firstPassSeatTotal = 0;
        for (const party of contest.parties) {
            const eligible = party.votes * 100 >= thresholdNumerator;
            const quotaNumerator = eligible ? party.votes * allocatedSeats : 0;
            const firstPassSeats = eligible ? Math.floor(quotaNumerator / eligibleVotes) : 0;
            const remainderNumerator = eligible ? quotaNumerator % eligibleVotes : null;
            firstPassSeatTotal += firstPassSeats;
            allocations.set(party.partyId, {
                party,
                eligible,
                firstPassSeats,
                remainderNumerator,
                largestRemainderSeats: 0
            });
        }

        const largestRemainderSeatTotal = allocatedSeats - firstPassSeatTotal;
        const remainderOrder = [...allocations.values()].filter(row => row.eligible)
            .sort((left, right) => right.remainderNumerator - left.remainderNumerator
                || codepointOrder(left.party.partyId, right.party.partyId));
        if (!nonnegativeInteger(largestRemainderSeatTotal)
                || largestRemainderSeatTotal > remainderOrder.length) {
            return unavailable(['seat-allocation-invariant'], { parties: [] });
        }
        if (largestRemainderSeatTotal > 0 && largestRemainderSeatTotal < remainderOrder.length
                && remainderOrder[largestRemainderSeatTotal - 1].remainderNumerator
                    === remainderOrder[largestRemainderSeatTotal].remainderNumerator) {
            return unavailable(['largest-remainder-boundary-tie'], { parties: [] });
        }
        for (let index = 0; index < largestRemainderSeatTotal; index += 1) {
            remainderOrder[index].largestRemainderSeats = 1;
        }

        const parties = contest.parties.map(party => {
            const allocation = allocations.get(party.partyId);
            return {
                ...party,
                eligible: allocation.eligible,
                firstPassSeats: allocation.firstPassSeats,
                remainder: allocation.eligible
                    ? rational(allocation.remainderNumerator, allocatedSeats)
                    : null,
                largestRemainderSeats: allocation.largestRemainderSeats,
                simulatedSeats: allocation.firstPassSeats + allocation.largestRemainderSeats,
                analysisFactStatus: COUNTERFACTUAL_FACT_STATUS
            };
        });
        const simulatedSeatTotal = parties.reduce((sum, party) => sum + party.simulatedSeats, 0);
        if (simulatedSeatTotal !== allocatedSeats) {
            return unavailable(['seat-allocation-invariant'], { parties: [] });
        }
        return {
            available: true,
            diagnostics: [],
            ruleId,
            year: contest.year,
            constituencyId: contest.constituencyId,
            constituencyType: contest.constituencyType,
            constituencyNameAr: contest.constituencyNameAr,
            regionId: contest.regionId,
            regionNameAr: contest.regionNameAr,
            sourceQueryId: contest.sourceQueryId,
            sourceUrl: contest.sourceUrl,
            allocatedSeats,
            totalVotes,
            eligibilityThreshold: { percent: thresholdPercent, ...rational(thresholdNumerator, 100) },
            eligibleVotes,
            electoralQuotient: rational(eligibleVotes, allocatedSeats),
            firstPassSeatTotal,
            largestRemainderSeatTotal,
            simulatedSeatTotal,
            analysisFactStatus: COUNTERFACTUAL_FACT_STATUS,
            parties
        };
    }

    function auditLocalConstituencies(payload, year, ballotType = 'local') {
        const diagnostics = [];
        const collection = ballotType === 'local' ? payload?.localConstituencyResults : payload?.regionalConstituencyResults;
        if (!Array.isArray(collection)) {
            return { available: false, diagnostics: ['local-constituency-roster'], contests: [] };
        }
        const election = records(payload?.elections).find(row => row?.year === year);
        const contests = collection.filter(row => row?.year === year);
        const constituencyIds = new Set();
        const observations = new Map(records(payload?.partyObservations)
            .filter(row => row?.year === year).map(row => [row.partyId, row]));
        const nationalResults = new Map(records(payload?.nationalPartyResults)
            .filter(row => row?.year === year).map(row => [row.partyId, row]));
        const localVotesByParty = new Map();
        if (contests.length !== (ballotType === 'local' ? EXPECTED_CONSTITUENCIES : 12)
                || contests.reduce((sum, row) => sum
                    + (positiveInteger(row?.allocatedSeats) ? row.allocatedSeats : 0), 0)
                    !== (ballotType === 'local' ? EXPECTED_LOCAL_SEATS : EXPECTED_LIST_SEATS)) {
            addDiagnostic(diagnostics, 'local-constituency-roster');
        }
        for (const contest of contests) {
            if (!contest || !text(contest.constituencyId)
                    || constituencyIds.has(contest.constituencyId)
                    || contest.constituencyType !== ballotType
                    || contest.aggregateFactStatus !== AGGREGATE_FACT_STATUS
                    || !text(contest.sourceQueryId) || !officialUrl(contest.sourceUrl)
                    || contest.sourceUrl !== election?.sourceUrl) {
                addDiagnostic(diagnostics, 'local-constituency-roster');
            }
            constituencyIds.add(contest?.constituencyId);
            const inputDiagnostics = auditAllocatorInput(contest);
            inputDiagnostics.forEach(code => addDiagnostic(diagnostics, code));
            const partyIds = new Set();
            const sourceRows = new Set();
            let officialSeats = 0;
            for (const party of records(contest?.parties)) {
                if (partyIds.has(party?.partyId) || sourceRows.has(party?.sourceRow)
                        || !nonnegativeInteger(party?.sourceRow)
                        || !nonnegativeInteger(party?.officialSeats)) {
                    addDiagnostic(diagnostics, 'local-party-roster');
                }
                partyIds.add(party?.partyId);
                sourceRows.add(party?.sourceRow);
                officialSeats += nonnegativeInteger(party?.officialSeats) ? party.officialSeats : 0;
                if (nonnegativeInteger(party?.votes)) {
                    localVotesByParty.set(party.partyId,
                        (localVotesByParty.get(party.partyId) || 0) + party.votes);
                }
                if (party?.voteFactStatus !== SOURCE_FACT_STATUS
                        || party?.officialSeatFactStatus !== SOURCE_FACT_STATUS
                        || !text(party?.votesSourceText)
                        || party?.abbreviationStatus !== ABBREVIATION_STATUS) {
                    addDiagnostic(diagnostics, 'local-source-evidence');
                }
                const observation = observations.get(party?.partyId);
                const national = nationalResults.get(party?.partyId);
                if (!observation || !national
                        || party?.comparisonKey !== observation.comparisonKey
                        || party?.nameAr !== observation.nameAr
                        || party?.abbreviation !== observation.abbreviation
                        || party?.abbreviationStatus !== observation.abbreviationStatus
                        || national.comparisonKey !== observation.comparisonKey) {
                    addDiagnostic(diagnostics, 'local-party-observation');
                }
            }
            if (officialSeats !== contest?.allocatedSeats) {
                addDiagnostic(diagnostics, 'local-official-seat-total');
            }
        }
        const electionLocalVotes = records(election?.ballots)
            .find(ballot => ballot?.type === ballotType)?.votes;
        const contestLocalVotes = contests.reduce((sum, contest) =>
            sum + (nonnegativeInteger(contest?.totalVotes) ? contest.totalVotes : 0), 0);
        if (contestLocalVotes !== electionLocalVotes) {
            addDiagnostic(diagnostics, 'national-local-vote-mismatch');
        }
        for (const partyId of new Set([...localVotesByParty.keys(), ...nationalResults.keys()])) {
            const nationalLocalVotes = records(nationalResults.get(partyId)?.ballotVotes)
                .find(ballot => ballot?.type === ballotType)?.votes;
            if (nationalLocalVotes === undefined && !localVotesByParty.has(partyId)) continue;
            if (nationalLocalVotes !== (localVotesByParty.get(partyId) || 0)) {
                addDiagnostic(diagnostics, 'national-local-vote-mismatch');
            }
        }
        return { available: diagnostics.length === 0, diagnostics, contests, election };
    }

    function verify2016LocalSeatRuleReproduction(payload) {
        const audit = auditLocalConstituencies(payload, 2016);
        if (!audit.available) return unavailable(audit.diagnostics, {
            ruleId: RULE_ID, year: 2016, constituencyCount: 0, seatCount: 0,
            exactConstituencyMatches: 0, exactSeatMatches: 0, boundaryTieCount: 0
        });

        let exactConstituencyMatches = 0;
        let exactSeatMatches = 0;
        let boundaryTieCount = 0;
        const diagnostics = [];
        for (const contest of audit.contests) {
            const allocation = allocateLocalSeatsUnder2016Rules(contest);
            if (!allocation.available) {
                allocation.diagnostics.forEach(code => addDiagnostic(diagnostics, code));
                if (allocation.diagnostics.includes('largest-remainder-boundary-tie')) {
                    boundaryTieCount += 1;
                }
                continue;
            }
            const exact = allocation.parties.every(party =>
                party.simulatedSeats === party.officialSeats);
            if (!exact) {
                addDiagnostic(diagnostics, 'official-allocation-mismatch');
                continue;
            }
            exactConstituencyMatches += 1;
            exactSeatMatches += allocation.simulatedSeatTotal;
        }
        if (exactConstituencyMatches !== EXPECTED_CONSTITUENCIES
                || exactSeatMatches !== EXPECTED_LOCAL_SEATS) {
            addDiagnostic(diagnostics, 'official-allocation-mismatch');
        }
        if (diagnostics.length) return unavailable(diagnostics, {
            ruleId: RULE_ID,
            year: 2016,
            constituencyCount: audit.contests.length,
            seatCount: audit.contests.reduce((sum, row) => sum + row.allocatedSeats, 0),
            exactConstituencyMatches,
            exactSeatMatches,
            boundaryTieCount
        });
        return {
            available: true,
            diagnostics: [],
            ruleId: RULE_ID,
            year: 2016,
            constituencyCount: EXPECTED_CONSTITUENCIES,
            seatCount: EXPECTED_LOCAL_SEATS,
            exactConstituencyMatches,
            exactSeatMatches,
            boundaryTieCount,
            proofStatus: PROOF_STATUS,
            sourceUrl: audit.election.sourceUrl
        };
    }

    function auditListBallot(payload, year, ballotType) {
        const diagnostics = [];
        const election = records(payload?.elections).find(row => row?.year === year);
        const rows = records(payload?.nationalPartyResults).filter(row => row?.year === year);
        const expectedBallot = records(election?.ballots).find(row => row?.type === ballotType);
        if (!election || !officialUrl(election.sourceUrl) || !expectedBallot
                || expectedBallot.seats !== EXPECTED_LIST_SEATS || rows.length === 0) {
            addDiagnostic(diagnostics, 'national-list-roster');
        }
        const partyIds = new Set();
        const parties = [];
        for (const row of rows) {
            const ballot = records(row?.ballotVotes).find(item => item?.type === ballotType);
            const ballotSeatTotal = records(row?.ballotVotes).reduce((sum, item) =>
                sum + (nonnegativeInteger(item?.seats) ? item.seats : 0), 0);
            if (!text(row?.partyId) || partyIds.has(row.partyId)
                    || row?.factStatus !== AGGREGATE_FACT_STATUS
                    || row?.abbreviationStatus !== ABBREVIATION_STATUS
                    || ballotSeatTotal !== row.seats) {
                addDiagnostic(diagnostics, 'national-list-party-roster');
                continue;
            }
            partyIds.add(row.partyId);
            if (!ballot) continue;
            if (!nonnegativeInteger(ballot.votes) || !nonnegativeInteger(ballot.seats)) {
                addDiagnostic(diagnostics, 'national-list-party-roster');
                continue;
            }
            parties.push({
                partyId: row.partyId,
                comparisonKey: row.comparisonKey,
                nameAr: row.nameAr,
                abbreviation: row.abbreviation,
                abbreviationStatus: row.abbreviationStatus,
                votes: ballot.votes,
                officialSeats: ballot.seats,
                voteFactStatus: AGGREGATE_FACT_STATUS,
                officialSeatFactStatus: AGGREGATE_FACT_STATUS
            });
        }
        const totalVotes = parties.reduce((sum, party) => sum + party.votes, 0);
        const officialSeatTotal = parties.reduce((sum, party) => sum + party.officialSeats, 0);
        if (totalVotes !== expectedBallot?.votes) {
            addDiagnostic(diagnostics, 'national-list-vote-total');
        }
        if (officialSeatTotal !== EXPECTED_LIST_SEATS) {
            addDiagnostic(diagnostics, 'national-list-seat-total');
        }
        return {
            available: diagnostics.length === 0,
            diagnostics,
            election,
            contest: {
                year,
                constituencyId: `${year}:${ballotType}:national-scenario`,
                constituencyNameAr: ballotType === 'national' ? '-الدائرة الوطنية-' : 'مجموع الدوائر الجهوية',
                allocatedSeats: EXPECTED_LIST_SEATS,
                totalVotes,
                sourceUrl: election?.sourceUrl,
                parties
            }
        };
    }

    function verify2016NationalListRuleReproduction(payload) {
        const audit = auditListBallot(payload, 2016, 'national');
        const empty = {
            ruleId: RULE_ID,
            year: 2016,
            partyCount: audit.contest.parties.length,
            seatCount: audit.contest.allocatedSeats,
            exactPartyMatches: 0,
            exactSeatMatches: 0,
            boundaryTieCount: 0
        };
        if (!audit.available) return unavailable(audit.diagnostics, empty);
        if (audit.contest.parties.length !== EXPECTED_2016_NATIONAL_LIST_PARTIES) {
            return unavailable(['national-list-roster'], empty);
        }
        const allocation = allocateLocalSeatsUnder2016Rules(audit.contest);
        if (!allocation.available) return unavailable(allocation.diagnostics, {
            ...empty,
            boundaryTieCount: allocation.diagnostics.includes('largest-remainder-boundary-tie') ? 1 : 0
        });
        const exactPartyMatches = allocation.parties.filter(party =>
            party.simulatedSeats === party.officialSeats).length;
        const exactSeatMatches = allocation.parties.filter(party =>
            party.simulatedSeats === party.officialSeats)
            .reduce((sum, party) => sum + party.simulatedSeats, 0);
        if (exactPartyMatches !== EXPECTED_2016_NATIONAL_LIST_PARTIES
                || exactSeatMatches !== EXPECTED_LIST_SEATS) {
            return unavailable(['official-allocation-mismatch'], {
                ...empty, exactPartyMatches, exactSeatMatches
            });
        }
        return {
            available: true,
            diagnostics: [],
            ruleId: RULE_ID,
            year: 2016,
            partyCount: EXPECTED_2016_NATIONAL_LIST_PARTIES,
            seatCount: EXPECTED_LIST_SEATS,
            exactPartyMatches,
            exactSeatMatches,
            boundaryTieCount: 0,
            proofStatus: NATIONAL_LIST_PROOF_STATUS,
            sourceUrl: audit.election.sourceUrl
        };
    }

    function derive2026LocalSeatCounterfactual(payload) {
        const validation = verify2016LocalSeatRuleReproduction(payload);
        if (!validation.available) return unavailable(validation.diagnostics, {
            ballotType: 'local', constituencies: [], partyDeltas: [], validation
        });
        const audit = auditLocalConstituencies(payload, 2026);
        if (!audit.available) return unavailable(audit.diagnostics, {
            ballotType: 'local', constituencies: [], partyDeltas: [], validation
        });

        const allocations = [];
        for (const contest of audit.contests) {
            const allocation = allocateLocalSeatsUnder2016Rules(contest);
            if (!allocation.available) return unavailable(allocation.diagnostics, {
                ballotType: 'local', constituencies: [], partyDeltas: [], validation
            });
            allocations.push(allocation);
        }

        const officialByParty = new Map();
        const simulatedByParty = new Map();
        for (const contest of allocations) {
            for (const party of contest.parties) {
                officialByParty.set(party.partyId,
                    (officialByParty.get(party.partyId) || 0) + party.officialSeats);
                simulatedByParty.set(party.partyId,
                    (simulatedByParty.get(party.partyId) || 0) + party.simulatedSeats);
            }
        }

        const nationalResults = records(payload?.nationalPartyResults)
            .filter(row => row?.year === 2026);
        const nationalByParty = new Map(nationalResults.map(row => [row.partyId, row]));
        const diagnostics = [];
        for (const partyId of new Set([...officialByParty.keys(), ...nationalByParty.keys()])) {
            const national = nationalByParty.get(partyId);
            const officialNational = records(national?.ballotVotes)
                .find(ballot => ballot?.type === 'local')?.seats;
            if (!national || officialNational !== (officialByParty.get(partyId) || 0)) {
                addDiagnostic(diagnostics, 'national-local-seat-mismatch');
            }
        }
        if (diagnostics.length) return unavailable(diagnostics, {
            ballotType: 'local', constituencies: [], partyDeltas: [], validation
        });

        const partyDeltas = nationalResults.map(national => {
            const officialLocalSeats = officialByParty.get(national.partyId) || 0;
            const simulatedLocalSeats = simulatedByParty.get(national.partyId) || 0;
            return {
                partyId: national.partyId,
                comparisonKey: national.comparisonKey,
                nameAr: national.nameAr,
                abbreviation: national.abbreviation,
                abbreviationStatus: national.abbreviationStatus,
                officialLocalSeats,
                simulatedLocalSeats,
                delta: simulatedLocalSeats - officialLocalSeats,
                officialFactStatus: AGGREGATE_FACT_STATUS,
                simulationFactStatus: COUNTERFACTUAL_FACT_STATUS
            };
        }).sort((left, right) => right.delta - left.delta
            || right.simulatedLocalSeats - left.simulatedLocalSeats
            || codepointOrder(left.nameAr, right.nameAr)
            || codepointOrder(left.partyId, right.partyId));
        const officialSeatTotal = [...officialByParty.values()].reduce((sum, value) => sum + value, 0);
        const simulatedSeatTotal = [...simulatedByParty.values()].reduce((sum, value) => sum + value, 0);
        if (officialSeatTotal !== EXPECTED_LOCAL_SEATS || simulatedSeatTotal !== EXPECTED_LOCAL_SEATS) {
            return unavailable(['national-local-seat-total'], {
                ballotType: 'local', constituencies: [], partyDeltas: [], validation
            });
        }
        return {
            available: true,
            diagnostics: [],
            ruleId: RULE_ID,
            ballotType: 'local',
            year: 2026,
            officialSeatTotal,
            simulatedSeatTotal,
            validation,
            sourceUrl: audit.election.sourceUrl,
            analysisFactStatus: COUNTERFACTUAL_FACT_STATUS,
            constituencies: allocations,
            partyDeltas
        };
    }

    function derive2026Full2016SystemCounterfactual(payload) {
        const local = derive2026LocalSeatCounterfactual(payload);
        if (!local.available) return unavailable(local.diagnostics, {
            partyDeltas: [], localValidation: local.validation, listValidation: null
        });
        const listValidation = verify2016NationalListRuleReproduction(payload);
        if (!listValidation.available) return unavailable(listValidation.diagnostics, {
            partyDeltas: [], localValidation: local.validation, listValidation
        });
        const audit = auditListBallot(payload, 2026, 'regional');
        if (!audit.available) return unavailable(audit.diagnostics, {
            partyDeltas: [], localValidation: local.validation, listValidation
        });
        const nationalListAllocation = allocateLocalSeatsUnder2016Rules(audit.contest);
        if (!nationalListAllocation.available) return unavailable(nationalListAllocation.diagnostics, {
            partyDeltas: [], localValidation: local.validation, listValidation
        });

        const localByParty = new Map(local.partyDeltas.map(row => [row.partyId, row]));
        const listByParty = new Map(nationalListAllocation.parties.map(row => [row.partyId, row]));
        const nationalByParty = new Map(records(payload?.nationalPartyResults)
            .filter(row => row?.year === 2026).map(row => [row.partyId, row]));
        const diagnostics = [];
        const partyDeltas = [];
        for (const [partyId, national] of nationalByParty) {
            const localRow = localByParty.get(partyId);
            const listRow = listByParty.get(partyId) || { simulatedSeats: 0 };
            const regionalBallot = records(national.ballotVotes)
                .find(row => row?.type === 'regional');
            const officialRegionalListSeats = regionalBallot?.seats || 0;
            if (!localRow || !nonnegativeInteger(officialRegionalListSeats)) {
                addDiagnostic(diagnostics, 'full-system-party-roster');
                continue;
            }
            const simulatedTotalSeats = localRow.simulatedLocalSeats + listRow.simulatedSeats;
            if (national.seats !== localRow.officialLocalSeats + officialRegionalListSeats) {
                addDiagnostic(diagnostics, 'full-system-official-total');
            }
            partyDeltas.push({
                partyId,
                comparisonKey: national.comparisonKey,
                nameAr: national.nameAr,
                abbreviation: national.abbreviation,
                abbreviationStatus: national.abbreviationStatus,
                officialLocalSeats: localRow.officialLocalSeats,
                simulatedLocalSeats: localRow.simulatedLocalSeats,
                officialRegionalListSeats,
                simulatedNationalListSeats: listRow.simulatedSeats,
                officialTotalSeats: national.seats,
                simulatedTotalSeats,
                delta: simulatedTotalSeats - national.seats,
                officialFactStatus: AGGREGATE_FACT_STATUS,
                simulationFactStatus: COUNTERFACTUAL_FACT_STATUS
            });
        }
        if (diagnostics.length) return unavailable(diagnostics, {
            partyDeltas: [], localValidation: local.validation, listValidation
        });
        partyDeltas.sort((left, right) => right.delta - left.delta
            || right.simulatedTotalSeats - left.simulatedTotalSeats
            || codepointOrder(left.nameAr, right.nameAr)
            || codepointOrder(left.partyId, right.partyId));
        const officialSeatTotal = partyDeltas.reduce((sum, row) => sum + row.officialTotalSeats, 0);
        const simulatedSeatTotal = partyDeltas.reduce((sum, row) => sum + row.simulatedTotalSeats, 0);
        if (officialSeatTotal !== EXPECTED_LOCAL_SEATS + EXPECTED_LIST_SEATS
                || simulatedSeatTotal !== EXPECTED_LOCAL_SEATS + EXPECTED_LIST_SEATS) {
            return unavailable(['full-system-seat-total'], {
                partyDeltas: [], localValidation: local.validation, listValidation
            });
        }
        return {
            available: true,
            diagnostics: [],
            year: 2026,
            listScenario: 'AGGREGATED_2026_REGIONAL_VOTES_AS_2016_NATIONAL_LIST',
            officialSeatTotal,
            simulatedSeatTotal,
            localValidation: local.validation,
            listValidation,
            localSimulation: local,
            nationalListAllocation,
            partyDeltas,
            sourceUrl: audit.election.sourceUrl,
            analysisFactStatus: COUNTERFACTUAL_FACT_STATUS
        };
    }

    function derive2026QuotientOnlyCounterfactual(payload) {
        const empty = { partyDeltas: [], constituencies: [] };
        if (payload?.generation?.sourceSha256 !== '13378267af52eecba43d0bab4d3de5330351f48f4c9e3c580107da995f6b7ce1') {
            return unavailable(['source-provenance'], empty);
        }
        const local = auditLocalConstituencies(payload, 2026);
        const regional = auditLocalConstituencies(payload, 2026, 'regional');
        if (!local.available || !regional.available) return unavailable([
            ...local.diagnostics, ...regional.diagnostics.map(code => `regional:${code}`)
        ], empty);
        const contests = [...local.contests, ...regional.contests];
        // Check the independent elected roster, including each constituency's allocation.
        for (const contest of contests) {
            const winners = records(payload.elected).filter(row => row.year === 2026
                && row.constituencyId === contest.constituencyId && row.constituencyType === contest.constituencyType);
            if (winners.length !== contest.allocatedSeats || winners.some(row => row.regionId !== contest.regionId)
                    || contest.parties.some(party => party.officialSeats !== winners.filter(row => row.partyId === party.partyId).length)) {
                return unavailable(['constituency-winner-reconciliation'], empty);
            }
        }
        if (new Set(regional.contests.map(c => c.regionId)).size !== 12
                || new Set(contests.map(c => c.constituencyId)).size !== 104) {
            return unavailable(['regional-geography'], empty);
        }
        for (const party of records(payload.nationalPartyResults).filter(row => row.year === 2026)) {
            let officialTotal = 0;
            for (const type of ['local', 'regional']) {
                const partyContests = contests.filter(c => c.constituencyType === type)
                    .flatMap(c => c.parties.filter(p => p.partyId === party.partyId));
                const seats = partyContests.reduce((sum, row) => sum + row.officialSeats, 0);
                const ballot = records(party.ballotVotes).find(b => b.type === type);
                if ((!ballot && partyContests.length) || (ballot && ballot.seats !== seats)) {
                    return unavailable(['official-party-seat-reconciliation'], empty);
                }
                officialTotal += seats;
            }
            if (party.seats !== officialTotal) return unavailable(['official-party-seat-reconciliation'], empty);
        }
        const ruleId = '2026_GEOGRAPHY_VALID_VOTES_NO_THRESHOLD_LARGEST_REMAINDER';
        const allocations = contests.map(contest => allocateSeats(contest, 0, ruleId));
        const errors = allocations.filter(row => !row.available).flatMap(row => row.diagnostics);
        if (errors.length) return unavailable(errors, empty);
        const partyDeltas = records(payload.nationalPartyResults).filter(row => row.year === 2026).map(party => {
            const seats = type => allocations.filter(c => c.constituencyType === type)
                .reduce((sum, c) => sum + (c.parties.find(p => p.partyId === party.partyId)?.simulatedSeats || 0), 0);
            const officialLocalSeats = party.ballotVotes.find(b => b.type === 'local').seats;
            const officialRegionalListSeats = party.ballotVotes.find(b => b.type === 'regional')?.seats || 0;
            const simulatedLocalSeats = seats('local');
            const simulatedRegionalListSeats = seats('regional');
            return { ...party, officialLocalSeats, officialRegionalListSeats, simulatedLocalSeats,
                simulatedRegionalListSeats, officialTotalSeats: party.seats,
                simulatedTotalSeats: simulatedLocalSeats + simulatedRegionalListSeats,
                delta: simulatedLocalSeats + simulatedRegionalListSeats - party.seats,
                simulationFactStatus: COUNTERFACTUAL_FACT_STATUS };
        }).sort((a,b) => b.delta - a.delta || codepointOrder(a.partyId,b.partyId));
        const simulatedSeatTotal = partyDeltas.reduce((sum,p) => sum + p.simulatedTotalSeats, 0);
        if (simulatedSeatTotal !== 395) return unavailable(['full-system-seat-total'], empty);
        return { available: true, diagnostics: [], ruleId, year: 2026,
            officialSeatTotal: 395, simulatedSeatTotal, partyDeltas, constituencies: allocations,
            sourceUrl: local.election.sourceUrl, analysisFactStatus: COUNTERFACTUAL_FACT_STATUS };
    }

    return {
        derive2026QuotientOnlyCounterfactual,
        allocateLocalSeatsUnder2016Rules,
        verify2016LocalSeatRuleReproduction,
        derive2026LocalSeatCounterfactual,
        verify2016NationalListRuleReproduction,
        derive2026Full2016SystemCounterfactual
    };
});
