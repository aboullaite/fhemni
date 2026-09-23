-- Canonical, replayable snapshot for Morocco's 2026 legislative election.
-- Current state: counting has started, but no official party result is recorded yet.
--
-- Update and commit this file whenever official results are loaded into production.
-- It intentionally replaces the complete 2026 result snapshot in one transaction.
-- Run with psql and ON_ERROR_STOP enabled; see docs/operations/election-results.md.

\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('fhemni:legislative-2026-results'));

INSERT INTO elections (
    id, slug, election_date, status, total_seats,
    registered_voters, votes_cast, valid_votes, vote_basis,
    source_label_ar, source_label_fr, source_label_en,
    source_url, source_updated_at, updated_at
) VALUES (
    '20260000-0000-4000-8000-000000000001', 'legislative-2026', DATE '2026-09-23', 'COUNTING', 395,
    NULL, NULL, NULL, NULL,
    'النتائج الرسمية المعلنة', 'Résultats officiels publiés', 'Published official results',
    NULL, NULL, CURRENT_TIMESTAMP
)
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
    updated_at = EXCLUDED.updated_at;

INSERT INTO election_regions (
    election_id, code, name_ar, name_fr, name_en, map_key,
    allocated_seats, status, sort_order, updated_at
) VALUES
    ('20260000-0000-4000-8000-000000000001', 'tanger-tetouan-al-hoceima', 'طنجة - تطوان - الحسيمة', 'Tanger-Tétouan-Al Hoceïma', 'Tanger-Tetouan-Al Hoceima', 'MA-01', NULL, 'PENDING', 1, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'oriental', 'الشرق', 'L''Oriental', 'Oriental', 'MA-02', NULL, 'PENDING', 2, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'fes-meknes', 'فاس - مكناس', 'Fès-Meknès', 'Fes-Meknes', 'MA-03', NULL, 'PENDING', 3, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'rabat-sale-kenitra', 'الرباط - سلا - القنيطرة', 'Rabat-Salé-Kénitra', 'Rabat-Sale-Kenitra', 'MA-04', NULL, 'PENDING', 4, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'beni-mellal-khenifra', 'بني ملال - خنيفرة', 'Béni Mellal-Khénifra', 'Beni Mellal-Khenifra', 'MA-05', NULL, 'PENDING', 5, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'الدار البيضاء - سطات', 'Casablanca-Settat', 'Casablanca-Settat', 'MA-06', NULL, 'PENDING', 6, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'marrakech-safi', 'مراكش - آسفي', 'Marrakech-Safi', 'Marrakech-Safi', 'MA-07', NULL, 'PENDING', 7, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'draa-tafilalet', 'درعة - تافيلالت', 'Drâa-Tafilalet', 'Draa-Tafilalet', 'MA-08', NULL, 'PENDING', 8, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'souss-massa', 'سوس - ماسة', 'Souss-Massa', 'Souss-Massa', 'MA-09', NULL, 'PENDING', 9, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'guelmim-oued-noun', 'كلميم - واد نون', 'Guelmim-Oued Noun', 'Guelmim-Oued Noun', 'MA-10', NULL, 'PENDING', 10, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'laayoune-sakia-el-hamra', 'العيون - الساقية الحمراء', 'Laâyoune-Sakia El Hamra', 'Laayoune-Sakia El Hamra', 'MA-11', NULL, 'PENDING', 11, CURRENT_TIMESTAMP),
    ('20260000-0000-4000-8000-000000000001', 'dakhla-oued-ed-dahab', 'الداخلة - وادي الذهب', 'Dakhla-Oued Ed-Dahab', 'Dakhla-Oued Ed-Dahab', 'MA-12', NULL, 'PENDING', 12, CURRENT_TIMESTAMP)
ON CONFLICT (election_id, code) DO UPDATE SET
    name_ar = EXCLUDED.name_ar,
    name_fr = EXCLUDED.name_fr,
    name_en = EXCLUDED.name_en,
    map_key = EXCLUDED.map_key,
    allocated_seats = EXCLUDED.allocated_seats,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = EXCLUDED.updated_at;

DELETE FROM election_region_party_results
WHERE election_id = '20260000-0000-4000-8000-000000000001';
DELETE FROM election_party_results
WHERE election_id = '20260000-0000-4000-8000-000000000001';

-- Add the complete current snapshot here before deployment-day execution.
-- National rows must satisfy total_seats = local_seats + regional_list_seats.
-- Example shape only (do not uncomment without verified official figures):
-- INSERT INTO election_party_results
--     (election_id, party_code, votes, local_seats, regional_list_seats, total_seats, updated_at)
-- VALUES
--     ('20260000-0000-4000-8000-000000000001', 'RNI', NULL, 0, 0, 0, CURRENT_TIMESTAMP);
--
-- Regional rows follow the same rule:
-- INSERT INTO election_region_party_results
--     (election_id, region_code, party_code, local_seats, regional_list_seats, total_seats, updated_at)
-- VALUES
--     ('20260000-0000-4000-8000-000000000001', 'casablanca-settat', 'RNI', 0, 0, 0, CURRENT_TIMESTAMP);

DO $$
DECLARE
    chamber_seats INTEGER;
    declared_seats BIGINT;
    result_status VARCHAR(16);
BEGIN
    SELECT total_seats, status INTO chamber_seats, result_status
    FROM elections WHERE slug = 'legislative-2026';
    SELECT COALESCE(SUM(total_seats), 0) INTO declared_seats
    FROM election_party_results
    WHERE election_id = '20260000-0000-4000-8000-000000000001';
    IF declared_seats > chamber_seats THEN
        RAISE EXCEPTION 'Declared seats (%) exceed chamber size (%)', declared_seats, chamber_seats;
    END IF;
    IF result_status IN ('FINAL', 'CORRECTED') AND declared_seats <> chamber_seats THEN
        RAISE EXCEPTION 'A final result must declare exactly % seats, found %', chamber_seats, declared_seats;
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
        GROUP BY regional.party_code, national.total_seats
        HAVING SUM(regional.total_seats) > COALESCE(national.total_seats, -1)
    ) THEN
        RAISE EXCEPTION 'A regional party total exceeds its national party total';
    END IF;
END $$;

COMMIT;
