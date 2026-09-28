#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const PINNED_SOURCE_SHA256 =
    '13378267af52eecba43d0bab4d3de5330351f48f4c9e3c580107da995f6b7ce1';

const EXPECTED_YEARS = [2016, 2021, 2026];
const EXPECTED_BALLOTS = {
    2016: {
        local: { seats: 305, votes: 5790552 },
        national: { seats: 90, votes: 5806004 }
    },
    2021: {
        local: { seats: 305, votes: 7588505 },
        regional: { seats: 90, votes: 7571623 }
    },
    2026: {
        local: { seats: 305, votes: 4900377 },
        regional: { seats: 90, votes: 4838149 }
    }
};
const DEMOGRAPHIC_DIMENSIONS = { gender: 2, age: 4, education: 4 };
const ANALYSIS_FACT_STATUS = 'FHEMNI_ANALYSIS_BASED_ON_ELECTIONS_MA';
const REPORTED_FACT_STATUS = 'REPORTED_ELECTIONS_MA';
const ABBREVIATION_STATUS = 'ARCHIVE_FIELD_NOT_VERIFIED_IDENTITY';
const DEFAULT_SOURCE = resolve(dirname(fileURLToPath(import.meta.url)),
    '../../data/elections/history/morocco-legislative-results.json');
const DEFAULT_MANIFEST = resolve(dirname(fileURLToPath(import.meta.url)),
    '../../data/elections/history/manifest.json');
const DEFAULT_OUTPUT = resolve('src/main/resources/static/data/elections/history.json');

function fail(message) {
    throw new Error(message);
}

function compareText(left, right) {
    return left < right ? -1 : left > right ? 1 : 0;
}

function compareYearThen(left, right, key) {
    return left.year - right.year || compareText(String(left[key]), String(right[key]));
}

function sum(rows, valueOf) {
    return rows.reduce((total, row) => total + valueOf(row), 0);
}

function assertUnique(rows, keyOf, label) {
    const seen = new Set();
    for (const row of rows) {
        const key = keyOf(row);
        if (seen.has(key)) {
            fail(`${label} contains duplicate key ${key}`);
        }
        seen.add(key);
    }
}

function assertNonnegativeInteger(value, label, { nullable = false } = {}) {
    if (nullable && value === null) return;
    if (!Number.isInteger(value) || value < 0) {
        fail(`${label} must be a nonnegative integer`);
    }
}

function assertPercentage(value, label) {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 100) {
        fail(`${label} must be between 0 and 100`);
    }
}

function assertLiteralSourceText(value, label) {
    if (typeof value !== 'string' || value.length === 0) {
        fail(`${label} requires literal source text`);
    }
}

function sameNullable(left, right) {
    return left === right || left == null && right == null;
}

function assertOfficialUrl(url, label) {
    let parsed;
    try {
        parsed = new URL(url);
    } catch {
        fail(`${label} is not a valid URL`);
    }
    if (parsed.protocol !== 'https:' || parsed.hostname !== 'www.elections.ma') {
        fail(`${label} must use https://www.elections.ma`);
    }
}

function yearFromElection(election) {
    const year = Number(String(election.election_date).slice(0, 4));
    if (!EXPECTED_YEARS.includes(year)) {
        fail(`Unexpected election year ${year}`);
    }
    return year;
}

export function normalizeOfficialName(name) {
    return String(name).normalize('NFC').trim().replace(/\s+/gu, ' ');
}

