ALTER TABLE elections
    ADD COLUMN turnout_percent NUMERIC(5, 2)
        CHECK (turnout_percent IS NULL OR (turnout_percent >= 0 AND turnout_percent <= 100));
