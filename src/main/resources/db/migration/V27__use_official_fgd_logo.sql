-- FGD artwork downloaded unchanged from the party's official 2026 election
-- platform. The display asset is a mechanical emblem crop of that source;
-- provenance and checksums live in static/assets/parties/README.md.

UPDATE political_parties
   SET symbol_asset = '/assets/parties/fgd-display.png',
       symbol_verified = TRUE
 WHERE code = 'FGD';
