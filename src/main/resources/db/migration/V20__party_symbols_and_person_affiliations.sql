-- Party symbols are stored as local, curated metadata. The asset paths point
-- to Fhemni-owned SVG illustrations of the recognisable electoral symbols;
-- no third-party site is hot-linked from a public page.
ALTER TABLE political_parties ADD COLUMN symbol_label_fr VARCHAR(100);
ALTER TABLE political_parties ADD COLUMN symbol_label_ar VARCHAR(100);
ALTER TABLE political_parties ADD COLUMN symbol_asset VARCHAR(255);
ALTER TABLE political_parties ADD COLUMN symbol_verified BOOLEAN NOT NULL DEFAULT TRUE;

-- Additional active parties from the official Maroc.ma directory. They stay
-- out of the public catalogue until an episode or programme is assigned. We
-- use a neutral local mark until each official electoral symbol is verified;
-- the UI must never invent a political symbol.
UPDATE political_parties SET sort_order = 98 WHERE code = 'IND';
UPDATE political_parties SET sort_order = 99 WHERE code = 'UNKNOWN';
INSERT INTO political_parties (
    code, name_fr, name_ar, color, sort_order, visible,
    symbol_label_fr, symbol_label_ar, symbol_asset, symbol_verified
) VALUES
    ('PE', 'Parti de l''Équité', 'حزب الإنصاف', '#7C3AED', 13, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE),
    ('PML', 'Parti Marocain Libéral', 'الحزب المغربي الحر', '#2563EB', 14, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE),
    ('PVM', 'Parti des Verts Marocains', 'حزب الخضر المغربي', '#15803D', 15, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE),
    ('ND', 'Parti des Néo-Démocrates', 'حزب الديمقراطيين الجدد', '#9333EA', 16, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE),
    ('PGV', 'Parti de la Gauche Verte', 'حزب اليسار الأخضر المغربي', '#4D7C0F', 17, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE),
    ('PEDD', 'Parti de l''Environnement et du Développement Durable', 'حزب البيئة والتنمية المستدامة', '#0F766E', 18, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE),
    ('PUD', 'Parti de l''Unité et de la Démocratie', 'حزب الوحدة والديمقراطية', '#B45309', 19, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE),
    ('PRV', 'Parti de la Renaissance et de la Vertu', 'حزب النهضة والفضيلة', '#BE123C', 20, TRUE, 'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE);

UPDATE political_parties SET symbol_label_fr = 'Colombe', symbol_label_ar = 'الحمامة', symbol_asset = '/assets/parties/rni-dove.svg' WHERE code = 'RNI';
UPDATE political_parties SET symbol_label_fr = 'Tracteur', symbol_label_ar = 'الجرار', symbol_asset = '/assets/parties/pam-tractor.svg' WHERE code = 'PAM';
UPDATE political_parties SET symbol_label_fr = 'Balance', symbol_label_ar = 'الميزان', symbol_asset = '/assets/parties/pi-scales.svg' WHERE code = 'PI';
UPDATE political_parties SET symbol_label_fr = 'Lampe', symbol_label_ar = 'المصباح', symbol_asset = '/assets/parties/pjd-lamp.svg' WHERE code = 'PJD';
UPDATE political_parties SET symbol_label_fr = 'Rose', symbol_label_ar = 'الوردة', symbol_asset = '/assets/parties/usfp-rose.svg' WHERE code = 'USFP';
UPDATE political_parties SET symbol_label_fr = 'Livre', symbol_label_ar = 'الكتاب', symbol_asset = '/assets/parties/pps-book.svg' WHERE code = 'PPS';
UPDATE political_parties SET symbol_label_fr = 'Épi de blé', symbol_label_ar = 'السنبلة', symbol_asset = '/assets/parties/mp-wheat.svg' WHERE code = 'MP';
UPDATE political_parties SET symbol_label_fr = 'Lettre', symbol_label_ar = 'الرسالة', symbol_asset = '/assets/parties/fgd-letter.svg' WHERE code = 'FGD';
UPDATE political_parties SET symbol_label_fr = 'Cheval', symbol_label_ar = 'الحصان', symbol_asset = '/assets/parties/uc-horse.svg' WHERE code = 'UC';
UPDATE political_parties SET symbol_label_fr = 'Branche d''olivier', symbol_label_ar = 'غصن الزيتون', symbol_asset = '/assets/parties/ffd-olive.svg' WHERE code = 'FFD';
UPDATE political_parties SET symbol_label_fr = 'Palmier', symbol_label_ar = 'النخلة', symbol_asset = '/assets/parties/mds-palm.svg' WHERE code = 'MDS';
UPDATE political_parties SET symbol_label_fr = 'Bougie', symbol_label_ar = 'الشمعة', symbol_asset = '/assets/parties/psu-candle.svg' WHERE code = 'PSU';
UPDATE political_parties SET symbol_label_fr = 'Indépendant', symbol_label_ar = 'مستقل', symbol_asset = '/assets/parties/independent.svg' WHERE code = 'IND';
UPDATE political_parties SET symbol_label_fr = 'Non renseigné', symbol_label_ar = 'غير معروف', symbol_asset = '/assets/parties/unknown.svg' WHERE code = 'UNKNOWN';

ALTER TABLE political_parties ALTER COLUMN symbol_label_fr SET NOT NULL;
ALTER TABLE political_parties ALTER COLUMN symbol_label_ar SET NOT NULL;
ALTER TABLE political_parties ALTER COLUMN symbol_asset SET NOT NULL;

-- A person's political affiliation is time-bound and sourced. This replaces
-- the single party_code column, which could not represent party changes.
CREATE TABLE person_affiliations (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    person_slug VARCHAR(160) NOT NULL REFERENCES directory_persons (slug) ON DELETE CASCADE,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code),
    valid_from DATE,
    valid_until DATE,
    source_url VARCHAR(2000),
    source_label VARCHAR(300) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);
CREATE INDEX person_affiliations_person_period_idx
    ON person_affiliations (person_slug, valid_from, valid_until);
CREATE INDEX person_affiliations_party_idx
    ON person_affiliations (party_code, person_slug);

-- Preserve every previously curated assignment. The old migration documented
-- sources only in comments, so these rows are deliberately marked as legacy
-- imports until an admin attaches a per-affiliation source.
INSERT INTO person_affiliations (
    person_slug, party_code, valid_from, valid_until,
    source_url, source_label, verified_at, created_at
)
SELECT slug, party_code, NULL, NULL,
       NULL, 'Imported from the original curated directory', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
 FROM directory_persons
 WHERE party_code <> 'UNKNOWN';

ALTER TABLE directory_persons DROP COLUMN party_code;
