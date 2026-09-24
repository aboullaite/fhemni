ALTER TABLE political_parties ADD COLUMN catalogue_name_fr VARCHAR(160);
ALTER TABLE political_parties ADD COLUMN catalogue_name_ar VARCHAR(160);

UPDATE political_parties
   SET catalogue_name_fr = name_fr,
       catalogue_name_ar = name_ar;

UPDATE political_parties
   SET name_fr = 'Fédération de la Gauche Démocratique',
       name_ar = 'فيدرالية اليسار الديمقراطي',
       catalogue_name_fr = 'Alliance de la Gauche',
       catalogue_name_ar = 'تحالف اليسار'
 WHERE code = 'FGD';

ALTER TABLE political_parties ALTER COLUMN catalogue_name_fr SET NOT NULL;
ALTER TABLE political_parties ALTER COLUMN catalogue_name_ar SET NOT NULL;

CREATE TABLE election_party_display_names (
    election_id UUID NOT NULL REFERENCES elections (id) ON DELETE CASCADE,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code),
    name_ar VARCHAR(160) NOT NULL,
    name_fr VARCHAR(160) NOT NULL,
    name_en VARCHAR(160) NOT NULL,
    PRIMARY KEY (election_id, party_code)
);

INSERT INTO election_party_display_names (
    election_id, party_code, name_ar, name_fr, name_en
) VALUES (
    '20260000-0000-4000-8000-000000000001',
    'FGD',
    'تحالف اليسار',
    'Alliance de la Gauche',
    'Alliance de la Gauche'
);
