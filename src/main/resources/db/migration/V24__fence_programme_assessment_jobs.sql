-- A monotonically increasing lease generation prevents a worker whose lease expired
-- from committing results after a replacement worker has claimed the same job.
ALTER TABLE programme_assessment_jobs
    ADD COLUMN lease_token BIGINT NOT NULL DEFAULT 0;
