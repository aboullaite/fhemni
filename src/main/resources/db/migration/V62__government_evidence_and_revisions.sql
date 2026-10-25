CREATE TABLE government_mandates (
    id VARCHAR(40) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE government_evidence (
    id UUID PRIMARY KEY,
    mandate_id VARCHAR(40) NOT NULL REFERENCES government_mandates(id),
    url VARCHAR(2048) NOT NULL,
    passage TEXT NOT NULL,
    locator VARCHAR(1000),
    original_language VARCHAR(16) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archive_bytes BYTEA,
    archived_sha256 VARCHAR(64),
    CHECK ((archive_bytes IS NULL AND archived_sha256 IS NULL) OR
           (archive_bytes IS NOT NULL AND archived_sha256 IS NOT NULL))
);
CREATE TABLE government_subjects (
    id UUID PRIMARY KEY,
    mandate_id VARCHAR(40) NOT NULL REFERENCES government_mandates(id),
    subject_type VARCHAR(16) NOT NULL CHECK(subject_type IN ('MANDATE','COMMITMENT','MEASURE')),
    slug VARCHAR(160) NOT NULL,
    UNIQUE(subject_type,slug)
);
CREATE TABLE government_revisions (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL REFERENCES government_subjects(id),
    revision_number INTEGER NOT NULL CHECK(revision_number>0),
    row_version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL CHECK(status IN ('DRAFT','REVIEWED','PUBLISHED','SUPERSEDED')),
    payload TEXT NOT NULL,
    author_id UUID NOT NULL,
    reviewer_id UUID,
    publisher_id UUID,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    material_hash VARCHAR(64),
    UNIQUE(subject_id,revision_number),
    UNIQUE(subject_id,id)
);
CREATE TABLE government_publications (
    subject_id UUID PRIMARY KEY REFERENCES government_subjects(id),
    revision_id UUID NOT NULL,
    FOREIGN KEY(subject_id,revision_id) REFERENCES government_revisions(subject_id,id)
);
CREATE TABLE government_programme_versions (
    id UUID PRIMARY KEY,
    mandate_id VARCHAR(40) NOT NULL REFERENCES government_mandates(id),
    version INTEGER NOT NULL,
    revision_id UUID NOT NULL REFERENCES government_revisions(id),
    published_at DATE,
    UNIQUE(mandate_id,version)
);
CREATE TABLE government_membership_history (
    revision_id UUID NOT NULL REFERENCES government_revisions(id),
    membership_id UUID NOT NULL,
    effective_from DATE NOT NULL,
    effective_until DATE,
    payload TEXT NOT NULL,
    PRIMARY KEY(revision_id,membership_id),
    CHECK(effective_until IS NULL OR effective_until>=effective_from)
);
CREATE INDEX government_revision_subject ON government_revisions(subject_id,status);
