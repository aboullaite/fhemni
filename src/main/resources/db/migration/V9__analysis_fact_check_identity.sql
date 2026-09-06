ALTER TABLE analysis_revisions
    ADD COLUMN fact_check_model VARCHAR(160) NOT NULL DEFAULT '';

ALTER TABLE analysis_revisions
    ADD COLUMN fact_check_prompt_version VARCHAR(80) NOT NULL DEFAULT '';

DROP INDEX analysis_revisions_reuse_idx;

CREATE INDEX analysis_revisions_reuse_idx
    ON analysis_revisions (
        youtube_video_id,
        output_language,
        model,
        prompt_version,
        fact_check_model,
        fact_check_prompt_version,
        status,
        created_at
    );
