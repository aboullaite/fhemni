CREATE TABLE analysis_revisions (
    id UUID PRIMARY KEY,
    youtube_video_id VARCHAR(11) NOT NULL,
    video_url VARCHAR(2048) NOT NULL,
    output_language VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    progress INTEGER NOT NULL,
    progress_message VARCHAR(500) NOT NULL,
    demo BOOLEAN NOT NULL,
    model VARCHAR(160) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    provider_interaction_id VARCHAR(255),
    report_json TEXT,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    published_by UUID,
    CONSTRAINT analysis_revisions_status_check
        CHECK (status IN ('QUEUED', 'ANALYZING', 'FACT_CHECKING', 'COMPLETED', 'FAILED')),
    CONSTRAINT analysis_revisions_progress_check CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT analysis_revisions_publisher_fk
        FOREIGN KEY (published_by) REFERENCES app_users (id)
);

CREATE INDEX analysis_revisions_reuse_idx
    ON analysis_revisions (youtube_video_id, output_language, model, prompt_version, status, created_at);
CREATE INDEX analysis_revisions_recent_idx
    ON analysis_revisions (created_at);

ALTER TABLE catalog_videos ADD COLUMN published_analysis_id UUID;
ALTER TABLE catalog_videos ADD CONSTRAINT catalog_videos_published_analysis_fk
    FOREIGN KEY (published_analysis_id) REFERENCES analysis_revisions (id);
CREATE INDEX catalog_videos_published_analysis_idx
    ON catalog_videos (published_analysis_id);
