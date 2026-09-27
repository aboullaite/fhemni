#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateExactPartition, validatePinnedCrosswalk } from './urban-rural-crosswalk.mjs';

const OUTPUT = resolve(process.argv[2]
    || 'src/main/resources/static/data/elections/2026/urban-rural-constituencies.js');
const PINNED_SOURCE = resolve(process.argv[3] || resolve(dirname(fileURLToPath(import.meta.url)),
    '../../data/elections/2026/urban-rural-source-v1.8.0.json'));
const PINNED_SOURCE_SHA256 = 'a4d912f11c843cca34dff418bb740091392c66c75c9fcc39fb89387fd864d025';

const SOURCE = {
    datasetVersion: '1.8.0',
    censusYear: 2024,
    sourceUrl: 'https://communes.pages.dev/data/v1/sources.json',
    sourceLabel: 'HCP RGPH 2024 via Morocco Communes',
    decreeUrl: 'https://www.sgg.gov.ma/BO/bo_ar/2011/BO_5988_Ar.pdf',
    decreeLabel: 'Decree 2.11.603, Official Bulletin 5988',
    revisedAt: '2026-09-27',
    localSeatTotal: 305,
    excludedRegionalSeatTotal: 90
};

// Full-province constituencies. The code is the HCP province, prefecture, or
// Casablanca prefecture-of-arrondissements code whose boundary is identical
// to the electoral constituency in Decree 2.11.603.
const FULL = [
    ['al-hoceima', 4, '01.051'], ['chefchaouen', 4, '01.151'], ['fahs-anjra', 2, '01.227'],
    ['larache', 4, '01.331'], ['mdiq-fnideq', 2, '01.573'], ['ouezzane', 3, '01.405'],
    ['tanger-assilah', 5, '01.511'], ['tetouan', 5, '01.571'],
    ['berkane', 3, '02.113'], ['driouch', 3, '02.167'], ['figuig', 3, '02.251'],
    ['guercif', 2, '02.265'], ['jerada', 2, '02.275'], ['nador', 4, '02.381'],
    ['oujda-angad', 4, '02.411'], ['taourirt', 2, '02.533'],
    ['boulemane', 3, '03.131'], ['el-hajeb', 2, '03.171'], ['ifrane', 2, '03.271'],
    ['meknes', 6, '03.061'], ['moulay-yaacoub', 2, '03.591'], ['sefrou', 3, '03.451'],
    ['taza', 5, '03.561'],
    ['sidi-kacem', 5, '04.481'], ['sidi-slimane', 3, '04.491'], ['skhirate-temara', 4, '04.501'],
    ['beni-mellal', 6, '05.091'], ['fquih-ben-salah', 4, '05.255'],
    ['khenifra', 3, '05.301'], ['khouribga', 6, '05.311'],
    ['ain-chock', 3, '06.141.01.40'], ['ben-msik', 3, '06.141.01.60'],
    ['ben-slimane', 3, '06.111'], ['berrechid', 4, '06.117'],
    ['casablanca-anfa', 4, '06.141.01.00'], ['el-jadida', 6, '06.181'],
    ['hay-hassani', 3, '06.141.01.30'], ['ain-sebaa-hay-mohammadi', 4, '06.141.01.20'],
    ['mediouna', 2, '06.355'], ['mohammedia', 3, '06.371'], ['nouaceur', 3, '06.385'],
    ['settat', 6, '06.461'], ['sidi-bennour', 4, '06.467'],
    ['sidi-bernoussi', 3, '06.141.01.50'],
    ['sidi-othmane-moulay-rachid', 3, '06.141.01.70'],
    ['chichaoua', 4, '07.161'], ['al-haouz', 4, '07.041'],
    ['essaouira', 4, '07.211'], ['el-kelaa-des-sraghna', 4, '07.191'],
    ['rehamna', 3, '07.427'], ['safi', 6, '07.431'], ['youssoufia', 2, '07.585'],
    ['errachidia', 5, '08.201'], ['midelt', 3, '08.363'], ['ouarzazate', 3, '08.401'],
    ['tinghir', 3, '08.577'], ['zagora', 3, '08.587'],
    ['agadir-ida-outanane', 4, '09.001'], ['chtouka-ait-baha', 3, '09.163'],
    ['inezgane-ait-melloul', 3, '09.273'], ['tata', 2, '09.551'], ['tiznit', 2, '09.581'],
    ['assa-zag', 2, '10.071'], ['guelmim', 2, '10.261'], ['sidi-ifni', 2, '10.473'],
    ['tan-tan', 2, '10.521'], ['boujdour', 2, '11.121'], ['es-semara', 2, '11.221'],
    ['laayoune', 3, '11.321'], ['tarfaya', 2, '11.537'],
    ['aousserd', 2, '12.066'], ['oued-ed-dahab', 2, '12.391']
];

