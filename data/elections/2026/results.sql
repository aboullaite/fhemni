-- Canonical, replayable snapshot for Morocco's 2026 legislative election.
-- Current state: counting has started, but no official party result is recorded yet.
--
-- Update and commit this file whenever official results are loaded into production.
-- It intentionally replaces the complete 2026 result snapshot in one transaction.
-- Run with psql and ON_ERROR_STOP enabled; see docs/operations/election-results.md.

\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('fhemni:legislative-2026-results'));

-- Edit this one row for every official snapshot. source_updated_at is the
-- ordering key that prevents an older file from replacing newer production data.
CREATE TEMP TABLE incoming_election_snapshot ON COMMIT DROP AS
SELECT
    UUID '20260000-0000-4000-8000-000000000001' AS id,
    CAST('legislative-2026' AS VARCHAR(40)) AS slug,
    DATE '2026-09-23' AS election_date,
    CAST('COUNTING' AS VARCHAR(16)) AS status,
    395 AS total_seats,
    CAST(NULL AS BIGINT) AS registered_voters,
    CAST(NULL AS BIGINT) AS votes_cast,
    CAST(NULL AS BIGINT) AS valid_votes,
    CAST(NULL AS VARCHAR(32)) AS vote_basis,
    CAST('النتائج الرسمية المعلنة' AS VARCHAR(300)) AS source_label_ar,
    CAST('Résultats officiels publiés' AS VARCHAR(300)) AS source_label_fr,
    CAST('Published official results' AS VARCHAR(300)) AS source_label_en,
    CAST(NULL AS VARCHAR(1200)) AS source_url,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS source_updated_at;

CREATE TEMP TABLE incoming_election_regions (
    election_id UUID NOT NULL,
    code VARCHAR(32) NOT NULL,
    name_ar VARCHAR(160) NOT NULL,
    name_fr VARCHAR(160) NOT NULL,
    name_en VARCHAR(160) NOT NULL,
    map_key VARCHAR(16) NOT NULL,
    allocated_seats INTEGER,
    status VARCHAR(16) NOT NULL,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (election_id, code)
) ON COMMIT DROP;

INSERT INTO incoming_election_regions (
    election_id, code, name_ar, name_fr, name_en, map_key,
    allocated_seats, status, sort_order
) VALUES
    ('20260000-0000-4000-8000-000000000001', 'tanger-tetouan-al-hoceima', 'طنجة - تطوان - الحسيمة', 'Tanger-Tétouan-Al Hoceïma', 'Tanger-Tetouan-Al Hoceima', 'MA-01', NULL, 'PENDING', 1),
    ('20260000-0000-4000-8000-000000000001', 'oriental', 'الشرق', 'L''Oriental', 'Oriental', 'MA-02', NULL, 'PENDING', 2),
    ('20260000-0000-4000-8000-000000000001', 'fes-meknes', 'فاس - مكناس', 'Fès-Meknès', 'Fes-Meknes', 'MA-03', NULL, 'PENDING', 3),
    ('20260000-0000-4000-8000-000000000001', 'rabat-sale-kenitra', 'الرباط - سلا - القنيطرة', 'Rabat-Salé-Kénitra', 'Rabat-Sale-Kenitra', 'MA-04', NULL, 'PENDING', 4),
    ('20260000-0000-4000-8000-000000000001', 'beni-mellal-khenifra', 'بني ملال - خنيفرة', 'Béni Mellal-Khénifra', 'Beni Mellal-Khenifra', 'MA-05', NULL, 'PENDING', 5),
    ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'الدار البيضاء - سطات', 'Casablanca-Settat', 'Casablanca-Settat', 'MA-06', NULL, 'PENDING', 6),
    ('20260000-0000-4000-8000-000000000001', 'marrakech-safi', 'مراكش - آسفي', 'Marrakech-Safi', 'Marrakech-Safi', 'MA-07', NULL, 'PENDING', 7),
    ('20260000-0000-4000-8000-000000000001', 'draa-tafilalet', 'درعة - تافيلالت', 'Drâa-Tafilalet', 'Draa-Tafilalet', 'MA-08', NULL, 'PENDING', 8),
    ('20260000-0000-4000-8000-000000000001', 'souss-massa', 'سوس - ماسة', 'Souss-Massa', 'Souss-Massa', 'MA-09', NULL, 'PENDING', 9),
    ('20260000-0000-4000-8000-000000000001', 'guelmim-oued-noun', 'كلميم - واد نون', 'Guelmim-Oued Noun', 'Guelmim-Oued Noun', 'MA-10', NULL, 'PENDING', 10),
    ('20260000-0000-4000-8000-000000000001', 'laayoune-sakia-el-hamra', 'العيون - الساقية الحمراء', 'Laâyoune-Sakia El Hamra', 'Laayoune-Sakia El Hamra', 'MA-11', NULL, 'PENDING', 11),
    ('20260000-0000-4000-8000-000000000001', 'dakhla-oued-ed-dahab', 'الداخلة - وادي الذهب', 'Dakhla-Oued Ed-Dahab', 'Dakhla-Oued Ed-Dahab', 'MA-12', NULL, 'PENDING', 12);

