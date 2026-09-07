-- Anything that used to be a hard-coded list now lives in reference tables:
-- which parties get public sheets, and which leading words are stripped from
-- speaker names before matching.
ALTER TABLE political_parties
    ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE political_parties SET visible = FALSE WHERE code IN ('IND', 'UNKNOWN');

CREATE TABLE honorific_prefixes (
    prefix VARCHAR(40) PRIMARY KEY
);

-- Leading speaker-name titles stripped before matching (Darija reports).
INSERT INTO honorific_prefixes (prefix) VALUES
    ('الدكتور'),
    ('دكتور'),
    ('الأستاذ'),
    ('أستاذ'),
    ('الاستاذ'),
    ('السيد'),
    ('السيدة'),
    ('سيد'),
    ('سيدة'),
    ('الآنسة'),
    ('آنسة'),
    ('الحاج'),
    ('حاج');