const c = code => `communes:${code}`;
const a = code => `arrondissements:${code}`;
const p = code => `provinces:${code}`;

// Explicit component lists are transcribed from Decree 2.11.603. For five
// two-way province splits, both sides are listed and their exact union is
// validated against the pinned HCP parent inventory. Omissions, overlaps, and
// upstream additions therefore fail generation instead of moving territory.
const PARTITIONS = [
    {
        provinceCode: '03.531',
        explicit: ['taounate-tissa', 3, [
            '03.531.01.05', '03.531.01.07', '03.531.01.09',
            '03.531.07.19', '03.531.07.05', '03.531.07.11', '03.531.07.03',
            '03.531.07.09', '03.531.07.21', '03.531.07.13', '03.531.07.17',
            '03.531.07.15', '03.531.07.07', '03.531.07.01',
            '03.531.09.01', '03.531.09.05', '03.531.09.17', '03.531.09.07',
            '03.531.09.21', '03.531.09.03', '03.531.09.15', '03.531.09.11',
            '03.531.09.19', '03.531.09.13', '03.531.09.09', '03.531.09.23'
        ]],
        complement: ['el-karia-ghafsai', 3, [
            '03.531.01.03', '03.531.01.01', '03.531.05.03', '03.531.05.07',
            '03.531.05.17', '03.531.05.09', '03.531.05.13', '03.531.05.01',
            '03.531.05.15', '03.531.05.11', '03.531.05.05', '03.531.03.09',
            '03.531.03.03', '03.531.03.13', '03.531.03.17', '03.531.03.07',
            '03.531.03.11', '03.531.03.01', '03.531.03.15', '03.531.03.23',
            '03.531.03.05', '03.531.03.19', '03.531.03.21'
        ]]
    },
    {
        provinceCode: '04.281',
        explicit: ['kenitra', 4, [
            '04.281.01.01', '04.281.01.05', '04.281.01.13', '04.281.03.11',
            '04.281.03.05', '04.281.05.09', '04.281.05.07', '04.281.05.03',
            '04.281.05.11', '04.281.03.01'
        ]],
        complement: ['kenitra-el-gharb', 3, [
            '04.281.01.11', '04.281.07.01', '04.281.07.07', '04.281.07.05',
            '04.281.07.03', '04.281.09.09', '04.281.09.15', '04.281.09.01',
            '04.281.09.13', '04.281.11.07', '04.281.11.05', '04.281.11.03',
            '04.281.11.11'
        ]]
    },
    {
        provinceCode: '04.291',
        explicit: ['tiflet-rommani', 3, [
            '04.291.01.05', '04.291.01.03', '04.291.01.17',
            '04.291.07.05', '04.291.07.15', '04.291.07.09', '04.291.07.11',
            '04.291.07.13', '04.291.07.01', '04.291.07.07',
            '04.291.09.11', '04.291.09.13', '04.291.09.07', '04.291.09.19',
            '04.291.09.02', '04.291.09.03', '04.291.09.09', '04.291.09.05',
            '04.291.03.15', '04.291.05.07'
        ]],
        complement: ['khemisset-oulmes', 3, [
            '04.291.01.01', '04.291.03.01', '04.291.03.03', '04.291.03.11',
            '04.291.03.09', '04.291.03.07', '04.291.03.13', '04.291.03.05',
            '04.291.03.17', '04.291.05.09', '04.291.05.03', '04.291.05.05',
            '04.291.05.01', '04.291.05.11', '04.291.05.13'
        ]]
    },
    {
        provinceCode: '05.081',
        explicit: ['bzou-ouaouizeght', 3, [
            '05.081.01.01', '05.081.05.07', '05.081.05.09', '05.081.05.17',
            '05.081.05.01', '05.081.05.15', '05.081.05.05', '05.081.05.13',
            '05.081.05.21', '05.081.05.11', '05.081.05.19',
            '05.081.11.03', '05.081.11.01', '05.081.11.25', '05.081.09.07',
            '05.081.09.13', '05.081.09.19', '05.081.09.15', '05.081.09.11',
            '05.081.11.05', '05.081.09.17', '05.081.09.21', '05.081.09.03',
            '05.081.09.23', '05.081.09.09', '05.081.03.01', '05.081.03.13'
        ]],
        complement: ['azilal-demnate', 3, [
            '05.081.01.03', '05.081.03.07', '05.081.03.03', '05.081.03.09',
            '05.081.03.05', '05.081.03.11', '05.081.15.13', '05.081.15.03',
            '05.081.15.21', '05.081.15.11', '05.081.15.15', '05.081.15.01',
            '05.081.13.05', '05.081.13.07', '05.081.13.09', '05.081.13.19',
            '05.081.13.17'
        ]]
    },
    {
        provinceCode: '09.541',
        explicit: ['taroudant-sud', 4, [
            '09.541.01.13', '09.541.01.01', '09.541.01.09', '09.541.01.03',
            '09.541.05.23', '09.541.09.15', '09.541.09.43', '09.541.11.01',
            '09.541.09.55', '09.541.09.27', '09.541.09.05', '09.541.09.51',
            '09.541.11.31', '09.541.09.45', '09.541.09.03', '09.541.09.57',
            '09.541.09.11', '09.541.09.63', '09.541.09.41', '09.541.05.25',
            '09.541.11.15', '09.541.11.29', '09.541.11.19', '09.541.11.71',
            '09.541.11.09', '09.541.11.03', '09.541.11.07', '09.541.11.33',
            '09.541.11.13', '09.541.05.01', '09.541.05.17', '09.541.05.27',
            '09.541.05.11', '09.541.05.21', '09.541.05.19', '09.541.05.05',
            '09.541.05.33'
        ]],
        complement: ['taroudant-nord', 3, [
            '09.541.01.05', '09.541.01.07', '09.541.01.11', '09.541.01.02',
            '09.541.03.19', '09.541.03.03', '09.541.03.29', '09.541.03.21',
            '09.541.03.31', '09.541.03.09', '09.541.03.25', '09.541.03.17',
            '09.541.03.05', '09.541.03.23', '09.541.03.07', '09.541.03.01',
            '09.541.03.11', '09.541.03.27', '09.541.03.13', '09.541.03.15',
            '09.541.04.25', '09.541.04.61', '09.541.04.29', '09.541.04.23',
            '09.541.04.33', '09.541.04.17', '09.541.04.39', '09.541.04.53',
            '09.541.04.67', '09.541.04.35', '09.541.04.49', '09.541.04.47',
            '09.541.04.59', '09.541.04.21', '09.541.04.13', '09.541.04.09',
            '09.541.04.69', '09.541.07.65', '09.541.07.37', '09.541.07.23',
            '09.541.07.03', '09.541.07.13', '09.541.07.17', '09.541.07.05',
            '09.541.07.19', '09.541.07.15', '09.541.07.07', '09.541.07.01',
            '09.541.07.09', '09.541.07.25', '09.541.07.21', '09.541.07.11'
        ]]
    }
];

