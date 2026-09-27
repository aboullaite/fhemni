export function validateExactPartition(parentCode, parentCodes, groups) {
    if (!Array.isArray(parentCodes) || !parentCodes.length) {
        throw new Error(`Partition coverage is empty for ${parentCode}`);
    }
    const parentSet = new Set(parentCodes);
    if (parentSet.size !== parentCodes.length) {
        throw new Error(`Duplicate parent administrative code for ${parentCode}`);
    }

    const assigned = new Set();
    for (const [groupName, codes] of groups) {
        if (!Array.isArray(codes) || !codes.length) {
            throw new Error(`Partition coverage is empty for ${parentCode}/${groupName}`);
        }
        for (const code of codes) {
            if (assigned.has(code)) {
                throw new Error(`Duplicate or overlapping administrative code ${code} in ${parentCode}`);
            }
            assigned.add(code);
        }
    }

    const missing = parentCodes.filter(code => !assigned.has(code));
    const extra = [...assigned].filter(code => !parentSet.has(code));
    if (missing.length || extra.length || assigned.size !== parentSet.size) {
        throw new Error(`Partition coverage mismatch for ${parentCode}: missing [${missing.join(', ')}], extra [${extra.join(', ')}]`);
    }
}

export function validatePinnedCrosswalk(rows, pinnedRows) {
    if (!Array.isArray(rows) || !Array.isArray(pinnedRows) || rows.length !== pinnedRows.length) {
        throw new Error('Pinned crosswalk mismatch: constituency count');
    }
    const pinnedByCode = new Map(pinnedRows.map(row => [row.constituencyCode, row]));
    if (pinnedByCode.size !== pinnedRows.length) {
        throw new Error('Pinned crosswalk mismatch: duplicate constituency');
    }
    for (const row of rows) {
        const pinned = pinnedByCode.get(row.constituencyCode);
        if (!pinned || pinned.allocatedSeats !== row.allocatedSeats
                || pinned.populationCoverage !== row.populationCoverage
                || JSON.stringify(pinned.componentCodes) !== JSON.stringify(row.componentCodes)) {
            throw new Error(`Pinned crosswalk mismatch for ${row.constituencyCode}`);
        }
    }
}