function validateSourceShape(source) {
    for (const key of ['elections', 'regions', 'constituencies', 'parties', 'votes', 'elected_candidates',
        'seat_results', 'turnout', 'demographics', 'queries']) {
        if (!Array.isArray(source?.[key])) {
            fail(`Archive field ${key} must be an array`);
        }
    }
    const years = source.elections.map(yearFromElection).sort((a, b) => a - b);
    if (years.length !== 3 || years.some((year, index) => year !== EXPECTED_YEARS[index])) {
        fail('Archive must contain exactly the three elections 2016, 2021, and 2026');
    }
    assertUnique(source.elections, row => row.election_id, 'elections');
    assertUnique(source.regions, row => `${row.election_id}:${row.region_id}`, 'regions');
    assertUnique(source.constituencies,
        row => `${row.election_id}:${row.constituency_id}`, 'constituencies');
    assertUnique(source.parties, row => `${row.election_id}:${row.party_id}`, 'parties');
    assertUnique(source.elected_candidates, row => row.elected_id, 'elected candidates');
    assertUnique(source.elected_candidates,
        row => `${row.election_id}:${row.source_query_id}:${row.source_row}`, 'elected mandate rows');
    assertUnique(source.votes,
        row => `${row.election_id}:${row.source_query_id}:${row.party_id}`, 'votes');
    assertUnique(source.seat_results,
        row => `${row.election_id}:${row.query_id}:${row.party_id}`, 'seat results');
    assertUnique(source.turnout,
        row => `${row.election_id}:${row.query_id}:${row.scope}`, 'turnout');
    assertUnique(source.demographics,
        row => `${row.election_id}:${row.query_id}:${row.dimension}:${row.category_ar}`, 'demographics');
    assertUnique(source.queries, row => `${row.election_id}:${row.query_id}`, 'queries');

    for (const row of source.votes) {
        assertNonnegativeInteger(row.votes, 'vote value');
        assertNonnegativeInteger(row.seats, 'vote-row seats');
        assertNonnegativeInteger(row.source_row, 'vote source row');
        assertLiteralSourceText(row.votes_source_text, 'parsed vote value');
    }
    for (const row of source.elected_candidates) {
        assertNonnegativeInteger(row.source_row, 'elected mandate source row');
    }
    for (const row of source.seat_results) {
        assertNonnegativeInteger(row.seats, 'seat-result seats');
        assertPercentage(row.seat_percentage, 'seat-result percentage');
        if (String(row.value_status).startsWith('reported')) {
            assertLiteralSourceText(row.seat_percentage_source_text, 'reported seat-result percentage');
        }
    }
    for (const row of source.turnout) {
        assertPercentage(row.turnout_percentage, 'turnout percentage');
        assertLiteralSourceText(row.source_percentage_text, 'turnout percentage');
    }
    for (const row of source.demographics) {
        assertPercentage(row.percentage, 'demographic percentage');
        assertNonnegativeInteger(row.count_inferred_from_percentage, 'demographic inferred count');
        assertNonnegativeInteger(row.population_seats, 'demographic population seats');
        if (row.value_status === 'reported') {
            assertLiteralSourceText(row.source_percentage_text, 'reported demographic percentage');
        }
    }
    for (const row of source.queries) {
        assertNonnegativeInteger(row.vote_rows, 'query vote-row count');
        assertNonnegativeInteger(row.elected_rows, 'query elected-row count');
        assertNonnegativeInteger(row.seat_rows, 'query seat-row count');
        assertNonnegativeInteger(row.seat_total, 'query seat total', { nullable: true });
    }
    for (const election of source.elections) {
        assertOfficialUrl(election.source_url, `${election.election_id} source URL`);
    }
}

function buildElectionIndexes(source) {
    const byId = new Map();
    for (const election of source.elections) {
        byId.set(election.election_id, { election, year: yearFromElection(election) });
    }
    const yearOf = electionId => {
        const entry = byId.get(electionId);
        if (!entry) {
            fail(`Unknown election ID ${electionId}`);
        }
        return entry.year;
    };
    return { byId, yearOf };
}

