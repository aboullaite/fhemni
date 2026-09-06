-- These catalogue entries are Moroccan Arabic/Darija. Keep source-language
-- filtering consistent with the public `ary` language code introduced in V8.
UPDATE catalog_videos
   SET source_language = 'ary', updated_at = CURRENT_TIMESTAMP
 WHERE youtube_video_id IN (
       'NHMuJT5yIy8', '14IF32HrTBs', 'quf5ok_tkB4', '1S0VW53-gL4',
       'GDHE889Thi8', 'fKXkaIDmGxI', '6XiNYyYCgDk', '7xur381nUmY',
       'nX4ePD_KS_c', 'JujTK_nS1r0', 'pu2qF375-f0', 'v-gfotKjSI0',
       'pUaHVb5V4zk', 'SS8hBq5eGe0', 'SnL3S4B3vg8', 'TMgYfNkfF14',
       'dzMVqYw5b2U', 'B1COnlcQLs4', 'scW1dED_R28', 'n5B3boj2MFM'
   )
   AND source_language <> 'ary';

-- Values come from each video's YouTube datePublished metadata. Existing
-- manually supplied dates remain authoritative and are never overwritten.
UPDATE catalog_videos
   SET published_on = CASE youtube_video_id
       WHEN '1S0VW53-gL4' THEN DATE '2026-09-04'
       WHEN 'GDHE889Thi8' THEN DATE '2026-04-16'
       WHEN 'fKXkaIDmGxI' THEN DATE '2026-04-23'
       WHEN '6XiNYyYCgDk' THEN DATE '2026-04-30'
       WHEN '7xur381nUmY' THEN DATE '2026-05-08'
       WHEN 'nX4ePD_KS_c' THEN DATE '2026-05-14'
       WHEN 'JujTK_nS1r0' THEN DATE '2026-05-21'
       WHEN 'pu2qF375-f0' THEN DATE '2026-06-04'
       WHEN 'v-gfotKjSI0' THEN DATE '2026-06-11'
       WHEN 'pUaHVb5V4zk' THEN DATE '2026-06-18'
       WHEN 'SS8hBq5eGe0' THEN DATE '2026-08-30'
       WHEN 'SnL3S4B3vg8' THEN DATE '2026-08-31'
       WHEN 'TMgYfNkfF14' THEN DATE '2026-09-01'
       WHEN 'dzMVqYw5b2U' THEN DATE '2026-09-03'
       WHEN 'B1COnlcQLs4' THEN DATE '2026-09-04'
       WHEN 'scW1dED_R28' THEN DATE '2026-09-05'
       WHEN 'n5B3boj2MFM' THEN DATE '2026-09-02'
       ELSE published_on
       END,
       updated_at = CURRENT_TIMESTAMP
 WHERE published_on IS NULL
   AND youtube_video_id IN (
       '1S0VW53-gL4', 'GDHE889Thi8', 'fKXkaIDmGxI', '6XiNYyYCgDk',
       '7xur381nUmY', 'nX4ePD_KS_c', 'JujTK_nS1r0', 'pu2qF375-f0',
       'v-gfotKjSI0', 'pUaHVb5V4zk', 'SS8hBq5eGe0', 'SnL3S4B3vg8',
       'TMgYfNkfF14', 'dzMVqYw5b2U', 'B1COnlcQLs4', 'scW1dED_R28',
       'n5B3boj2MFM'
   );