const EXPLICIT_PARTITIONS = [
    {
        parentCode: '03.231',
        constituencies: [
            ['fes-nord', 4, [a('03.231.01.13'), a('03.231.01.11'), a('03.231.01.07'), c('03.231.01.03')]],
            ['fes-sud', 4, [a('03.231.01.01'), a('03.231.01.05'), a('03.231.01.09'),
                c('03.231.01.15'), c('03.231.81.05'), c('03.231.81.03')]]
        ]
    },
    {
        parentCode: '04.441',
        constituencies: [
            ['sale-medina', 4, [a('04.441.01.09'), a('04.441.01.05'), a('04.441.01.03')]],
            ['sale-nouvelle', 3, [a('04.441.01.06'), a('04.441.01.07'),
                c('04.441.01.08'), c('04.441.01.11'), c('04.441.01.13')]]
        ]
    },
    {
        parentCode: '07.351',
        constituencies: [
            ['gueliz', 3, [a('07.351.01.05'), a('07.351.01.03'), c('07.351.03.11'),
                c('07.351.03.03'), c('07.351.03.05'), c('07.351.02.07'),
                c('07.351.02.09'), c('07.351.02.01')]],
            ['menara', 3, [a('07.351.01.09'), c('07.351.05.05'), c('07.351.05.03'),
                c('07.351.05.01'), c('07.351.05.09'), c('07.351.07.11'), c('07.351.07.07')]],
            ['sidi-youssef-ben-ali', 3, [a('07.351.01.07'), a('07.351.01.11'),
                c('07.351.01.01'), c('07.351.01.13')]]
        ]
    },
    {
        parentCode: '06.141.01.10',
        constituencies: [
            ['al-fida-mers-sultan', 3, [p('06.141.01.10'), c('06.141.01.81')]]
        ]
    }
];

