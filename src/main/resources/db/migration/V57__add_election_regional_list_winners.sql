CREATE TABLE election_regional_list_winners (
    election_id UUID NOT NULL,
    region_code VARCHAR(32) NOT NULL,
    candidate_key VARCHAR(96) NOT NULL,
    candidate_name VARCHAR(180) NOT NULL,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code),
    result_status VARCHAR(16) NOT NULL
        CHECK (result_status IN ('PRELIMINARY', 'FINAL', 'CORRECTED')),
    source_label VARCHAR(300) NOT NULL,
    source_url VARCHAR(1200) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sort_order INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, region_code, candidate_key),
    UNIQUE (election_id, candidate_key),
    FOREIGN KEY (election_id, region_code)
        REFERENCES election_regions (election_id, code) ON DELETE CASCADE
);

CREATE INDEX election_regional_list_winners_party_idx
    ON election_regional_list_winners (
        election_id,
        region_code,
        party_code,
        sort_order
    );
