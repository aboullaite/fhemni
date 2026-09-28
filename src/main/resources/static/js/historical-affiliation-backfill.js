(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.FhemniHistoricalAffiliationBackfill = api;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
    'use strict';
    const text = value => typeof value === 'string' && value.trim().length > 0;
    const date = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '') && Number.isFinite(Date.parse(value))
        && new Date(value).toISOString().slice(0, 10) === value;
    const safeUrl = value => {
        try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password; }
        catch (_) { return false; }
    };
    const match = (row, ref) => row.year === ref?.year && row.nameAr === ref?.nameAr
        && row.constituencyId === ref?.constituencyId;
    const exactMatch = (row, ref) => match(row, ref) && row.partyId === ref?.partyId
        && row.sourceQueryId === ref?.sourceQueryId;

    // Supplemental observations never mutate the election archive or manufacture candidates.
    function augmentGroups(payload, groups, backfill, fromYear, toYear, scope) {
        if (backfill == null) return { diagnostics: [], groups };
        const invalid = () => ({ diagnostics: ['affiliation-backfill'], groups: [] });
        if (backfill.schemaVersion !== 1 || !Array.isArray(backfill.records)) return invalid();
        const ids = new Set(), identities = new Set();
        const prepared = [];
        for (const row of backfill.records) {
            const party = payload.partyObservations.find(p => p.year === row?.year && p.partyId === row.partyId);
            const targets = payload.candidateRecords.filter(c => match(c, row?.target));
            const key = `${row?.year}:${row?.target?.year}:${row?.target?.nameAr}:${row?.target?.constituencyId}`;
            if (!text(row?.id) || ids.has(row.id) || identities.has(key) || !party || targets.length !== 1
                || ![2016, 2021].includes(row.year) || row.year >= row.target.year
                || !text(row.nameAr) || !text(row.constituencyNameAr) || !text(row.identityBasis)
                || ((row.kind === 'retrospective_local_office' || row.datePrecision === 'year') && row.observedAt === null ? false
                    : !date(row.observedAt) || Number(row.observedAt.slice(0, 4)) !== row.year)
                || !['name_alias', 'parliament_roster', 'local_office', 'legislative_candidate',
                    'party_membership', 'retrospective_local_office'].includes(row.kind)
                || row.elected !== null || !Array.isArray(row.evidence) || !row.evidence.length
                || row.evidence.some(e => !safeUrl(e.url) || !text(e.publisher) || !text(e.claim)
                    || (e.publishedAt !== null && !date(e.publishedAt)))) return invalid();
            ids.add(row.id); identities.add(key);
            const supersedes = row.supersedesNameMatch;
            let superseded;
            if (Object.hasOwn(row, 'supersedesNameMatch')) {
                const priors = payload.candidateRecords.filter(c => exactMatch(c, supersedes));
                if (!supersedes || Array.isArray(supersedes) || row.kind === 'name_alias'
                    || supersedes.year !== row.year || !text(supersedes.nameAr)
                    || !text(supersedes.constituencyId) || !text(supersedes.partyId)
                    || !text(supersedes.sourceQueryId) || priors.length !== 1
                    || priors[0].normalizedName !== targets[0].normalizedName) return invalid();
                superseded = priors[0];
            }
            let occurrence;
            if (row.kind === 'name_alias') {
                const aliases = payload.candidateRecords.filter(c => match(c, row.aliasOf));
                if (aliases.length !== 1 || aliases[0].year !== row.year || aliases[0].partyId !== row.partyId
                    || aliases[0].nameAr !== row.nameAr) return invalid();
                occurrence = { ...aliases[0] };
            } else {
                if (row.aliasOf != null) return invalid();
                occurrence = { year: row.year, nameAr: row.nameAr, normalizedName: targets[0].normalizedName,
                    partyId: party.partyId, abbreviation: party.abbreviation,
                    comparisonKey: party.comparisonKey, abbreviationStatus: party.abbreviationStatus,
                    constituencyNameAr: row.constituencyNameAr, constituencyType: null,
                    elected: null, electedId: null, factStatus: 'SOURCED_PRIOR_AFFILIATION' };
            }
            occurrence.affiliationKind = row.kind;
            occurrence.observedAt = row.observedAt;
            occurrence.datePrecision = row.observedAt === null ? 'year' : 'day';
            occurrence.evidence = row.evidence.map(e => ({ ...e }));
            prepared.push({ row, occurrence, target: targets[0], superseded });
        }
        const result = groups.map(g => ({ ...g, occurrences: g.occurrences.map(r => ({ ...r })) }));
        for (const { row, occurrence, target, superseded } of prepared) {
            if (row.year !== Number(fromYear) || target.year !== Number(toYear)) continue;
            const includeOccurrence = scope !== 'elected'
                || (row.kind === 'name_alias' && occurrence.elected && target.elected);
            if (!includeOccurrence && !(superseded?.elected && target.elected)) continue;
            let group = result.find(g => g.normalizedName === target.normalizedName);
            if (row.supersedesNameMatch) {
                // A reviewed correction removes only its exact derived name-link, never archive data.
                const priors = group?.occurrences.filter(r => exactMatch(r, row.supersedesNameMatch)) || [];
                if (result.filter(g => g.normalizedName === target.normalizedName).length !== 1
                    || priors.length !== 1 || priors[0].affiliationKind
                    || group.occurrences.filter(r => r.year === target.year).length !== 1
                    || !group.occurrences.some(r => exactMatch(r, target))) return invalid();
                group.occurrences = group.occurrences.filter(r => r !== priors[0]);
            }
            // Reject a disproven parliamentary link without replacing it with a municipal outcome.
            if (!includeOccurrence) continue;
            if (!group) {
                group = { normalizedName: target.normalizedName, occurrences: [{ ...target }] };
                result.push(group);
            }
            // Unreviewed collisions and any remaining ambiguous observations still fail closed.
            if (group.occurrences.some(r => r.year === row.year)) return invalid();
            group.occurrences.push(occurrence);
            group.evidenceStatus = row.kind === 'name_alias' ? 'reviewed_name_alias' : 'sourced_affiliation';
        }
        return { diagnostics: [], groups: result };
    }
    return { augmentGroups };
});
