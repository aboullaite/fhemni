ALTER TABLE video_suggestions ADD COLUMN suggested_by_user_id UUID;

ALTER TABLE video_suggestions ADD CONSTRAINT video_suggestions_submitter_fk
    FOREIGN KEY (suggested_by_user_id) REFERENCES app_users (id) ON DELETE SET NULL;

CREATE INDEX video_suggestions_submitter_idx
    ON video_suggestions (suggested_by_user_id);