function validateContestMandates(source) {
    const electionById = new Map(source.elections.map(row => [row.election_id, row]));
    const partyByKey = new Map(source.parties
        .map(row => [`${row.election_id}:${row.party_id}`, row]));
    const queryByKey = new Map(source.queries
        .map(row => [`${row.election_id}:${row.query_id}`, row]));
    const constituencyByKey = new Map(source.constituencies
        .map(row => [`${row.election_id}:${row.constituency_id}`, row]));
    const canonicalQueryKeys = new Set(source.queries
        .filter(row => row.is_canonical_constituency)
        .map(row => `${row.election_id}:${row.query_id}`));
    const mappedCanonicalQueries = new Set();

    for (const constituency of source.constituencies) {
        const contestKey = `${constituency.election_id}:${constituency.source_query_id}`;
        const query = queryByKey.get(contestKey);
        if (!query || !query.is_canonical_constituency) {
            fail(`Constituency ${contestKey} must reference one canonical query`);
        }
        mappedCanonicalQueries.add(contestKey);
        const expectedRegion = constituency.region_id ?? '0';
        const expectedProvince = constituency.province_id ?? '0';
        if (query.constituency_filter_id !== constituency.constituency_id
                || query.region_id !== expectedRegion || query.province_id !== expectedProvince) {
            fail(`Constituency/query geography mismatch for ${contestKey}`);
        }
        const election = electionById.get(constituency.election_id);
        if (!election || query.source_url !== election.source_url) {
            fail(`Constituency query source mismatch for ${contestKey}`);
        }

        const votes = source.votes.filter(row => row.election_id === constituency.election_id
            && row.source_query_id === constituency.source_query_id);
        const mandates = source.elected_candidates.filter(row => row.election_id === constituency.election_id
            && row.source_query_id === constituency.source_query_id);
        const seatResults = source.seat_results.filter(row => row.election_id === constituency.election_id
            && row.query_id === constituency.source_query_id);
        if (query.vote_rows !== votes.length || query.elected_rows !== mandates.length
                || query.seat_rows !== seatResults.length || query.seat_total !== mandates.length
                || sum(votes, row => row.seats) !== mandates.length) {
            fail(`Exact contest allocation mismatch for ${contestKey}`);
        }

        const voteSeatsByParty = new Map();
        for (const vote of votes) {
            const party = partyByKey.get(`${vote.election_id}:${vote.party_id}`);
            if (!party || vote.party_name_ar !== party.name_ar) {
                fail(`Contest vote references inconsistent party ${vote.party_id}`);
            }
            if (vote.constituency_id !== constituency.constituency_id
                    || vote.constituency_type !== constituency.type
                    || !sameNullable(vote.region_id, constituency.region_id)
                    || !sameNullable(vote.province_id, constituency.province_id)) {
                fail(`Contest vote geography mismatch for ${contestKey}`);
            }
            voteSeatsByParty.set(vote.party_id, vote.seats);
        }

        const mandateSeatsByParty = new Map();
        for (const mandate of mandates) {
            const party = partyByKey.get(`${mandate.election_id}:${mandate.party_id}`);
            if (!party || mandate.party_abbreviation !== party.abbreviation) {
                fail(`Mandate party evidence mismatch for ${mandate.elected_id}`);
            }
            if (!voteSeatsByParty.has(mandate.party_id)) {
                fail(`Mandate party has no contest vote fact for ${mandate.elected_id}`);
            }
            if (mandate.constituency_id !== constituency.constituency_id
                    || mandate.constituency_type !== constituency.type
                    || !sameNullable(mandate.region_id, constituency.region_id)
                    || !sameNullable(mandate.province_id, constituency.province_id)) {
                fail(`Mandate region/constituency mismatch for ${mandate.elected_id}`);
            }
            mandateSeatsByParty.set(mandate.party_id,
                (mandateSeatsByParty.get(mandate.party_id) || 0) + 1);
        }
        for (const partyId of new Set([...voteSeatsByParty.keys(), ...mandateSeatsByParty.keys()])) {
            if ((voteSeatsByParty.get(partyId) || 0) !== (mandateSeatsByParty.get(partyId) || 0)) {
                fail(`Mandate party allocation mismatch for ${contestKey}:${partyId}`);
            }
        }
    }
    if (mappedCanonicalQueries.size !== canonicalQueryKeys.size
            || [...canonicalQueryKeys].some(key => !mappedCanonicalQueries.has(key))) {
        fail('Every canonical query must map to exactly one constituency contest');
    }
    for (const row of [...source.votes, ...source.elected_candidates]) {
        const constituency = constituencyByKey.get(`${row.election_id}:${row.constituency_id}`);
        if (!constituency || constituency.source_query_id !== row.source_query_id) {
            fail(`Observation has no matching constituency/query fact ${row.source_query_id}`);
        }
    }
}

