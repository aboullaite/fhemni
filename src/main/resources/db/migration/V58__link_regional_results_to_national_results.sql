ALTER TABLE election_region_party_results
    ADD CONSTRAINT election_region_party_results_national_party_fk
        FOREIGN KEY (election_id, party_code)
        REFERENCES election_party_results (election_id, party_code)
        ON DELETE CASCADE;
