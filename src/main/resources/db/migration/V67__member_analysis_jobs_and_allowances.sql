CREATE TABLE ai_budget_locks (id VARCHAR(32) PRIMARY KEY);
INSERT INTO ai_budget_locks(id) VALUES('GLOBAL');
CREATE TABLE member_analysis_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    video_url VARCHAR(256) NOT NULL,
    video_id VARCHAR(11) NOT NULL,
    language VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK(state IN ('QUEUED','PROCESSING','READY','FAILED')),
    allowance_month DATE NOT NULL,
    allowance_charged BOOLEAN NOT NULL DEFAULT TRUE,
    duration_seconds BIGINT NOT NULL,
    usage_reservation_id UUID NOT NULL REFERENCES ai_usage_events(id),
    model VARCHAR(160) NOT NULL,
    prompt_version VARCHAR(160) NOT NULL,
    fact_check_model VARCHAR(160) NOT NULL,
    fact_check_prompt_version VARCHAR(160) NOT NULL,
    credential_version VARCHAR(160) NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    provider_interaction_id TEXT,
    report_json TEXT,
    error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(user_id,idempotency_key)
);
CREATE INDEX member_analysis_owner_history ON member_analysis_jobs(user_id,created_at);
CREATE INDEX member_analysis_queue ON member_analysis_jobs(state,created_at);
-- Serializing on the owner row is portable across PostgreSQL and the H2 tests.
-- This separate active slot also enforces at most one queued/processing job per account.
CREATE TABLE member_analysis_active (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL UNIQUE REFERENCES member_analysis_jobs(id) ON DELETE CASCADE
);
