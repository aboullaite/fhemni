-- Party artwork downloaded unchanged from the official Maroc.ma directory on
-- 2026-09-09. Exact source URLs and SHA-256 checksums live beside the assets in
-- static/assets/parties/README.md. The display derivatives are cropped and
-- centred consistently without redrawing the artwork. FGD has no exact entry in
-- that directory, so its existing local electoral-symbol placeholder remains.

UPDATE political_parties SET symbol_asset = '/assets/parties/rni-display.png', symbol_verified = TRUE WHERE code = 'RNI';
UPDATE political_parties SET symbol_asset = '/assets/parties/pam-display.png', symbol_verified = TRUE WHERE code = 'PAM';
UPDATE political_parties SET symbol_asset = '/assets/parties/pi-display.png', symbol_verified = TRUE WHERE code = 'PI';
UPDATE political_parties SET symbol_asset = '/assets/parties/pjd-display.png', symbol_verified = TRUE WHERE code = 'PJD';
UPDATE political_parties SET symbol_asset = '/assets/parties/usfp-display.png', symbol_verified = TRUE WHERE code = 'USFP';
UPDATE political_parties SET symbol_asset = '/assets/parties/pps-display.png', symbol_verified = TRUE WHERE code = 'PPS';
UPDATE political_parties SET symbol_asset = '/assets/parties/mp-display.png', symbol_verified = TRUE WHERE code = 'MP';
UPDATE political_parties SET symbol_asset = '/assets/parties/uc-display.png', symbol_verified = TRUE WHERE code = 'UC';
UPDATE political_parties SET symbol_asset = '/assets/parties/ffd-display.png', symbol_verified = TRUE WHERE code = 'FFD';
UPDATE political_parties SET symbol_asset = '/assets/parties/mds-display.png', symbol_verified = TRUE WHERE code = 'MDS';
UPDATE political_parties SET symbol_asset = '/assets/parties/psu-display.png', symbol_verified = TRUE WHERE code = 'PSU';

UPDATE political_parties
   SET symbol_label_fr = 'Pomme', symbol_label_ar = 'التفاحة',
       symbol_asset = '/assets/parties/pe-display.png', symbol_verified = TRUE
 WHERE code = 'PE';
UPDATE political_parties
   SET symbol_label_fr = 'Lion', symbol_label_ar = 'الأسد',
       symbol_asset = '/assets/parties/pml-display.png', symbol_verified = TRUE
 WHERE code = 'PML';
UPDATE political_parties
   SET symbol_label_fr = 'Voilier', symbol_label_ar = 'المركب الشراعي',
       symbol_asset = '/assets/parties/pvm-display.png', symbol_verified = TRUE
 WHERE code = 'PVM';
UPDATE political_parties
   SET symbol_label_fr = 'Pouce levé', symbol_label_ar = 'الإبهام المرفوع',
       symbol_asset = '/assets/parties/nd-display.png', symbol_verified = TRUE
 WHERE code = 'ND';
UPDATE political_parties
   SET symbol_label_fr = 'Logo vert', symbol_label_ar = 'الشعار الأخضر',
       symbol_asset = '/assets/parties/pgv-display.png', symbol_verified = TRUE
 WHERE code = 'PGV';
UPDATE political_parties
   SET symbol_label_fr = 'Gazelle', symbol_label_ar = 'الغزالة',
       symbol_asset = '/assets/parties/pedd-display.png', symbol_verified = TRUE
 WHERE code = 'PEDD';
UPDATE political_parties
   SET symbol_label_fr = 'Robinet', symbol_label_ar = 'الصنبور',
       symbol_asset = '/assets/parties/pud-display.png', symbol_verified = TRUE
 WHERE code = 'PUD';
UPDATE political_parties
   SET symbol_label_fr = 'Soleil', symbol_label_ar = 'الشمس',
       symbol_asset = '/assets/parties/prv-display.png', symbol_verified = TRUE
 WHERE code = 'PRV';
