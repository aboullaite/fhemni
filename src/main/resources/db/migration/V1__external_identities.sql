CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(320),
    avatar_url VARCHAR(2048),
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT app_users_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE external_identities (
    provider VARCHAR(64) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    provider_username VARCHAR(255),
    email_verified BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (provider, subject),
    CONSTRAINT external_identities_user_fk
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE INDEX external_identities_user_idx ON external_identities (user_id);
CREATE INDEX app_users_email_idx ON app_users (email);
