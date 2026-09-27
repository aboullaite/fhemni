ALTER TABLE election_party_results
    ADD COLUMN local_votes BIGINT CHECK (local_votes IS NULL OR local_votes >= 0);

ALTER TABLE election_party_results
    ADD COLUMN regional_votes BIGINT CHECK (regional_votes IS NULL OR regional_votes >= 0);

ALTER TABLE election_party_results
    ADD CONSTRAINT election_party_results_vote_components_check
        CHECK (
            local_votes IS NULL
            OR regional_votes IS NULL
            OR (votes IS NOT NULL AND votes = local_votes + regional_votes)
        );