CREATE TEMP TABLE incoming_election_party_results (
    election_id UUID NOT NULL,
    party_code VARCHAR(10) NOT NULL,
    votes BIGINT,
    local_seats INTEGER NOT NULL,
    regional_list_seats INTEGER NOT NULL,
    total_seats INTEGER NOT NULL,
    PRIMARY KEY (election_id, party_code)
) ON COMMIT DROP;

CREATE TEMP TABLE incoming_election_region_party_results (
    election_id UUID NOT NULL,
    region_code VARCHAR(32) NOT NULL,
    party_code VARCHAR(10) NOT NULL,
    local_seats INTEGER NOT NULL,
    regional_list_seats INTEGER NOT NULL,
    total_seats INTEGER NOT NULL,
    PRIMARY KEY (election_id, region_code, party_code)
) ON COMMIT DROP;

CREATE TEMP TABLE incoming_election_constituencies (
    election_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    region_code VARCHAR(32) NOT NULL,
    name_ar VARCHAR(160) NOT NULL,
    name_fr VARCHAR(160) NOT NULL,
    name_en VARCHAR(160) NOT NULL,
    allocated_seats INTEGER,
    status VARCHAR(16) NOT NULL,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (election_id, code)
) ON COMMIT DROP;

CREATE TEMP TABLE incoming_election_constituency_winners (
    election_id UUID NOT NULL,
    constituency_code VARCHAR(64) NOT NULL,
    candidate_key VARCHAR(96) NOT NULL,
    candidate_name VARCHAR(180) NOT NULL,
    party_code VARCHAR(10) NOT NULL,
    votes BIGINT,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (election_id, constituency_code, candidate_key)
) ON COMMIT DROP;

