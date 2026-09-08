-- Durable programme feasibility jobs. The active_marker uniqueness rule keeps one
-- live job per programme while allowing any number of historical terminal jobs.
CREATE TABLE programme_assessment_jobs (
    id UUID PRIMARY KEY,
    programme_id UUID NOT NULL REFERENCES party_programmes (id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'QUEUED', 'RUNNING', 'RETRY_WAIT', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'
    )),
    provider_mode VARCHAR(20) NOT NULL,
    total_items INTEGER NOT NULL DEFAULT 0,
    completed_items INTEGER NOT NULL DEFAULT 0,
    failed_items INTEGER NOT NULL DEFAULT 0,
    current_promise_slug VARCHAR(180),
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(500),
    active_marker BOOLEAN,
    lock_owner VARCHAR(64),
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (programme_id, active_marker)
);
CREATE INDEX programme_assessment_jobs_dispatch_idx
    ON programme_assessment_jobs (active_marker, status, lease_until, updated_at);
CREATE INDEX programme_assessment_jobs_programme_idx
    ON programme_assessment_jobs (programme_id, created_at);

CREATE TABLE programme_assessment_job_items (
    job_id UUID NOT NULL REFERENCES programme_assessment_jobs (id) ON DELETE CASCADE,
    promise_id UUID NOT NULL REFERENCES party_promises (id) ON DELETE CASCADE,
    promise_slug VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PENDING', 'RUNNING', 'RETRY_WAIT', 'COMPLETED', 'FAILED'
    )),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (job_id, promise_id)
);
CREATE INDEX programme_assessment_job_items_dispatch_idx
    ON programme_assessment_job_items (job_id, status, next_attempt_at, created_at);
