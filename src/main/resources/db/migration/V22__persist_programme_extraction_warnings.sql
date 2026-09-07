-- Keep extraction review notes across a failed feasibility pass and retry.
ALTER TABLE party_programmes
    ADD COLUMN extraction_warnings TEXT NOT NULL DEFAULT '[]';