-- Add the complete current snapshot to the two incoming result tables.
-- National rows must satisfy total_seats = local_seats + regional_list_seats.
-- Example shape only (do not uncomment without verified official figures):
-- INSERT INTO incoming_election_party_results
--     (election_id, party_code, votes, local_seats, regional_list_seats, total_seats)
-- VALUES
--     ('20260000-0000-4000-8000-000000000001', 'RNI', NULL, 0, 0, 0);
--
-- Regional rows follow the same rule:
-- INSERT INTO incoming_election_region_party_results
--     (election_id, region_code, party_code, local_seats, regional_list_seats, total_seats)
-- VALUES
--     ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'RNI', 0, 0, 0);
--
-- Constituency and winner rows are a complete sub-snapshot too. A winner is one
-- identified local seat; candidate_key must remain stable across corrections.
-- INSERT INTO incoming_election_constituencies
--     (election_id, code, region_code, name_ar, name_fr, name_en,
--      allocated_seats, status, sort_order)
-- VALUES
--     ('20260000-0000-4000-8000-000000000001', 'example', 'casablanca-settat',
--      'Example', 'Example', 'Example', 1, 'PROVISIONAL', 1);
-- INSERT INTO incoming_election_constituency_winners
--     (election_id, constituency_code, candidate_key, candidate_name,
--      party_code, votes, sort_order)
-- VALUES
--     ('20260000-0000-4000-8000-000000000001', 'example', 'candidate',
--      'Candidate', 'RNI', NULL, 1);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM elections current_snapshot
          JOIN incoming_election_snapshot incoming ON incoming.id = current_snapshot.id
         WHERE current_snapshot.source_updated_at IS NOT NULL
           AND (incoming.source_updated_at IS NULL
                OR incoming.source_updated_at < current_snapshot.source_updated_at)
    ) THEN
        RAISE EXCEPTION 'Refusing to replace the current election result with an older source snapshot';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM elections current_snapshot
          JOIN incoming_election_snapshot incoming ON incoming.id = current_snapshot.id
         WHERE current_snapshot.source_updated_at IS NOT DISTINCT FROM incoming.source_updated_at
           AND (
               ROW(
                   current_snapshot.slug, current_snapshot.election_date, current_snapshot.status,
                   current_snapshot.total_seats, current_snapshot.registered_voters,
                   current_snapshot.votes_cast, current_snapshot.valid_votes, current_snapshot.vote_basis,
                   current_snapshot.source_label_ar, current_snapshot.source_label_fr,
                   current_snapshot.source_label_en, current_snapshot.source_url
               ) IS DISTINCT FROM ROW(
                   incoming.slug, incoming.election_date, incoming.status,
                   incoming.total_seats, incoming.registered_voters,
                   incoming.votes_cast, incoming.valid_votes, incoming.vote_basis,
                   incoming.source_label_ar, incoming.source_label_fr,
                   incoming.source_label_en, incoming.source_url
               )
               OR EXISTS (
                   (SELECT code, name_ar, name_fr, name_en, map_key, allocated_seats, status, sort_order
                      FROM election_regions WHERE election_id = current_snapshot.id
                    EXCEPT
                    SELECT code, name_ar, name_fr, name_en, map_key, allocated_seats, status, sort_order
                      FROM incoming_election_regions WHERE election_id = incoming.id)
                   UNION ALL
                   (SELECT code, name_ar, name_fr, name_en, map_key, allocated_seats, status, sort_order
                      FROM incoming_election_regions WHERE election_id = incoming.id
                    EXCEPT
                    SELECT code, name_ar, name_fr, name_en, map_key, allocated_seats, status, sort_order
                      FROM election_regions WHERE election_id = current_snapshot.id)
               )
               OR EXISTS (
                   (SELECT party_code, votes, local_seats, regional_list_seats, total_seats
                      FROM election_party_results WHERE election_id = current_snapshot.id
                    EXCEPT
                    SELECT party_code, votes, local_seats, regional_list_seats, total_seats
                      FROM incoming_election_party_results WHERE election_id = incoming.id)
                   UNION ALL
                   (SELECT party_code, votes, local_seats, regional_list_seats, total_seats
                      FROM incoming_election_party_results WHERE election_id = incoming.id
                    EXCEPT
                    SELECT party_code, votes, local_seats, regional_list_seats, total_seats
                      FROM election_party_results WHERE election_id = current_snapshot.id)
               )
               OR EXISTS (
                   (SELECT region_code, party_code, local_seats, regional_list_seats, total_seats
                      FROM election_region_party_results WHERE election_id = current_snapshot.id
                    EXCEPT
                    SELECT region_code, party_code, local_seats, regional_list_seats, total_seats
                      FROM incoming_election_region_party_results WHERE election_id = incoming.id)
                   UNION ALL
                   (SELECT region_code, party_code, local_seats, regional_list_seats, total_seats
                      FROM incoming_election_region_party_results WHERE election_id = incoming.id
                    EXCEPT
                    SELECT region_code, party_code, local_seats, regional_list_seats, total_seats
                      FROM election_region_party_results WHERE election_id = current_snapshot.id)
               )
               OR EXISTS (
                   (SELECT code, region_code, name_ar, name_fr, name_en,
                           allocated_seats, status, sort_order
                      FROM election_constituencies WHERE election_id = current_snapshot.id
                    EXCEPT
                    SELECT code, region_code, name_ar, name_fr, name_en,
                           allocated_seats, status, sort_order
                      FROM incoming_election_constituencies WHERE election_id = incoming.id)
                   UNION ALL
                   (SELECT code, region_code, name_ar, name_fr, name_en,
                           allocated_seats, status, sort_order
                      FROM incoming_election_constituencies WHERE election_id = incoming.id
                    EXCEPT
                    SELECT code, region_code, name_ar, name_fr, name_en,
                           allocated_seats, status, sort_order
                      FROM election_constituencies WHERE election_id = current_snapshot.id)
               )
               OR EXISTS (
                   (SELECT constituency_code, candidate_key, candidate_name,
                           party_code, votes, sort_order
                      FROM election_constituency_winners WHERE election_id = current_snapshot.id
                    EXCEPT
                    SELECT constituency_code, candidate_key, candidate_name,
                           party_code, votes, sort_order
                      FROM incoming_election_constituency_winners WHERE election_id = incoming.id)
                   UNION ALL
                   (SELECT constituency_code, candidate_key, candidate_name,
                           party_code, votes, sort_order
                      FROM incoming_election_constituency_winners WHERE election_id = incoming.id
                    EXCEPT
                    SELECT constituency_code, candidate_key, candidate_name,
                           party_code, votes, sort_order
                      FROM election_constituency_winners WHERE election_id = current_snapshot.id)
               )
           )
    ) THEN
        RAISE EXCEPTION 'Refusing a different election snapshot with the same source_updated_at; use a newer official revision timestamp';
    END IF;
