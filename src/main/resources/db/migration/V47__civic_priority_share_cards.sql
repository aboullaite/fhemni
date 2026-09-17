CREATE TABLE civic_priority_shares (
    id UUID PRIMARY KEY,
    share_token VARCHAR(32) NOT NULL UNIQUE,
    share_kind VARCHAR(16) NOT NULL CHECK (share_kind IN ('COMPASS', 'PARTIES')),
    language VARCHAR(2) NOT NULL CHECK (language IN ('ar', 'fr', 'en')),
    image_png BYTEA NOT NULL,
    image_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (image_sha256, share_kind, language)
);

