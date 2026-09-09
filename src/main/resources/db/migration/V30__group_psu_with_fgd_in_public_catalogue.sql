-- PSU and FGD keep their distinct party identities and affiliations, but are
-- presented in one public catalogue folder for their common 2026 campaign.
-- Keeping this relation in reference data avoids a fragile frontend/code map.
ALTER TABLE political_parties ADD COLUMN catalogue_code VARCHAR(10);

UPDATE political_parties SET catalogue_code = code;
UPDATE political_parties SET catalogue_code = 'FGD' WHERE code = 'PSU';

ALTER TABLE political_parties ALTER COLUMN catalogue_code SET NOT NULL;
ALTER TABLE political_parties ADD CONSTRAINT political_parties_catalogue_code_fk
    FOREIGN KEY (catalogue_code) REFERENCES political_parties (code);