function validateElectionInvariants(source, yearOf) {
    const canonicalQueries = new Set(source.queries
        .filter(row => row.is_canonical_constituency)
        .map(row => `${row.election_id}:${row.query_id}`));
    for (const row of [...source.votes, ...source.elected_candidates]) {
        if (!canonicalQueries.has(`${row.election_id}:${row.source_query_id}`)) {
            fail(`Observation references non-canonical query ${row.source_query_id}`);
        }
    }

    for (const year of EXPECTED_YEARS) {
        const electionId = source.elections.find(row => yearFromElection(row) === year).election_id;
        const elected = source.elected_candidates.filter(row => row.election_id === electionId);
        if (elected.length !== 395) {
            fail(`${year} must contain exactly 395 elected observations`);
        }
        const electedByBallot = Object.fromEntries(Object.entries(EXPECTED_BALLOTS[year])
            .map(([type]) => [type, elected.filter(row => row.constituency_type === type).length]));
        if (electedByBallot.local !== 305
                || electedByBallot.national === 90 && year !== 2016
                || electedByBallot.regional === 90 && year === 2016
                || Object.entries(EXPECTED_BALLOTS[year])
                    .some(([type, expected]) => electedByBallot[type] !== expected.seats)) {
            fail(`${year} ballot seats must reconcile to 305 local and 90 ${year === 2016 ? 'national' : 'regional'}`);
        }

        const voteRows = source.votes.filter(row => row.election_id === electionId);
        const observedTypes = [...new Set(voteRows.map(row => row.constituency_type))].sort(compareText);
        const expectedTypes = Object.keys(EXPECTED_BALLOTS[year]).sort(compareText);
        if (JSON.stringify(observedTypes) !== JSON.stringify(expectedTypes)) {
            fail(`${year} ballot types do not match the pinned local/list ballot contract`);
        }
        for (const [type, expected] of Object.entries(EXPECTED_BALLOTS[year])) {
            const rows = voteRows.filter(row => row.constituency_type === type);
            const votes = sum(rows, row => row.votes);
            const seats = sum(rows, row => row.seats);
            if (votes !== expected.votes || seats !== expected.seats) {
                fail(`${year} ${type} ballot total mismatch: expected ${expected.votes} votes and ${expected.seats} seats`);
            }
        }

        const nationalSeats = source.seat_results
            .filter(row => row.election_id === electionId && row.query_id === `${year}:r0_p0_c0`);
        if (sum(nationalSeats, row => row.seats) !== 395) {
            fail(`${year} national seat results must total 395`);
        }
        assertUnique(nationalSeats, row => row.party_id, `${year} national seat results`);
        const nationalSeatsByParty = new Map(nationalSeats.map(row => [row.party_id, row.seats]));
        const ballotSeatsByParty = new Map();
        for (const row of voteRows) {
            ballotSeatsByParty.set(row.party_id,
                (ballotSeatsByParty.get(row.party_id) || 0) + row.seats);
        }
        for (const partyId of new Set([...nationalSeatsByParty.keys(), ...ballotSeatsByParty.keys()])) {
            if ((nationalSeatsByParty.get(partyId) || 0) !== (ballotSeatsByParty.get(partyId) || 0)) {
                fail(`${year} party seat total mismatch for ${partyId}`);
            }
        }

        const turnout = source.turnout.filter(row => row.election_id === electionId
            && row.query_id === `${year}:r0_p0_c0` && row.scope === 'national_reference');
        if (turnout.length !== 1 || typeof turnout[0].turnout_percentage !== 'number') {
            fail(`${year} must have exactly one national_reference turnout observation`);
        }

        const demographics = source.demographics.filter(row => row.election_id === electionId
            && row.query_id === `${year}:r0_p0_c0`);
        for (const [dimension, expectedCategories] of Object.entries(DEMOGRAPHIC_DIMENSIONS)) {
            const categories = demographics.filter(row => row.dimension === dimension);
            if (categories.length !== expectedCategories
                    || sum(categories, row => row.count_inferred_from_percentage) !== 395) {
                fail(`${year} ${dimension} national demographic counts must reconcile to 395`);
            }
            const percentageTotal = sum(categories, row => row.percentage);
            if (Math.abs(percentageTotal - 100) > 0.02) {
                fail(`${year} ${dimension} national demographic percentages do not reconcile to source rounding`);
            }
            if (categories.some(row => !row.value_status)) {
                fail(`${year} ${dimension} national demographic observations require value status`);
            }
        }

        const regions = source.regions.filter(row => row.election_id === electionId);
        if (regions.length !== 12) {
            fail(`${year} must contain exactly 12 regions`);
        }
    }

    for (const row of [...source.regions, ...source.parties, ...source.votes,
        ...source.elected_candidates, ...source.seat_results, ...source.turnout,
        ...source.demographics, ...source.queries]) {
        yearOf(row.election_id);
        if (typeof row.source_url === 'string') {
            assertOfficialUrl(row.source_url, 'result source URL');
        }
    }
}

