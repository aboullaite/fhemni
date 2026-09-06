UPDATE app_users
   SET display_name = CASE
       WHEN POSITION(' ' IN TRIM(display_name)) > 0
           THEN SUBSTRING(TRIM(display_name) FROM 1 FOR POSITION(' ' IN TRIM(display_name)) - 1)
       ELSE TRIM(display_name)
   END;

ALTER TABLE app_users DROP CONSTRAINT app_users_role_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('USER', 'PREMIUM', 'ADMIN'));

CREATE TABLE suggestion_votes (
    suggestion_id UUID NOT NULL,
    user_id UUID NOT NULL,
    vote_value SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (suggestion_id, user_id),
    CONSTRAINT suggestion_votes_suggestion_fk
        FOREIGN KEY (suggestion_id) REFERENCES video_suggestions (id) ON DELETE CASCADE,
    CONSTRAINT suggestion_votes_user_fk
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT suggestion_votes_value_check CHECK (vote_value IN (-1, 1))
);

CREATE INDEX suggestion_votes_user_idx ON suggestion_votes (user_id);
