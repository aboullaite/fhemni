CREATE TABLE magic_link_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    return_target VARCHAR(1024) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX magic_link_tokens_email_created_idx ON magic_link_tokens (email, created_at);
CREATE INDEX magic_link_tokens_expiry_idx ON magic_link_tokens (expires_at);
