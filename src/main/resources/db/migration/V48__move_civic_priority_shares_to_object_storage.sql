ALTER TABLE civic_priority_shares
    ADD COLUMN image_object_key VARCHAR(255);

-- Share cards existed only in local/test builds before this migration. They cannot
-- be moved to object storage safely from SQL, so discard those ephemeral rows.
DELETE FROM civic_priority_shares;

ALTER TABLE civic_priority_shares
    ALTER COLUMN image_object_key SET NOT NULL;

ALTER TABLE civic_priority_shares
    DROP COLUMN image_png;

CREATE UNIQUE INDEX idx_civic_priority_shares_object_key
    ON civic_priority_shares (image_object_key);

CREATE INDEX idx_civic_priority_shares_created_at
    ON civic_priority_shares (created_at);
