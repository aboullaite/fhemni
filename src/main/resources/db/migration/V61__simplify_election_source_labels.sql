-- Public presentation only. Preserve the evidence timestamps, source URL and all results.
UPDATE elections
SET source_label_ar = 'elections.ma، Maroc.ma',
    source_label_fr = 'elections.ma, Maroc.ma',
    source_label_en = 'elections.ma, Maroc.ma'
WHERE slug = 'legislative-2026';
