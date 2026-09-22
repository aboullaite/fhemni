CREATE TABLE civic_priority_share_deletions (
    share_token VARCHAR(32) PRIMARY KEY,
    image_object_key VARCHAR(255) NOT NULL UNIQUE,
    queued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempted_at TIMESTAMP WITH TIME ZONE,
    claim_token UUID
);

CREATE INDEX idx_civic_priority_share_deletions_ready
    ON civic_priority_share_deletions (next_attempt_at, queued_at);
