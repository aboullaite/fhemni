-- Official artwork downloaded unchanged from the Maroc.ma political-party
-- directory. Source URLs and SHA-256 checksums are recorded beside the
-- assets in static/assets/parties/README.md.

UPDATE political_parties
   SET symbol_label_fr = 'Avion', symbol_label_ar = 'الطائرة',
       symbol_asset = '/assets/parties/alamal-display.png', symbol_verified = TRUE
 WHERE code = 'ALAMAL';

UPDATE political_parties
   SET symbol_label_fr = 'Croissant et étoiles', symbol_label_ar = 'الهلال والنجوم',
       symbol_asset = '/assets/parties/prd-display.png', symbol_verified = TRUE
 WHERE code = 'PRD';