function buildPartyObservations(source, yearOf) {
    const exactLabelGroups = new Map();
    for (const party of source.parties) {
        const rows = exactLabelGroups.get(party.party_id) || [];
        rows.push(party);
        exactLabelGroups.set(party.party_id, rows);
    }
    const repeatedExactLabels = new Set([...exactLabelGroups]
        .filter(([, rows]) => new Set(rows.map(row => yearOf(row.election_id))).size >= 2
            && new Set(rows.map(row => row.name_ar)).size === 1)
        .map(([partyId]) => partyId));

    return source.parties.map(party => {
        const year = yearOf(party.election_id);
        const repeatedExactLabel = repeatedExactLabels.has(party.party_id);
        return {
            year,
            partyId: party.party_id,
            nameAr: party.name_ar,
            abbreviation: party.abbreviation,
            abbreviationStatus: ABBREVIATION_STATUS,
            comparisonKey: repeatedExactLabel
                ? `exact-source-label:${party.party_id}`
                : `source:${year}:${party.party_id}`,
            continuityBasis: repeatedExactLabel ? 'same_exact_source_label' : 'source_observation_only',
            comparisonFactStatus: ANALYSIS_FACT_STATUS
        };
    }).sort((left, right) => compareYearThen(left, right, 'partyId'));
}

function buildNationalResults(source, partyObservations, yearOf) {
    const partyByElection = new Map(partyObservations.map(row => [`${row.year}:${row.partyId}`, row]));
    const groups = new Map();
    for (const row of source.votes) {
        const year = yearOf(row.election_id);
        const key = `${year}:${row.party_id}`;
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key).push(row);
    }
    return [...groups].map(([key, rows]) => {
        const party = partyByElection.get(key);
        if (!party) fail(`Vote rows reference unknown party ${key}`);
        const ballotVotes = [...new Set(rows.map(row => row.constituency_type))]
            .sort(compareText)
            .map(type => ({
                type,
                votes: sum(rows.filter(row => row.constituency_type === type), row => row.votes),
                seats: sum(rows.filter(row => row.constituency_type === type), row => row.seats)
            }));
        return {
            year: party.year,
            partyId: party.partyId,
            comparisonKey: party.comparisonKey,
            nameAr: party.nameAr,
            abbreviation: party.abbreviation,
            abbreviationStatus: ABBREVIATION_STATUS,
            seats: sum(rows, row => row.seats),
            ballotVotes,
            factStatus: ANALYSIS_FACT_STATUS
        };
    }).sort((left, right) => compareYearThen(left, right, 'partyId'));
}

function buildElected(source, partyObservations, yearOf) {
    const partyByElection = new Map(partyObservations.map(row => [`${row.year}:${row.partyId}`, row]));
    return source.elected_candidates.map(row => {
        const year = yearOf(row.election_id);
        const party = partyByElection.get(`${year}:${row.party_id}`);
        if (!party) fail(`Elected observation references unknown party ${year}:${row.party_id}`);
        return {
            year,
            electedId: row.elected_id,
            nameAr: row.name_ar,
            normalizedName: normalizeOfficialName(row.name_ar),
            partyId: row.party_id,
            abbreviation: row.party_abbreviation,
            abbreviationStatus: ABBREVIATION_STATUS,
            comparisonKey: party.comparisonKey,
            constituencyType: row.constituency_type,
            regionId: row.region_id === '0' ? null : row.region_id,
            provinceId: row.province_id === '0' ? null : row.province_id,
            constituencyId: row.constituency_id,
            constituencyNameAr: row.constituency_name_source_ar,
            sourceQueryId: row.source_query_id,
            sourceRow: row.source_row,
            factStatus: REPORTED_FACT_STATUS,
            normalizedNameStatus: ANALYSIS_FACT_STATUS
        };
    }).sort((left, right) => left.year - right.year
        || compareText(left.constituencyType, right.constituencyType)
        || compareText(left.electedId, right.electedId));
}

