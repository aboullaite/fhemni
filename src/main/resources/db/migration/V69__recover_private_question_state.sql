ALTER TABLE member_analysis_conversations ADD COLUMN last_question_state VARCHAR(16) NOT NULL DEFAULT 'READY'
    CHECK(last_question_state IN ('READY','PROCESSING','ANSWERED','FAILED','UNCERTAIN'));
UPDATE member_analysis_conversations SET last_question_state='PROCESSING' WHERE lease_token IS NOT NULL;