END $$;

INSERT INTO elections (
    id, slug, election_date, status, total_seats,
    registered_voters, votes_cast, valid_votes, vote_basis,
    source_label_ar, source_label_fr, source_label_en,
    source_url, source_updated_at, updated_at
) SELECT
    id, slug, election_date, status, total_seats,
    registered_voters, votes_cast, valid_votes, vote_basis,
    source_label_ar, source_label_fr, source_label_en,
    source_url, source_updated_at, CURRENT_TIMESTAMP
FROM incoming_election_snapshot
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    election_date = EXCLUDED.election_date,
    status = EXCLUDED.status,
    total_seats = EXCLUDED.total_seats,
    registered_voters = EXCLUDED.registered_voters,
    votes_cast = EXCLUDED.votes_cast,
    valid_votes = EXCLUDED.valid_votes,
    vote_basis = EXCLUDED.vote_basis,
    source_label_ar = EXCLUDED.source_label_ar,
    source_label_fr = EXCLUDED.source_label_fr,
    source_label_en = EXCLUDED.source_label_en,
    source_url = EXCLUDED.source_url,
    source_updated_at = EXCLUDED.source_updated_at,
    updated_at = EXCLUDED.updated_at
WHERE ROW(
    elections.slug, elections.election_date, elections.status, elections.total_seats,
    elections.registered_voters, elections.votes_cast, elections.valid_votes, elections.vote_basis,
    elections.source_label_ar, elections.source_label_fr, elections.source_label_en,
    elections.source_url, elections.source_updated_at
) IS DISTINCT FROM ROW(
    EXCLUDED.slug, EXCLUDED.election_date, EXCLUDED.status, EXCLUDED.total_seats,
    EXCLUDED.registered_voters, EXCLUDED.votes_cast, EXCLUDED.valid_votes, EXCLUDED.vote_basis,
    EXCLUDED.source_label_ar, EXCLUDED.source_label_fr, EXCLUDED.source_label_en,
    EXCLUDED.source_url, EXCLUDED.source_updated_at
);

INSERT INTO election_regions (
    election_id, code, name_ar, name_fr, name_en, map_key,
    allocated_seats, status, sort_order, updated_at
) SELECT
    election_id, code, name_ar, name_fr, name_en, map_key,
    allocated_seats, status, sort_order, CURRENT_TIMESTAMP
FROM incoming_election_regions
ON CONFLICT (election_id, code) DO UPDATE SET
    name_ar = EXCLUDED.name_ar,
    name_fr = EXCLUDED.name_fr,
    name_en = EXCLUDED.name_en,
    map_key = EXCLUDED.map_key,
    allocated_seats = EXCLUDED.allocated_seats,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = EXCLUDED.updated_at
WHERE ROW(
    election_regions.name_ar, election_regions.name_fr, election_regions.name_en,
    election_regions.map_key, election_regions.allocated_seats,
    election_regions.status, election_regions.sort_order
) IS DISTINCT FROM ROW(
    EXCLUDED.name_ar, EXCLUDED.name_fr, EXCLUDED.name_en,
    EXCLUDED.map_key, EXCLUDED.allocated_seats,
    EXCLUDED.status, EXCLUDED.sort_order
);

INSERT INTO election_constituencies (
    election_id, code, region_code, name_ar, name_fr, name_en,
    allocated_seats, status, sort_order, updated_at
) SELECT
    election_id, code, region_code, name_ar, name_fr, name_en,
    allocated_seats, status, sort_order, CURRENT_TIMESTAMP
