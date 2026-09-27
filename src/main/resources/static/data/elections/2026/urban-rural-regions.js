// HCP RGPH 2024 regional population snapshot, redistributed by Morocco Communes dataset 1.8.0.
// Evidence: https://communes.pages.dev/data/v1/sources.json (retrieved 2026-09-17 to 2026-09-20).
(function (root, factory) {
    const data = factory();
    if (typeof module === 'object' && module.exports) module.exports = data;
    else root.FhemniElectionRegionDemographics = data;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
    return {
        datasetVersion: '1.8.0',
        censusYear: 2024,
        sourceUrl: 'https://communes.pages.dev/data/v1/sources.json',
        sourceLabel: 'HCP RGPH 2024 via Morocco Communes',
        license: 'CC BY 4.0',
        regions: [
            { regionCode: 'tanger-tetouan-al-hoceima', hcpCode: '01', totalPopulation: 4030222, urbanPopulation: 2638815, ruralPopulation: 1391407 },
            { regionCode: 'oriental', hcpCode: '02', totalPopulation: 2294665, urbanPopulation: 1505714, ruralPopulation: 788951 },
            { regionCode: 'fes-meknes', hcpCode: '03', totalPopulation: 4467911, urbanPopulation: 2855366, ruralPopulation: 1612545 },
            { regionCode: 'rabat-sale-kenitra', hcpCode: '04', totalPopulation: 5132639, urbanPopulation: 3627178, ruralPopulation: 1505461 },
            { regionCode: 'beni-mellal-khenifra', hcpCode: '05', totalPopulation: 2525801, urbanPopulation: 1283492, ruralPopulation: 1242309 },
            { regionCode: 'casablanca-settat', hcpCode: '06', totalPopulation: 7688967, urbanPopulation: 5633748, ruralPopulation: 2055219 },
            { regionCode: 'marrakech-safi', hcpCode: '07', totalPopulation: 4892393, urbanPopulation: 2248954, ruralPopulation: 2643439 },
            { regionCode: 'draa-tafilalet', hcpCode: '08', totalPopulation: 1655623, urbanPopulation: 607724, ruralPopulation: 1047899 },
            { regionCode: 'souss-massa', hcpCode: '09', totalPopulation: 3020431, urbanPopulation: 1816102, ruralPopulation: 1204329 },
            { regionCode: 'guelmim-oued-noun', hcpCode: '10', totalPopulation: 448685, urbanPopulation: 299543, ruralPopulation: 149142 },
            { regionCode: 'laayoune-sakia-el-hamra', hcpCode: '11', totalPopulation: 451028, urbanPopulation: 416636, ruralPopulation: 34392 },
            { regionCode: 'dakhla-oued-ed-dahab', hcpCode: '12', totalPopulation: 219965, urbanPopulation: 176836, ruralPopulation: 43129 }
        ]
    };
});
