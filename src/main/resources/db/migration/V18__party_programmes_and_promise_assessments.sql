-- Curated 2026 electoral programmes and five-year promise assessments.
-- Draft material stays private. Published assessment revisions are immutable and
-- remain attributable to the evidence and source snapshot reviewed by an admin.
CREATE TABLE party_programmes (
    id UUID PRIMARY KEY,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code),
    election_year INTEGER NOT NULL CHECK (election_year = 2026),
    term_start_year INTEGER NOT NULL,
    term_end_year INTEGER NOT NULL,
    title_ar VARCHAR(300) NOT NULL,
    title_fr VARCHAR(300) NOT NULL,
    title_en VARCHAR(300) NOT NULL,
    summary_ar TEXT NOT NULL,
    summary_fr TEXT NOT NULL,
    summary_en TEXT NOT NULL,
    source_url VARCHAR(2000) NOT NULL,
    source_label VARCHAR(300) NOT NULL,
    source_language VARCHAR(12) NOT NULL,
    source_snapshot TEXT NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    source_retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_verified BOOLEAN NOT NULL DEFAULT FALSE,
    editorial_status VARCHAR(20) NOT NULL CHECK (editorial_status IN ('DRAFT', 'PUBLISHED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (party_code, election_year),
    CHECK (term_end_year - term_start_year = 5)
);

CREATE TABLE party_promises (
    id UUID PRIMARY KEY,
    programme_id UUID NOT NULL REFERENCES party_programmes (id) ON DELETE CASCADE,
    slug VARCHAR(180) NOT NULL UNIQUE,
    topic VARCHAR(80) NOT NULL,
    title_ar VARCHAR(300) NOT NULL,
    title_fr VARCHAR(300) NOT NULL,
    title_en VARCHAR(300) NOT NULL,
    promise_text TEXT NOT NULL,
    source_locator VARCHAR(300) NOT NULL,
    mechanism TEXT NOT NULL,
    financing TEXT NOT NULL,
    editorial_status VARCHAR(20) NOT NULL CHECK (editorial_status IN ('DRAFT', 'PUBLISHED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX party_promises_programme_idx ON party_promises (programme_id, editorial_status);

CREATE TABLE promise_assessments (
    id UUID PRIMARY KEY,
    promise_id UUID NOT NULL REFERENCES party_promises (id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    horizon_years INTEGER NOT NULL CHECK (horizon_years = 5),
    verdict VARCHAR(32) NOT NULL CHECK (verdict IN ('POSSIBLE', 'HARD', 'NOT_ACHIEVABLE', 'INSUFFICIENT_DATA')),
    summary_ar TEXT NOT NULL,
    summary_fr TEXT NOT NULL,
    summary_en TEXT NOT NULL,
    requirements_ar TEXT NOT NULL,
    requirements_fr TEXT NOT NULL,
    requirements_en TEXT NOT NULL,
    assumptions_ar TEXT NOT NULL,
    assumptions_fr TEXT NOT NULL,
    assumptions_en TEXT NOT NULL,
    calculation_notes_ar TEXT NOT NULL,
    calculation_notes_fr TEXT NOT NULL,
    calculation_notes_en TEXT NOT NULL,
    methodology_version VARCHAR(40) NOT NULL,
    data_cutoff DATE NOT NULL,
    editorial_status VARCHAR(20) NOT NULL CHECK (editorial_status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (promise_id, revision_number)
);
CREATE INDEX promise_assessments_promise_idx ON promise_assessments (promise_id, editorial_status, revision_number);

CREATE TABLE promise_evidence (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES promise_assessments (id) ON DELETE CASCADE,
    publisher VARCHAR(200) NOT NULL,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    published_on DATE,
    note TEXT NOT NULL,
    sort_order INTEGER NOT NULL
);
CREATE INDEX promise_evidence_assessment_idx ON promise_evidence (assessment_id, sort_order);
