CREATE TABLE video_suggestions (
    id UUID PRIMARY KEY,
    youtube_video_id VARCHAR(11) NOT NULL,
    canonical_url VARCHAR(2048) NOT NULL,
    status VARCHAR(20) NOT NULL,
    submission_count INTEGER NOT NULL,
    first_suggested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_suggested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT video_suggestions_youtube_id_unique UNIQUE (youtube_video_id),
    CONSTRAINT video_suggestions_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'DISMISSED')),
    CONSTRAINT video_suggestions_count_check CHECK (submission_count > 0)
);

CREATE INDEX video_suggestions_queue_idx
    ON video_suggestions (status, submission_count, last_suggested_at);
