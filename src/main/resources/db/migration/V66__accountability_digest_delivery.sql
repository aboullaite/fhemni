CREATE TABLE accountability_digest_preferences (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    locale VARCHAR(2) NOT NULL CHECK(locale IN ('ar','fr','en')),
    consent_email VARCHAR(320),
    consent_version UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE accountability_digest_outbox (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    consent_version UUID NOT NULL,
    state VARCHAR(16) NOT NULL CHECK(state IN ('PENDING','LEASED','SENDING','SENT','FAILED','UNKNOWN','CANCELLED')),
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(user_id,period_start)
);
CREATE INDEX accountability_digest_queue ON accountability_digest_outbox(state,created_at);
