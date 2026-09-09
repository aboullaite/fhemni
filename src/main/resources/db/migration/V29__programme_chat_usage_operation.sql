ALTER TABLE ai_usage_events DROP CONSTRAINT ai_usage_operation_check;
ALTER TABLE ai_usage_events ADD CONSTRAINT ai_usage_operation_check CHECK (
    operation IN (
        'ANALYSIS',
        'FACT_CHECK',
        'PROGRAMME_EXTRACTION',
        'PROMISE_FEASIBILITY',
        'CHAT_VIDEO',
        'CHAT_CHECK',
        'CHAT_PROGRAMME'
    )
);
