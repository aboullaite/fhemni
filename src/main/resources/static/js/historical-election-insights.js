(function (root, factory) {
    const insights = factory();
    if (typeof module === 'object' && module.exports) module.exports = insights;
    else root.FhemniHistoricalElectionInsights = insights;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
    const EXPECTED_YEARS = [2016, 2021, 2026];
    const TOTAL_SEATS = 395;
    const LOCAL_SEATS = 305;
    const LIST_SEATS = 90;
    const REGION_COUNT = 12;
    const PAGE_SIZE = 10;
    const DIMENSIONS = ['gender', 'age', 'education'];
    const REPORTED_FACT_STATUS = 'REPORTED_ELECTIONS_MA';
    const ANALYSIS_FACT_STATUS = 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA';
    const ABBREVIATION_STATUS = 'ARCHIVE_FIELD_NOT_VERIFIED_IDENTITY';
    const EXPECTED_SOURCE_URLS = [
        'https://www.elections.ma/elections/legislatives/resultats.aspx?Id=l1Vr5AJaDkA534Qqp+Idqg==&IE=1',
        'https://www.elections.ma/elections/legislatives/resultats.aspx?Id=T1uzm+f7U/WFF+rn+x03Zg==&IE=1',
        'https://www.elections.ma/elections/legislatives/resultats.aspx?Id=8waOZwF4QzhMMKY7yKQzGQ==&IE=1'
    ];

    function records(value) {
        return Array.isArray(value) ? value : [];
    }

    function integer(value) {
        return Number.isSafeInteger(value) && value >= 0;
    }

    function number(value) {
        return Number.isFinite(value) && value >= 0;
    }

    function text(value) {
        return typeof value === 'string' && value.trim().length > 0;
    }

    function addDiagnostic(diagnostics, code) {
        if (!diagnostics.includes(code)) diagnostics.push(code);
    }

    function sameValues(left, right) {
        return left.length === right.length && left.every((value, index) => value === right[index]);
    }

    function codepointOrder(left, right) {
        const a = String(left ?? '');
        const b = String(right ?? '');
        return a < b ? -1 : a > b ? 1 : 0;
    }

    function round(value) {
        return Math.round((value + Number.EPSILON) * 100) / 100;
    }

    function electionIndex(payload) {
        return new Map(records(payload?.elections).map(election => [election?.year, election]));
    }

    function resultIndex(payload) {
        return new Map(records(payload?.nationalPartyResults).map(result =>
            [`${result?.year}:${result?.comparisonKey}`, result]));
    }

    function observationIndex(payload) {
        return new Map(records(payload?.partyObservations).map(observation =>
            [`${observation?.year}:${observation?.partyId}`, observation]));
    }

    function auditElections(payload, diagnostics) {
        const elections = records(payload?.elections);
        const years = elections.map(election => election?.year);
        if (!sameValues(years, EXPECTED_YEARS)) addDiagnostic(diagnostics, 'election-years');
        const seen = new Set();
        for (const election of elections) {
            if (!election || seen.has(election.year)) {
                addDiagnostic(diagnostics, 'election-years');
                continue;
            }
            seen.add(election.year);
            if (election.totalSeats !== TOTAL_SEATS || election.electedCount !== TOTAL_SEATS
                    || !number(election.turnoutPercentage) || election.turnoutPercentage > 100
                    || !text(election.turnoutSourcePercentageText)
                    || !text(election.turnoutSourceQueryId)
                    || election.turnoutFactStatus !== REPORTED_FACT_STATUS) {
                addDiagnostic(diagnostics, 'election-summary');
            }
            const ballots = records(election.ballots);
            const types = new Set(ballots.map(ballot => ballot?.type));
            const local = ballots.find(ballot => ballot?.type === 'local');
            const list = ballots.find(ballot => ballot?.type !== 'local');
            if (ballots.length !== 2 || types.size !== 2 || !local || !list
                    || local.seats !== LOCAL_SEATS || list.seats !== LIST_SEATS
                    || !integer(local.votes) || !integer(list.votes)
                    || !['national', 'regional'].includes(list.type)
                    || ballots.some(ballot => ballot?.factStatus !== ANALYSIS_FACT_STATUS)) {
                addDiagnostic(diagnostics, 'election-ballots');
            }
            if (election?.capabilities?.regionalComparisonBallot !== 'local'
                    || election?.capabilities?.listBallotType !== list?.type
                    || election?.capabilities?.listBallotType
                        !== (election?.year === 2016 ? 'national' : 'regional')
                    || election?.capabilities?.listBallotAssignableToRegions
                        !== (election?.year !== 2016)) {
                addDiagnostic(diagnostics, 'election-capabilities');
            }
        }
        return years;
    }

    function auditGeneration(payload, diagnostics) {
        const generation = payload?.generation;
        const sourceUrls = records(generation?.sourceUrls);
        const electionUrls = records(payload?.elections).map(election => election?.sourceUrl);
        if (!generation || generation.sourceArchive !== 'morocco-legislative-results.json'
                || generation.sourceDatasetId !== 'morocco-legislative-2016-2021-2026'
                || !/^[a-f0-9]{64}$/.test(generation.sourceSha256 || '')
                || generation.aggregateFactStatus !== ANALYSIS_FACT_STATUS
                || !sameValues(sourceUrls, EXPECTED_SOURCE_URLS)
                || !sameValues(electionUrls, EXPECTED_SOURCE_URLS)) {
            addDiagnostic(diagnostics, 'source-provenance');
        }
    }

    function auditParties(payload, diagnostics) {
        const observations = records(payload?.partyObservations);
        const results = records(payload?.nationalPartyResults);
        if (!Array.isArray(payload?.partyObservations) || !Array.isArray(payload?.nationalPartyResults)) {
            addDiagnostic(diagnostics, 'national-party-roster');
            return;
        }
        const observationsById = observationIndex(payload);
        const observationIds = new Set();
        const resultIds = new Set();
        const comparisonKeysByYear = new Set();
        const elections = electionIndex(payload);

        for (const observation of observations) {
            const id = `${observation?.year}:${observation?.partyId}`;
            const comparisonId = `${observation?.year}:${observation?.comparisonKey}`;
            if (!EXPECTED_YEARS.includes(observation?.year) || !text(observation?.partyId)
                    || !text(observation?.nameAr) || !text(observation?.comparisonKey)
                    || observationIds.has(id) || comparisonKeysByYear.has(comparisonId)
                    || !['same_exact_source_label', 'source_observation_only']
                        .includes(observation?.continuityBasis)
                    || observation?.abbreviationStatus !== ABBREVIATION_STATUS
                    || observation?.comparisonFactStatus !== ANALYSIS_FACT_STATUS) {
                addDiagnostic(diagnostics, 'party-observations');
            }
            if (observation?.continuityBasis === 'same_exact_source_label'
                    && observation.comparisonKey !== `exact-source-label:${observation.partyId}`) {
                addDiagnostic(diagnostics, 'party-continuity');
            }
            if (observation?.continuityBasis === 'source_observation_only'
                    && observation?.comparisonKey !== `source:${observation?.year}:${observation?.partyId}`) {
                addDiagnostic(diagnostics, 'party-continuity');
            }
            observationIds.add(id);
            comparisonKeysByYear.add(comparisonId);
        }
        const exactNames = new Map();
        for (const observation of observations.filter(row =>
            row?.continuityBasis === 'same_exact_source_label')) {
            const earlierName = exactNames.get(observation.comparisonKey);
            if (earlierName !== undefined && earlierName !== observation.nameAr) {
                addDiagnostic(diagnostics, 'party-continuity');
            }
            exactNames.set(observation.comparisonKey, observation.nameAr);
        }

        for (const result of results) {
            const id = `${result?.year}:${result?.partyId}`;
            const observation = observationsById.get(id);
            if (!observation || resultIds.has(id) || !integer(result?.seats)
                    || result?.comparisonKey !== observation?.comparisonKey
                    || result?.nameAr !== observation?.nameAr
                    || result?.abbreviation !== observation?.abbreviation
                    || result?.abbreviationStatus !== ABBREVIATION_STATUS
                    || result?.factStatus !== ANALYSIS_FACT_STATUS) {
                addDiagnostic(diagnostics, 'national-party-roster');
            }
            resultIds.add(id);
            const election = elections.get(result?.year);
            const electionTypes = new Set(records(election?.ballots).map(ballot => ballot.type));
            const ballotTypes = new Set();
            let rowSeats = 0;
            for (const ballot of records(result?.ballotVotes)) {
                if (!electionTypes.has(ballot?.type) || ballotTypes.has(ballot?.type)
                        || !integer(ballot?.votes) || !integer(ballot?.seats)) {
                    addDiagnostic(diagnostics, 'national-party-ballots');
                }
                ballotTypes.add(ballot?.type);
                rowSeats += integer(ballot?.seats) ? ballot.seats : 0;
            }
            if (!Array.isArray(result?.ballotVotes) || rowSeats !== result?.seats) {
                addDiagnostic(diagnostics, 'national-party-ballots');
            }
        }
        if (observationIds.size !== resultIds.size
                || [...observationIds].some(id => !resultIds.has(id))) {
            addDiagnostic(diagnostics, 'national-party-roster');
        }

        for (const year of EXPECTED_YEARS) {
            const election = elections.get(year);
            const yearResults = results.filter(result => result?.year === year);
            if (yearResults.reduce((sum, result) => sum + (integer(result?.seats) ? result.seats : 0), 0)
                    !== TOTAL_SEATS) addDiagnostic(diagnostics, 'national-party-seat-total');
            for (const ballot of records(election?.ballots)) {
                const rows = yearResults.flatMap(result => records(result?.ballotVotes)
                    .filter(item => item?.type === ballot.type));
                if (rows.reduce((sum, item) => sum + item.seats, 0) !== ballot.seats
                        || rows.reduce((sum, item) => sum + item.votes, 0) !== ballot.votes) {
                    addDiagnostic(diagnostics, 'national-party-ballot-total');
                }
            }
        }
    }

    function auditRegions(payload, diagnostics) {
        const regions = records(payload?.regionalLocalSeats);
        if (!Array.isArray(payload?.regionalLocalSeats)) {
            addDiagnostic(diagnostics, 'regional-local-seat-roster');
            return;
        }
        const results = resultIndex(payload);
        const ids = new Set();
        for (const region of regions) {
            const id = `${region?.year}:${region?.regionId}`;
            if (!EXPECTED_YEARS.includes(region?.year) || !text(region?.regionId)
                    || !text(region?.regionNameAr) || ids.has(id)
                    || region?.factStatus !== ANALYSIS_FACT_STATUS) {
                addDiagnostic(diagnostics, 'regional-local-seat-roster');
            }
            ids.add(id);
            const keys = new Set();
            for (const party of records(region?.parties)) {
                if (!results.has(`${region?.year}:${party?.comparisonKey}`)
                        || keys.has(party?.comparisonKey) || !integer(party?.seats)) {
                    addDiagnostic(diagnostics, 'regional-local-seat-roster');
                }
                if (party?.abbreviationStatus !== ABBREVIATION_STATUS) {
                    addDiagnostic(diagnostics, 'regional-party-label-status');
                }
                keys.add(party?.comparisonKey);
            }
            if (!Array.isArray(region?.parties)) addDiagnostic(diagnostics, 'regional-local-seat-roster');
        }
        for (const year of EXPECTED_YEARS) {
            const yearRegions = regions.filter(region => region?.year === year);
            if (yearRegions.length !== REGION_COUNT
                    || yearRegions.flatMap(region => records(region?.parties))
                        .reduce((sum, party) => sum + (integer(party?.seats) ? party.seats : 0), 0)
                        !== LOCAL_SEATS) {
                addDiagnostic(diagnostics, 'regional-local-seat-total');
            }
            const regionTotals = new Map();
            for (const region of yearRegions) {
                for (const party of records(region?.parties)) {
                    if (!party || !text(party.comparisonKey) || !integer(party.seats)) continue;
                    regionTotals.set(party.comparisonKey,
                        (regionTotals.get(party.comparisonKey) || 0) + party.seats);
                }
            }
            for (const result of records(payload?.nationalPartyResults).filter(row => row?.year === year)) {
                const localSeats = records(result.ballotVotes)
                    .find(ballot => ballot.type === 'local')?.seats ?? 0;
                if ((regionTotals.get(result.comparisonKey) || 0) !== localSeats) {
                    addDiagnostic(diagnostics, 'regional-party-seat-total');
                }
            }
        }
    }

    function auditDemographics(payload, diagnostics) {
        const demographics = records(payload?.nationalDemographics);
        if (!Array.isArray(payload?.nationalDemographics)) {
            addDiagnostic(diagnostics, 'demographic-roster');
            return;
        }
        const ids = new Set();
        for (const row of demographics) {
            const id = `${row?.year}:${row?.dimension}:${row?.categoryAr}`;
            if (!EXPECTED_YEARS.includes(row?.year) || !DIMENSIONS.includes(row?.dimension)
                    || !text(row?.categoryAr) || ids.has(id)
                    || Object.hasOwn(row || {}, 'count')
                    || !number(row?.percentage) || row.percentage > 100
                    || row?.valueStatus !== 'reported'
                    || row?.factStatus !== REPORTED_FACT_STATUS
                    || !text(row?.sourcePercentageText) || !text(row?.sourceQueryId)) {
                addDiagnostic(diagnostics, 'demographic-roster');
            }
            ids.add(id);
        }
    }

    function increment(map, key) {
        map.set(key, (map.get(key) || 0) + 1);
    }

    function projectedOccurrence(row) {
        return {
            year: row.year,
            electedId: row.electedId,
            nameAr: row.nameAr,
            partyId: row.partyId,
            abbreviation: row.abbreviation,
            abbreviationStatus: row.abbreviationStatus,
            comparisonKey: row.comparisonKey,
            constituencyType: row.constituencyType,
            constituencyNameAr: row.constituencyNameAr
        };
    }

    function sameOccurrence(left, right) {
        return left?.year === right?.year && left?.electedId === right?.electedId
            && left?.nameAr === right?.nameAr && left?.partyId === right?.partyId
            && left?.abbreviation === right?.abbreviation
            && left?.abbreviationStatus === right?.abbreviationStatus
            && left?.comparisonKey === right?.comparisonKey
            && left?.constituencyType === right?.constituencyType
            && left?.constituencyNameAr === right?.constituencyNameAr;
    }

    function expectedRepeatedGroups(elected) {
        const grouped = new Map();
        for (const row of elected) {
            if (!row || !text(row.normalizedName)) continue;
            if (!grouped.has(row.normalizedName)) grouped.set(row.normalizedName, []);
            grouped.get(row.normalizedName).push(row);
        }
        return [...grouped].filter(([, rows]) => {
            const perYear = new Map();
            for (const row of rows) increment(perYear, row.year);
            return perYear.size >= 2 && [...perYear.values()].every(count => count === 1);
        }).map(([normalizedName, rows]) => ({
            normalizedName,
            occurrences: rows.map(projectedOccurrence)
                .sort((left, right) => left.year - right.year)
        })).sort((left, right) => codepointOrder(left.normalizedName, right.normalizedName));
    }

    function auditElectedSeatReconciliation(payload, elected, diagnostics) {
        const allByParty = new Map();
        const localByParty = new Map();
        const localByRegionParty = new Map();
        for (const row of elected) {
            if (!row) continue;
            increment(allByParty, `${row.year}:${row.comparisonKey}`);
            if (row.constituencyType === 'local') {
                increment(localByParty, `${row.year}:${row.comparisonKey}`);
                increment(localByRegionParty, `${row.year}:${row.regionId}:${row.comparisonKey}`);
            }
        }
        for (const result of records(payload?.nationalPartyResults)) {
            if (!result) continue;
            const key = `${result.year}:${result.comparisonKey}`;
            const localSeats = records(result.ballotVotes)
                .find(ballot => ballot?.type === 'local')?.seats ?? 0;
            if ((allByParty.get(key) || 0) !== result.seats) {
                addDiagnostic(diagnostics, 'elected-party-seat-total');
            }
            if ((localByParty.get(key) || 0) !== localSeats) {
                addDiagnostic(diagnostics, 'elected-local-party-seat-total');
            }
        }

        const vectorKeys = new Set();
        for (const region of records(payload?.regionalLocalSeats)) {
            if (!region) continue;
            for (const party of records(region.parties)) {
                if (!party) continue;
                const key = `${region.year}:${region.regionId}:${party.comparisonKey}`;
                vectorKeys.add(key);
                if ((localByRegionParty.get(key) || 0) !== party.seats) {
                    addDiagnostic(diagnostics, 'regional-elected-seat-total');
                }
            }
        }
        if ([...localByRegionParty].some(([key, seats]) =>
            !vectorKeys.has(key) && seats !== 0)) {
            addDiagnostic(diagnostics, 'regional-elected-seat-total');
        }
    }

    function auditElectedAndRepeated(payload, diagnostics) {
        const elected = records(payload?.elected);
        const repeated = records(payload?.repeatedNames);
        if (!Array.isArray(payload?.elected) || !Array.isArray(payload?.repeatedNames)) {
            addDiagnostic(diagnostics, 'elected-roster');
            return;
        }
        const partyByKey = new Map(records(payload?.partyObservations).map(row =>
            [`${row?.year}:${row?.comparisonKey}`, row]));
        const electedIds = new Set();
        for (const row of elected) {
            const party = partyByKey.get(`${row?.year}:${row?.comparisonKey}`);
            if (!EXPECTED_YEARS.includes(row?.year) || !text(row?.electedId) || !text(row?.nameAr)
                    || !text(row?.normalizedName) || electedIds.has(row?.electedId)
                    || !party
                    || row?.abbreviationStatus !== ABBREVIATION_STATUS
                    || row?.factStatus !== REPORTED_FACT_STATUS
                    || row?.normalizedNameStatus !== ANALYSIS_FACT_STATUS) {
                addDiagnostic(diagnostics, 'elected-roster');
            }
            if (!party || row?.partyId !== party.partyId
                    || row?.abbreviation !== party.abbreviation
                    || row?.abbreviationStatus !== party.abbreviationStatus) {
                addDiagnostic(diagnostics, 'elected-party-observation');
            }
            electedIds.add(row?.electedId);
        }
        for (const year of EXPECTED_YEARS) {
            if (elected.filter(row => row?.year === year).length !== TOTAL_SEATS) {
                addDiagnostic(diagnostics, 'elected-total');
            }
        }
        auditElectedSeatReconciliation(payload, elected, diagnostics);

        const expectedRepeated = expectedRepeatedGroups(elected);
        if (repeated.length !== expectedRepeated.length) {
            addDiagnostic(diagnostics, 'repeated-name-roster');
        }
        for (let index = 0; index < Math.max(repeated.length, expectedRepeated.length); index++) {
            const actual = repeated[index];
            const expected = expectedRepeated[index];
            if (!actual || !expected || actual.normalizedName !== expected.normalizedName
                    || actual.evidenceStatus !== 'name_match_only'
                    || actual.factStatus !== ANALYSIS_FACT_STATUS
                    || !Array.isArray(actual.occurrences)
                    || actual.occurrences.length !== expected.occurrences.length
                    || expected.occurrences.some((occurrence, occurrenceIndex) =>
                        !sameOccurrence(actual.occurrences[occurrenceIndex], occurrence))) {
                addDiagnostic(diagnostics, 'repeated-name-roster');
            }
        }
    }

    function auditHistoricalPayload(payload) {
        const diagnostics = [];
        if (!payload || typeof payload !== 'object' || payload.schemaVersion !== 1) {
            addDiagnostic(diagnostics, 'schema-version');
        }
        auditGeneration(payload, diagnostics);
        const years = auditElections(payload, diagnostics);
        auditParties(payload, diagnostics);
        auditRegions(payload, diagnostics);
        auditDemographics(payload, diagnostics);
        auditElectedAndRepeated(payload, diagnostics);
        return { available: diagnostics.length === 0, diagnostics, years };
    }

    function unavailable(audit, extra = {}) {
        return { available: false, diagnostics: [...audit.diagnostics], ...extra };
    }

    function validPairs(years) {
        const pairs = [];
        for (let from = 0; from < years.length; from++) {
            for (let to = from + 1; to < years.length; to++) {
                pairs.push({ fromYear: years[from], toYear: years[to] });
            }
        }
        return pairs;
    }

    function listValidYearPairs(payload) {
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit, { pairs: [], defaultPair: null });
        const pairs = validPairs(audit.years);
        return { available: true, diagnostics: [], pairs,
            defaultPair: pairs[pairs.length - 1] };
    }

    function resolveYearPair(payload, fromYear, toYear) {
        const state = listValidYearPairs(payload);
        if (!state.available) return { ...state, pair: null, usedFallback: true };
        const requested = { fromYear: Number(fromYear), toYear: Number(toYear) };
        const pair = state.pairs.find(item => item.fromYear === requested.fromYear
            && item.toYear === requested.toYear);
        return { available: true, diagnostics: [], pair: pair || state.defaultPair,
            usedFallback: !pair };
    }

    function electionSummary(election) {
        return {
            year: election.year,
            turnoutPercentage: election.turnoutPercentage,
            turnoutSourcePercentageText: election.turnoutSourcePercentageText,
            turnoutSourceQueryId: election.turnoutSourceQueryId,
            turnoutFactStatus: election.turnoutFactStatus,
            totalSeats: election.totalSeats,
            electedCount: election.electedCount,
            ballots: election.ballots.map(ballot => ({ ...ballot }))
        };
    }

    function deriveNationalOverview(payload, fromYear, toYear) {
        const state = resolveYearPair(payload, fromYear, toYear);
        if (!state.available) return unavailable(state, { earlier: null, later: null });
        const elections = electionIndex(payload);
        const earlier = electionSummary(elections.get(state.pair.fromYear));
        const later = electionSummary(elections.get(state.pair.toYear));
        return { available: true, diagnostics: [], pair: state.pair,
            usedFallback: state.usedFallback, earlier, later,
            turnoutDelta: round(later.turnoutPercentage - earlier.turnoutPercentage) };
    }

    function reportedMeasures(result, election) {
        const local = result.ballotVotes.find(ballot => ballot.type === 'local');
        const list = result.ballotVotes.find(ballot => ballot.type !== 'local');
        return {
            status: 'present_in_complete_roster',
            totalSeats: result.seats,
            localSeats: local?.seats ?? null,
            listSeats: list?.seats ?? (local ? result.seats - local.seats : null),
            listBallotType: election.capabilities.listBallotType,
            localVotes: local?.votes ?? null,
            listVotes: list?.votes ?? null,
            factStatus: result.factStatus
        };
    }

    function absentMeasures(election) {
        return {
            status: 'established_zero_from_complete_roster',
            totalSeats: 0,
            localSeats: 0,
            listSeats: 0,
            listBallotType: election.capabilities.listBallotType,
            localVotes: null,
            listVotes: null,
            factStatus: ANALYSIS_FACT_STATUS
        };
    }

    function partyMetadata(payload, comparisonKey, preferredYear) {
        const observations = records(payload.partyObservations)
            .filter(row => row.comparisonKey === comparisonKey)
            .sort((left, right) => Math.abs(left.year - preferredYear)
                - Math.abs(right.year - preferredYear) || left.year - right.year);
        const observation = observations[0];
        return {
            comparisonKey,
            nameAr: observation.nameAr,
            abbreviation: observation.abbreviation,
            abbreviationStatus: observation.abbreviationStatus,
            continuityBasis: observation.continuityBasis,
            comparisonFactStatus: observation.comparisonFactStatus
        };
    }

    function normalize(value) {
        return String(value ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
    }

    function partyDeltaOrder(rows, sort, measure) {
        const deltaField = measure === 'local' ? 'localSeats' : measure === 'list' ? 'listSeats' : 'totalSeats';
        const compareName = (left, right) => codepointOrder(left.nameAr, right.nameAr)
            || codepointOrder(left.comparisonKey, right.comparisonKey);
        return [...rows].sort((left, right) => {
            if (sort === 'name') return compareName(left, right);
            if (sort === 'delta-asc') return left.selectedDelta - right.selectedDelta || compareName(left, right);
            if (sort === 'later-desc') {
                return right.later[deltaField] - left.later[deltaField]
                    || right.selectedDelta - left.selectedDelta || compareName(left, right);
            }
            if (sort === 'earlier-desc') {
                return right.earlier[deltaField] - left.earlier[deltaField]
                    || right.selectedDelta - left.selectedDelta || compareName(left, right);
            }
            return right.selectedDelta - left.selectedDelta
                || right.later[deltaField] - left.later[deltaField] || compareName(left, right);
        });
    }

    function derivePartyDeltas(payload, fromYear, toYear, options = {}) {
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit, { rows: [], totalRows: 0 });
        const pair = resolveYearPair(payload, fromYear, toYear);
        const measure = ['total', 'local', 'list'].includes(options.measure) ? options.measure : 'total';
        const sort = ['delta-desc', 'delta-asc', 'later-desc', 'earlier-desc', 'name']
            .includes(options.sort) ? options.sort : 'delta-desc';
        const field = measure === 'local' ? 'localSeats' : measure === 'list' ? 'listSeats' : 'totalSeats';
        const elections = electionIndex(payload);
        if (measure === 'list' && elections.get(pair.pair.fromYear).capabilities.listBallotType
                !== elections.get(pair.pair.toYear).capabilities.listBallotType) {
            return unavailable({ diagnostics: ['list-ballot-type'] }, { rows: [], totalRows: 0,
                pair: pair.pair, usedFallback: pair.usedFallback, measure, sort });
        }
        const results = resultIndex(payload);
        const keys = new Set(records(payload.nationalPartyResults)
            .filter(row => row.year === pair.pair.fromYear || row.year === pair.pair.toYear)
            .map(row => row.comparisonKey));
        const allRows = [...keys].map(comparisonKey => {
            const earlierResult = results.get(`${pair.pair.fromYear}:${comparisonKey}`);
            const laterResult = results.get(`${pair.pair.toYear}:${comparisonKey}`);
            const earlier = earlierResult
                ? reportedMeasures(earlierResult, elections.get(pair.pair.fromYear))
                : absentMeasures(elections.get(pair.pair.fromYear));
            const later = laterResult
                ? reportedMeasures(laterResult, elections.get(pair.pair.toYear))
                : absentMeasures(elections.get(pair.pair.toYear));
            const delta = {
                totalSeats: later.totalSeats - earlier.totalSeats,
                localSeats: later.localSeats - earlier.localSeats,
                listSeats: later.listSeats - earlier.listSeats
            };
            return { ...partyMetadata(payload, comparisonKey, pair.pair.toYear), earlier, later, delta,
                selectedMeasure: measure, selectedDelta: delta[field] };
        });
        const query = normalize(options.query);
        const filtered = query ? allRows.filter(row => [row.nameAr, row.abbreviation, row.comparisonKey]
            .some(value => normalize(value).includes(query))) : allRows;
        const ordered = partyDeltaOrder(filtered, sort, measure);
        return { available: true, diagnostics: [], pair: pair.pair,
            usedFallback: pair.usedFallback, measure, sort,
            totalRows: ordered.length, unfilteredTotalRows: allRows.length,
            rows: options.showAll || query ? ordered : ordered.slice(0, 10) };
    }

    function derivePartyTrajectory(payload, comparisonKey) {
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit, { points: [] });
        if (!records(payload.partyObservations).some(row => row.comparisonKey === comparisonKey)) {
            return unavailable({ diagnostics: ['comparison-key'] }, { points: [] });
        }
        const elections = electionIndex(payload);
        const results = resultIndex(payload);
        const points = audit.years.map(year => {
            const result = results.get(`${year}:${comparisonKey}`);
            const measures = result ? reportedMeasures(result, elections.get(year))
                : absentMeasures(elections.get(year));
            return { year, ...measures };
        });
        return { available: true, diagnostics: [],
            ...partyMetadata(payload, comparisonKey, audit.years.at(-1)), points };
    }

    function deriveRegionComparison(payload, regionId) {
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit, { rows: [], years: [] });
        const rowsByYear = audit.years.map(year => records(payload.regionalLocalSeats)
            .find(row => row.year === year && String(row.regionId) === String(regionId)));
        if (rowsByYear.some(row => !row)) {
            return unavailable({ diagnostics: ['region-id'] }, { rows: [], years: audit.years });
        }
        const keys = new Set(rowsByYear.flatMap(row => row.parties.map(party => party.comparisonKey)));
        const rows = [...keys].map(comparisonKey => ({
            ...partyMetadata(payload, comparisonKey, audit.years.at(-1)),
            points: audit.years.map((year, index) => ({
                year,
                localSeats: rowsByYear[index].parties
                    .find(party => party.comparisonKey === comparisonKey)?.seats ?? 0,
                factStatus: rowsByYear[index].factStatus
            }))
        })).sort((left, right) => {
            const leftLatest = left.points.at(-1).localSeats;
            const rightLatest = right.points.at(-1).localSeats;
            return rightLatest - leftLatest || codepointOrder(left.nameAr, right.nameAr)
                || codepointOrder(left.comparisonKey, right.comparisonKey);
        });
        return { available: true, diagnostics: [], ballotType: 'local', years: audit.years,
            regionId: String(regionId), regionNameAr: rowsByYear.at(-1).regionNameAr, rows };
    }

    function deriveTurnoutTrend(payload) {
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit, { points: [] });
        const points = records(payload.elections).map((election, index, elections) => ({
            year: election.year,
            turnoutPercentage: election.turnoutPercentage,
            deltaFromPrevious: index === 0 ? null
                : round(election.turnoutPercentage - elections[index - 1].turnoutPercentage),
            sourcePercentageText: election.turnoutSourcePercentageText,
            sourceQueryId: election.turnoutSourceQueryId,
            factStatus: election.turnoutFactStatus
        }));
        return { available: true, diagnostics: [], points };
    }

    function deriveDemographicTrends(payload, options = {}) {
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit, { rows: [], years: [] });
        const dimension = options.dimension || null;
        if (dimension && !DIMENSIONS.includes(dimension)) {
            return unavailable({ diagnostics: ['demographic-dimension'] },
                { rows: [], years: audit.years });
        }
        const observations = records(payload.nationalDemographics)
            .filter(row => !dimension || row.dimension === dimension);
        const keys = new Set(observations.map(row => `${row.dimension}:${row.categoryAr}`));
        const rows = [...keys].map(key => {
            const [rowDimension, ...categoryParts] = key.split(':');
            const categoryAr = categoryParts.join(':');
            return {
                dimension: rowDimension,
                categoryAr,
                points: observations.filter(row => row.dimension === rowDimension
                    && row.categoryAr === categoryAr).map(row => {
                    const point = {
                        year: row.year,
                        percentage: row.percentage,
                        valueStatus: row.valueStatus,
                        factStatus: row.factStatus,
                        sourceQueryId: row.sourceQueryId
                    };
                    if (Object.hasOwn(row, 'count')) point.count = row.count;
                    if (Object.hasOwn(row, 'sourcePercentageText')) {
                        point.sourcePercentageText = row.sourcePercentageText;
                    }
                    return point;
                })
            };
        }).sort((left, right) => DIMENSIONS.indexOf(left.dimension) - DIMENSIONS.indexOf(right.dimension)
            || codepointOrder(left.categoryAr, right.categoryAr));
        return { available: true, diagnostics: [], years: audit.years, dimension, rows };
    }

    function deriveRepeatedNameGroups(payload, options = {}) {
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit,
            { rows: [], totalRows: 0, page: 1, pageCount: 0, pageSize: PAGE_SIZE });
        const query = normalize(options.query);
        const filtered = records(payload.repeatedNames).filter(group => {
            const searchable = [group.normalizedName,
                ...group.occurrences.flatMap(row => [row.nameAr, row.abbreviation, row.constituencyNameAr])];
            const differentLabels = new Set(group.occurrences.map(row => row.comparisonKey)).size > 1;
            return (!query || searchable.some(value => normalize(value).includes(query)))
                && (!options.differentPartyLabelsOnly || differentLabels);
        }).sort((left, right) => codepointOrder(left.normalizedName, right.normalizedName));
        const pageCount = Math.ceil(filtered.length / PAGE_SIZE);
        const requestedPage = Number.isSafeInteger(Number(options.page)) ? Number(options.page) : 1;
        const page = pageCount ? Math.min(Math.max(requestedPage, 1), pageCount) : 1;
        const start = (page - 1) * PAGE_SIZE;
        return { available: true, diagnostics: [], page, pageCount, pageSize: PAGE_SIZE,
            totalRows: filtered.length, rows: filtered.slice(start, start + PAGE_SIZE)
                .map(group => ({ ...group, occurrences: group.occurrences.map(row => ({ ...row })) })) };
    }

    function deriveRepeatedNamePartyMovements(payload, fromYear, toYear, options = {}) {
        const empty = { gains: [], losses: [], totalMovements: 0, maximum: 0,
            evidenceStatus: 'name_match_only' };
        const audit = auditHistoricalPayload(payload);
        if (!audit.available) return unavailable(audit, empty);
        const from = Number(fromYear);
        const to = Number(toYear);
        const supported = listValidYearPairs(payload).pairs.some(pair =>
            pair.fromYear === from && pair.toYear === to);
        if (!supported) return unavailable({ diagnostics: ['year-pair'] }, empty);

        const observations = new Map(records(payload.partyObservations).map(row =>
            [`${row.year}:${row.comparisonKey}`, row]));
        const gains = new Map();
        const losses = new Map();
        let totalMovements = 0;
        for (const group of records(payload.repeatedNames)) {
            const earlier = group.occurrences.filter(row => row.year === from);
            const later = group.occurrences.filter(row => row.year === to);
            if (earlier.length !== 1 || later.length !== 1
                || earlier[0].comparisonKey === later[0].comparisonKey) continue;
            gains.set(later[0].comparisonKey, (gains.get(later[0].comparisonKey) || 0) + 1);
            losses.set(earlier[0].comparisonKey, (losses.get(earlier[0].comparisonKey) || 0) + 1);
            totalMovements += 1;
        }

        const limit = Number.isSafeInteger(Number(options.limit))
            ? Math.max(1, Number(options.limit)) : 5;
        const rows = (counts, year) => [...counts].map(([comparisonKey, count]) => {
            const observation = observations.get(`${year}:${comparisonKey}`);
            return {
                comparisonKey,
                partyNameAr: observation?.nameAr || comparisonKey,
                abbreviation: observation?.abbreviation || null,
                abbreviationStatus: observation?.abbreviationStatus || null,
                count,
                evidenceStatus: 'name_match_only'
            };
        }).sort((left, right) => right.count - left.count
            || codepointOrder(left.partyNameAr, right.partyNameAr)
            || codepointOrder(left.comparisonKey, right.comparisonKey)).slice(0, limit);
        const gainRows = rows(gains, to);
        const lossRows = rows(losses, from);
        return {
            available: true,
            diagnostics: [],
            fromYear: from,
            toYear: to,
            evidenceStatus: 'name_match_only',
            totalMovements,
            maximum: Math.max(0, ...gainRows.map(row => row.count), ...lossRows.map(row => row.count)),
            gains: gainRows,
            losses: lossRows
        };
    }

    return {
        auditHistoricalPayload,
        listValidYearPairs,
        resolveYearPair,
        deriveNationalOverview,
        derivePartyDeltas,
        derivePartyTrajectory,
        deriveRegionComparison,
        deriveTurnoutTrend,
        deriveDemographicTrends,
        deriveRepeatedNameGroups,
        deriveRepeatedNamePartyMovements
    };
});