FROM incoming_election_constituencies
ON CONFLICT (election_id, code) DO UPDATE SET
    region_code = EXCLUDED.region_code,
    name_ar = EXCLUDED.name_ar,
    name_fr = EXCLUDED.name_fr,
    name_en = EXCLUDED.name_en,
    allocated_seats = EXCLUDED.allocated_seats,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = EXCLUDED.updated_at
WHERE ROW(
    election_constituencies.region_code,
    election_constituencies.name_ar,
    election_constituencies.name_fr,
    election_constituencies.name_en,
    election_constituencies.allocated_seats,
    election_constituencies.status,
    election_constituencies.sort_order
) IS DISTINCT FROM ROW(
    EXCLUDED.region_code,
    EXCLUDED.name_ar,
    EXCLUDED.name_fr,
    EXCLUDED.name_en,
    EXCLUDED.allocated_seats,
    EXCLUDED.status,
    EXCLUDED.sort_order
);

DELETE FROM election_constituency_winners
WHERE election_id = '20260000-0000-4000-8000-000000000001'
  AND NOT EXISTS (
      SELECT 1
        FROM incoming_election_constituency_winners incoming
       WHERE incoming.election_id = election_constituency_winners.election_id
         AND incoming.constituency_code = election_constituency_winners.constituency_code
         AND incoming.candidate_key = election_constituency_winners.candidate_key
  );

DELETE FROM election_constituencies
WHERE election_id = '20260000-0000-4000-8000-000000000001'
  AND NOT EXISTS (
      SELECT 1
        FROM incoming_election_constituencies incoming
       WHERE incoming.election_id = election_constituencies.election_id
         AND incoming.code = election_constituencies.code
  );

DELETE FROM election_region_party_results
WHERE election_id = '20260000-0000-4000-8000-000000000001'
  AND NOT EXISTS (
      SELECT 1
        FROM incoming_election_region_party_results incoming
       WHERE incoming.election_id = election_region_party_results.election_id
         AND incoming.region_code = election_region_party_results.region_code
         AND incoming.party_code = election_region_party_results.party_code
  );

DELETE FROM election_regions
WHERE election_id = '20260000-0000-4000-8000-000000000001'
  AND NOT EXISTS (
      SELECT 1
        FROM incoming_election_regions incoming
       WHERE incoming.election_id = election_regions.election_id
         AND incoming.code = election_regions.code
  );

DELETE FROM election_party_results
WHERE election_id = '20260000-0000-4000-8000-000000000001'
  AND NOT EXISTS (
      SELECT 1
        FROM incoming_election_party_results incoming
       WHERE incoming.election_id = election_party_results.election_id
         AND incoming.party_code = election_party_results.party_code
  );

INSERT INTO election_party_results (
    election_id, party_code, votes, local_seats,
    regional_list_seats, total_seats, updated_at
) SELECT
    election_id, party_code, votes, local_seats,
    regional_list_seats, total_seats, CURRENT_TIMESTAMP
FROM incoming_election_party_results
ON CONFLICT (election_id, party_code) DO UPDATE SET
    votes = EXCLUDED.votes,
    local_seats = EXCLUDED.local_seats,
    regional_list_seats = EXCLUDED.regional_list_seats,
    total_seats = EXCLUDED.total_seats,
    updated_at = EXCLUDED.updated_at
WHERE ROW(
    election_party_results.votes, election_party_results.local_seats,
    election_party_results.regional_list_seats, election_party_results.total_seats
) IS DISTINCT FROM ROW(
    EXCLUDED.votes, EXCLUDED.local_seats,
    EXCLUDED.regional_list_seats, EXCLUDED.total_seats
);

INSERT INTO election_region_party_results (
    election_id, region_code, party_code, local_seats,
    regional_list_seats, total_seats, updated_at
) SELECT
    election_id, region_code, party_code, local_seats,
    regional_list_seats, total_seats, CURRENT_TIMESTAMP
FROM incoming_election_region_party_results
ON CONFLICT (election_id, region_code, party_code) DO UPDATE SET
    local_seats = EXCLUDED.local_seats,
    regional_list_seats = EXCLUDED.regional_list_seats,
    total_seats = EXCLUDED.total_seats,
    updated_at = EXCLUDED.updated_at
WHERE ROW(
    election_region_party_results.local_seats,
    election_region_party_results.regional_list_seats,
    election_region_party_results.total_seats
) IS DISTINCT FROM ROW(
    EXCLUDED.local_seats, EXCLUDED.regional_list_seats, EXCLUDED.total_seats
);

