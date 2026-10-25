CREATE TABLE member_allowance_receipts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL CHECK(kind IN ('VIDEO','QUESTION')),
    charged BOOLEAN NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX member_allowance_owner_window ON member_allowance_receipts(user_id,kind,requested_at);
-- No job FK: expiring content must not reset the account's consumed allowance.
INSERT INTO member_allowance_receipts(id,job_id,user_id,kind,charged,requested_at)
SELECT id,id,user_id,'VIDEO',allowance_charged,created_at FROM member_analysis_jobs;
INSERT INTO member_allowance_receipts(id,job_id,user_id,kind,charged,requested_at)
SELECT a.id,j.id,j.user_id,'QUESTION',TRUE,a.requested_at
FROM ai_usage_events a JOIN member_analysis_jobs j ON j.id=a.analysis_id
WHERE a.operation IN ('CHAT_VIDEO','CHAT_CHECK');