const SHARE_ONLY = [
    ['rabat-chellah', 3, [
        a('04.421.01.03'), a('04.421.01.06'), c('04.421.01.07'),
        'legal:04.421.01.05-annexes-1-2-22'
    ]],
    ['rabat-ocean', 4, [
        a('04.421.01.09'), a('04.421.01.01'),
        'legal:04.421.01.05-annexes-3-4'
    ]]
];

function buildCrosswalk(source) {
    const rows = FULL.map(([constituencyCode, allocatedSeats, provinceCode]) => ({
        constituencyCode, allocatedSeats, componentCodes: [p(provinceCode)], populationCoverage: 'exact'
    }));
    for (const partition of EXPLICIT_PARTITIONS) {
        validateExactPartition(partition.parentCode,
            source.explicitParentComponentCodes?.[partition.parentCode],
            partition.constituencies.map(([constituencyCode, , componentCodes]) =>
                [constituencyCode, componentCodes]));
        rows.push(...partition.constituencies.map(([constituencyCode, allocatedSeats, componentCodes]) => ({
            constituencyCode, allocatedSeats, componentCodes, populationCoverage: 'exact'
        })));
    }
    for (const partition of PARTITIONS) {
        const all = source.partitionParentCodes?.[partition.provinceCode];
        const [explicitCode, explicitSeats, explicitCodes] = partition.explicit;
        const [complementCode, complementSeats, complementCodes] = partition.complement;
        validateExactPartition(partition.provinceCode, all,
            [[explicitCode, explicitCodes], [complementCode, complementCodes]]);
        rows.push({ constituencyCode: explicitCode, allocatedSeats: explicitSeats,
            componentCodes: explicitCodes.map(c), populationCoverage: 'exact' });
        rows.push({ constituencyCode: complementCode, allocatedSeats: complementSeats,
            componentCodes: complementCodes.map(c), populationCoverage: 'exact' });
    }
    rows.push(...SHARE_ONLY.map(([constituencyCode, allocatedSeats, componentCodes]) => ({
        constituencyCode, allocatedSeats, componentCodes, populationCoverage: 'share-only', urbanShare: 100
    })));
    rows.sort((left, right) => left.constituencyCode.localeCompare(right.constituencyCode));
    if (rows.length !== 92 || new Set(rows.map(row => row.constituencyCode)).size !== 92
            || rows.reduce((sum, row) => sum + row.allocatedSeats, 0) !== 305) {
        throw new Error('Crosswalk must contain exactly 92 constituencies and 305 local seats');
    }
    return rows;
}