function buildRepeatedNames(elected) {
    const groups = new Map();
    for (const row of elected) {
        if (!groups.has(row.normalizedName)) groups.set(row.normalizedName, []);
        groups.get(row.normalizedName).push(row);
    }
    return [...groups]
        .filter(([, rows]) => {
            const counts = new Map();
            for (const row of rows) counts.set(row.year, (counts.get(row.year) || 0) + 1);
            return counts.size >= 2 && [...counts.values()].every(count => count === 1);
        })
        .map(([normalizedName, rows]) => ({
            normalizedName,
            evidenceStatus: 'name_match_only',
            factStatus: ANALYSIS_FACT_STATUS,
            occurrences: rows.map(row => ({
                year: row.year,
                electedId: row.electedId,
                nameAr: row.nameAr,
                partyId: row.partyId,
                abbreviation: row.abbreviation,
                abbreviationStatus: ABBREVIATION_STATUS,
                comparisonKey: row.comparisonKey,
                constituencyType: row.constituencyType,
                constituencyNameAr: row.constituencyNameAr
            })).sort((left, right) => left.year - right.year)
        }))
        .sort((left, right) => compareText(left.normalizedName, right.normalizedName));
}

function buildRegionalLocalSeats(source, elected, partyObservations, yearOf) {
    const partyByElection = new Map(partyObservations.map(row => [`${row.year}:${row.partyId}`, row]));
    return source.regions.map(region => {
        const year = yearOf(region.election_id);
        const counts = new Map();
        for (const row of elected.filter(candidate => candidate.year === year
                && candidate.constituencyType === 'local' && candidate.regionId === region.region_id)) {
            counts.set(row.partyId, (counts.get(row.partyId) || 0) + 1);
        }
        const parties = [...counts].map(([partyId, seats]) => {
            const party = partyByElection.get(`${year}:${partyId}`);
            return {
                partyId,
                comparisonKey: party.comparisonKey,
                nameAr: party.nameAr,
                abbreviation: party.abbreviation,
                abbreviationStatus: ABBREVIATION_STATUS,
                seats
            };
        }).sort((left, right) => compareText(left.partyId, right.partyId));
        return {
            year,
            regionId: region.region_id,
            regionNameAr: region.name_ar,
            parties,
            factStatus: ANALYSIS_FACT_STATUS
        };
    }).sort((left, right) => compareYearThen(left, right, 'regionId'));
}

function buildNationalDemographics(source, yearOf) {
    return source.demographics
        .filter(row => row.query_id === `${yearOf(row.election_id)}:r0_p0_c0`
            && Object.hasOwn(DEMOGRAPHIC_DIMENSIONS, row.dimension)
            && row.value_status === 'reported'
            && typeof row.source_percentage_text === 'string'
            && row.source_percentage_text.length > 0)
        .map(row => ({
            year: yearOf(row.election_id),
            dimension: row.dimension,
            categoryAr: row.category_ar,
            percentage: row.percentage,
            valueStatus: row.value_status,
            sourcePercentageText: row.source_percentage_text,
            sourceQueryId: row.query_id,
            factStatus: REPORTED_FACT_STATUS
        }))
        .sort((left, right) => left.year - right.year
            || Object.keys(DEMOGRAPHIC_DIMENSIONS).indexOf(left.dimension)
                - Object.keys(DEMOGRAPHIC_DIMENSIONS).indexOf(right.dimension)
            || compareText(left.categoryAr, right.categoryAr));
}

