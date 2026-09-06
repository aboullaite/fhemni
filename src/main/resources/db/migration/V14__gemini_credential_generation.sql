ALTER TABLE analysis_revisions
    ADD COLUMN provider_credential_version VARCHAR(64) NOT NULL DEFAULT 'legacy';

CREATE INDEX idx_analysis_revisions_provider_credential
    ON analysis_revisions (provider_credential_version, status);

CREATE TABLE video_contexts (
    id UUID PRIMARY KEY,
    youtube_video_id VARCHAR(11) NOT NULL,
    output_language VARCHAR(20) NOT NULL,
    model VARCHAR(160) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    provider_credential_version VARCHAR(64) NOT NULL,
    provider_interaction_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT video_contexts_identity_unique UNIQUE (
        youtube_video_id,
        output_language,
        model,
        prompt_version,
        provider_credential_version
    )
);

CREATE INDEX idx_video_contexts_lookup
    ON video_contexts (
        youtube_video_id,
        output_language,
        model,
        prompt_version,
        provider_credential_version
    );