function validatePinnedSource(source, rows) {
    if (source?.schemaVersion !== 1 || source.datasetVersion !== SOURCE.datasetVersion
            || source.censusYear !== SOURCE.censusYear || source.sourceUrl !== SOURCE.sourceUrl
            || source.sourceLabel !== SOURCE.sourceLabel || source.decreeUrl !== SOURCE.decreeUrl
            || source.decreeLabel !== SOURCE.decreeLabel || source.revisedAt !== SOURCE.revisedAt
            || source.localSeatTotal !== SOURCE.localSeatTotal
            || source.excludedRegionalSeatTotal !== SOURCE.excludedRegionalSeatTotal
            || source.license !== 'CC BY 4.0') {
        throw new Error('Pinned demographic source metadata does not match the declared source revision');
    }
    if (!Array.isArray(source.constituencies) || source.constituencies.length !== rows.length) {
        throw new Error('Pinned demographic source must contain all 92 constituencies');
    }
    validatePinnedCrosswalk(rows, source.constituencies);
    const pinnedByCode = new Map(source.constituencies.map(row => [row.constituencyCode, row]));
    for (const row of rows) {
        const pinned = pinnedByCode.get(row.constituencyCode);
        if (row.populationCoverage === 'exact') {
            if (![pinned.totalPopulation, pinned.urbanPopulation, pinned.ruralPopulation]
                    .every(Number.isSafeInteger) || pinned.totalPopulation <= 0
                    || pinned.urbanPopulation + pinned.ruralPopulation !== pinned.totalPopulation
                    || Math.abs(pinned.urbanShare - 100 * pinned.urbanPopulation / pinned.totalPopulation) > 1e-9) {
                throw new Error(`Invalid pinned population for ${row.constituencyCode}`);
            }
        } else if (pinned.urbanShare !== 100 || pinned.totalPopulation !== undefined
                || pinned.urbanPopulation !== undefined || pinned.ruralPopulation !== undefined) {
            throw new Error(`Invalid share-only population for ${row.constituencyCode}`);
        }
    }
}

function serialize(data) {
    return `// Generated by scripts/elections/generate-urban-rural-constituencies.mjs.\n`
        + `// Electoral boundaries: Decree 2.11.603, Official Bulletin 5988 (20 October 2011).\n`
        + `// Population: HCP RGPH 2024 through Morocco Communes dataset 1.8.0.\n`
        + `(function (root, factory) {\n`
        + `    const data = factory();\n`
        + `    if (typeof module === 'object' && module.exports) module.exports = data;\n`
        + `    else root.FhemniElectionConstituencyDemographics = data;\n`
        + `})(typeof globalThis === 'undefined' ? this : globalThis, function () {\n`
        + `    return ${JSON.stringify(data, null, 4).replace(/^/gm, '    ').trimStart()};\n`
        + `});\n`;
}

async function main() {
    const sourceBytes = await readFile(PINNED_SOURCE);
    const sourceSha256 = createHash('sha256').update(sourceBytes).digest('hex');
    if (sourceSha256 !== PINNED_SOURCE_SHA256) {
        throw new Error(`Pinned demographic source SHA-256 integrity mismatch: ${sourceSha256}`);
    }
    const source = JSON.parse(sourceBytes.toString('utf8'));
    const rows = buildCrosswalk(source);
    validatePinnedSource(source, rows);
    const constituencies = source.constituencies;
    const result = { ...SOURCE, license: 'CC BY 4.0', constituencies };
    await mkdir(dirname(OUTPUT), { recursive: true });
    await writeFile(OUTPUT, serialize(result), 'utf8');
    process.stdout.write(`Wrote ${constituencies.length} constituencies to ${OUTPUT}\n`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    await main();
}

export { buildCrosswalk };
