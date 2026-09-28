(function (root, factory) {
    const api = factory(root);
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.FhemniHistoricalDataIntegrity = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (root) {
    'use strict';

    // Pins the served projection, not the source archive declared inside it.
    // Update only after regeneration and source reconciliation, never to bless a manual data edit.
    const EXPECTED_ARTIFACT_SHA256 = '3d2cea11a125ea9fa6cecfd0d4afd83b1b95bd9d2bf4d0a76feae0fe7e015323';

    async function parseVerifiedHistoricalData(bytes, cryptoProvider = root.crypto) {
        if (!cryptoProvider?.subtle?.digest) throw new Error('historical-data-integrity: cryptography unavailable');
        // Own the bytes before awaiting, so callers cannot change the verified input mid-flight.
        const snapshot = bytes instanceof Uint8Array ? new Uint8Array(bytes)
            : bytes instanceof ArrayBuffer ? new Uint8Array(bytes.slice(0)) : null;
        if (!snapshot) throw new Error('historical-data-integrity: invalid byte input');
        const digest = await cryptoProvider.subtle.digest('SHA-256', snapshot);
        const actual = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
        if (actual !== EXPECTED_ARTIFACT_SHA256) throw new Error('historical-data-integrity: artifact checksum mismatch');
        return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(snapshot));
    }

    return { parseVerifiedHistoricalData };
});
