-- Measures share the evidence-backed revision and publication lifecycle.
CREATE INDEX government_subject_mandate_type ON government_subjects(mandate_id,subject_type);
