CREATE TABLE elections (
    id UUID PRIMARY KEY,
    slug VARCHAR(40) NOT NULL UNIQUE,
    election_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('SCHEDULED', 'COUNTING', 'PRELIMINARY', 'FINAL', 'CORRECTED')),
    total_seats INTEGER NOT NULL CHECK (total_seats > 0),
    registered_voters BIGINT CHECK (registered_voters IS NULL OR registered_voters >= 0),
    votes_cast BIGINT CHECK (votes_cast IS NULL OR votes_cast >= 0),
    valid_votes BIGINT CHECK (valid_votes IS NULL OR valid_votes >= 0),
    vote_basis VARCHAR(32) CHECK (vote_basis IS NULL OR vote_basis IN ('LOCAL_CONSTITUENCY', 'REGIONAL_LIST', 'OFFICIAL_AGGREGATE')),
    source_label_ar VARCHAR(300) NOT NULL,
    source_label_fr VARCHAR(300) NOT NULL,
    source_label_en VARCHAR(300) NOT NULL,
    source_url VARCHAR(1200),
    source_updated_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (votes_cast IS NULL OR registered_voters IS NULL OR votes_cast <= registered_voters),
    CHECK (valid_votes IS NULL OR votes_cast IS NULL OR valid_votes <= votes_cast)
);

CREATE TABLE election_regions (
    election_id UUID NOT NULL REFERENCES elections (id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    name_ar VARCHAR(160) NOT NULL,
    name_fr VARCHAR(160) NOT NULL,
    name_en VARCHAR(160) NOT NULL,
    map_key VARCHAR(16) NOT NULL,
    allocated_seats INTEGER CHECK (allocated_seats IS NULL OR allocated_seats >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PARTIAL', 'FINAL')),
    sort_order INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, code),
    UNIQUE (election_id, map_key)
);

CREATE TABLE election_party_results (
    election_id UUID NOT NULL REFERENCES elections (id) ON DELETE CASCADE,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code),
    votes BIGINT CHECK (votes IS NULL OR votes >= 0),
    local_seats INTEGER NOT NULL CHECK (local_seats >= 0),
    regional_list_seats INTEGER NOT NULL CHECK (regional_list_seats >= 0),
    total_seats INTEGER NOT NULL CHECK (total_seats >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, party_code),
    CHECK (total_seats = local_seats + regional_list_seats)
);

CREATE TABLE election_region_party_results (
    election_id UUID NOT NULL,
    region_code VARCHAR(32) NOT NULL,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code),
    local_seats INTEGER NOT NULL CHECK (local_seats >= 0),
    regional_list_seats INTEGER NOT NULL CHECK (regional_list_seats >= 0),
    total_seats INTEGER NOT NULL CHECK (total_seats >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, region_code, party_code),
    FOREIGN KEY (election_id, region_code) REFERENCES election_regions (election_id, code) ON DELETE CASCADE,
    CHECK (total_seats = local_seats + regional_list_seats)
);

CREATE INDEX election_region_party_results_election_party_idx
    ON election_region_party_results (election_id, party_code);

INSERT INTO elections (
    id, slug, election_date, status, total_seats,
    source_label_ar, source_label_fr, source_label_en, updated_at
) VALUES (
    '20260000-0000-4000-8000-000000000001',
    'legislative-2026',
    DATE '2026-09-23',
    'COUNTING',
    395,
    'النتائج الرسمية المعلنة',
    'Résultats officiels publiés',
    'Published official results',
    CURRENT_TIMESTAMP
);

INSERT INTO election_regions (
    election_id, code, name_ar, name_fr, name_en, map_key,
    allocated_seats, status, sort_order, updated_at
) VALUES
    ('20260000-0000-4000-8000-000000000001', 'tanger-tetouan-al-hoceima', 'طنجة - تطوان - الحسيمة', 'Tanger-Tétouan-Al Hoceïma', 'Tanger-Tetouan-Al Hoceima', 'MA-01', NULL, 'PENDING', 1, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'oriental', 'الشرق', 'L''Oriental', 'Oriental', 'MA-02', NULL, 'PENDING', 2, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'fes-meknes', 'فاس - مكناس', 'Fès-Meknès', 'Fes-Meknes', 'MA-03', NULL, 'PENDING', 3, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'rabat-sale-kenitra', 'الرباط - سلا - القنيطرة', 'Rabat-Salé-Kénitra', 'Rabat-Sale-Kenitra', 'MA-04', NULL, 'PENDING', 4, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'beni-mellal-khenifra', 'بني ملال - خنيفرة', 'Béni Mellal-Khénifra', 'Beni Mellal-Khenifra', 'MA-05', NULL, 'PENDING', 5, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'الدار البيضاء - سطات', 'Casablanca-Settat', 'Casablanca-Settat', 'MA-06', NULL, 'PENDING', 6, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'marrakech-safi', 'مراكش - آسفي', 'Marrakech-Safi', 'Marrakech-Safi', 'MA-07', NULL, 'PENDING', 7, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'draa-tafilalet', 'درعة - تافيلالت', 'Drâa-Tafilalet', 'Draa-Tafilalet', 'MA-08', NULL, 'PENDING', 8, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'souss-massa', 'سوس - ماسة', 'Souss-Massa', 'Souss-Massa', 'MA-09', NULL, 'PENDING', 9, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'guelmim-oued-noun', 'كلميم - واد نون', 'Guelmim-Oued Noun', 'Guelmim-Oued Noun', 'MA-10', NULL, 'PENDING', 10, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'laayoune-sakia-el-hamra', 'العيون - الساقية الحمراء', 'Laâyoune-Sakia El Hamra', 'Laayoune-Sakia El Hamra', 'MA-11', NULL, 'PENDING', 11, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'dakhla-oued-ed-dahab', 'الداخلة - وادي الذهب', 'Dakhla-Oued Ed-Dahab', 'Dakhla-Oued Ed-Dahab', 'MA-12', NULL, 'PENDING', 12, CURRENT_TIMESTAMP);
