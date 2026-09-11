ALTER TABLE programme_assessment_jobs
    ADD COLUMN reassessment BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE programme_assessment_jobs
    ADD COLUMN review_context VARCHAR(4000);

CREATE TABLE promise_assessment_reports (
    id UUID PRIMARY KEY,
    promise_id UUID NOT NULL REFERENCES party_promises (id) ON DELETE CASCADE,
    assessment_id UUID NOT NULL REFERENCES promise_assessments (id) ON DELETE CASCADE,
    reporter_user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    category VARCHAR(40) NOT NULL CHECK (category IN (
        'FACTUAL_OR_LEGAL_ERROR', 'OUTDATED_OR_MISSING_SOURCE', 'UNCLEAR_REASONING', 'OTHER'
    )),
    details VARCHAR(1500) NOT NULL,
    source_url VARCHAR(2048),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (assessment_id, reporter_user_id)
);

CREATE INDEX promise_assessment_reports_open_idx
    ON promise_assessment_reports (status, created_at);

CREATE INDEX promise_assessment_reports_promise_idx
    ON promise_assessment_reports (promise_id, status, created_at);