function buildLocalConstituencyResults(source, partyObservations, yearOf) {
    const includedYears = new Set([2016, 2026]);
    const partyByElection = new Map(partyObservations.map(row => [`${row.year}:${row.partyId}`, row]));
    const queryByElection = new Map(source.queries
        .map(row => [`${row.election_id}:${row.query_id}`, row]));
    const regionByElection = new Map(source.regions
        .map(row => [`${row.election_id}:${row.region_id}`, row]));
    return source.constituencies
        .filter(row => row.type === 'local' && includedYears.has(yearOf(row.election_id)))
        .map(constituency => {
            const year = yearOf(constituency.election_id);
            const query = queryByElection.get(
                `${constituency.election_id}:${constituency.source_query_id}`);
            const region = regionByElection.get(
                `${constituency.election_id}:${constituency.region_id}`);
            const votes = source.votes.filter(row => row.election_id === constituency.election_id
                && row.source_query_id === constituency.source_query_id)
                .sort((left, right) => left.source_row - right.source_row);
            const parties = votes.map(row => {
                const party = partyByElection.get(`${year}:${row.party_id}`);
                if (!party) fail(`Local contest references unknown party ${year}:${row.party_id}`);
                return {
                    partyId: row.party_id,
                    comparisonKey: party.comparisonKey,
                    nameAr: party.nameAr,
                    abbreviation: party.abbreviation,
                    abbreviationStatus: ABBREVIATION_STATUS,
                    votes: row.votes,
                    votesSourceText: row.votes_source_text,
                    officialSeats: row.seats,
                    sourceRow: row.source_row,
                    voteFactStatus: REPORTED_FACT_STATUS,
                    officialSeatFactStatus: REPORTED_FACT_STATUS
                };
            });
            return {
                year,
                constituencyId: constituency.constituency_id,
                constituencyType: constituency.type,
                constituencyNameAr: constituency.name_ar,
                regionId: constituency.region_id,
                regionNameAr: region?.name_ar,
                provinceId: constituency.province_id,
                sourceQueryId: constituency.source_query_id,
                sourceUrl: query?.source_url,
                allocatedSeats: query?.seat_total,
                totalVotes: sum(votes, row => row.votes),
                aggregateFactStatus: ANALYSIS_FACT_STATUS,
                parties
            };
        })
        .sort((left, right) => left.year - right.year
            || compareText(left.constituencyId, right.constituencyId));
}

export function buildHistoricalPayload(source, manifest = undefined) {
    validateSourceShape(source);
    const { yearOf } = buildElectionIndexes(source);
    validateContestMandates(source);
    validateElectionInvariants(source, yearOf);
    const partyObservations = buildPartyObservations(source, yearOf);
    const nationalPartyResults = buildNationalResults(source, partyObservations, yearOf);
    const elected = buildElected(source, partyObservations, yearOf);
    const regionalLocalSeats = buildRegionalLocalSeats(source, elected, partyObservations, yearOf);
    for (const year of EXPECTED_YEARS) {
        const localSeats = sum(regionalLocalSeats.filter(row => row.year === year)
            .flatMap(row => row.parties), row => row.seats);
        if (localSeats !== 305) fail(`${year} regional local-seat vectors must total 305`);
    }
    const sourceUrls = manifest?.sourceUrls || source.elections.map(row => row.source_url);
    sourceUrls.forEach((url, index) => assertOfficialUrl(url, `manifest source URL ${index + 1}`));

    const elections = source.elections.map(election => {
        const year = yearFromElection(election);
        const voteRows = source.votes.filter(row => row.election_id === election.election_id);
        const turnout = source.turnout.find(row => row.election_id === election.election_id
            && row.query_id === `${year}:r0_p0_c0` && row.scope === 'national_reference');
        return {
            year,
            electionId: election.election_id,
            date: election.election_date,
            sourceDateLabelAr: election.source_date_label_ar,
            sourceUrl: election.source_url,
            turnoutPercentage: turnout.turnout_percentage,
            turnoutSourcePercentageText: turnout.source_percentage_text,
            turnoutSourceQueryId: turnout.query_id,
            turnoutFactStatus: REPORTED_FACT_STATUS,
            totalSeats: 395,
            electedCount: 395,
            ballots: Object.keys(EXPECTED_BALLOTS[year]).map(type => ({
                type,
                seats: sum(voteRows.filter(row => row.constituency_type === type), row => row.seats),
                votes: sum(voteRows.filter(row => row.constituency_type === type), row => row.votes),
                factStatus: ANALYSIS_FACT_STATUS
            })),
            capabilities: {
                regionalComparisonBallot: 'local',
                listBallotType: year === 2016 ? 'national' : 'regional',
                listBallotAssignableToRegions: year !== 2016
            }
        };
    }).sort((left, right) => left.year - right.year);

    return {
        schemaVersion: 1,
        generation: {
            sourceSha256: manifest?.sha256 || PINNED_SOURCE_SHA256,
            sourceArchive: manifest?.archive || 'morocco-legislative-results.json',
            sourceDatasetId: source.metadata?.dataset_id,
            sourceUrls,
            aggregateFactStatus: ANALYSIS_FACT_STATUS
        },
        elections,
        partyObservations,
        nationalPartyResults,
        nationalDemographics: buildNationalDemographics(source, yearOf),
        regionalLocalSeats,
        localConstituencyResults: buildLocalConstituencyResults(
            source, partyObservations, yearOf),
        elected,
        repeatedNames: buildRepeatedNames(elected)
    };
}