INSERT INTO election_constituency_winners (
    election_id, constituency_code, candidate_key, candidate_name,
    party_code, votes, sort_order, updated_at
) SELECT
    election_id, constituency_code, candidate_key, candidate_name,
    party_code, votes, sort_order, CURRENT_TIMESTAMP
FROM incoming_election_constituency_winners
ON CONFLICT (election_id, constituency_code, candidate_key) DO UPDATE SET
    candidate_name = EXCLUDED.candidate_name,
    party_code = EXCLUDED.party_code,
    votes = EXCLUDED.votes,
    sort_order = EXCLUDED.sort_order,
    updated_at = EXCLUDED.updated_at
WHERE ROW(
    election_constituency_winners.candidate_name,
    election_constituency_winners.party_code,
    election_constituency_winners.votes,
    election_constituency_winners.sort_order
) IS DISTINCT FROM ROW(
    EXCLUDED.candidate_name,
    EXCLUDED.party_code,
    EXCLUDED.votes,
    EXCLUDED.sort_order
);

DO $$
DECLARE
    chamber_seats INTEGER;
    declared_seats BIGINT;
    declared_votes BIGINT;
    valid_vote_count BIGINT;
    result_status VARCHAR(16);
BEGIN
    IF EXISTS (
        SELECT 1 FROM election_party_results
         WHERE election_id = '20260000-0000-4000-8000-000000000001'
           AND party_code = 'UNKNOWN'
        UNION ALL
        SELECT 1 FROM election_region_party_results
         WHERE election_id = '20260000-0000-4000-8000-000000000001'
           AND party_code = 'UNKNOWN'
    ) THEN
        RAISE EXCEPTION 'UNKNOWN cannot hold seats in a published election snapshot';
    END IF;
    SELECT total_seats, valid_votes, status
      INTO chamber_seats, valid_vote_count, result_status
    FROM elections WHERE slug = 'legislative-2026';
    SELECT COALESCE(SUM(total_seats), 0) INTO declared_seats
    FROM election_party_results
    WHERE election_id = '20260000-0000-4000-8000-000000000001';
    SELECT COALESCE(SUM(votes), 0) INTO declared_votes
    FROM election_party_results
    WHERE election_id = '20260000-0000-4000-8000-000000000001';
    IF declared_seats > chamber_seats THEN
        RAISE EXCEPTION 'Declared seats (%) exceed chamber size (%)', declared_seats, chamber_seats;
    END IF;
    IF valid_vote_count IS NOT NULL AND declared_votes > valid_vote_count THEN
        RAISE EXCEPTION 'Declared party votes (%) exceed valid votes (%)', declared_votes, valid_vote_count;
    END IF;
    IF result_status IN ('FINAL', 'CORRECTED') AND declared_seats <> chamber_seats THEN
        RAISE EXCEPTION 'A final result must declare exactly % seats, found %', chamber_seats, declared_seats;
    END IF;
    IF valid_vote_count IS NOT NULL
       AND (
           declared_votes <> valid_vote_count
           OR EXISTS (
               SELECT 1 FROM election_party_results
                WHERE election_id = '20260000-0000-4000-8000-000000000001'
                  AND votes IS NULL
           )
       ) THEN
        RAISE EXCEPTION 'A snapshot with valid_votes must reconcile every party vote exactly';
    END IF;
END $$;

DO $$
DECLARE
    chamber_seats INTEGER;
    allocated_regional_seats BIGINT;
    all_regions_final BOOLEAN;
    result_status VARCHAR(16);
