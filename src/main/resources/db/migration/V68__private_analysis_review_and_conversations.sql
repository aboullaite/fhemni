CREATE TABLE member_analysis_publication_requests (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES member_analysis_jobs(id) ON DELETE CASCADE,
    state VARCHAR(16) NOT NULL CHECK(state IN ('REQUESTED','REVIEWED','PUBLISHED','REJECTED')),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewer_id UUID REFERENCES app_users(id),
    review_note VARCHAR(4000),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    derivative_id UUID REFERENCES analysis_revisions(id)
);
CREATE TABLE member_analysis_conversations (
    job_id UUID PRIMARY KEY REFERENCES member_analysis_jobs(id) ON DELETE CASCADE,
    interaction_id TEXT,
    answers_json TEXT NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    question_count INTEGER NOT NULL DEFAULT 0
);
-- Admin recovery is scoped to its worker instance rather than every running application.
ALTER TABLE analysis_revisions ADD COLUMN worker_instance VARCHAR(36);
ALTER TABLE analysis_revisions ADD COLUMN worker_lease_until TIMESTAMP WITH TIME ZONE;
