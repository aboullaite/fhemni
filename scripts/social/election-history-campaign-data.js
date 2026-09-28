const defaultInsights = require('../../src/main/resources/static/js/historical-election-insights.js');
const defaultQuotient = require('../../src/main/resources/static/js/historical-electoral-quotient.js');

function codepointOrder(left, right) {
    return left < right ? -1 : left > right ? 1 : 0;
}

function requireAvailable(result, label) {
    if (result.available) return result;
    const diagnostics = result.diagnostics || [];
    const error = new Error(`${label} unavailable: ${diagnostics.join(', ')}`);
    error.diagnostics = diagnostics;
    throw error;
}

function partyRow(row) {
    return {
        comparisonKey: row.comparisonKey,
        nameAr: row.nameAr,
        abbreviation: row.abbreviation,
        continuityBasis: row.continuityBasis,
        earlierSeats: row.earlier.totalSeats,
        laterSeats: row.later.totalSeats,
        delta: row.delta.totalSeats
    };
}

function transhumanceRow(row) {
    return {
        comparisonKey: row.comparisonKey,
        nameAr: row.partyNameAr,
        abbreviation: row.abbreviation,
        count: row.count
    };
}

function demographicPoint(point) {
    return {
        year: point.year,
        percentage: point.percentage,
        valueStatus: point.valueStatus,
        factStatus: point.factStatus,
        sourceQueryId: point.sourceQueryId,
        sourcePercentageText: point.sourcePercentageText
    };
}

function buildCampaignData(payload, {
    insights = defaultInsights,
    quotient = defaultQuotient,
    sourceSha256
} = {}) {
    const audit = requireAvailable(insights.auditHistoricalPayload(payload), 'historical audit');
    const parties = requireAvailable(insights.derivePartyDeltas(payload, 2021, 2026,
        { measure: 'total', showAll: true }), 'party movement');
    const politicalTranshumance = requireAvailable(
        insights.deriveRepeatedNamePartyMovements(payload, 2021, 2026),
        'political transhumance');
    const region = requireAvailable(insights.deriveRegionComparison(payload, '6'), 'region comparison');
    const demographics = requireAvailable(insights.deriveDemographicTrends(payload), 'demographic trends');
    const simulation = requireAvailable(quotient.derive2026Full2016SystemCounterfactual(payload),
        'full-house simulation');

    const tieBreak = (left, right) => codepointOrder(left.nameAr, right.nameAr)
        || codepointOrder(left.comparisonKey, right.comparisonKey);
    const movements = parties.rows.filter(row => row.continuityBasis === 'same_exact_source_label')
        .map(partyRow);
    const gains = movements.filter(row => row.delta > 0)
        .sort((left, right) => right.delta - left.delta || tieBreak(left, right));
    const losses = movements.filter(row => row.delta < 0)
        .sort((left, right) => left.delta - right.delta || tieBreak(left, right));
    const regionalRows = region.rows.map(row => ({
        comparisonKey: row.comparisonKey,
        nameAr: row.nameAr,
        abbreviation: row.abbreviation,
        points: row.points.map(point => ({ year: point.year, localSeats: point.localSeats }))
    })).sort((left, right) =>
        Math.max(...right.points.map(point => point.localSeats))
            - Math.max(...left.points.map(point => point.localSeats)) || tieBreak(left, right))
        .slice(0, 6);
    const women = demographics.rows.find(row => row.dimension === 'gender'
        && row.categoryAr === 'نساء');
    if (!women) throw new Error('demographic trends unavailable: women');

    return {
        overview: {
            years: [...audit.years],
            electedRecordCount: payload.elected.length,
            electionCount: audit.years.length
        },
        partyMovement: {
            fromYear: 2021,
            toYear: 2026,
            measure: 'total',
            gains,
            losses
        },
        politicalTranshumance: {
            fromYear: politicalTranshumance.fromYear,
            toYear: politicalTranshumance.toYear,
            totalMovements: politicalTranshumance.totalMovements,
            gains: politicalTranshumance.gains.map(transhumanceRow),
            losses: politicalTranshumance.losses.map(transhumanceRow)
        },
        region: {
            regionId: region.regionId,
            regionNameAr: region.regionNameAr,
            ballotType: region.ballotType,
            qualifier: 'المقاعد المحلية فقط',
            years: [...region.years],
            rows: regionalRows
        },
        demographics: {
            women: women.points.map(demographicPoint),
            age: demographics.rows.filter(row => row.dimension === 'age')
                .map(row => ({ categoryAr: row.categoryAr,
                    points: row.points.map(demographicPoint) })),
            education: demographics.rows.filter(row => row.dimension === 'education')
                .map(row => ({ categoryAr: row.categoryAr,
                    points: row.points.map(demographicPoint) }))
        },
        quotient: {
            year: simulation.year,
            qualifier: 'محاكاة، ماشي نتيجة رسمية',
            listScenario: simulation.listScenario,
            officialSeatTotal: simulation.officialSeatTotal,
            simulatedSeatTotal: simulation.simulatedSeatTotal,
            rows: simulation.partyDeltas.filter(row => row.delta !== 0).map(row => ({
                partyId: row.partyId,
                comparisonKey: row.comparisonKey,
                nameAr: row.nameAr,
                abbreviation: row.abbreviation,
                officialSeats: row.officialTotalSeats,
                simulatedSeats: row.simulatedTotalSeats,
                delta: row.delta
            })).sort((left, right) => Math.abs(right.delta) - Math.abs(left.delta)
                || tieBreak(left, right))
        },
        provenance: {
            sourceSha256,
            sourceUrls: [...payload.generation.sourceUrls]
        }
    };
}

module.exports = { buildCampaignData };
