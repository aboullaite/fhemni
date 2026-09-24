ALTER TABLE election_party_results
    ALTER COLUMN regional_list_seats DROP NOT NULL;

ALTER TABLE election_party_results
    ADD CONSTRAINT election_party_results_partial_breakdown_check
        CHECK (regional_list_seats IS NOT NULL OR local_seats <= total_seats);
