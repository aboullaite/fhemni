CREATE TABLE accountability_follows (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    target_type VARCHAR(16) NOT NULL CHECK(target_type IN ('COMMITMENT','REGION')),
    target_id VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(user_id,target_type,target_id)
);
CREATE TABLE accountability_corrections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    target_type VARCHAR(16) NOT NULL CHECK(target_type IN ('COMMITMENT','MEASURE')),
    target_id UUID NOT NULL,
    evidence_url VARCHAR(2048) NOT NULL,
    correction_text VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','REVIEWED','DISMISSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewer_id UUID REFERENCES app_users(id),
    review_note VARCHAR(4000),
    reviewed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX accountability_corrections_queue ON accountability_corrections(status,created_at);
