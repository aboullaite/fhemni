CREATE TABLE ai_usage_events (
    id UUID PRIMARY KEY,
    operation VARCHAR(30) NOT NULL,
    user_id UUID,
    analysis_id UUID,
    model VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    input_tokens INTEGER,
    output_tokens INTEGER,
    cached_tokens INTEGER,
    thought_tokens INTEGER,
    tool_use_tokens INTEGER,
    grounding_queries INTEGER,
    CONSTRAINT ai_usage_operation_check CHECK (operation IN ('ANALYSIS', 'CHAT_VIDEO', 'CHAT_CHECK')),
    CONSTRAINT ai_usage_status_check CHECK (status IN ('RESERVED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ai_usage_user_fk FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE SET NULL
);

CREATE INDEX ai_usage_operation_time_idx ON ai_usage_events (operation, requested_at);
CREATE INDEX ai_usage_user_time_idx ON ai_usage_events (user_id, requested_at);
