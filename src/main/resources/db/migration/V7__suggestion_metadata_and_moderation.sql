ALTER TABLE video_suggestions ADD COLUMN title VARCHAR(500);
ALTER TABLE video_suggestions ADD COLUMN author_name VARCHAR(300);
ALTER TABLE video_suggestions ADD COLUMN thumbnail_url VARCHAR(2048);
ALTER TABLE video_suggestions ADD COLUMN moderation_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE video_suggestions ADD COLUMN moderation_reason VARCHAR(500);
ALTER TABLE video_suggestions ADD COLUMN metadata_checked_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE video_suggestions ADD CONSTRAINT video_suggestions_moderation_status_check
    CHECK (moderation_status IN ('APPROVED', 'REVIEW_REQUIRED'));

CREATE INDEX video_suggestions_moderation_queue_idx
    ON video_suggestions (status, moderation_status, last_suggested_at);
