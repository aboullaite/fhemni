ALTER TABLE programme_assessment_jobs
    ALTER COLUMN review_context SET DATA TYPE TEXT;

ALTER TABLE programme_assessment_job_items
    ADD COLUMN generated_assessment_id UUID REFERENCES promise_assessments (id) ON DELETE SET NULL;

CREATE TABLE programme_assessment_job_reports (
    job_id UUID NOT NULL REFERENCES programme_assessment_jobs (id) ON DELETE CASCADE,
    report_id UUID NOT NULL REFERENCES promise_assessment_reports (id) ON DELETE CASCADE,
    report_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (job_id, report_id)
);

CREATE INDEX programme_assessment_job_items_generated_assessment_idx
    ON programme_assessment_job_items (generated_assessment_id);
