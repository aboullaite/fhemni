-- A regional ballot is cast for a list, not separately for each elected member.
-- Keep the count once per region/party; NULL means not yet verified/backfilled.
ALTER TABLE election_region_party_results
    ADD COLUMN regional_votes BIGINT CHECK (regional_votes IS NULL OR regional_votes >= 0);