function validateManifest(manifest, source) {
    if (manifest?.schemaVersion !== 1
            || manifest.archive !== 'morocco-legislative-results.json'
            || manifest.sha256 !== PINNED_SOURCE_SHA256
            || !Array.isArray(manifest.sourceUrls)
            || manifest.sourceUrls.length !== 3) {
        fail('Historical archive manifest does not match the pinned SHA-256 contract');
    }
    manifest.sourceUrls.forEach((url, index) => assertOfficialUrl(url, `manifest source URL ${index + 1}`));
    const expectedSourceUrls = source.elections
        .map(election => ({ year: yearFromElection(election), sourceUrl: election.source_url }))
        .sort((left, right) => left.year - right.year)
        .map(row => row.sourceUrl);
    if (new Set(manifest.sourceUrls).size !== manifest.sourceUrls.length
            || JSON.stringify(manifest.sourceUrls) !== JSON.stringify(expectedSourceUrls)) {
        fail('Manifest source URLs must be unique and exactly match election URLs in year order');
    }
}

export async function generateHistoricalElectionData({
    sourcePath = DEFAULT_SOURCE,
    manifestPath = DEFAULT_MANIFEST,
    outputPath = DEFAULT_OUTPUT
} = {}) {
    const [sourceBytes, manifestBytes] = await Promise.all([
        readFile(sourcePath),
        readFile(manifestPath, 'utf8')
    ]);
    const digest = createHash('sha256').update(sourceBytes).digest('hex');
    if (digest !== PINNED_SOURCE_SHA256) {
        fail(`Historical archive SHA-256 digest mismatch: expected ${PINNED_SOURCE_SHA256}, received ${digest}`);
    }
    const manifest = JSON.parse(manifestBytes);
    const source = JSON.parse(sourceBytes.toString('utf8'));
    validateManifest(manifest, source);
    if (source.metadata?.dataset_id !== manifest.datasetId) {
        fail('Historical archive dataset ID does not match its manifest');
    }
    const payload = buildHistoricalPayload(source, manifest);
    const output = `${JSON.stringify(payload)}\n`;
    await mkdir(dirname(outputPath), { recursive: true });
    await writeFile(outputPath, output);
    return payload;
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
    const outputPath = resolve(process.argv[2] || DEFAULT_OUTPUT);
    const sourcePath = resolve(process.argv[3] || DEFAULT_SOURCE);
    const manifestPath = resolve(process.argv[4] || DEFAULT_MANIFEST);
    generateHistoricalElectionData({ sourcePath, manifestPath, outputPath })
        .then(() => process.stdout.write(`Generated ${outputPath}\n`))
        .catch(error => {
            process.stderr.write(`${error.stack || error.message}\n`);
            process.exitCode = 1;
        });
}