BEGIN
    SELECT total_seats, status INTO chamber_seats, result_status
    FROM elections WHERE slug = 'legislative-2026';
    IF result_status IN ('FINAL', 'CORRECTED')
       AND EXISTS (
           SELECT 1 FROM election_regions
            WHERE election_id = '20260000-0000-4000-8000-000000000001'
              AND status <> 'FINAL'
       ) THEN
        RAISE EXCEPTION 'Every region must be final before the national result is final';
    END IF;
    SELECT COALESCE(SUM(allocated_seats), 0),
           COALESCE(BOOL_AND(status = 'FINAL'), FALSE)
      INTO allocated_regional_seats, all_regions_final
    FROM election_regions
    WHERE election_id = '20260000-0000-4000-8000-000000000001';
    IF allocated_regional_seats > chamber_seats THEN
        RAISE EXCEPTION 'Regional allocations (%) exceed chamber size (%)',
            allocated_regional_seats, chamber_seats;
    END IF;
    IF (result_status IN ('FINAL', 'CORRECTED') OR all_regions_final)
       AND allocated_regional_seats <> chamber_seats THEN
        RAISE EXCEPTION 'Complete regional allocations (%) must equal chamber size (%)',
            allocated_regional_seats, chamber_seats;
    END IF;
    IF EXISTS (
        SELECT 1
          FROM election_regions region
          LEFT JOIN election_region_party_results result
            ON result.election_id = region.election_id
           AND result.region_code = region.code
         WHERE region.election_id = '20260000-0000-4000-8000-000000000001'
           AND region.status = 'FINAL'
         GROUP BY region.code, region.allocated_seats
        HAVING region.allocated_seats IS NULL
            OR COALESCE(SUM(result.total_seats), 0) <> region.allocated_seats
    ) THEN
        RAISE EXCEPTION 'Every final region must reconcile exactly with its allocated seats';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM election_region_party_results regional
        LEFT JOIN election_party_results national
          ON national.election_id = regional.election_id
         AND national.party_code = regional.party_code
        WHERE regional.election_id = '20260000-0000-4000-8000-000000000001'
        GROUP BY regional.party_code,
                 national.local_seats,
                 national.regional_list_seats,
                 national.total_seats
        HAVING SUM(regional.local_seats) > COALESCE(national.local_seats, -1)
            OR SUM(regional.regional_list_seats) > COALESCE(national.regional_list_seats, -1)
            OR SUM(regional.total_seats) > COALESCE(national.total_seats, -1)
    ) THEN
        RAISE EXCEPTION 'A regional party result exceeds a national seat component';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM election_constituency_winners winner
          JOIN election_constituencies constituency
            ON constituency.election_id = winner.election_id
           AND constituency.code = winner.constituency_code
          LEFT JOIN election_region_party_results regional
            ON regional.election_id = winner.election_id
           AND regional.region_code = constituency.region_code
           AND regional.party_code = winner.party_code
         WHERE winner.election_id = '20260000-0000-4000-8000-000000000001'
           AND regional.party_code IS NULL
    ) THEN
        RAISE EXCEPTION 'Every constituency winner must belong to a declared regional party result';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM election_constituency_winners winner
          JOIN election_constituencies constituency
            ON constituency.election_id = winner.election_id
           AND constituency.code = winner.constituency_code
          JOIN election_region_party_results regional
            ON regional.election_id = winner.election_id
           AND regional.region_code = constituency.region_code
           AND regional.party_code = winner.party_code
         WHERE winner.election_id = '20260000-0000-4000-8000-000000000001'
         GROUP BY constituency.region_code, winner.party_code, regional.local_seats
        HAVING COUNT(*) > regional.local_seats
    ) THEN
        RAISE EXCEPTION 'Constituency winners exceed a regional party local-seat total';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM election_constituency_winners winner
          JOIN election_party_results national
            ON national.election_id = winner.election_id
           AND national.party_code = winner.party_code
         WHERE winner.election_id = '20260000-0000-4000-8000-000000000001'
         GROUP BY winner.party_code, national.local_seats
        HAVING COUNT(*) > national.local_seats
    ) THEN
        RAISE EXCEPTION 'Constituency winners exceed a national party local-seat total';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM election_constituencies constituency
          LEFT JOIN election_constituency_winners winner
            ON winner.election_id = constituency.election_id
           AND winner.constituency_code = constituency.code
         WHERE constituency.election_id = '20260000-0000-4000-8000-000000000001'
           AND constituency.allocated_seats IS NOT NULL
         GROUP BY constituency.code, constituency.allocated_seats, constituency.status
        HAVING COUNT(winner.candidate_key) > constituency.allocated_seats
            OR (constituency.status IN ('PROVISIONAL', 'OFFICIAL')
                AND COUNT(winner.candidate_key) <> constituency.allocated_seats)
    ) THEN
        RAISE EXCEPTION 'Constituency winners do not reconcile with the constituency seat allocation';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM election_constituency_winners
         WHERE election_id = '20260000-0000-4000-8000-000000000001'
         GROUP BY candidate_key
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'A candidate cannot hold more than one constituency seat';
    END IF;
END $$;

COMMIT;
