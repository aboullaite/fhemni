CREATE TABLE election_constituencies (
    election_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    region_code VARCHAR(32) NOT NULL,
    name_ar VARCHAR(160) NOT NULL,
    name_fr VARCHAR(160) NOT NULL,
    name_en VARCHAR(160) NOT NULL,
    allocated_seats INTEGER CHECK (allocated_seats IS NULL OR allocated_seats > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PARTIAL', 'PROVISIONAL', 'OFFICIAL')),
    sort_order INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, code),
    FOREIGN KEY (election_id, region_code)
        REFERENCES election_regions (election_id, code) ON DELETE CASCADE
);

CREATE INDEX election_constituencies_region_idx
    ON election_constituencies (election_id, region_code, sort_order);

CREATE TABLE election_constituency_winners (
    election_id UUID NOT NULL,
    constituency_code VARCHAR(64) NOT NULL,
    candidate_key VARCHAR(96) NOT NULL,
    candidate_name VARCHAR(180) NOT NULL,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code),
    votes BIGINT CHECK (votes IS NULL OR votes >= 0),
    sort_order INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, constituency_code, candidate_key),
    FOREIGN KEY (election_id, constituency_code)
        REFERENCES election_constituencies (election_id, code) ON DELETE CASCADE
);

CREATE INDEX election_constituency_winners_party_idx
    ON election_constituency_winners (election_id, party_code);
