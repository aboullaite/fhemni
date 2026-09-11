-- Source-locked Darija programme summaries. A programme keeps at most one
-- editable generation and one published edition; NULL markers preserve history.
CREATE TABLE programme_media (
    id UUID PRIMARY KEY,
    programme_id UUID NOT NULL REFERENCES party_programmes (id) ON DELETE CASCADE,
    party_code VARCHAR(10) NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'QUEUED_SCRIPT', 'GENERATING_SCRIPT', 'SCRIPT_REVIEW',
        'QUEUED_MEDIA', 'RENDERING_MEDIA', 'MEDIA_REVIEW',
        'PUBLISHED', 'FAILED', 'STALE'
    )),
    working_marker BOOLEAN,
    published_marker BOOLEAN,
    script_revision INTEGER NOT NULL DEFAULT 1,
    script_json TEXT,
    script_text TEXT,
    script_model VARCHAR(120),
    tts_model VARCHAR(120),
    tts_voice VARCHAR(80),
    pronunciation_version VARCHAR(40) NOT NULL,
    audio_object_key VARCHAR(500),
    video_object_key VARCHAR(500),
    captions_object_key VARCHAR(500),
    duration_ms BIGINT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    available_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(500),
    lock_owner VARCHAR(64),
    lease_until TIMESTAMP WITH TIME ZONE,
    lease_token BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    script_reviewed_at TIMESTAMP WITH TIME ZONE,
    media_reviewed_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (programme_id, working_marker),
    UNIQUE (programme_id, published_marker)
);

CREATE INDEX programme_media_dispatch_idx
    ON programme_media (working_marker, status, available_at, lease_until, created_at);
CREATE INDEX programme_media_programme_idx
    ON programme_media (programme_id, created_at);
