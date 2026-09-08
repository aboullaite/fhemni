-- Provider verdict enums belong in the structured verdict column and must not
-- leak into reader-facing prose. Preserve meaning while naturalizing any
-- previously stored uppercase enum tokens in each localized text field.

UPDATE promise_assessments
SET summary_ar = REPLACE(summary_ar, 'POSSIBLE', 'قابل للتحقيق'),
    requirements_ar = REPLACE(requirements_ar, 'POSSIBLE', 'قابل للتحقيق'),
    assumptions_ar = REPLACE(assumptions_ar, 'POSSIBLE', 'قابل للتحقيق'),
    calculation_notes_ar = REPLACE(calculation_notes_ar, 'POSSIBLE', 'قابل للتحقيق');

UPDATE promise_assessments
SET summary_ar = REPLACE(summary_ar, 'HARD', 'صعيب التحقيق فـ5 سنين'),
    requirements_ar = REPLACE(requirements_ar, 'HARD', 'صعيب التحقيق فـ5 سنين'),
    assumptions_ar = REPLACE(assumptions_ar, 'HARD', 'صعيب التحقيق فـ5 سنين'),
    calculation_notes_ar = REPLACE(calculation_notes_ar, 'HARD', 'صعيب التحقيق فـ5 سنين');

UPDATE promise_assessments
SET summary_ar = REPLACE(summary_ar, 'NOT_ACHIEVABLE', 'بعيد بزاف يتحقق فـ5 سنين'),
    requirements_ar = REPLACE(requirements_ar, 'NOT_ACHIEVABLE', 'بعيد بزاف يتحقق فـ5 سنين'),
    assumptions_ar = REPLACE(assumptions_ar, 'NOT_ACHIEVABLE', 'بعيد بزاف يتحقق فـ5 سنين'),
    calculation_notes_ar = REPLACE(calculation_notes_ar, 'NOT_ACHIEVABLE', 'بعيد بزاف يتحقق فـ5 سنين');

UPDATE promise_assessments
SET summary_ar = REPLACE(summary_ar, 'INSUFFICIENT_DATA', 'المعطيات ما كافياش'),
    requirements_ar = REPLACE(requirements_ar, 'INSUFFICIENT_DATA', 'المعطيات ما كافياش'),
    assumptions_ar = REPLACE(assumptions_ar, 'INSUFFICIENT_DATA', 'المعطيات ما كافياش'),
    calculation_notes_ar = REPLACE(calculation_notes_ar, 'INSUFFICIENT_DATA', 'المعطيات ما كافياش');

UPDATE promise_assessments
SET summary_fr = REPLACE(summary_fr, 'POSSIBLE', 'réalisable'),
    requirements_fr = REPLACE(requirements_fr, 'POSSIBLE', 'réalisable'),
    assumptions_fr = REPLACE(assumptions_fr, 'POSSIBLE', 'réalisable'),
    calculation_notes_fr = REPLACE(calculation_notes_fr, 'POSSIBLE', 'réalisable');

UPDATE promise_assessments
SET summary_fr = REPLACE(summary_fr, 'HARD', 'difficile à réaliser en cinq ans'),
    requirements_fr = REPLACE(requirements_fr, 'HARD', 'difficile à réaliser en cinq ans'),
    assumptions_fr = REPLACE(assumptions_fr, 'HARD', 'difficile à réaliser en cinq ans'),
    calculation_notes_fr = REPLACE(calculation_notes_fr, 'HARD', 'difficile à réaliser en cinq ans');

UPDATE promise_assessments
SET summary_fr = REPLACE(summary_fr, 'NOT_ACHIEVABLE', 'très improbable en cinq ans'),
    requirements_fr = REPLACE(requirements_fr, 'NOT_ACHIEVABLE', 'très improbable en cinq ans'),
    assumptions_fr = REPLACE(assumptions_fr, 'NOT_ACHIEVABLE', 'très improbable en cinq ans'),
    calculation_notes_fr = REPLACE(calculation_notes_fr, 'NOT_ACHIEVABLE', 'très improbable en cinq ans');

UPDATE promise_assessments
SET summary_fr = REPLACE(summary_fr, 'INSUFFICIENT_DATA', 'données insuffisantes'),
    requirements_fr = REPLACE(requirements_fr, 'INSUFFICIENT_DATA', 'données insuffisantes'),
    assumptions_fr = REPLACE(assumptions_fr, 'INSUFFICIENT_DATA', 'données insuffisantes'),
    calculation_notes_fr = REPLACE(calculation_notes_fr, 'INSUFFICIENT_DATA', 'données insuffisantes');

UPDATE promise_assessments
SET summary_en = REPLACE(summary_en, 'POSSIBLE', 'achievable'),
    requirements_en = REPLACE(requirements_en, 'POSSIBLE', 'achievable'),
    assumptions_en = REPLACE(assumptions_en, 'POSSIBLE', 'achievable'),
    calculation_notes_en = REPLACE(calculation_notes_en, 'POSSIBLE', 'achievable');

UPDATE promise_assessments
SET summary_en = REPLACE(summary_en, 'HARD', 'difficult to achieve within five years'),
    requirements_en = REPLACE(requirements_en, 'HARD', 'difficult to achieve within five years'),
    assumptions_en = REPLACE(assumptions_en, 'HARD', 'difficult to achieve within five years'),
    calculation_notes_en = REPLACE(calculation_notes_en, 'HARD', 'difficult to achieve within five years');

UPDATE promise_assessments
SET summary_en = REPLACE(summary_en, 'NOT_ACHIEVABLE', 'very unlikely within five years'),
    requirements_en = REPLACE(requirements_en, 'NOT_ACHIEVABLE', 'very unlikely within five years'),
    assumptions_en = REPLACE(assumptions_en, 'NOT_ACHIEVABLE', 'very unlikely within five years'),
    calculation_notes_en = REPLACE(calculation_notes_en, 'NOT_ACHIEVABLE', 'very unlikely within five years');

UPDATE promise_assessments
SET summary_en = REPLACE(summary_en, 'INSUFFICIENT_DATA', 'insufficient data'),
    requirements_en = REPLACE(requirements_en, 'INSUFFICIENT_DATA', 'insufficient data'),
    assumptions_en = REPLACE(assumptions_en, 'INSUFFICIENT_DATA', 'insufficient data'),
    calculation_notes_en = REPLACE(calculation_notes_en, 'INSUFFICIENT_DATA', 'insufficient data');
