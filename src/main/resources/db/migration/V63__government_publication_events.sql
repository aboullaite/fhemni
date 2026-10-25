ALTER TABLE government_revisions ADD COLUMN review_context_hash VARCHAR(64);
CREATE TABLE government_publication_events (
    id UUID PRIMARY KEY,
    mandate_id VARCHAR(40) NOT NULL REFERENCES government_mandates(id),
    revision_id UUID NOT NULL UNIQUE REFERENCES government_revisions(id),
    subject_id UUID NOT NULL REFERENCES government_subjects(id),
    material_hash VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX government_events_date ON government_publication_events(mandate_id,published_at);
