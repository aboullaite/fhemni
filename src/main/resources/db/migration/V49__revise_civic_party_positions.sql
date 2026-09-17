-- Revise civic party positions after full programme re-read (2026-09-17).
-- 23 stance corrections across 12 parties verified against original programme PDFs.

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FGD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'targeted-subsidies')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: NO_POSITION→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PPS' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'essential-tax-relief')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'UC' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'essential-tax-relief')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FFD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: OPPOSES→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FGD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'MP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: OPPOSES→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PPS' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'UC' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'USFP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FFD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'sme-jobs')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FGD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'sme-jobs')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FFD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'learning-accountability')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'learning-accountability')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PUD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-allocation')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'USFP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-allocation')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-demand')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PUD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-demand')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'USFP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-demand')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'NO_POSITION',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→NO_POSITION after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PUD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'RNI' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'UC' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';
