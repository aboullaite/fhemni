-- Preserve how each cached verdict was produced even after the configured provider changes.
ALTER TABLE promise_assessments
    ADD COLUMN provider_mode VARCHAR(20) NOT NULL DEFAULT 'gemini';

ALTER TABLE promise_assessments
    ADD COLUMN model_names VARCHAR(300) NOT NULL DEFAULT 'gemini-3.8-flash';
